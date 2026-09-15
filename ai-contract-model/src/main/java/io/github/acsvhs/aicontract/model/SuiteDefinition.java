package io.github.acsvhs.aicontract.model;

import java.util.List;

public record SuiteDefinition(String name, String description, Integer defaultTimeoutMs, List<String> tags) {
    public SuiteDefinition {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public int effectiveTimeoutMs() {
        return defaultTimeoutMs == null ? 3000 : defaultTimeoutMs;
    }
}
