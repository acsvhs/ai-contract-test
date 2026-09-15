package io.github.acsvhs.aicontract.core;

import io.github.acsvhs.aicontract.model.AssertionResult;
import io.github.acsvhs.aicontract.model.CaseResult;
import io.github.acsvhs.aicontract.model.ContractSuite;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.nio.file.Path;
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
        return run(suite, Path.of(".").toAbsolutePath().normalize().resolve("contract.yaml"));
    }

    public SuiteResult run(ContractSuite suite, Path contractFile) {
        var adapter = adapters.get(suite.target().type());
        if (adapter == null) {
            throw new ContractConfigurationException(
                    "No adapter registered for target type '" + suite.target().type() + "'");
        }
        var absoluteContractFile = contractFile.toAbsolutePath().normalize();
        var contractDirectory = absoluteContractFile.getParent();
        if (contractDirectory == null) {
            contractDirectory = absoluteContractFile;
        }
        var caseResults = new ArrayList<CaseResult>();
        for (var contractCase : suite.cases()) {
            var response = adapter.execute(
                    suite.target(), contractCase.request(), suite.suite().effectiveTimeoutMs(), contractCase.id());
            var assertionResults = new ArrayList<AssertionResult>();
            var context = new ExecutionContext(contractCase, response, redactor, contractDirectory);
            for (var definition : contractCase.assertions()) {
                var assertion = assertions.get(definition.type());
                if (assertion == null) {
                    throw new ContractConfigurationException(
                            "No assertion registered for type '" + definition.type() + "'");
                }
                assertionResults.add(sanitize(assertion.evaluate(definition, context)));
            }
            var passed = assertionResults.stream().allMatch(AssertionResult::passed);
            caseResults.add(new CaseResult(
                    redactor.redact(contractCase.id()), passed, response.durationMs(), assertionResults));
        }
        return new SuiteResult(redactor.redact(suite.suite().name()), caseResults);
    }

    private AssertionResult sanitize(AssertionResult result) {
        return new AssertionResult(
                redactor.redact(result.type()),
                result.passed(),
                redactor.redact(result.expected()),
                redactor.redact(result.actual()),
                redactor.redact(result.message()));
    }
}
