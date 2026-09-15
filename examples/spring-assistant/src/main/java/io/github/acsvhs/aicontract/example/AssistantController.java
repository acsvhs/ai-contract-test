package io.github.acsvhs.aicontract.example;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
final class AssistantController {
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    AssistantResponse answer(@RequestBody AssistantRequest request) {
        var answer = request.prompt() == null || request.prompt().isBlank()
                ? "Please provide a prompt."
                : "The local assistant is ready.";
        return new AssistantResponse("ok", answer, "deterministic-stub");
    }

    record AssistantRequest(String prompt) {}

    record AssistantResponse(String status, String answer, String model) {}
}
