package io.github.acsvhs.aicontract.model;

/** A provider-neutral tool call returned by an AI target. */
public record ToolCall(String name, String arguments) {
    public ToolCall {
        name = name == null ? "" : name;
        arguments = arguments == null ? "" : arguments;
    }
}
