package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;

public final class MaxLatencyAssertion implements ContractAssertion {
    @Override
    public String type() {
        return "maxLatency";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        if (context.response().metadata().replayed()) {
            return AssertionResult.passed(type());
        }
        long expected = definition.parameter("milliseconds").asLong();
        long actual = context.response().durationMs();
        return actual <= expected
                ? AssertionResult.passed(type())
                : AssertionResult.failed(
                        type(), "<= " + expected + " ms", actual + " ms", "Response exceeded maximum latency");
    }
}
