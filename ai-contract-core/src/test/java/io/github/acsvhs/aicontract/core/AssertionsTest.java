package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.assertion.ContainsAssertion;
import io.github.acsvhs.aicontract.core.assertion.HttpStatusAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxLatencyAssertion;
import io.github.acsvhs.aicontract.core.assertion.RegexAbsentAssertion;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.ContractCase;
import io.github.acsvhs.aicontract.model.TargetResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssertionsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutionContext context = new ExecutionContext(
            new ContractCase("case", null, List.of(), null, List.of()),
            new TargetResponse(200, Map.of(), "safe response", 12),
            new DefaultSecretRedactor(List.of()));

    @Test
    void evaluatesAllPhaseOneAssertions() throws Exception {
        assertTrue(new HttpStatusAssertion()
                .evaluate(definition("{\"type\":\"httpStatus\",\"equals\":200}"), context)
                .passed());
        assertTrue(new ContainsAssertion()
                .evaluate(definition("{\"type\":\"contains\",\"value\":\"safe\"}"), context)
                .passed());
        assertTrue(new RegexAbsentAssertion()
                .evaluate(definition("{\"type\":\"regexAbsent\",\"patterns\":[\"password\"]}"), context)
                .passed());
        assertTrue(new MaxLatencyAssertion()
                .evaluate(definition("{\"type\":\"maxLatency\",\"milliseconds\":20}"), context)
                .passed());
        assertFalse(new HttpStatusAssertion()
                .evaluate(definition("{\"type\":\"httpStatus\",\"equals\":201}"), context)
                .passed());
        assertTrue(new HttpStatusAssertion()
                .evaluate(definition("{\"type\":\"httpStatus\",\"oneOf\":[200,204]}"), context)
                .passed());
    }

    @Test
    void reportsSanitizedAndTruncatedContainsFailures() throws Exception {
        var body = "api_key=secret-value " + "x".repeat(600);
        var unsafeContext = context(new TargetResponse(200, Map.of(), body, 12));
        var result = new ContainsAssertion()
                .evaluate(definition("{\"type\":\"contains\",\"value\":\"missing\"}"), unsafeContext);
        assertFalse(result.passed());
        assertFalse(result.actual().contains("secret-value"));
        assertTrue(result.actual().length() <= 500);
    }

    @Test
    void reportsRegexAndLatencyFailures() throws Exception {
        var regex = new RegexAbsentAssertion()
                .evaluate(definition("{\"type\":\"regexAbsent\",\"patterns\":[\"safe\"]}"), context);
        var latency = new MaxLatencyAssertion()
                .evaluate(definition("{\"type\":\"maxLatency\",\"milliseconds\":11}"), context);
        assertFalse(regex.passed());
        assertEquals("matched: [REDACTED]", regex.actual());
        assertFalse(latency.passed());
        assertEquals("12 ms", latency.actual());
    }

    @Test
    void rejectsAStatusOutsideTheAllowedSet() throws Exception {
        var result = new HttpStatusAssertion()
                .evaluate(definition("{\"type\":\"httpStatus\",\"oneOf\":[201,204]}"), context);
        assertFalse(result.passed());
        assertEquals("[201,204]", result.expected());
    }

    private ExecutionContext context(TargetResponse response) {
        return new ExecutionContext(
                new ContractCase("case", null, List.of(), null, List.of()),
                response,
                new DefaultSecretRedactor(List.of()));
    }

    private AssertionDefinition definition(String json) throws Exception {
        return mapper.readValue(json, AssertionDefinition.class);
    }
}
