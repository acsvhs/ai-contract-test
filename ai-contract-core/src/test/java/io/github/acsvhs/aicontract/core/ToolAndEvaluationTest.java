package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.acsvhs.aicontract.core.assertion.EvaluationAssertion;
import io.github.acsvhs.aicontract.core.assertion.ToolContractAssertion;
import io.github.acsvhs.aicontract.model.AiResponseMetadata;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.ContractCase;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetResponse;
import io.github.acsvhs.aicontract.model.TokenUsage;
import io.github.acsvhs.aicontract.model.ToolCall;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolAndEvaluationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void checksToolPresenceArgumentsOrderAndCount() throws Exception {
        var response = new TargetResponse(
                200,
                Map.of(),
                "{}",
                1,
                new AiResponseMetadata(
                        List.of(new ToolCall("search", "{\"query\":\"A\"}"), new ToolCall("answer", "{}")),
                        TokenUsage.unknown()));
        var context = context(response);
        assertTrue(tool("{\"type\":\"toolCalled\",\"name\":\"search\"}", context));
        assertFalse(tool("{\"type\":\"toolNotCalled\",\"name\":\"search\"}", context));
        assertTrue(tool("{\"type\":\"toolArgs\",\"name\":\"search\",\"path\":\"$.query\",\"equals\":\"A\"}", context));
        assertTrue(tool("{\"type\":\"toolCallOrder\",\"names\":[\"search\",\"answer\"]}", context));
        assertFalse(tool("{\"type\":\"maxToolCalls\",\"maximum\":1}", context));
    }

    @Test
    void scoresEmbeddingsAndJudgeResponses() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/embeddings",
                exchange -> respond(exchange, "{\"data\":[{\"embedding\":[1,0]},{\"embedding\":[0.8,0.6]}]}"));
        server.createContext(
                "/judge",
                exchange -> respond(exchange, "{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":0.75}\"}}]}"));
        server.start();
        try {
            var context = context(new TargetResponse(200, Map.of(), "{\"answer\":\"hello\"}", 1));
            var base = "http://127.0.0.1:" + server.getAddress().getPort();
            var similarity = definition("semanticSimilarity", base + "/embeddings", 0.7);
            var judge = definition("llmJudge", base + "/judge", 0.8);
            assertTrue(new EvaluationAssertion("semanticSimilarity")
                    .evaluate(similarity, context)
                    .passed());
            assertEquals(
                    "0.800000",
                    new EvaluationAssertion("semanticSimilarity")
                            .evaluate(similarity, context)
                            .actual());
            assertFalse(
                    new EvaluationAssertion("llmJudge").evaluate(judge, context).passed());
        } finally {
            server.stop(0);
        }
    }

    private AssertionDefinition definition(String type, String endpoint, double minimum) throws Exception {
        return mapper.readValue(
                mapper.writeValueAsString(Map.of(
                        "type",
                        type,
                        "expected",
                        "hello",
                        "minimum",
                        minimum,
                        "endpoint",
                        endpoint,
                        "model",
                        "test",
                        "responsePath",
                        "$.answer")),
                AssertionDefinition.class);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private boolean tool(String json, ExecutionContext context) throws Exception {
        var definition = mapper.readValue(json, AssertionDefinition.class);
        return new ToolContractAssertion(definition.type())
                .evaluate(definition, context)
                .passed();
    }

    private ExecutionContext context(TargetResponse response) {
        return new ExecutionContext(
                new ContractCase(
                        "case", null, List.of(), new ContractRequest("GET", "/", Map.of(), Map.of(), null), List.of()),
                response,
                value -> value);
    }
}
