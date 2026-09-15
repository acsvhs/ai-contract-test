package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import java.util.regex.Pattern;

public final class RegexAbsentAssertion implements ContractAssertion {
    @Override
    public String type() {
        return "regexAbsent";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        for (var patternNode : definition.parameter("patterns")) {
            var patternText = patternNode.asText();
            if (Pattern.compile(patternText).matcher(context.response().body()).find()) {
                return AssertionResult.failed(
                        type(),
                        "pattern absent: " + patternText,
                        "matched: [REDACTED]",
                        "Forbidden response pattern was present");
            }
        }
        return AssertionResult.passed(type());
    }
}
