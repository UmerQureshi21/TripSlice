package com.umerqureshicodes.tripslice.common;

import org.springframework.http.HttpStatus;

// Thrown when a request can't be served, the message is shown to the user
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
