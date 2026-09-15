package io.github.acsvhs.aicontract.model;

import java.util.Map;

public record TargetDefinition(String type, String baseUrl, Map<String, String> headers, Integer maxResponseBytes) {
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;

    public TargetDefinition {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public TargetDefinition(String type, String baseUrl, Map<String, String> headers) {
        this(type, baseUrl, headers, null);
    }

    public int effectiveMaxResponseBytes() {
        return maxResponseBytes == null ? DEFAULT_MAX_RESPONSE_BYTES : maxResponseBytes;
    }
}
