package io.github.acsvhs.aicontract.core;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public final class DefaultSecretRedactor implements SecretRedactor {
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(authorization|api[_-]?key|token|password)(\\s*[:=]\\s*)(?:bearer\\s+)?[^\\s,;\\\"}]+");
    private final List<String> secretValues;

    public DefaultSecretRedactor(Collection<String> secretValues) {
        this.secretValues = secretValues.stream()
                .filter(value -> value != null && value.length() >= 4)
                .toList();
    }

    @Override
    public String redact(String value) {
        if (value == null) {
            return "";
        }
        var redacted = value;
        for (var secret : secretValues) {
            redacted = redacted.replace(secret, "[REDACTED]");
        }
        return NAMED_SECRET.matcher(redacted).replaceAll("$1$2[REDACTED]");
    }
}
