package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;

public final class HttpStatusAssertion implements ContractAssertion {
    @Override
    public String type() {
        return "httpStatus";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        int actual = context.response().status();
        var equals = definition.parameter("equals");
        var oneOf = definition.parameter("oneOf");
        boolean passed = equals != null
                ? equals.asInt() == actual
                : java.util.stream.StreamSupport.stream(oneOf.spliterator(), false)
                        .anyMatch(status -> status.asInt() == actual);
        var expected = equals != null ? equals.asText() : oneOf.toString();
        return passed
                ? AssertionResult.passed(type())
                : AssertionResult.failed(type(), expected, Integer.toString(actual), "Unexpected HTTP status");
    }
}
