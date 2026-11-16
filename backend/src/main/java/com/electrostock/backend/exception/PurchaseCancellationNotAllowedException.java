package com.electrostock.backend.exception;

public class PurchaseCancellationNotAllowedException extends IllegalStateException {

    public PurchaseCancellationNotAllowedException(String message) {
        super(message);
    }
}