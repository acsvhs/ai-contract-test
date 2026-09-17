package io.github.acsvhs.aicontract.core.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ContractExecutionException;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Explicit OpenAI-compatible embedding or chat judge evaluation. */
public final class EvaluationAssertion implements ContractAssertion {
    private final String type;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvaluationAssertion(String type) {
        this(type, HttpClient.newHttpClient());
    }

    public EvaluationAssertion(String type, HttpClient client) {
        if (!List.of("semanticSimilarity", "llmJudge").contains(type))
            throw new IllegalArgumentException("Unknown evaluation type: " + type);
        this.type = type;
        this.client = client;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        try {
            String actual = context.response().body();
            var responsePath = definition.parameter("responsePath");
            if (responsePath != null) {
                Object extracted = JsonPath.read(actual, responsePath.asText());
                actual = extracted instanceof String text ? text : mapper.writeValueAsString(extracted);
            }
            String expected = definition.parameter("expected").asText();
            double minimum = definition.parameter("minimum").asDouble();
            double score = type.equals("semanticSimilarity")
                    ? similarity(definition, expected, actual)
                    : judge(definition, expected, actual);
            if (!Double.isFinite(score) || score < -1 || score > 1) {
                throw new ContractExecutionException("Evaluator returned an invalid score", null);
            }
            String formatted = String.format(java.util.Locale.ROOT, "%.6f", score);
            return new AssertionResult(
                    type,
                    score >= minimum,
                    ">= " + minimum,
                    formatted,
                    score >= minimum ? "" : "Evaluation score below minimum");
        } catch (ContractExecutionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ContractExecutionException("Evaluation failed: " + exception.getMessage(), exception);
        }
    }

    private double similarity(AssertionDefinition definition, String expected, String actual) throws Exception {
        var response = post(
                definition,
                Map.of("model", definition.parameter("model").asText(), "input", List.of(expected, actual)));
        var data = response.path("data");
        if (!data.isArray() || data.size() != 2) throw new IllegalArgumentException("Expected two embeddings");
        var first = vector(data.get(0).path("embedding"));
        var second = vector(data.get(1).path("embedding"));
        if (first.size() != second.size() || first.isEmpty())
            throw new IllegalArgumentException("Embedding size mismatch");
        double dot = 0, left = 0, right = 0;
        for (int index = 0; index < first.size(); index++) {
            dot += first.get(index) * second.get(index);
            left += first.get(index) * first.get(index);
            right += second.get(index) * second.get(index);
        }
        if (left == 0 || right == 0) throw new IllegalArgumentException("Zero embedding vector");
        return dot / Math.sqrt(left * right);
    }

    private double judge(AssertionDefinition definition, String expected, String actual) throws Exception {
        var input = mapper.writeValueAsString(Map.of("expected", expected, "actual", actual));
        var response = post(
                definition,
                Map.of(
                        "model",
                        definition.parameter("model").asText(),
                        "messages",
                        List.of(
                                Map.of(
                                        "role",
                                        "system",
                                        "content",
                                        "Rate semantic correctness from 0 to 1. Return only JSON with a numeric score field."),
                                Map.of("role", "user", "content", input))));
        var content =
                response.path("choices").path(0).path("message").path("content").asText();
        var result = mapper.readTree(content).path("score");
        if (!result.isNumber() || result.asDouble() < 0 || result.asDouble() > 1)
            throw new IllegalArgumentException("Judge score must be between 0 and 1");
        return result.asDouble();
    }

    private JsonNode post(AssertionDefinition definition, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(
                        URI.create(definition.parameter("endpoint").asText()))
                .timeout(Duration.ofMillis(
                        definition.parameter("timeoutMs") == null
                                ? 10000
                                : definition.parameter("timeoutMs").asInt()))
                .header("Content-Type", "application/json");
        var headers = definition.parameter("headers");
        if (headers != null)
            headers.properties()
                    .forEach(entry ->
                            builder.header(entry.getKey(), entry.getValue().asText()));
        var request = builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            var bytes = stream.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new IllegalArgumentException("Evaluator response exceeded 1 MiB");
            if (response.statusCode() != 200)
                throw new IllegalArgumentException("Evaluator returned HTTP " + response.statusCode());
            return mapper.readTree(bytes);
        }
    }

    private List<Double> vector(JsonNode node) {
        if (!node.isArray()) throw new IllegalArgumentException("Missing embedding vector");
        var result = new ArrayList<Double>();
        for (var item : node) {
            if (!item.isNumber() || !Double.isFinite(item.asDouble()))
                throw new IllegalArgumentException("Invalid embedding value");
            result.add(item.asDouble());
        }
        return result;
    }
}
