package io.github.acsvhs.aicontract.model;

/** Token counts reported by a target; absent values remain unknown rather than zero. */
public record TokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
    public static TokenUsage unknown() {
        return new TokenUsage(null, null, null);
    }
}
