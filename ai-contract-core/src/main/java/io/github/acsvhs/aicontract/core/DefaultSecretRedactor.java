package io.github.acsvhs.aicontract.core;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public final class DefaultSecretRedactor implements SecretRedactor {
    private static final Pattern SENSITIVE_HEADER = Pattern.compile(
            "(?i)([\\\"']?(?:authorization|proxy[_-]?authorization|cookie|set-cookie)[\\\"']?\\s*[:=]\\s*)[^\\r\\n}]+");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)([\\\"']?(?:api[_-]?key|access[_-]?token|token|secret|password)[\\\"']?\\s*[:=]\\s*[\\[\\\"']?)(?:bearer\\s+)?[^\\s,;\\\"'}\\]]+");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(\\bbearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern PRIVATE_KEY =
            Pattern.compile("(?s)-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----.*?-----END (?:[A-Z ]+ )?PRIVATE KEY-----");
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
        var redacted = PRIVATE_KEY.matcher(value).replaceAll("[REDACTED PRIVATE KEY]");
        redacted = SENSITIVE_HEADER.matcher(redacted).replaceAll("$1[REDACTED]");
        for (var secret : secretValues) {
            redacted = redacted.replace(secret, "[REDACTED]");
        }
        redacted = NAMED_SECRET.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = BEARER_TOKEN.matcher(redacted).replaceAll("$1[REDACTED]");
        return JWT.matcher(redacted).replaceAll("[REDACTED JWT]");
    }
}
