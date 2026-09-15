package io.github.acsvhs.aicontract.model;

import java.util.List;

/** Provider-neutral metadata extracted from an AI response. */
public record AiResponseMetadata(List<ToolCall> toolCalls, TokenUsage tokenUsage) {
    public AiResponseMetadata {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        tokenUsage = tokenUsage == null ? TokenUsage.unknown() : tokenUsage;
    }

    public static AiResponseMetadata empty() {
        return new AiResponseMetadata(List.of(), TokenUsage.unknown());
    }
}
