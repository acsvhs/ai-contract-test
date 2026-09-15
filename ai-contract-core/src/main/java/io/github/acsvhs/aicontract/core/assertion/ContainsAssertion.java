package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;

public final class ContainsAssertion implements ContractAssertion {
    private static final int ACTUAL_LIMIT = 500;

    @Override
    public String type() {
        return "contains";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        var expected = definition.parameter("value").asText();
        var body = context.response().body();
        if (body.contains(expected)) {
            return AssertionResult.passed(type());
        }
        var actual = body.substring(0, Math.min(body.length(), ACTUAL_LIMIT));
        return AssertionResult.failed(
                type(), expected, context.redactor().redact(actual), "Response body did not contain expected text");
    }
}
