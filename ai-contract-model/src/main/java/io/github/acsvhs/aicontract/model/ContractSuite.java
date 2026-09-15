package io.github.acsvhs.aicontract.model;

import java.util.List;
import java.util.Map;

public record ContractSuite(
        String version,
        SuiteDefinition suite,
        Map<String, String> variables,
        TargetDefinition target,
        List<ContractCase> cases) {
    public ContractSuite {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
