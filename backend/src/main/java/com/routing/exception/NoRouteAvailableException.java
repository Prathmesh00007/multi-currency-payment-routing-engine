package com.routing.exception;

/**
 * Thrown when no valid route can be found between source and target nodes.
 */
public class NoRouteAvailableException extends RuntimeException {
    public NoRouteAvailableException(String message) {
        super(message);
    }
}
