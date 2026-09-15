package io.github.acsvhs.aicontract.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.ContractSuite;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ContractValidator {
    private static final Set<String> ASSERTIONS =
            Set.of("httpStatus", "contains", "regexAbsent", "maxLatency", "jsonSchema", "jsonPath");
    private static final Map<String, Set<String>> ASSERTION_PARAMETERS = Map.of(
            "httpStatus", Set.of("equals", "oneOf"),
            "contains", Set.of("value"),
            "regexAbsent", Set.of("patterns"),
            "maxLatency", Set.of("milliseconds"),
            "jsonSchema", Set.of("file"),
            "jsonPath", Set.of("path", "exists", "equals"));

    public void validate(ContractSuite contract, Path file) {
        var errors = new java.util.ArrayList<String>();
        if (contract == null) {
            errors.add("$: document must not be empty");
        } else {
            required("version", contract.version(), errors);
            if (!"1".equals(contract.version())) {
                errors.add("version: only version \"1\" is supported");
            }
            if (contract.suite() == null) {
                errors.add("suite: is required");
            } else {
                required("suite.name", contract.suite().name(), errors);
                if (contract.suite().effectiveTimeoutMs() <= 0) {
                    errors.add("suite.defaultTimeoutMs: must be greater than zero");
                }
            }
            validateTarget(contract, errors);
            validateCases(contract, errors);
        }
        if (!errors.isEmpty()) {
            throw new ContractConfigurationException(
                    file.toAbsolutePath().normalize() + ": " + String.join("; ", errors));
        }
    }

    private void validateTarget(ContractSuite contract, java.util.List<String> errors) {
        if (contract.target() == null) {
            errors.add("target: is required");
            return;
        }
        if (!Set.of("http", "openai-compatible").contains(contract.target().type())) {
            errors.add("target.type: unsupported adapter '" + contract.target().type() + "'");
        }
        try {
            var uri = URI.create(contract.target().baseUrl());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                errors.add("target.baseUrl: must be an absolute HTTP(S) URL");
            }
        } catch (RuntimeException exception) {
            errors.add("target.baseUrl: must be an absolute HTTP(S) URL");
        }
    }

    private void validateCases(ContractSuite contract, java.util.List<String> errors) {
        if (contract.cases().isEmpty()) {
            errors.add("cases: at least one case is required");
        }
        var ids = new HashSet<String>();
        for (int index = 0; index < contract.cases().size(); index++) {
            var item = contract.cases().get(index);
            var prefix = "cases[" + index + "]";
            required(prefix + ".id", item.id(), errors);
            if (item.id() != null && !ids.add(item.id())) {
                errors.add(prefix + ".id: duplicate case ID '" + item.id() + "'");
            }
            if (item.request() == null) {
                errors.add(prefix + ".request: is required");
            } else if (item.request().path() == null || !item.request().path().startsWith("/")) {
                errors.add(prefix + ".request.path: must start with '/'");
            } else if ("openai-compatible".equals(contract.target().type())
                    && !"/v1/chat/completions".equals(item.request().path())) {
                errors.add(prefix + ".request.path: openai-compatible targets require '/v1/chat/completions'");
            }
            if (item.assertions().isEmpty()) {
                errors.add(prefix + ".assertions: at least one assertion is required");
            }
            for (int assertionIndex = 0; assertionIndex < item.assertions().size(); assertionIndex++) {
                validateAssertion(
                        item.assertions().get(assertionIndex), prefix + ".assertions[" + assertionIndex + "]", errors);
            }
        }
    }

    private void validateAssertion(AssertionDefinition definition, String path, java.util.List<String> errors) {
        if (definition.type() == null || !ASSERTIONS.contains(definition.type())) {
            errors.add(path + ".type: unsupported assertion '" + definition.type() + "'");
            return;
        }
        var unexpected = definition.parameters().keySet().stream()
                .filter(parameter ->
                        !ASSERTION_PARAMETERS.get(definition.type()).contains(parameter))
                .sorted()
                .toList();
        if (!unexpected.isEmpty()) {
            errors.add(path + ": unsupported parameters " + unexpected + " for '" + definition.type() + "'");
        }
        switch (definition.type()) {
            case "httpStatus" -> validateHttpStatus(definition, path, errors);
            case "contains" -> requireText(definition.parameter("value"), path + ".value", errors);
            case "regexAbsent" -> {
                var patterns = definition.parameter("patterns");
                if (patterns == null || !patterns.isArray() || patterns.isEmpty()) {
                    errors.add(path + ".patterns: must be a non-empty array");
                } else {
                    for (var pattern : patterns) {
                        try {
                            java.util.regex.Pattern.compile(pattern.asText());
                        } catch (java.util.regex.PatternSyntaxException exception) {
                            errors.add(path + ".patterns: invalid regular expression '" + pattern.asText() + "'");
                        }
                    }
                }
            }
            case "maxLatency" -> requireInteger(definition.parameter("milliseconds"), path + ".milliseconds", errors);
            case "jsonSchema" -> requireText(definition.parameter("file"), path + ".file", errors);
            case "jsonPath" -> validateJsonPath(definition, path, errors);
            default -> throw new IllegalStateException("validated assertion was not handled");
        }
    }

    private void validateJsonPath(AssertionDefinition definition, String path, java.util.List<String> errors) {
        var expression = definition.parameter("path");
        requireText(expression, path + ".path", errors);
        if (expression != null && expression.isTextual()) {
            try {
                JsonPath.compile(expression.asText());
            } catch (InvalidPathException exception) {
                errors.add(path + ".path: invalid JSONPath '" + expression.asText() + "'");
            }
        }
        var exists = definition.parameter("exists");
        var equals = definition.parameter("equals");
        if ((exists == null) == (equals == null)) {
            errors.add(path + ": requires exactly one of 'exists' or 'equals'");
        } else if (exists != null && !exists.isBoolean()) {
            errors.add(path + ".exists: must be a boolean");
        }
    }

    private void validateHttpStatus(AssertionDefinition definition, String path, java.util.List<String> errors) {
        var equals = definition.parameter("equals");
        var oneOf = definition.parameter("oneOf");
        if ((equals == null) == (oneOf == null)) {
            errors.add(path + ": requires exactly one of 'equals' or 'oneOf'");
        } else if (equals != null) {
            requireStatus(equals, path + ".equals", errors);
        } else if (!oneOf.isArray() || oneOf.isEmpty()) {
            errors.add(path + ".oneOf: must be a non-empty array of HTTP status codes");
        } else {
            for (var status : oneOf) {
                requireStatus(status, path + ".oneOf", errors);
            }
        }
    }

    private void requireStatus(JsonNode value, String path, java.util.List<String> errors) {
        if (value == null || !value.isIntegralNumber() || value.asInt() < 100 || value.asInt() > 599) {
            errors.add(path + ": must contain HTTP status codes from 100 to 599");
        }
    }

    private void required(String path, String value, java.util.List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(path + ": is required");
        }
    }

    private void requireInteger(JsonNode value, String path, java.util.List<String> errors) {
        if (value == null || !value.isIntegralNumber() || value.asLong() < 0) {
            errors.add(path + ": must be a non-negative integer");
        }
    }

    private void requireText(JsonNode value, String path, java.util.List<String> errors) {
        if (value == null || !value.isTextual()) {
            errors.add(path + ": must be a string");
        }
    }
}
