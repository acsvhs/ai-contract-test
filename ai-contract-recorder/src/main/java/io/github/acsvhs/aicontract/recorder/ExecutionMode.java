package io.github.acsvhs.aicontract.recorder;

import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import java.util.Locale;

public enum ExecutionMode {
    LIVE,
    RECORD,
    REPLAY;

    public static ExecutionMode parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ContractConfigurationException("Unknown execution mode '" + value + "'");
        }
    }
}
