package com.electrostock.backend.exception;

public class SaleCancellationNotAllowedException extends IllegalStateException {

    public SaleCancellationNotAllowedException(String message) {
        super(message);
    }
}