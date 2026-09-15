package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import java.util.HashSet;

public final class AllowedToolCallsAssertion implements ContractAssertion {
    @Override
    public String type() {
        return "allowedToolCalls";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        var allowed = new HashSet<String>();
        definition.parameter("names").forEach(node -> allowed.add(node.asText()));
        var actual = context.response().metadata().toolCalls().stream()
                .map(call -> call.name())
                .toList();
        var unexpected = actual.stream()
                .filter(name -> !allowed.contains(name))
                .distinct()
                .toList();
        return unexpected.isEmpty()
                ? AssertionResult.passed(type())
                : AssertionResult.failed(
                        type(),
                        allowed.toString(),
                        unexpected.toString(),
                        "Response contained a tool call that is not allowed");
    }
}
