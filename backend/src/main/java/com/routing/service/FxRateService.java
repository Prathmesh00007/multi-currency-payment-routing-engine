package com.routing.service;

import com.routing.domain.entity.FxRateSnapshot;
import com.routing.domain.enums.FxRateSource;
import com.routing.repository.FxRateSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * FX rate service with real API + deterministic mock fallback.
 *
 * DATA SOURCES:
 * 1. PRIMARY: Frankfurter API (https://api.frankfurter.app)
 *    - Free, no auth, ECB reference rates
 *    - ISO 20022: maps to ExchangeRate element in pacs.008
 * 2. FALLBACK: Deterministic mock rates (clearly labeled MOCK_FALLBACK)
 *    - Used when Frankfurter is unreachable
 *    - Based on approximate market rates for demo purposes
 *
 * IMPORTANT: The baseFee + fxSpreadMargin from RoutingEdge is applied ON TOP of
 * the mid-market rate to simulate institutional pricing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FxRateService {

    private final FxRateSnapshotRepository fxRateSnapshotRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${app.fx.api.url:https://api.frankfurter.app}")
    private String fxApiUrl;

    @Value("${app.fx.api.enabled:true}")
    private boolean fxApiEnabled;

    @Value("${app.fx.api.timeout-ms:5000}")
    private int timeoutMs;

    /**
     * Mock FX rates (FALLBACK only) - approximate mid-market rates as of mid-2024.
     * Source: Derived from publicly available reference data. For demo purposes only.
     * These are USD-based rates: 1 USD = X target currency.
     */
    private static final Map<String, BigDecimal> MOCK_USD_RATES = Map.of(
        "EUR", new BigDecimal("0.9250"),
        "GBP", new BigDecimal("0.7890"),
        "MXN", new BigDecimal("17.2500"),
        "KES", new BigDecimal("129.5000"),
        "INR", new BigDecimal("83.4500"),
        "USD", BigDecimal.ONE
    );

    /**
     * Get the mid-market FX rate between two currencies.
     * Attempts live API first, falls back to mock rates.
     *
     * @param from ISO 4217 base currency
     * @param to   ISO 4217 quote currency
     * @return FxRateSnapshot with rate and source metadata
     */
    public FxRateSnapshot getRate(String from, String to) {
        if (from.equals(to)) {
            return buildSnapshot(from, to, BigDecimal.ONE, FxRateSource.MOCK_FALLBACK);
        }

        // Check recent cached rate (< 5 minutes old)
        Optional<FxRateSnapshot> cached = fxRateSnapshotRepository.findLatestRate(from, to);
        if (cached.isPresent() &&
            cached.get().getFetchedAt().isAfter(LocalDateTime.now().minusMinutes(5)) &&
            cached.get().getSource() == FxRateSource.FRANKFURTER_API) {
            log.debug("Using cached FX rate {}/{}: {}", from, to, cached.get().getRate());
            return cached.get();
        }

        // Try live API
        if (fxApiEnabled) {
            try {
                BigDecimal liveRate = fetchFromFrankfurter(from, to);
                FxRateSnapshot snapshot = buildSnapshot(from, to, liveRate, FxRateSource.FRANKFURTER_API);
                fxRateSnapshotRepository.save(snapshot);
                log.info("Fetched live FX rate {}/{}: {} (Frankfurter API)", from, to, liveRate);
                return snapshot;
            } catch (Exception e) {
                log.warn("Frankfurter API unavailable: {}. Falling back to mock rates.", e.getMessage());
            }
        }

        // Fall back to mock rates
        return getMockRate(from, to);
    }

    /**
     * Apply the FX spread margin to compute effective institutional rate.
     * effectiveRate = midRate * (1 - spreadMargin) for source-to-target conversion.
     *
     * @param midRate      the base mid-market rate
     * @param spreadMargin the spread margin percentage (e.g., 0.005 = 0.5%)
     * @return effective rate after spread
     */
    public BigDecimal applySpread(BigDecimal midRate, BigDecimal spreadMargin) {
        // Effective rate: midRate × (1 + spreadMargin) means you get less target currency
        // i.e., customer pays more for the same amount of target currency
        // Correct logic: subtract the spread so the customer gets a worse rate
        BigDecimal spreadFactor = BigDecimal.ONE.subtract(spreadMargin);
        return midRate.multiply(spreadFactor).setScale(8, RoundingMode.HALF_UP);        
    }

    /**
     * Calculate FX spread cost in source currency units.
     *
     * @param amount       original amount in source currency
     * @param spreadMargin spread margin percentage
     * @return cost of spread in source currency
     */
    public BigDecimal calculateSpreadCost(BigDecimal amount, BigDecimal spreadMargin) {
        return amount.multiply(spreadMargin).setScale(4, RoundingMode.HALF_UP);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Calls Frankfurter API: GET /latest?from={from}&to={to}
     * Returns ECB-sourced mid-market rate.
     */
    private BigDecimal fetchFromFrankfurter(String from, String to) {
        WebClient client = webClientBuilder
            .baseUrl(fxApiUrl)
            .build();

        FrankfurterResponse response = client
            .get()
            // Using the v2 pair endpoint: /rate/{base}/{quote}
            .uri(uriBuilder -> uriBuilder
                .path("/rate/{from}/{to}")
                .build(from, to)) // Maps the variables positionally 
            .retrieve()
            .bodyToMono(FrankfurterResponse.class)
            .block(java.time.Duration.ofMillis(timeoutMs));

        if (response == null || response.rate() == null) {
            throw new IllegalStateException("Unexpected response from Frankfurter API");
        }

        return response.rate();
    }

    /**
     * Compute mock rate using USD as pivot currency.
     * from/to rate = (USD/from inverse) × (USD/to rate)
     */
    private FxRateSnapshot getMockRate(String from, String to) {
        BigDecimal fromUsd = MOCK_USD_RATES.get(from);
        BigDecimal toUsd = MOCK_USD_RATES.get(to);

        if (fromUsd == null || toUsd == null) {
            throw new com.routing.exception.UnsupportedCurrencyPairException(
                String.format("Currency pair %s/%s is not supported. " +
                    "Supported currencies: USD, EUR, GBP, MXN, KES, INR.", from, to));
        }

        // Cross rate: from → USD → to
        BigDecimal crossRate = toUsd.divide(fromUsd, 8, RoundingMode.HALF_UP);
        FxRateSnapshot snapshot = buildSnapshot(from, to, crossRate, FxRateSource.MOCK_FALLBACK);
        fxRateSnapshotRepository.save(snapshot);

        log.info("Using MOCK FX rate {}/{}: {} (FALLBACK - not real market data)", from, to, crossRate);
        return snapshot;
    }

    private FxRateSnapshot buildSnapshot(String from, String to, BigDecimal rate, FxRateSource source) {
        return FxRateSnapshot.builder()
            .baseCurrency(from)
            .quoteCurrency(to)
            .rate(rate)
            .source(source)
            .fetchedAt(LocalDateTime.now())
            .build();
    }

    /** Frankfurter API response DTO */
    /** Frankfurter API v2 response DTO */
    public record FrankfurterResponse(
        String date,
        String base,
        String quote,
        BigDecimal rate
    ) {}
}
