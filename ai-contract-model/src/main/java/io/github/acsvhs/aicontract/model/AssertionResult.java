package io.github.acsvhs.aicontract.model;

public record AssertionResult(String type, boolean passed, String expected, String actual, String message) {
    public static AssertionResult passed(String type) {
        return new AssertionResult(type, true, "", "", "");
    }

    public static AssertionResult failed(String type, String expected, String actual, String message) {
        return new AssertionResult(type, false, expected, actual, message);
    }
}
