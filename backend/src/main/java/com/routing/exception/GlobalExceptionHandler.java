package com.routing.exception;

import com.routing.domain.dto.ApiErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler ensuring consistent, machine-readable error responses.
 * All routing failures return structured ApiErrorResponse with errorCode.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoRouteAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleNoRoute(NoRouteAvailableException ex) {
        log.warn("Route calculation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiErrorResponse.builder()
                .status(422)
                .message(ex.getMessage())
                .errorCode("NO_ROUTE_AVAILABLE")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @ExceptionHandler(InsufficientLiquidityException.class)
    public ResponseEntity<ApiErrorResponse> handleLiquidity(InsufficientLiquidityException ex) {
        log.warn("Liquidity check failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiErrorResponse.builder()
                .status(422)
                .message(ex.getMessage())
                .errorCode("INSUFFICIENT_LIQUIDITY")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @ExceptionHandler(UnsupportedCurrencyPairException.class)
    public ResponseEntity<ApiErrorResponse> handleCurrencyPair(UnsupportedCurrencyPairException ex) {
        log.warn("Currency pair not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.builder()
                .status(400)
                .message(ex.getMessage())
                .errorCode("UNSUPPORTED_CURRENCY_PAIR")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiErrorResponse.builder()
                .status(404)
                .message(ex.getMessage())
                .errorCode("RESOURCE_NOT_FOUND")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.builder()
                .status(400)
                .message("Validation failed")
                .errorCode("INVALID_INPUT")
                .fieldErrors(fieldErrors)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.builder()
                .status(400)
                .message(ex.getMessage())
                .errorCode("INVALID_INPUT")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiErrorResponse.builder()
                .status(500)
                .message("Internal Error: " + ex.getMessage() + " (Class: " + ex.getClass().getSimpleName() + ")")
                .errorCode("INTERNAL_ERROR")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
