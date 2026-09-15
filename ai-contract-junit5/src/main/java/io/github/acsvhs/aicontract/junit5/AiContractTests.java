package io.github.acsvhs.aicontract.junit5;

import io.github.acsvhs.aicontract.core.ContractParser;
import io.github.acsvhs.aicontract.core.ContractRunner;
import io.github.acsvhs.aicontract.core.DefaultSecretRedactor;
import io.github.acsvhs.aicontract.core.assertion.ContainsAssertion;
import io.github.acsvhs.aicontract.core.assertion.HttpStatusAssertion;
import io.github.acsvhs.aicontract.core.assertion.JsonPathAssertion;
import io.github.acsvhs.aicontract.core.assertion.JsonSchemaAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxLatencyAssertion;
import io.github.acsvhs.aicontract.core.assertion.RegexAbsentAssertion;
import io.github.acsvhs.aicontract.http.HttpTargetAdapter;
import io.github.acsvhs.aicontract.model.ContractSuite;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public final class AiContractTests {
    private AiContractTests() {}

    public static Stream<DynamicTest> from(Path contractFile) {
        return from(contractFile, Map.of());
    }

    public static Stream<DynamicTest> from(Path contractFile, Map<String, String> variables) {
        var normalizedFile = contractFile.toAbsolutePath().normalize();
        var contract = new ContractParser().parse(normalizedFile, variables);
        return contract.cases().stream()
                .map(contractCase -> DynamicTest.dynamicTest(
                        contract.suite().name() + " / " + contractCase.id(),
                        () -> executeCase(contract, contractFile, contractCase)));
    }

    private static void executeCase(
            ContractSuite contract, Path contractFile, io.github.acsvhs.aicontract.model.ContractCase contractCase) {
        var singleCase = new ContractSuite(
                contract.version(), contract.suite(), contract.variables(), contract.target(), List.of(contractCase));
        var redactor = new DefaultSecretRedactor(secretVariableValues(contract.variables()));
        var result = runner(redactor).run(singleCase, contractFile);
        if (!result.passed()) {
            var failures = result.cases().getFirst().assertions().stream()
                    .filter(assertion -> !assertion.passed())
                    .map(assertion -> assertion.type()
                            + ": "
                            + assertion.message()
                            + " (expected "
                            + assertion.expected()
                            + ", actual "
                            + assertion.actual()
                            + ")")
                    .toList();
            throw new AssertionError("AI contract failed: " + String.join("; ", failures));
        }
    }

    private static ContractRunner runner(DefaultSecretRedactor redactor) {
        return new ContractRunner(
                List.of(new HttpTargetAdapter()),
                List.of(
                        new HttpStatusAssertion(),
                        new ContainsAssertion(),
                        new RegexAbsentAssertion(),
                        new MaxLatencyAssertion(),
                        new JsonSchemaAssertion(),
                        new JsonPathAssertion()),
                redactor);
    }

    private static List<String> secretVariableValues(Map<String, String> variables) {
        return variables.entrySet().stream()
                .filter(entry -> entry.getKey().matches("(?i).*(KEY|TOKEN|SECRET|PASSWORD).*$"))
                .map(Map.Entry::getValue)
                .toList();
    }
}
