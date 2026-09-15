package io.github.acsvhs.aicontract.openai;

import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.core.TargetAdapter;
import io.github.acsvhs.aicontract.http.HttpTargetAdapter;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import io.github.acsvhs.aicontract.model.TargetResponse;

/** Executes the provider-neutral subset of the OpenAI-compatible chat completions protocol. */
public final class OpenAiCompatibleTargetAdapter implements TargetAdapter {
    public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private final TargetAdapter httpAdapter;

    public OpenAiCompatibleTargetAdapter() {
        this(new HttpTargetAdapter());
    }

    OpenAiCompatibleTargetAdapter(TargetAdapter httpAdapter) {
        this.httpAdapter = httpAdapter;
    }

    @Override
    public String type() {
        return "openai-compatible";
    }

    @Override
    public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
        validate(request);
        var httpTarget = new TargetDefinition("http", target.baseUrl(), target.headers(), target.maxResponseBytes());
        return httpAdapter.execute(httpTarget, request, timeoutMs);
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
