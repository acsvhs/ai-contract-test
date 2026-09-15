package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;

public final class MaxTokensAssertion implements ContractAssertion {
    @Override
    public String type() {
        return "maxTokens";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        int maximum = definition.parameter("maximum").asInt();
        var actual = context.response().metadata().tokenUsage().totalTokens();
        if (actual == null) {
            return AssertionResult.failed(
                    type(), "<= " + maximum, "unavailable", "Target did not report total token usage");
        }
        return actual <= maximum
                ? AssertionResult.passed(type())
                : AssertionResult.failed(
                        type(), "<= " + maximum, actual.toString(), "Response exceeded maximum tokens");
    }
}
