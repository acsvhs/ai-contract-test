package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.assertion.HttpStatusAssertion;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import io.github.acsvhs.aicontract.model.ContractCase;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.ContractSuite;
import io.github.acsvhs.aicontract.model.SuiteDefinition;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import io.github.acsvhs.aicontract.model.TargetResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractRunnerTest {
    @Test
    void runsCasesSequentiallyAndKeepsFailures() throws Exception {
        var assertion =
                new ObjectMapper().readValue("{\"type\":\"httpStatus\",\"equals\":200}", AssertionDefinition.class);
        var cases = List.of(contractCase("one", assertion), contractCase("two", assertion));
        var order = new java.util.ArrayList<String>();
        TargetAdapter adapter = new TargetAdapter() {
            @Override
            public String type() {
                return "http";
            }

            @Override
            public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
                order.add(request.path());
                return new TargetResponse(order.size() == 1 ? 200 : 500, Map.of(), "", 1);
            }
        };
        var suite = new ContractSuite(
                "1",
                new SuiteDefinition("demo", null, 100, List.of()),
                Map.of(),
                new TargetDefinition("http", "http://localhost", Map.of()),
                cases);
        var result =
                new ContractRunner(List.of(adapter), List.of(new HttpStatusAssertion()), value -> value).run(suite);
        assertEquals(List.of("/one", "/two"), order);
        assertFalse(result.passed());
    }

    @Test
    void sanitizesEveryAssertionResultBeforeReporting() throws Exception {
        var definition = new ObjectMapper().readValue("{\"type\":\"unsafe\"}", AssertionDefinition.class);
        var suite = new ContractSuite(
                "1",
                new SuiteDefinition("suite super-secret-value", null, 100, List.of()),
                Map.of(),
                new TargetDefinition("http", "http://localhost", Map.of()),
                List.of(contractCase("case super-secret-value", definition)));
        TargetAdapter adapter = new TargetAdapter() {
            @Override
            public String type() {
                return "http";
            }

            @Override
            public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
                return new TargetResponse(200, Map.of("Authorization", List.of("Bearer header-secret")), "", 1);
            }
        };
        ContractAssertion assertion = new ContractAssertion() {
            @Override
            public String type() {
                return "unsafe";
            }

            @Override
            public AssertionResult evaluate(AssertionDefinition ignored, ExecutionContext context) {
                return AssertionResult.failed(
                        "unsafe", "password=expected-secret", "api_key=actual-secret", "super-secret-value");
            }
        };

        var result = new ContractRunner(
                        List.of(adapter), List.of(assertion), new DefaultSecretRedactor(List.of("super-secret-value")))
                .run(suite);
        var serialized = new ObjectMapper().writeValueAsString(result);
        assertFalse(serialized.contains("super-secret-value"));
        assertFalse(serialized.contains("expected-secret"));
        assertFalse(serialized.contains("actual-secret"));
    }

    private ContractCase contractCase(String id, AssertionDefinition assertion) {
        return new ContractCase(
                id,
                null,
                List.of(),
                new ContractRequest("GET", "/" + id, Map.of(), Map.of(), null),
                List.of(assertion));
    }
}
