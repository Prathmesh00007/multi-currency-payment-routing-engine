package com.routing.domain.enums;

/**
 * Payment transaction lifecycle status.
 * Mirrors ISO 20022 transaction status codes.
 */
public enum TransactionStatus {
    /** Initial state - payment instruction received but not yet routed */
    PENDING,

    /** Route successfully calculated and assigned */
    ROUTED,

    /** No valid route found or routing preconditions failed */
    FAILED,

    /** Payment fully settled across all nodes */
    SETTLED,

    /** Payment cancelled before settlement */
    CANCELLED
}
