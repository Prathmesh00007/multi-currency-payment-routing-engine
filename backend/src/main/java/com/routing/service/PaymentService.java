package com.routing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routing.domain.dto.*;
import com.routing.domain.entity.PaymentTransaction;
import com.routing.domain.enums.TransactionStatus;
import com.routing.exception.UnsupportedCurrencyPairException;
import com.routing.repository.PaymentTransactionRepository;
import com.routing.service.fx.FxPricingEngine;
import com.routing.service.stellar.StellarRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Payment orchestration service — refactored for hybrid live-data architecture.
 *
 * Pipeline:
 *   1. Validate currency pair
 *   2. StellarRoutingService → dynamic multi-hop path from Stellar Testnet
 *   3. FxPricingEngine → live FX rate (Finnhub WS → ExchangeRate-API → Frankfurter → mock)
 *   4. LiquidityService → mock internal ledger feasibility check
 *   5. Build and persist RouteResultResponse
 *
 * ISO 20022: Orchestrates pacs.008 FIToFICustmrCdtTrf processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final Set<String> SUPPORTED_SOURCE_CURRENCIES = Set.of("USD");
    private static final Set<String> SUPPORTED_TARGET_CURRENCIES = Set.of("MXN", "KES", "INR");

    // Institutional spread applied on top of mid-rate
    private static final BigDecimal INSTITUTIONAL_SPREAD = new BigDecimal("0.0035");

    private final StellarRoutingService stellarRoutingService;
    private final FxPricingEngine fxPricingEngine;
    private final LiquidityService liquidityService;
    private final PaymentTransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Stateless route calculation — does not persist a transaction.
     * POST /api/routing/calculate
     */
    @Transactional
    public RouteResultResponse calculateRoute(PaymentInstructionRequest request) {
        log.info("Calculating route: {} → {} amount={} preference={}",
            request.getSourceCurrency(), request.getTargetCurrency(),
            request.getAmount(), request.getOptimizationPreference());

        validateCurrencySupport(request.getSourceCurrency(), request.getTargetCurrency());
        try {
            return buildRouteResult(request, null);
        } catch (UnsupportedCurrencyPairException e) {
            return buildFailedResult(request, e.getMessage(), null);
        } catch (Exception e) {
            log.error("Route calculation error: {}", e.getMessage(), e);
            return buildFailedResult(request, e.getMessage(), null);
        }
    }

    /**
     * Create, route, and persist a payment transaction.
     * POST /api/transactions/submit
     */
    @Transactional
    public RouteResultResponse submitTransaction(PaymentInstructionRequest request) {
        log.info("Submitting transaction: {} → {} amount={}",
            request.getSourceCurrency(), request.getTargetCurrency(), request.getAmount());

        validateCurrencySupport(request.getSourceCurrency(), request.getTargetCurrency());

        PaymentTransaction txn = PaymentTransaction.builder()
            .sourceCurrency(request.getSourceCurrency())
            .targetCurrency(request.getTargetCurrency())
            .amount(request.getAmount())
            .optimizationPreference(request.getOptimizationPreference())
            .status(TransactionStatus.PENDING)
            .build();
        txn = transactionRepository.save(txn);

        try {
            RouteResultResponse result = buildRouteResult(request, txn.getId());

            txn.setStatus(result.getStatus());
            txn.setSelectedPath(serializePath(result.getSelectedPath()));
            txn.setTotalFee(result.getTotalFeePercentage());
            txn.setTotalFxImpact(result.getTotalFxImpact());
            txn.setEstimatedExecutionTimeMs(result.getEstimatedExecutionTimeMs());
            transactionRepository.save(txn);
            return result;

        } catch (Exception e) {
            log.warn("Transaction {} routing failed: {}", txn.getId(), e.getMessage());
            txn.setStatus(TransactionStatus.FAILED);
            txn.setFailureReason(e.getMessage());
            transactionRepository.save(txn);
            return buildFailedResult(request, e.getMessage(), txn.getId());
        }
    }

    /**
     * Retrieve a stored transaction by ID.
     * GET /api/transactions/{id}
     */
    @Transactional(readOnly = true)
    public RouteResultResponse getTransaction(Long id) {
        PaymentTransaction txn = transactionRepository.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Transaction not found: " + id));

        return RouteResultResponse.builder()
            .transactionId(txn.getId())
            .status(txn.getStatus())
            .sourceCurrency(txn.getSourceCurrency())
            .targetCurrency(txn.getTargetCurrency())
            .amount(txn.getAmount())
            .optimizationPreference(txn.getOptimizationPreference())
            .totalFeePercentage(txn.getTotalFee())
            .totalFxImpact(txn.getTotalFxImpact())
            .estimatedExecutionTimeMs(txn.getEstimatedExecutionTimeMs())
            .failureReason(txn.getFailureReason())
            .calculatedAt(txn.getUpdatedAt())
            .build();
    }

    // ─── Core orchestration ───────────────────────────────────────────────────

    private RouteResultResponse buildRouteResult(PaymentInstructionRequest request, Long txnId) {
        String src = request.getSourceCurrency();
        String tgt = request.getTargetCurrency();
        BigDecimal amount = request.getAmount();

        // Step 1: Stellar routing (with fallback to mock)
        StellarRoutingService.StellarRouteResult stellarResult =
            stellarRoutingService.findRoute(src, tgt, amount, request.getOptimizationPreference());

        // Step 2: FX pricing (multi-tier: Finnhub → ER-API → Frankfurter → mock)
        LiveFxQuote fxQuote = fxPricingEngine.getBestRate(src, tgt);

        // Step 3: Liquidity check against mock internal ledger
        boolean liquidityOk = false;
        String liquidityDetail;
        try {
            // Use the source clearing node (node ID 1 = primary USD hub)
            liquidityOk = liquidityService.hasLiquidity(1L, src, amount);
            liquidityDetail = liquidityOk
                ? String.format("✓ Sufficient liquidity at primary clearing node (%s %s available above minimum)", src, amount)
                : String.format("⚠ Warning: Liquidity at primary clearing node may be insufficient for %s %s", amount, src);
        } catch (Exception e) {
            liquidityDetail = "Liquidity check unavailable: " + e.getMessage();
        }

        // If liquidity fails, we still route but flag it
        // (throw for hard failure scenarios in production; here we warn for demo)
        if (!liquidityOk && amount.compareTo(new BigDecimal("50000000")) > 0) {
            throw new com.routing.exception.InsufficientLiquidityException(
                String.format("Insufficient liquidity for %s %s. " +
                    "Internal ledger balance is below required threshold.", amount, src));
        }

        // Step 4: Calculate totals from Stellar path
        BigDecimal totalFeePercentage = stellarResult.feeBreakdown().stream()
            .map(FeeBreakdownDto::getBaseFeePercentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpreadPercentage = stellarResult.feeBreakdown().stream()
            .map(FeeBreakdownDto::getFxSpreadMarginPercentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFeeAbsolute = amount.multiply(totalFeePercentage)
            .setScale(4, RoundingMode.HALF_UP);
        BigDecimal fxSpreadCost = fxPricingEngine.calculateSpreadCost(amount,
            totalSpreadPercentage.add(INSTITUTIONAL_SPREAD));
        BigDecimal effectiveRate = fxPricingEngine.applySpread(
            fxQuote.getMidRate(), INSTITUTIONAL_SPREAD);
        BigDecimal convertedAmount = amount.multiply(effectiveRate)
            .setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalCost = totalFeeAbsolute.add(fxSpreadCost).add(stellarResult.stellarFeeUsd());

        String settlementTime = formatSettlementTime(stellarResult.totalLatencyMs());

        log.info("Route built via {}: {} hops, stellarFee={}XLM, fxSource={}, liquidity={}",
            stellarResult.routeSource(), stellarResult.hops().size(),
            stellarResult.stellarFeeXlm(), fxQuote.getSourceLabel(), liquidityOk ? "OK" : "WARNING");

        return RouteResultResponse.builder()
            .transactionId(txnId)
            .status(TransactionStatus.ROUTED)
            .selectedPath(stellarResult.hops())
            .optimizationPreference(request.getOptimizationPreference())
            .sourceCurrency(src)
            .targetCurrency(tgt)
            .amount(amount)
            // FX
            .baseFxRate(fxQuote.getMidRate())
            .fxRateSource(fxQuote.getSource().name())
            .fxSourceLabel(fxQuote.getSourceLabel())
            .fxIsLiveTick(fxQuote.isLiveTick())
            .effectiveFxRate(effectiveRate)
            .fxSpreadCost(fxSpreadCost)
            .convertedAmount(convertedAmount)
            // Fees
            .feeBreakdown(stellarResult.feeBreakdown())
            .totalFeePercentage(totalFeePercentage)
            .totalFeeAbsolute(totalFeeAbsolute)
            .totalFxImpact(fxSpreadCost)
            .totalCost(totalCost)
            // Timing
            .estimatedExecutionTimeMs(stellarResult.totalLatencyMs())
            .estimatedSettlementTime(settlementTime)
            // Stellar
            .stellarFeeXlm(stellarResult.stellarFeeXlm())
            .stellarFeeUsd(stellarResult.stellarFeeUsd())
            .routingSource(stellarResult.routeSource())
            // Liquidity
            .liquidityCheckPassed(liquidityOk)
            .liquidityCheckDetail(liquidityDetail)
            .calculatedAt(LocalDateTime.now())
            .build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private RouteResultResponse buildFailedResult(
            PaymentInstructionRequest request, String reason, Long txnId) {
        return RouteResultResponse.builder()
            .transactionId(txnId)
            .status(TransactionStatus.FAILED)
            .sourceCurrency(request.getSourceCurrency())
            .targetCurrency(request.getTargetCurrency())
            .amount(request.getAmount())
            .optimizationPreference(request.getOptimizationPreference())
            .failureReason(reason)
            .selectedPath(List.of())
            .feeBreakdown(List.of())
            .liquidityCheckPassed(false)
            .calculatedAt(LocalDateTime.now())
            .build();
    }

    private void validateCurrencySupport(String source, String target) {
        if (!SUPPORTED_SOURCE_CURRENCIES.contains(source)) {
            throw new UnsupportedCurrencyPairException(
                String.format("Source currency '%s' is not supported. Supported: %s",
                    source, SUPPORTED_SOURCE_CURRENCIES));
        }
        if (!SUPPORTED_TARGET_CURRENCIES.contains(target)) {
            throw new UnsupportedCurrencyPairException(
                String.format("Target currency '%s' is not supported. Supported: %s",
                    target, SUPPORTED_TARGET_CURRENCIES));
        }
    }

    private String formatSettlementTime(long latencyMs) {
        if (latencyMs < 1000) return latencyMs + "ms (Near-instant)";
        if (latencyMs < 60000) return String.format("%.1fs", latencyMs / 1000.0);
        if (latencyMs < 3600000) return String.format("%.0f minutes", latencyMs / 60000.0);
        return String.format("%.1f hours", latencyMs / 3600000.0);
    }

    private String serializePath(List<RouteHopDto> path) {
        if (path == null) return "[]";
        try { return objectMapper.writeValueAsString(path); }
        catch (JsonProcessingException e) { return "[]"; }
    }
}
