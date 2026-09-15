package io.github.acsvhs.aicontract.model;

import java.util.Map;

public record TargetDefinition(String type, String baseUrl, Map<String, String> headers) {
    public TargetDefinition {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
