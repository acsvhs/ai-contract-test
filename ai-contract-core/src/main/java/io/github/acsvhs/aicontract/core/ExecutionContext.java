package io.github.acsvhs.aicontract.core;

import io.github.acsvhs.aicontract.model.ContractCase;
import io.github.acsvhs.aicontract.model.TargetResponse;
import java.nio.file.Path;

public record ExecutionContext(
        ContractCase contractCase, TargetResponse response, SecretRedactor redactor, Path contractDirectory) {
    public ExecutionContext(ContractCase contractCase, TargetResponse response, SecretRedactor redactor) {
        this(contractCase, response, redactor, Path.of(".").toAbsolutePath().normalize());
    }
}
