package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.assertion.AllowedToolCallsAssertion;
import io.github.acsvhs.aicontract.core.assertion.ContainsAssertion;
import io.github.acsvhs.aicontract.core.assertion.ForbiddenToolCallsAssertion;
import io.github.acsvhs.aicontract.core.assertion.HttpStatusAssertion;
import io.github.acsvhs.aicontract.core.assertion.JsonPathAssertion;
import io.github.acsvhs.aicontract.core.assertion.JsonSchemaAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxEstimatedCostAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxLatencyAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxTokensAssertion;
import io.github.acsvhs.aicontract.core.assertion.RegexAbsentAssertion;
import io.github.acsvhs.aicontract.model.AiResponseMetadata;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.ContractCase;
import io.github.acsvhs.aicontract.model.TargetResponse;
import io.github.acsvhs.aicontract.model.TokenUsage;
import io.github.acsvhs.aicontract.model.ToolCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssertionsTest {
    @TempDir
    private java.nio.file.Path temporaryDirectory;

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

    @Test
    void evaluatesJsonPathPresenceAndEquality() throws Exception {
        var jsonContext =
                context(new TargetResponse(200, Map.of(), "{\"answer\":{\"text\":\"safe\"},\"items\":[1,2]}", 12));
        var assertion = new JsonPathAssertion();

        assertTrue(assertion
                .evaluate(
                        definition("{\"type\":\"jsonPath\",\"path\":\"$.answer.text\",\"equals\":\"safe\"}"),
                        jsonContext)
                .passed());
        assertTrue(assertion
                .evaluate(definition("{\"type\":\"jsonPath\",\"path\":\"$.missing\",\"exists\":false}"), jsonContext)
                .passed());
        assertFalse(assertion
                .evaluate(definition("{\"type\":\"jsonPath\",\"path\":\"$.items[0]\",\"equals\":2}"), jsonContext)
                .passed());
        assertFalse(assertion
                .evaluate(definition("{\"type\":\"jsonPath\",\"path\":\"$.missing\",\"exists\":true}"), jsonContext)
                .passed());
    }

    @Test
    void evaluatesAContractRelativeJsonSchema() throws Exception {
        java.nio.file.Files.writeString(
                temporaryDirectory.resolve("response.schema.json"),
                "{\"type\":\"object\",\"required\":[\"answer\"],\"properties\":{\"answer\":{\"type\":\"string\"}}}");
        var assertion = new JsonSchemaAssertion();
        var passingContext =
                context(new TargetResponse(200, Map.of(), "{\"answer\":\"safe\"}", 12), temporaryDirectory);
        var failingContext = context(new TargetResponse(200, Map.of(), "{\"answer\":12}", 12), temporaryDirectory);
        var schema = definition("{\"type\":\"jsonSchema\",\"file\":\"response.schema.json\"}");

        assertTrue(assertion.evaluate(schema, passingContext).passed());
        assertFalse(assertion.evaluate(schema, failingContext).passed());
        assertFalse(assertion
                .evaluate(schema, context(new TargetResponse(200, Map.of(), "not-json", 12), temporaryDirectory))
                .passed());
    }

    @Test
    void rejectsUnsafeJsonSchemaResources() throws Exception {
        java.nio.file.Files.writeString(
                temporaryDirectory.resolve("remote.schema.json"), "{\"$ref\":\"https://example.invalid/schema.json\"}");
        var assertion = new JsonSchemaAssertion();
        var localContext = context(new TargetResponse(200, Map.of(), "{}", 12), temporaryDirectory);

        org.junit.jupiter.api.Assertions.assertThrows(
                ContractConfigurationException.class,
                () -> assertion.evaluate(
                        definition("{\"type\":\"jsonSchema\",\"file\":\"../outside.schema.json\"}"), localContext));
        org.junit.jupiter.api.Assertions.assertThrows(
                ContractConfigurationException.class,
                () -> assertion.evaluate(
                        definition("{\"type\":\"jsonSchema\",\"file\":\"remote.schema.json\"}"), localContext));
    }

    @Test
    void evaluatesAllowedAndForbiddenToolCalls() throws Exception {
        var aiContext = context(new TargetResponse(
                200,
                Map.of(),
                "{}",
                12,
                new AiResponseMetadata(
                        List.of(new ToolCall("lookupOrder", "{}"), new ToolCall("deleteCustomer", "{}")),
                        TokenUsage.unknown())));

        assertFalse(new AllowedToolCallsAssertion()
                .evaluate(definition("{\"type\":\"allowedToolCalls\",\"names\":[\"lookupOrder\"]}"), aiContext)
                .passed());
        assertFalse(new ForbiddenToolCallsAssertion()
                .evaluate(definition("{\"type\":\"forbiddenToolCalls\",\"names\":[\"deleteCustomer\"]}"), aiContext)
                .passed());
        assertTrue(new ForbiddenToolCallsAssertion()
                .evaluate(definition("{\"type\":\"forbiddenToolCalls\",\"names\":[\"exportDatabase\"]}"), aiContext)
                .passed());
    }

    @Test
    void evaluatesTokenAndUserConfiguredCostLimits() throws Exception {
        var aiContext = context(new TargetResponse(
                200, Map.of(), "{}", 12, new AiResponseMetadata(List.of(), new TokenUsage(1_000, 500, 1_500))));

        assertTrue(new MaxTokensAssertion()
                .evaluate(definition("{\"type\":\"maxTokens\",\"maximum\":1500}"), aiContext)
                .passed());
        assertFalse(new MaxTokensAssertion()
                .evaluate(definition("{\"type\":\"maxTokens\",\"maximum\":1499}"), aiContext)
                .passed());
        var cost = definition(
                """
                {"type":"maxEstimatedCost","maximum":0.0019,"inputCostPerMillionTokens":1.0,"outputCostPerMillionTokens":2.0,"currency":"USD"}
                """);
        assertFalse(new MaxEstimatedCostAssertion().evaluate(cost, aiContext).passed());
    }

    @Test
    void failsMetricsAssertionsWhenUsageIsUnavailable() throws Exception {
        var tokens = new MaxTokensAssertion().evaluate(definition("{\"type\":\"maxTokens\",\"maximum\":10}"), context);
        var cost = new MaxEstimatedCostAssertion()
                .evaluate(
                        definition(
                                """
                                {"type":"maxEstimatedCost","maximum":1,"inputCostPerMillionTokens":1,"outputCostPerMillionTokens":1,"currency":"EUR"}
                                """),
                        context);

        assertFalse(tokens.passed());
        assertEquals("unavailable", tokens.actual());
        assertFalse(cost.passed());
        assertEquals("unavailable", cost.actual());
    }

    private ExecutionContext context(TargetResponse response) {
        return context(response, java.nio.file.Path.of(".").toAbsolutePath().normalize());
    }

    private ExecutionContext context(TargetResponse response, java.nio.file.Path contractDirectory) {
        return new ExecutionContext(
                new ContractCase("case", null, List.of(), null, List.of()),
                response,
                new DefaultSecretRedactor(List.of()),
                contractDirectory);
    }

    private AssertionDefinition definition(String json) throws Exception {
        return mapper.readValue(json, AssertionDefinition.class);
    }
}
