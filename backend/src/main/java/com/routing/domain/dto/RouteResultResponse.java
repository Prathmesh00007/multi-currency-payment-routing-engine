package com.routing.domain.dto;

import com.routing.domain.enums.OptimizationPreference;
import com.routing.domain.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Route calculation result returned to the frontend.
 * Contains complete routing decision data for UI display.
 */
@Data
@Builder
public class RouteResultResponse {

    /** Transaction ID (null for stateless calculate endpoint) */
    private Long transactionId;

    /** Route calculation status */
    private TransactionStatus status;

    /** Ordered list of nodes in the selected path */
    private List<RouteHopDto> selectedPath;

    /** Optimization preference used */
    private OptimizationPreference optimizationPreference;

    // ─── Payment Details ──────────────────────────────────────────────────────

    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal amount;

    // ─── FX Details (ISO 20022: ExchangeRate element) ────────────────────────

    /** Mid-market base FX rate (1 sourceCurrency = rate targetCurrency) */
    private BigDecimal baseFxRate;

    /** Data source: FINNHUB_WEBSOCKET, EXCHANGE_RATE_API, FRANKFURTER_API, or MOCK_FALLBACK */
    private String fxRateSource;

    /** Human-readable FX source label for UI display (e.g. "FINNHUB_WS", "ER-API") */
    private String fxSourceLabel;

    /** Whether rate is a live real-time tick */
    private boolean fxIsLiveTick;

    /** Effective rate after spread applied */
    private BigDecimal effectiveFxRate;

    /** Total FX spread cost in source currency */
    private BigDecimal fxSpreadCost;

    /** Amount in target currency at effective rate */
    private BigDecimal convertedAmount;

    // ─── Fee Breakdown ────────────────────────────────────────────────────────

    /** Breakdown of fees per hop */
    private List<FeeBreakdownDto> feeBreakdown;

    /** Total correspondent fee as percentage of amount */
    private BigDecimal totalFeePercentage;

    /** Total correspondent fee in absolute source currency amount */
    private BigDecimal totalFeeAbsolute;

    /** Total FX spread impact in source currency */
    private BigDecimal totalFxImpact;

    /** Combined total cost (fees + FX impact) */
    private BigDecimal totalCost;

    // ─── Timing ───────────────────────────────────────────────────────────────

    /** Total estimated execution time in milliseconds */
    private Long estimatedExecutionTimeMs;

    /** Human-readable settlement time estimate */
    private String estimatedSettlementTime;

    // ─── Stellar Network Details ──────────────────────────────────────────────

    /** Stellar network fee in XLM (converted from stroops). ~0.00001 XLM per op. */
    private BigDecimal stellarFeeXlm;

    /** Stellar fee USD equivalent (for display in financial ledger) */
    private BigDecimal stellarFeeUsd;

    /** Source of routing graph: "STELLAR_TESTNET" or "MOCK_STELLAR_FALLBACK" */
    private String routingSource;

    // ─── Liquidity Check ─────────────────────────────────────────────────────

    /** Whether the internal mock liquidity ledger check passed */
    private boolean liquidityCheckPassed;

    /** Detail message from liquidity check */
    private String liquidityCheckDetail;

    // ─── Status / Error ───────────────────────────────────────────────────────

    /** Machine-readable failure reason (null if successful) */
    private String failureReason;

    private LocalDateTime calculatedAt;
}
