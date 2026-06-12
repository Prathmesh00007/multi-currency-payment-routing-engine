package com.routing.domain.enums;

/**
 * Optimization preference for payment routing.
 * Aligns with ISO 20022 PmtTpInf (Payment Type Information) service level concepts.
 */
public enum OptimizationPreference {
    /**
     * COST: Minimize total fees and FX spread across all route hops.
     * Dijkstra weight = baseFee + fxSpreadMargin + transferSurcharge
     */
    COST,

    /**
     * SPEED: Minimize total latency across all route hops.
     * Dijkstra weight = latencyMs
     */
    SPEED
}
