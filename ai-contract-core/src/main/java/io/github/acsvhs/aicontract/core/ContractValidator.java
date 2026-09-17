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
    private static final Set<String> ASSERTIONS = Set.of(
            "httpStatus",
            "contains",
            "regexAbsent",
            "maxLatency",
            "jsonSchema",
            "jsonPath",
            "allowedToolCalls",
            "forbiddenToolCalls",
            "toolCalled",
            "toolNotCalled",
            "toolArgs",
            "toolCallOrder",
            "maxToolCalls",
            "semanticSimilarity",
            "llmJudge",
            "maxTokens",
            "maxEstimatedCost",
            "secretLeak",
            "piiLeak");
    private static final Map<String, Set<String>> ASSERTION_PARAMETERS = Map.ofEntries(
            Map.entry("httpStatus", Set.of("equals", "oneOf")),
            Map.entry("contains", Set.of("value")),
            Map.entry("regexAbsent", Set.of("patterns")),
            Map.entry("maxLatency", Set.of("milliseconds")),
            Map.entry("jsonSchema", Set.of("file")),
            Map.entry("jsonPath", Set.of("path", "exists", "equals")),
            Map.entry("allowedToolCalls", Set.of("names")),
            Map.entry("forbiddenToolCalls", Set.of("names")),
            Map.entry("toolCalled", Set.of("name")),
            Map.entry("toolNotCalled", Set.of("name")),
            Map.entry("toolArgs", Set.of("name", "path", "equals")),
            Map.entry("toolCallOrder", Set.of("names")),
            Map.entry("maxToolCalls", Set.of("maximum")),
            Map.entry(
                    "semanticSimilarity",
                    Set.of("expected", "minimum", "endpoint", "model", "headers", "responsePath", "timeoutMs")),
            Map.entry(
                    "llmJudge",
                    Set.of("expected", "minimum", "endpoint", "model", "headers", "responsePath", "timeoutMs")),
            Map.entry("maxTokens", Set.of("maximum")),
            Map.entry("secretLeak", Set.of("patterns")),
            Map.entry("piiLeak", Set.of("patterns")),
            Map.entry(
                    "maxEstimatedCost",
                    Set.of("maximum", "inputCostPerMillionTokens", "outputCostPerMillionTokens", "currency")));

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
        if (contract.target().type() == null
                || !Set.of("http", "openai-compatible", "openai", "anthropic", "gemini")
                        .contains(contract.target().type())) {
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
            if (item.effectiveRepeat() < 1 || item.effectiveRepeat() > 1000) {
                errors.add(prefix + ".repeat: must be between 1 and 1000");
            }
            if (!Double.isFinite(item.effectiveMinimumPassRate())
                    || item.effectiveMinimumPassRate() < 0
                    || item.effectiveMinimumPassRate() > 1) {
                errors.add(prefix + ".minimumPassRate: must be between 0 and 1");
            }
            if (item.request() == null) {
                errors.add(prefix + ".request: is required");
            } else if (item.request().path() == null || !item.request().path().startsWith("/")) {
                errors.add(prefix + ".request.path: must start with '/'");
            } else {
                validateRequest(contract.target().type(), item.request(), prefix + ".request", errors);
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

    private void validateRequest(
            String targetType,
            io.github.acsvhs.aicontract.model.ContractRequest request,
            String path,
            java.util.List<String> errors) {
        if (request.path().contains("?") || request.path().contains("#")) {
            errors.add(path + ".path: put query parameters in request.query and omit fragments");
        }
        if (request.method() != null && !request.method().matches("[A-Za-z]+")) {
            errors.add(path + ".method: must contain only letters");
        }
        String requiredPath = targetType == null
                ? null
                : switch (targetType) {
                    case "openai", "openai-compatible" -> "/v1/chat/completions";
                    case "anthropic" -> "/v1/messages";
                    default -> null;
                };
        if (requiredPath != null && !requiredPath.equals(request.path())) {
            errors.add(path + ".path: " + targetType + " targets require '" + requiredPath + "'");
        }
        if ("gemini".equals(targetType) && !request.path().matches("/v1beta/models/[^/]+:generateContent")) {
            errors.add(path + ".path: gemini targets require '/v1beta/models/{model}:generateContent'");
        }
        if (targetType == null
                || !Set.of("openai", "openai-compatible", "anthropic", "gemini").contains(targetType)) return;
        if (!"POST".equalsIgnoreCase(request.method())) errors.add(path + ".method: " + targetType + " requires POST");
        var body = request.body();
        if (body == null || !body.isObject()) {
            errors.add(path + ".body: " + targetType + " requires an object");
            return;
        }
        if ("gemini".equals(targetType)) {
            if (!body.path("contents").isArray() || body.path("contents").isEmpty())
                errors.add(path + ".body.contents: must be a non-empty array");
        } else {
            if (!body.path("model").isTextual() || body.path("model").asText().isBlank())
                errors.add(path + ".body.model: must be a non-empty string");
            if (!body.path("messages").isArray() || body.path("messages").isEmpty())
                errors.add(path + ".body.messages: must be a non-empty array");
            if ("anthropic".equals(targetType)
                    && (!body.path("max_tokens").isIntegralNumber()
                            || body.path("max_tokens").asInt() < 1))
                errors.add(path + ".body.max_tokens: must be a positive integer");
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
            case "allowedToolCalls", "forbiddenToolCalls" -> requireNames(definition.parameter("names"), path, errors);
            case "toolCalled", "toolNotCalled" -> requireNonBlankText(
                    definition.parameter("name"), path + ".name", errors);
            case "toolArgs" -> {
                requireNonBlankText(definition.parameter("name"), path + ".name", errors);
                requireNonBlankText(definition.parameter("path"), path + ".path", errors);
                if (definition.parameter("equals") == null) errors.add(path + ".equals: is required");
                if (definition.parameter("path") != null
                        && definition.parameter("path").isTextual()) {
                    try {
                        JsonPath.compile(definition.parameter("path").asText());
                    } catch (InvalidPathException exception) {
                        errors.add(path + ".path: invalid JSONPath");
                    }
                }
            }
            case "toolCallOrder" -> {
                requireNames(definition.parameter("names"), path, errors);
                if (definition.parameter("names") != null
                        && definition.parameter("names").isArray()
                        && definition.parameter("names").isEmpty()) errors.add(path + ".names: must be non-empty");
            }
            case "maxToolCalls" -> requireInteger(definition.parameter("maximum"), path + ".maximum", errors);
            case "semanticSimilarity", "llmJudge" -> validateEvaluation(definition, path, errors);
            case "secretLeak", "piiLeak" -> requirePatterns(definition.parameter("patterns"), path, errors);
            case "maxTokens" -> requireInteger(definition.parameter("maximum"), path + ".maximum", errors);
            case "maxEstimatedCost" -> {
                requireNumber(definition.parameter("maximum"), path + ".maximum", errors);
                requireNumber(
                        definition.parameter("inputCostPerMillionTokens"), path + ".inputCostPerMillionTokens", errors);
                requireNumber(
                        definition.parameter("outputCostPerMillionTokens"),
                        path + ".outputCostPerMillionTokens",
                        errors);
                requireNonBlankText(definition.parameter("currency"), path + ".currency", errors);
            }
            default -> throw new IllegalStateException("validated assertion was not handled");
        }
    }

    private void validateEvaluation(AssertionDefinition definition, String path, java.util.List<String> errors) {
        requireText(definition.parameter("expected"), path + ".expected", errors);
        requireNonBlankText(definition.parameter("model"), path + ".model", errors);
        var minimum = definition.parameter("minimum");
        if (minimum == null || !minimum.isNumber() || minimum.asDouble() < 0 || minimum.asDouble() > 1) {
            errors.add(path + ".minimum: must be between 0 and 1");
        }
        var endpoint = definition.parameter("endpoint");
        requireNonBlankText(endpoint, path + ".endpoint", errors);
        if (endpoint != null && endpoint.isTextual()) {
            try {
                var uri = URI.create(endpoint.asText());
                if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
                    errors.add(path + ".endpoint: must be an absolute HTTP(S) URL");
            } catch (RuntimeException exception) {
                errors.add(path + ".endpoint: must be an absolute HTTP(S) URL");
            }
        }
        var responsePath = definition.parameter("responsePath");
        if (responsePath != null) {
            requireNonBlankText(responsePath, path + ".responsePath", errors);
            if (responsePath.isTextual()) {
                try {
                    JsonPath.compile(responsePath.asText());
                } catch (InvalidPathException exception) {
                    errors.add(path + ".responsePath: invalid JSONPath");
                }
            }
        }
        var timeout = definition.parameter("timeoutMs");
        if (timeout != null && (!timeout.isIntegralNumber() || timeout.asInt() < 1))
            errors.add(path + ".timeoutMs: must be positive");
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

    private void requireNonBlankText(JsonNode value, String path, java.util.List<String> errors) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            errors.add(path + ": must be a non-empty string");
        }
    }

    private void requireNumber(JsonNode value, String path, java.util.List<String> errors) {
        if (value == null || !value.isNumber() || value.decimalValue().signum() < 0) {
            errors.add(path + ": must be a non-negative number");
        }
    }

    private void requireNames(JsonNode value, String path, java.util.List<String> errors) {
        if (value == null || !value.isArray()) {
            errors.add(path + ".names: must be an array");
            return;
        }
        for (var name : value) {
            requireNonBlankText(name, path + ".names", errors);
        }
    }

    private void requirePatterns(JsonNode value, String path, java.util.List<String> errors) {
        if (value == null || !value.isArray() || value.isEmpty()) {
            errors.add(path + ".patterns: must be a non-empty array");
            return;
        }
        for (var pattern : value) {
            requireNonBlankText(pattern, path + ".patterns", errors);
            if (pattern.isTextual()) {
                try {
                    java.util.regex.Pattern.compile(pattern.asText());
                } catch (java.util.regex.PatternSyntaxException exception) {
                    errors.add(path + ".patterns: invalid regular expression '" + pattern.asText() + "'");
                }
            }
        }
    }
}
