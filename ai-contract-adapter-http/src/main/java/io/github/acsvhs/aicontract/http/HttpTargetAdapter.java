package io.github.acsvhs.aicontract.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.ContractExecutionException;
import io.github.acsvhs.aicontract.core.TargetAdapter;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import io.github.acsvhs.aicontract.model.TargetResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class HttpTargetAdapter implements TargetAdapter {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public HttpTargetAdapter() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), new ObjectMapper());
    }

    HttpTargetAdapter(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "http";
    }

    @Override
    public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
        try {
            var httpRequest = buildRequest(target, request, timeoutMs);
            long started = System.nanoTime();
            var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            byte[] bytes;
            try (var stream = response.body()) {
                bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new ContractExecutionException("HTTP response exceeded the 1 MiB safety limit", null);
            }
            return new TargetResponse(
                    response.statusCode(),
                    response.headers().map(),
                    new String(bytes, StandardCharsets.UTF_8),
                    durationMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ContractExecutionException("HTTP request was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ContractExecutionException("HTTP request failed: " + exception.getMessage(), exception);
        }
    }

    private HttpRequest buildRequest(TargetDefinition target, ContractRequest request, int timeoutMs)
            throws JsonProcessingException {
        var query = new StringBuilder();
        request.query().forEach((name, value) -> {
            query.append(query.isEmpty() ? '?' : '&');
            query.append(URLEncoder.encode(name, StandardCharsets.UTF_8));
            query.append('=');
            query.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        var base = target.baseUrl().endsWith("/")
                ? target.baseUrl().substring(0, target.baseUrl().length() - 1)
                : target.baseUrl();
        var builder = HttpRequest.newBuilder(URI.create(base + request.path() + query))
                .timeout(Duration.ofMillis(timeoutMs));
        target.headers().forEach(builder::header);
        request.headers().forEach(builder::header);
        var method = request.method() == null ? "GET" : request.method().toUpperCase(java.util.Locale.ROOT);
        var body = request.body() == null
                ? ""
                : request.body().isTextual() ? request.body().asText() : mapper.writeValueAsString(request.body());
        builder.method(
                method,
                body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return builder.build();
    }
}
