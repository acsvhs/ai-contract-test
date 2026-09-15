# Spring Boot assistant example

This deterministic Spring Boot service demonstrates AI Contract Test without an AI provider, API key or external network call. Its `/api/assistant` endpoint returns a stable local response.

From the repository root, run:

```bash
./mvnw -pl examples/spring-assistant -am verify
```

The Spring Boot Maven plugin starts the application before integration tests and stops it afterwards. The AI Contract Test Maven plugin executes `src/test/ai-contract/assistant.yaml` against the running service and writes `target/ai-contract/TEST-ai-contract.xml`.
