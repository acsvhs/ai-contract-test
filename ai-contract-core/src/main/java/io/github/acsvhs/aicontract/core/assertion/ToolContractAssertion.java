package io.github.acsvhs.aicontract.core.assertion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import java.util.ArrayList;
import java.util.List;

/** Assertions over the provider-neutral, ordered tool call list. */
public final class ToolContractAssertion implements ContractAssertion {
    private final String type;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolContractAssertion(String type) {
        if (!List.of("toolCalled", "toolNotCalled", "toolArgs", "toolCallOrder", "maxToolCalls")
                .contains(type)) {
            throw new IllegalArgumentException("Unsupported tool assertion: " + type);
        }
        this.type = type;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        var calls = context.response().metadata().toolCalls();
        var names = calls.stream().map(call -> call.name()).toList();
        return switch (type) {
            case "toolCalled" -> {
                var name = definition.parameter("name").asText();
                yield names.contains(name)
                        ? AssertionResult.passed(type)
                        : AssertionResult.failed(type, name, names.toString(), "Required tool was not called");
            }
            case "toolNotCalled" -> {
                var name = definition.parameter("name").asText();
                yield !names.contains(name)
                        ? AssertionResult.passed(type)
                        : AssertionResult.failed(type, "no " + name, names.toString(), "Forbidden tool was called");
            }
            case "toolArgs" -> checkArguments(definition, calls);
            case "toolCallOrder" -> checkOrder(definition, names);
            case "maxToolCalls" -> {
                int maximum = definition.parameter("maximum").asInt();
                yield calls.size() <= maximum
                        ? AssertionResult.passed(type)
                        : AssertionResult.failed(
                                type, "<= " + maximum, Integer.toString(calls.size()), "Too many tool calls");
            }
            default -> throw new IllegalStateException(type);
        };
    }

    private AssertionResult checkArguments(
            AssertionDefinition definition, List<io.github.acsvhs.aicontract.model.ToolCall> calls) {
        var name = definition.parameter("name").asText();
        var path = definition.parameter("path").asText();
        var expected = definition.parameter("equals");
        var actuals = new ArrayList<String>();
        for (var call : calls) {
            if (!name.equals(call.name())) continue;
            try {
                var arguments = mapper.readTree(call.arguments());
                Object value = JsonPath.read(arguments.toString(), path);
                var actual = mapper.valueToTree(value);
                actuals.add(actual.toString());
                if (expected.equals(actual)) return AssertionResult.passed(type);
            } catch (Exception exception) {
                actuals.add("unavailable");
            }
        }
        return AssertionResult.failed(
                type, expected.toString(), actuals.toString(), "No matching tool call had the expected argument value");
    }

    private AssertionResult checkOrder(AssertionDefinition definition, List<String> actual) {
        var expected = new ArrayList<String>();
        definition.parameter("names").forEach(node -> expected.add(node.asText()));
        int position = 0;
        for (var name : actual) {
            if (position < expected.size() && expected.get(position).equals(name)) position++;
        }
        return position == expected.size()
                ? AssertionResult.passed(type)
                : AssertionResult.failed(
                        type, expected.toString(), actual.toString(), "Required tool call order was not observed");
    }
}
