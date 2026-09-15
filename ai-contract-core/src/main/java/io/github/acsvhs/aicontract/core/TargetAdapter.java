package io.github.acsvhs.aicontract.core;

import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import io.github.acsvhs.aicontract.model.TargetResponse;

public interface TargetAdapter {
    String type();

    TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs);

    default TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs, String caseId) {
        return execute(target, request, timeoutMs);
    }
}
