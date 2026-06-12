package com.routing.exception;

/**
 * Thrown when the requested currency pair is not supported by any active corridor.
 */
public class UnsupportedCurrencyPairException extends RuntimeException {
    public UnsupportedCurrencyPairException(String message) {
        super(message);
    }
}
