package io.github.acsvhs.aicontract.recorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.core.ContractExecutionException;
import io.github.acsvhs.aicontract.core.SecretRedactor;
import io.github.acsvhs.aicontract.core.TargetAdapter;
import io.github.acsvhs.aicontract.model.AiResponseMetadata;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import io.github.acsvhs.aicontract.model.TargetResponse;
import io.github.acsvhs.aicontract.model.ToolCall;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Adds deterministic, sanitized record/replay behavior to any target adapter. */
public final class RecordingTargetAdapter implements TargetAdapter {
    private static final String FORMAT_VERSION = "1";
    private static final Pattern SENSITIVE_HEADER =
            Pattern.compile("(?i)(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api-key)");
    private final TargetAdapter delegate;
    private final ExecutionMode mode;
    private final Path cassetteDirectory;
    private final SecretRedactor redactor;
    private final ObjectMapper mapper;

    public RecordingTargetAdapter(
            TargetAdapter delegate, ExecutionMode mode, Path cassetteDirectory, SecretRedactor redactor) {
        this(delegate, mode, cassetteDirectory, redactor, new ObjectMapper());
    }

    RecordingTargetAdapter(
            TargetAdapter delegate,
            ExecutionMode mode,
            Path cassetteDirectory,
            SecretRedactor redactor,
            ObjectMapper mapper) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.cassetteDirectory = cassetteDirectory.toAbsolutePath().normalize();
        this.redactor = java.util.Objects.requireNonNull(redactor, "redactor");
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return delegate.type();
    }

    @Override
    public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
        return execute(target, request, timeoutMs, "default");
    }

    @Override
    public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs, String caseId) {
        return switch (mode) {
            case LIVE -> delegate.execute(target, request, timeoutMs, caseId);
            case RECORD -> record(
                    caseId, fingerprint(target, request), delegate.execute(target, request, timeoutMs, caseId));
            case REPLAY -> replay(caseId, fingerprint(target, request));
        };
    }

    private TargetResponse record(String caseId, String fingerprint, TargetResponse response) {
        try {
            Files.createDirectories(cassetteDirectory);
            if (Files.isSymbolicLink(cassetteDirectory)) {
                throw new ContractConfigurationException("Cassette directory must not be a symbolic link");
            }
            var file = cassetteFile(caseId);
            if (Files.isSymbolicLink(file)) {
                throw new ContractConfigurationException("Cassette file must not be a symbolic link: " + file);
            }
            var safeCalls = response.metadata().toolCalls().stream()
                    .map(call -> new ToolCall(redactor.redact(call.name()), redactor.redact(call.arguments())))
                    .toList();
            var cassette = new Cassette(
                    FORMAT_VERSION,
                    redactor.redact(caseId),
                    fingerprint,
                    response.status(),
                    redactor.redact(response.body()),
                    safeCalls,
                    response.metadata().tokenUsage(),
                    response.durationMs());
            var temporary = Files.createTempFile(cassetteDirectory, ".ai-contract-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), cassette);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return response;
        } catch (ContractConfigurationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ContractExecutionException("Cannot write cassette: " + exception.getMessage(), exception);
        }
    }

    private TargetResponse replay(String caseId, String fingerprint) {
        var file = cassetteFile(caseId);
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new ContractConfigurationException("Replay cassette does not exist: " + file);
            }
            var root = cassetteDirectory.toRealPath();
            var realFile = file.toRealPath();
            if (!realFile.startsWith(root)) {
                throw new ContractConfigurationException("Cassette path escapes configured directory: " + file);
            }
            var cassette = mapper.readValue(realFile.toFile(), Cassette.class);
            if (!FORMAT_VERSION.equals(cassette.formatVersion())) {
                throw new ContractConfigurationException(
                        "Unsupported cassette format version '" + cassette.formatVersion() + "'");
            }
            if (!fingerprint.equals(cassette.requestFingerprint())) {
                throw new ContractConfigurationException("Cassette request fingerprint does not match case '"
                        + redactor.redact(caseId) + "'; record it again");
            }
            return new TargetResponse(
                    cassette.status(),
                    Map.of(),
                    cassette.body(),
                    cassette.recordedDurationMs(),
                    new AiResponseMetadata(cassette.toolCalls(), cassette.tokenUsage(), true));
        } catch (ContractConfigurationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ContractConfigurationException(
                    "Cannot read replay cassette: " + file + ": " + exception.getMessage(), exception);
        }
    }

    private String fingerprint(TargetDefinition target, ContractRequest request) {
        try {
            var canonical = new LinkedHashMap<String, Object>();
            canonical.put("adapter", type());
            canonical.put("baseUrl", target.baseUrl());
            canonical.put(
                    "method",
                    request.method() == null ? "GET" : request.method().toUpperCase(Locale.ROOT));
            canonical.put("path", request.path());
            canonical.put("query", new TreeMap<>(request.query()));
            canonical.put("headers", safeHeaders(target.headers(), request.headers()));
            canonical.put("body", request.body());
            return sha256(redactor.redact(mapper.writeValueAsString(canonical)));
        } catch (IOException exception) {
            throw new ContractConfigurationException(
                    "Cannot fingerprint request: " + exception.getMessage(), exception);
        }
    }

    private Map<String, String> safeHeaders(Map<String, String> targetHeaders, Map<String, String> requestHeaders) {
        var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        targetHeaders.forEach((name, value) -> {
            if (!SENSITIVE_HEADER.matcher(name).matches()) {
                headers.put(name, redactor.redact(value));
            }
        });
        requestHeaders.forEach((name, value) -> {
            if (!SENSITIVE_HEADER.matcher(name).matches()) {
                headers.put(name, redactor.redact(value));
            }
        });
        return headers;
    }

    private Path cassetteFile(String caseId) {
        return cassetteDirectory.resolve(sha256(caseId).substring(0, 24) + ".ai-contract-cassette.json");
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
