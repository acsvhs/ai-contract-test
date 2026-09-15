package io.github.acsvhs.aicontract.core.assertion;

import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MaxEstimatedCostAssertion implements ContractAssertion {
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    @Override
    public String type() {
        return "maxEstimatedCost";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        var usage = context.response().metadata().tokenUsage();
        var currency = definition.parameter("currency").asText();
        var maximum = definition.parameter("maximum").decimalValue();
        if (usage.inputTokens() == null || usage.outputTokens() == null) {
            return AssertionResult.failed(
                    type(),
                    "<= " + maximum + " " + currency,
                    "unavailable",
                    "Target did not report input and output token usage");
        }
        var inputRate = definition.parameter("inputCostPerMillionTokens").decimalValue();
        var outputRate = definition.parameter("outputCostPerMillionTokens").decimalValue();
        var actual = inputRate
                .multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(outputRate.multiply(BigDecimal.valueOf(usage.outputTokens())))
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return actual.compareTo(maximum) <= 0
                ? AssertionResult.passed(type())
                : AssertionResult.failed(
                        type(),
                        "<= " + maximum + " " + currency,
                        actual + " " + currency,
                        "Estimated cost exceeded maximum");
    }
}
