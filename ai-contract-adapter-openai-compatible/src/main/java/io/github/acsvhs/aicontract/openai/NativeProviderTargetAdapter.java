package io.github.acsvhs.aicontract.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.core.TargetAdapter;
import io.github.acsvhs.aicontract.http.HttpTargetAdapter;
import io.github.acsvhs.aicontract.model.AiResponseMetadata;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import io.github.acsvhs.aicontract.model.TargetResponse;
import io.github.acsvhs.aicontract.model.TokenUsage;
import io.github.acsvhs.aicontract.model.ToolCall;
import java.util.ArrayList;

/** Native, non-streaming JSON APIs. Authentication is supplied through contract headers. */
public final class NativeProviderTargetAdapter implements TargetAdapter {
    private final String provider;
    private final TargetAdapter http;
    private final ObjectMapper mapper = new ObjectMapper();

    public NativeProviderTargetAdapter(String provider) {
        this(provider, new HttpTargetAdapter());
    }

    public NativeProviderTargetAdapter(String provider, TargetAdapter http) {
        if (!java.util.Set.of("openai", "anthropic", "gemini").contains(provider)) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }
        this.provider = provider;
        this.http = http;
    }

    @Override
    public String type() {
        return provider;
    }

    @Override
    public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
        if (!"POST".equalsIgnoreCase(request.method())
                || request.body() == null
                || !request.body().isObject()) {
            throw new ContractConfigurationException(provider + " requires POST with an object request.body");
        }
        boolean validPath =
                switch (provider) {
                    case "openai" -> "/v1/chat/completions".equals(request.path());
                    case "anthropic" -> "/v1/messages".equals(request.path());
                    case "gemini" -> request.path().matches("/v1beta/models/[^/]+:generateContent");
                    default -> false;
                };
        if (!validPath) throw new ContractConfigurationException(provider + " request.path is invalid");
        var headers = new java.util.HashMap<>(target.headers());
        headers.putIfAbsent("Content-Type", "application/json");
        if (provider.equals("anthropic")) headers.putIfAbsent("anthropic-version", "2023-06-01");
        var response = http.execute(
                new TargetDefinition("http", target.baseUrl(), headers, target.maxResponseBytes()), request, timeoutMs);
        return new TargetResponse(
                response.status(),
                response.headers(),
                response.body(),
                response.durationMs(),
                normalize(response.body()));
    }

    private AiResponseMetadata normalize(String body) {
        try {
            var root = mapper.readTree(body);
            var calls = new ArrayList<ToolCall>();
            JsonNode usage;
            TokenUsage tokens;
            switch (provider) {
                case "openai" -> {
                    for (var choice : root.path("choices")) {
                        for (var call : choice.path("message").path("tool_calls")) {
                            var function = call.path("function");
                            if (function.path("name").isTextual())
                                calls.add(new ToolCall(
                                        function.path("name").asText(),
                                        function.path("arguments").asText("")));
                        }
                    }
                    usage = root.path("usage");
                    tokens = new TokenUsage(
                            number(usage, "prompt_tokens"),
                            number(usage, "completion_tokens"),
                            number(usage, "total_tokens"));
                }
                case "anthropic" -> {
                    for (var block : root.path("content")) {
                        if ("tool_use".equals(block.path("type").asText())
                                && block.path("name").isTextual())
                            calls.add(new ToolCall(block.path("name").asText(), json(block.path("input"))));
                    }
                    usage = root.path("usage");
                    Integer input = number(usage, "input_tokens");
                    Integer output = number(usage, "output_tokens");
                    tokens = new TokenUsage(input, output, input == null || output == null ? null : input + output);
                }
                case "gemini" -> {
                    for (var candidate : root.path("candidates")) {
                        for (var part : candidate.path("content").path("parts")) {
                            var call = part.path("functionCall");
                            if (call.path("name").isTextual())
                                calls.add(new ToolCall(call.path("name").asText(), json(call.path("args"))));
                        }
                    }
                    usage = root.path("usageMetadata");
                    tokens = new TokenUsage(
                            number(usage, "promptTokenCount"),
                            number(usage, "candidatesTokenCount"),
                            number(usage, "totalTokenCount"));
                }
                default -> throw new IllegalStateException(provider);
            }
            return new AiResponseMetadata(calls, tokens);
        } catch (JsonProcessingException exception) {
            return AiResponseMetadata.empty();
        }
    }

    private Integer number(JsonNode node, String field) {
        var value = node.path(field);
        return value.canConvertToInt() && value.intValue() >= 0 ? value.intValue() : null;
    }

    private String json(JsonNode value) throws JsonProcessingException {
        return value.isMissingNode() ? "{}" : mapper.writeValueAsString(value);
    }
}
