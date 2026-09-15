package io.github.acsvhs.aicontract.core;

public final class ContractConfigurationException extends RuntimeException {
    public ContractConfigurationException(String message) {
        super(message);
    }

    public ContractConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
