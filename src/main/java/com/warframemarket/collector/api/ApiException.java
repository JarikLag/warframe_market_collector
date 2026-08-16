package com.warframemarket.collector.api;

/** Raised when the warframe.market API cannot be reached or answers with an error. */
public class ApiException extends Exception {

    private final int statusCode;

    public ApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /** HTTP status that caused the failure, or {@code -1} for transport-level errors. */
    public int statusCode() {
        return statusCode;
    }
}
