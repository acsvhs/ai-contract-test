package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import java.util.HashSet;

public final class ForbiddenToolCallsAssertion implements ContractAssertion {
    @Override
    public String type() {
        return "forbiddenToolCalls";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        var forbidden = new HashSet<String>();
        definition.parameter("names").forEach(node -> forbidden.add(node.asText()));
        var detected = context.response().metadata().toolCalls().stream()
                .map(call -> call.name())
                .filter(forbidden::contains)
                .distinct()
                .toList();
        return detected.isEmpty()
                ? AssertionResult.passed(type())
                : AssertionResult.failed(
                        type(),
                        "none of " + forbidden,
                        detected.toString(),
                        "Response contained a forbidden tool call");
    }
}
