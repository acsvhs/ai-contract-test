package io.github.acsvhs.aicontract.core;

import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;

public interface ContractAssertion {
    String type();

    AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context);
}
