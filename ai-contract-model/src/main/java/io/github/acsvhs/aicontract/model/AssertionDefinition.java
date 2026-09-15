package io.github.acsvhs.aicontract.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AssertionDefinition {
    private String type;
    private final Map<String, JsonNode> parameters = new LinkedHashMap<>();

    public String type() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonAnySetter
    public void parameter(String name, JsonNode value) {
        parameters.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> parameters() {
        return Map.copyOf(parameters);
    }

    public JsonNode parameter(String name) {
        return parameters.get(name);
    }
}
