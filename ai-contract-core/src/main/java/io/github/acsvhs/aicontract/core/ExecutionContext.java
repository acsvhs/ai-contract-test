package io.github.acsvhs.aicontract.core;

import io.github.acsvhs.aicontract.model.ContractCase;
import io.github.acsvhs.aicontract.model.TargetResponse;

public record ExecutionContext(ContractCase contractCase, TargetResponse response, SecretRedactor redactor) {}
