package io.github.acsvhs.aicontract.recorder;

import io.github.acsvhs.aicontract.model.TokenUsage;
import io.github.acsvhs.aicontract.model.ToolCall;
import java.util.List;

record Cassette(
        String formatVersion,
        String caseId,
        String requestFingerprint,
        int status,
        String body,
        List<ToolCall> toolCalls,
        TokenUsage tokenUsage,
        long recordedDurationMs) {
    Cassette {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        tokenUsage = tokenUsage == null ? TokenUsage.unknown() : tokenUsage;
    }
}
