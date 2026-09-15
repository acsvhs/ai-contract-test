package io.github.acsvhs.aicontract.core.assertion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;

public final class JsonPathAssertion implements ContractAssertion {
    private static final int ACTUAL_LIMIT = 500;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String type() {
        return "jsonPath";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        var path = definition.parameter("path").asText();
        final Object document;
        try {
            document = mapper.readValue(context.response().body(), Object.class);
        } catch (JsonProcessingException exception) {
            return AssertionResult.failed(type(), path, "invalid JSON", "Response body was not valid JSON");
        }

        final Object actual;
        try {
            actual = JsonPath.read(document, path);
        } catch (PathNotFoundException exception) {
            return missingResult(definition, path);
        } catch (InvalidPathException exception) {
            throw new ContractConfigurationException("Invalid JSONPath '" + path + "'", exception);
        }

        var exists = definition.parameter("exists");
        if (exists != null) {
            return exists.asBoolean()
                    ? AssertionResult.passed(type())
                    : AssertionResult.failed(type(), "path absent", compact(actual), "JSONPath matched a value");
        }
        var expected = definition.parameter("equals");
        var actualNode = mapper.valueToTree(actual);
        return expected.equals(actualNode)
                ? AssertionResult.passed(type())
                : AssertionResult.failed(
                        type(), expected.toString(), compact(actual), "JSONPath value did not equal expected JSON");
    }

    private AssertionResult missingResult(AssertionDefinition definition, String path) {
        var exists = definition.parameter("exists");
        if (exists != null && !exists.asBoolean()) {
            return AssertionResult.passed(type());
        }
        return AssertionResult.failed(
                type(),
                exists == null ? definition.parameter("equals").toString() : "path present",
                "path absent",
                "JSONPath did not match a value: " + path);
    }

    private String compact(Object actual) {
        var value = mapper.valueToTree(actual).toString();
        return value.substring(0, Math.min(value.length(), ACTUAL_LIMIT));
    }
}
