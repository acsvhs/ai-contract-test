package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import java.util.regex.Pattern;

public final class SecretLeakAssertion implements ContractAssertion {
    @Override
    public String type() {
        return "secretLeak";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        for (var configured : definition.parameter("patterns")) {
            if (Pattern.compile(configured.asText())
                    .matcher(context.response().body())
                    .find()) {
                return AssertionResult.failed(
                        type(), "no configured secret pattern", "[REDACTED MATCH]", "Potential secret leak detected");
            }
        }
        return AssertionResult.passed(type());
    }
}
