package com.routing.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard API error response for all exception cases.
 * Always machine-readable with a structured reason code.
 */
@Data
@Builder
public class ApiErrorResponse {

    /** HTTP status code */
    private int status;

    /** Human-readable error message */
    private String message;

    /**
     * Machine-readable error code.
     * Examples: NO_ROUTE_AVAILABLE, INSUFFICIENT_LIQUIDITY,
     *           UNSUPPORTED_CURRENCY_PAIR, NODE_INACTIVE, INVALID_INPUT
     */
    private String errorCode;

    /** Field-level validation errors if applicable */
    private Map<String, String> fieldErrors;

    private LocalDateTime timestamp;
}
