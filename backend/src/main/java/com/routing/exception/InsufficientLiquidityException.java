package com.routing.exception;

/**
 * Thrown when a node in the proposed route has insufficient liquidity.
 */
public class InsufficientLiquidityException extends RuntimeException {
    public InsufficientLiquidityException(String message) {
        super(message);
    }
}
