package com.routing.service.fx;

import com.routing.domain.entity.FxRateSnapshot;
import com.routing.domain.enums.FxRateSource;
import com.routing.repository.FxRateSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tier A FX Service: Frankfurter API — daily ECB reference rates.
 *
 * DATA SOURCE: https://api.frankfurter.dev/v2
 * Schedule: Weekdays at 06:00 UTC (after ECB daily fix around 16:00 CET prior day)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FrankfurterScheduledService {

    /** * DTO matching the working FxRateService for the /rate/{from}/{to} endpoint 
     */
    public record FrankfurterResponse(
        String date,
        String base,
        String quote,
        BigDecimal rate
    ) {}

    private final FxRateSnapshotRepository fxRateSnapshotRepository;
    private final WebClient.Builder webClientBuilder;

    // Using the .dev/v2 base URL as requested
    @Value("${app.fx.frankfurter.url:https://api.frankfurter.dev/v2}")
    private String baseUrl;

    @Value("${app.fx.frankfurter.enabled:true}")
    private boolean enabled;

    @Value("${app.fx.frankfurter.timeout-ms:6000}")
    private int timeoutMs;

    // In-memory cache keyed "FROM/TO"
    private final ConcurrentHashMap<String, BigDecimal> rateCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initOnStartup() {
        log.info("[Frankfurter] Fetching initial ECB reference rates on startup...");
        refreshRates();
    }

    @Scheduled(cron = "0 0 6 * * MON-FRI")
    public void scheduledRefresh() {
        log.info("[Frankfurter] Daily scheduled ECB rate refresh triggered.");
        refreshRates();
    }

    public Optional<BigDecimal> getRate(String from, String to) {
        String key = from + "/" + to;
        BigDecimal rate = rateCache.get(key);
        if (rate != null) {
            return Optional.of(rate);
        }
        
        BigDecimal reverse = rateCache.get(to + "/" + from);
        if (reverse != null && reverse.compareTo(BigDecimal.ZERO) != 0) {
            return Optional.of(BigDecimal.ONE.divide(reverse, 8, java.math.RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void refreshRates() {
        if (!enabled) {
            log.info("[Frankfurter] Disabled. Skipping rate fetch.");
            return;
        }
        // Fetch EUR-based rates individually to match the working API structure
        String[] targetCurrencies = {"USD", "GBP", "MXN", "KES", "INR"};
        fetchAndStore("EUR", targetCurrencies);
        
        deriveUsdCrossRates();
    }

    private void fetchAndStore(String base, String[] quotes) {
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();

        for (String quote : quotes) {
            try {
                // Exact WebClient call structure from your working FxRateService
                FrankfurterResponse response = client
                    .get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/rate/{from}/{to}")
                        .build(base, quote))
                    .retrieve()
                    .bodyToMono(FrankfurterResponse.class)
                    .block(java.time.Duration.ofMillis(timeoutMs));

                if (response != null && response.rate() != null) {
                    BigDecimal rate = response.rate();
                    String key = base + "/" + quote;
                    
                    rateCache.put(key, rate);
                    
                    if (rate.compareTo(BigDecimal.ZERO) != 0) {
                        rateCache.put(quote + "/" + base,
                            BigDecimal.ONE.divide(rate, 8, java.math.RoundingMode.HALF_UP));
                    }
                    persistRate(base, quote, rate);
                    log.debug("[Frankfurter] Fetched {}/{} = {}", base, quote, rate);
                }

            } catch (WebClientResponseException e) {
                log.warn("[Frankfurter] HTTP {} fetching {}/{}: {}", e.getStatusCode(), base, quote, e.getMessage());
            } catch (Exception e) {
                log.warn("[Frankfurter] Failed to fetch rate for {}/{}: {}", base, quote, e.getMessage());
            }
        }
        log.info("[Frankfurter] Completed rate fetch cycle for base={}", base);
    }

    private void deriveUsdCrossRates() {
        BigDecimal eurUsd = rateCache.get("EUR/USD");
        if (eurUsd == null || eurUsd.compareTo(BigDecimal.ZERO) == 0) return;

        String[] targets = {"GBP", "MXN", "KES", "INR", "EUR"};
        for (String target : targets) {
            if ("USD".equals(target)) continue;
            BigDecimal eurTarget = rateCache.get("EUR/" + target);
            if (eurTarget == null) continue;

            BigDecimal usdTarget = eurTarget.divide(eurUsd, 8, java.math.RoundingMode.HALF_UP);
            rateCache.put("USD/" + target, usdTarget);
            
            if (usdTarget.compareTo(BigDecimal.ZERO) != 0) {
                rateCache.put(target + "/USD",
                    BigDecimal.ONE.divide(usdTarget, 8, java.math.RoundingMode.HALF_UP));
            }
            persistRate("USD", target, usdTarget);
        }
        log.info("[Frankfurter] Derived USD cross rates successfully.");
    }

    private void persistRate(String base, String quote, BigDecimal rate) {
        try {
            FxRateSnapshot snapshot = FxRateSnapshot.builder()
                .baseCurrency(base)
                .quoteCurrency(quote)
                .rate(rate)
                .source(FxRateSource.FRANKFURTER_API)
                .fetchedAt(LocalDateTime.now())
                .build();
            fxRateSnapshotRepository.save(snapshot);
        } catch (Exception e) {
            log.debug("[Frankfurter] Skipping DB persist for {}/{}: {}", base, quote, e.getMessage());
        }
    }
}