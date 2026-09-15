package io.github.acsvhs.aicontract.core;

import io.github.acsvhs.aicontract.model.AssertionResult;
import io.github.acsvhs.aicontract.model.CaseResult;
import io.github.acsvhs.aicontract.model.ContractSuite;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ContractRunner {
    private final Map<String, TargetAdapter> adapters;
    private final Map<String, ContractAssertion> assertions;
    private final SecretRedactor redactor;

    public ContractRunner(List<TargetAdapter> adapters, List<ContractAssertion> assertions, SecretRedactor redactor) {
        this.adapters =
                adapters.stream().collect(Collectors.toUnmodifiableMap(TargetAdapter::type, Function.identity()));
        this.assertions =
                assertions.stream().collect(Collectors.toUnmodifiableMap(ContractAssertion::type, Function.identity()));
        this.redactor = redactor;
    }

    public SuiteResult run(ContractSuite suite) {
        var adapter = adapters.get(suite.target().type());
        if (adapter == null) {
            throw new ContractConfigurationException(
                    "No adapter registered for target type '" + suite.target().type() + "'");
        }
        var caseResults = new ArrayList<CaseResult>();
        for (var contractCase : suite.cases()) {
            var response = adapter.execute(
                    suite.target(), contractCase.request(), suite.suite().effectiveTimeoutMs());
            var assertionResults = new ArrayList<AssertionResult>();
            var context = new ExecutionContext(contractCase, response, redactor);
            for (var definition : contractCase.assertions()) {
                var assertion = assertions.get(definition.type());
                if (assertion == null) {
                    throw new ContractConfigurationException(
                            "No assertion registered for type '" + definition.type() + "'");
                }
                assertionResults.add(assertion.evaluate(definition, context));
            }
            var passed = assertionResults.stream().allMatch(AssertionResult::passed);
            caseResults.add(new CaseResult(contractCase.id(), passed, response.durationMs(), assertionResults));
        }
        return new SuiteResult(suite.suite().name(), caseResults);
    }
}
