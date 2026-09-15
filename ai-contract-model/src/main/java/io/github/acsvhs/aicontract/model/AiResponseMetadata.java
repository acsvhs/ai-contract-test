package io.github.acsvhs.aicontract.model;

import java.util.List;

/** Provider-neutral metadata extracted from an AI response. */
public record AiResponseMetadata(List<ToolCall> toolCalls, TokenUsage tokenUsage, boolean replayed) {
    public AiResponseMetadata {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        tokenUsage = tokenUsage == null ? TokenUsage.unknown() : tokenUsage;
    }

    public AiResponseMetadata(List<ToolCall> toolCalls, TokenUsage tokenUsage) {
        this(toolCalls, tokenUsage, false);
    }

    public static AiResponseMetadata empty() {
        return new AiResponseMetadata(List.of(), TokenUsage.unknown(), false);
    }
}
