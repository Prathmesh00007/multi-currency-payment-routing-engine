package com.routing.service.fx;

import com.routing.domain.dto.LiveFxQuote;
import com.routing.domain.enums.FxRateSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * FX Pricing Engine — unified facade for all 3 data tiers.
 *
 * Priority order for rate resolution:
 * 1. Tier B: Finnhub WebSocket live tick (sub-second latency, real-time)
 * 2. Tier C: ExchangeRate-API (5-min freshness, covers exotic pairs)
 * 3. Tier A: Frankfurter API (daily ECB, always available on weekdays)
 * 4. Fallback: Deterministic mock rates (never fails, clearly labeled)
 *
 * ISO 20022: The resolved rate flows into ExchangeRate.UnitCcy / XchgRate
 * fields in the pacs.008 CdtTrfTxInf block.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FxPricingEngine {

    private final FinnhubWebSocketService finnhubService;
    private final ExchangeRateApiService exchangeRateService;
    private final FrankfurterScheduledService frankfurterService;

    /**
     * Deterministic mock rates (USD-based) — absolute last resort.
     * Based on approximate market rates for demo purposes only.
     * Clearly labeled as MOCK_FALLBACK in the response.
     */
    private static final Map<String, BigDecimal> MOCK_RATES = Map.of(
        "USD/EUR", new BigDecimal("0.9250"),
        "USD/GBP", new BigDecimal("0.7890"),
        "USD/MXN", new BigDecimal("17.2500"),
        "USD/KES", new BigDecimal("129.5000"),
        "USD/INR", new BigDecimal("83.4500"),
        "EUR/USD", new BigDecimal("1.0811"),
        "GBP/USD", new BigDecimal("1.2675"),
        "EUR/INR", new BigDecimal("90.2162"),
        "EUR/MXN", new BigDecimal("18.6486"),
        "EUR/KES", new BigDecimal("140.0000")
    );

    /**
     * Get the best available FX quote for a currency pair.
     * Tries all tiers in order and returns the first successful rate.
     *
     * @param from ISO 4217 base currency
     * @param to   ISO 4217 quote currency
     * @return LiveFxQuote with rate and provenance metadata
     */
    public LiveFxQuote getBestRate(String from, String to) {
        if (from.equals(to)) {
            return LiveFxQuote.builder()
                .baseCurrency(from).quoteCurrency(to)
                .midRate(BigDecimal.ONE).source(FxRateSource.MOCK_FALLBACK)
                .sourceLabel("IDENTITY").fetchedAt(Instant.now()).isLiveTick(false).build();
        }

        String pairKey = from + "/" + to;

        // Tier B: Finnhub live tick
        BigDecimal finnhubRate = finnhubService.getLatestTick(from, to);
        if (finnhubRate != null) {
            log.debug("[FxEngine] {} from Finnhub WS: {}", pairKey, finnhubRate);
            return buildQuote(from, to, finnhubRate, FxRateSource.FINNHUB_WEBSOCKET, "FINNHUB_WS", true);
        }
        // Try reverse and invert (e.g., if we have EUR/USD but need USD/EUR)
        BigDecimal finnhubReverse = finnhubService.getLatestTick(to, from);
        if (finnhubReverse != null && finnhubReverse.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal inverted = BigDecimal.ONE.divide(finnhubReverse, 8, RoundingMode.HALF_UP);
            log.debug("[FxEngine] {} (inverted) from Finnhub WS: {}", pairKey, inverted);
            return buildQuote(from, to, inverted, FxRateSource.FINNHUB_WEBSOCKET, "FINNHUB_WS", true);
        }

        // Tier C: ExchangeRate-API
        Optional<BigDecimal> erRate = exchangeRateService.getRate(from, to);
        if (erRate.isPresent()) {
            log.debug("[FxEngine] {} from ExchangeRate-API: {}", pairKey, erRate.get());
            return buildQuote(from, to, erRate.get(), FxRateSource.EXCHANGE_RATE_API, "ER-API", false);
        }

        // Tier A: Frankfurter
        Optional<BigDecimal> frankfurterRate = frankfurterService.getRate(from, to);
        if (frankfurterRate.isPresent()) {
            log.debug("[FxEngine] {} from Frankfurter: {}", pairKey, frankfurterRate.get());
            return buildQuote(from, to, frankfurterRate.get(), FxRateSource.FRANKFURTER_API, "FRANKFURTER", false);
        }

        // Final fallback: static mock
        BigDecimal mockRate = MOCK_RATES.get(pairKey);
        if (mockRate != null) {
            log.info("[FxEngine] {} using MOCK_FALLBACK rate: {}", pairKey, mockRate);
            return buildQuote(from, to, mockRate, FxRateSource.MOCK_FALLBACK, "MOCK", false);
        }

        // Try deriving via USD cross
        BigDecimal usdFrom = MOCK_RATES.get("USD/" + from);
        BigDecimal usdTo = MOCK_RATES.get("USD/" + to);
        if (usdFrom != null && usdTo != null && usdFrom.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal crossRate = usdTo.divide(usdFrom, 8, RoundingMode.HALF_UP);
            log.info("[FxEngine] {}/{} derived via USD cross (MOCK)", from, to);
            return buildQuote(from, to, crossRate, FxRateSource.MOCK_FALLBACK, "MOCK_CROSS", false);
        }

        throw new com.routing.exception.UnsupportedCurrencyPairException(
            String.format("No FX rate available for %s/%s from any source. Supported: USD, EUR, GBP, MXN, KES, INR.", from, to));
    }

    /**
     * Apply institutional spread on top of mid-rate.
     * effectiveRate = midRate × (1 + spreadMargin)
     * The spread widens the rate against the client (they pay more target currency).
     */
    public BigDecimal applySpread(BigDecimal midRate, BigDecimal spreadMargin) {
        return midRate.multiply(BigDecimal.ONE.add(spreadMargin))
            .setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the absolute FX spread cost in source currency.
     */
    public BigDecimal calculateSpreadCost(BigDecimal amount, BigDecimal spreadMargin) {
        return amount.multiply(spreadMargin).setScale(4, RoundingMode.HALF_UP);
    }

    // ─── Private builders ─────────────────────────────────────────────────────

    private LiveFxQuote buildQuote(String from, String to, BigDecimal rate,
                                    FxRateSource source, String label, boolean isLive) {
        return LiveFxQuote.builder()
            .baseCurrency(from).quoteCurrency(to)
            .midRate(rate).source(source).sourceLabel(label)
            .fetchedAt(Instant.now()).isLiveTick(isLive)
            .build();
    }
}
