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

/** Executes the provider-neutral subset of the OpenAI-compatible chat completions protocol. */
public final class OpenAiCompatibleTargetAdapter implements TargetAdapter {
    public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private final TargetAdapter httpAdapter;
    private final ObjectMapper mapper;

    public OpenAiCompatibleTargetAdapter() {
        this(new HttpTargetAdapter(), new ObjectMapper());
    }

    OpenAiCompatibleTargetAdapter(TargetAdapter httpAdapter, ObjectMapper mapper) {
        this.httpAdapter = httpAdapter;
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "openai-compatible";
    }

    @Override
    public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
        validate(request);
        var httpTarget = new TargetDefinition("http", target.baseUrl(), target.headers(), target.maxResponseBytes());
        var response = httpAdapter.execute(httpTarget, request, timeoutMs);
        return new TargetResponse(
                response.status(),
                response.headers(),
                response.body(),
                response.durationMs(),
                normalize(response.body()));
    }

    private AiResponseMetadata normalize(String responseBody) {
        try {
            var root = mapper.readTree(responseBody);
            var toolCalls = new ArrayList<ToolCall>();
            var choices = root.path("choices");
            if (choices.isArray()) {
                for (var choice : choices) {
                    var calls = choice.path("message").path("tool_calls");
                    if (calls.isArray()) {
                        for (var call : calls) {
                            var function = call.path("function");
                            var name = textOrEmpty(function.get("name"));
                            if (!name.isEmpty()) {
                                toolCalls.add(new ToolCall(name, textOrEmpty(function.get("arguments"))));
                            }
                        }
                    }
                }
            }
            var usage = root.path("usage");
            var tokenUsage = new TokenUsage(
                    integerOrNull(usage.get("prompt_tokens")),
                    integerOrNull(usage.get("completion_tokens")),
                    integerOrNull(usage.get("total_tokens")));
            return new AiResponseMetadata(toolCalls, tokenUsage);
        } catch (JsonProcessingException exception) {
            return AiResponseMetadata.empty();
        }
    }

    private String textOrEmpty(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : "";
    }

    private Integer integerOrNull(JsonNode node) {
        return node != null && node.canConvertToInt() ? node.intValue() : null;
    }

    private void validate(ContractRequest request) {
        if (!"POST".equalsIgnoreCase(request.method())) {
            throw new ContractConfigurationException("OpenAI-compatible chat completions require request.method POST");
        }
        if (!CHAT_COMPLETIONS_PATH.equals(request.path())) {
            throw new ContractConfigurationException(
                    "OpenAI-compatible chat completions require request.path " + CHAT_COMPLETIONS_PATH);
        }
        var body = request.body();
        if (body == null || !body.isObject()) {
            throw new ContractConfigurationException("OpenAI-compatible request.body must be an object");
        }
        var model = body.get("model");
        if (model == null || !model.isTextual() || model.asText().isBlank()) {
            throw new ContractConfigurationException("OpenAI-compatible request.body.model must be a non-empty string");
        }
        var messages = body.get("messages");
        if (messages == null || !messages.isArray() || messages.isEmpty()) {
            throw new ContractConfigurationException(
                    "OpenAI-compatible request.body.messages must be a non-empty array");
        }
    }
}
