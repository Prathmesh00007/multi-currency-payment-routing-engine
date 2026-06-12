package com.routing.service.fx;

import com.routing.domain.dto.FxTickMessage;
import com.routing.domain.entity.FxRateSnapshot;
import com.routing.domain.enums.FxRateSource;
import com.routing.repository.FxRateSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tier C FX Service: ExchangeRate-API — exotic pairs polling (5-minute interval).
 *
 * DATA SOURCE: https://open.er-api.com/v6/latest/USD
 * - FREE, no API key required
 * - Covers MXN, KES, INR (illiquid pairs with no Finnhub WebSocket coverage)
 * - Also covers EUR, GBP as backup
 * - Accuracy: Updated hourly on their end; we poll every 5 minutes
 *
 * Broadcasts updated rates to frontend STOMP topic on each refresh.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateApiService {

    private final FxRateSnapshotRepository fxRateSnapshotRepository;
    private final WebClient.Builder webClientBuilder;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.fx.exchangerate.url:https://open.er-api.com/v6/latest}")
    private String baseUrl;

    @Value("${app.fx.exchangerate.enabled:true}")
    private boolean enabled;

    // In-memory cache: "USD/INR" → rate
    private final ConcurrentHashMap<String, BigDecimal> rateCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BigDecimal> previousRates = new ConcurrentHashMap<>();

    // Currencies to cache (all relative to USD base)
    private static final String[] TARGET_CURRENCIES = {"EUR", "GBP", "MXN", "KES", "INR"};

    @PostConstruct
    public void initOnStartup() {
        log.info("[ExchangeRateAPI] Initial rate fetch on startup...");
        fetchRates();
    }

    /**
     * Poll every 5 minutes for fresh rates.
     * ExchangeRate-API is updated hourly, so 5-min polling is sufficient.
     */
    @Scheduled(fixedDelayString = "${app.fx.exchangerate.poll-interval-ms:300000}")
    public void scheduledFetch() {
        log.debug("[ExchangeRateAPI] Scheduled refresh triggered.");
        fetchRates();
    }

    /**
     * Get cached rate for a pair.
     * Returns Optional.empty() if not available.
     */
    public Optional<BigDecimal> getRate(String from, String to) {
        String key = from + "/" + to;
        BigDecimal rate = rateCache.get(key);
        if (rate != null) return Optional.of(rate);

        // Try cross-rate via USD pivot
        if (!"USD".equals(from)) {
            BigDecimal fromUsd = rateCache.get("USD/" + from);
            BigDecimal toUsd = rateCache.get("USD/" + to);
            if (fromUsd != null && toUsd != null && fromUsd.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal crossRate = toUsd.divide(fromUsd, 8, RoundingMode.HALF_UP);
                rateCache.put(key, crossRate);
                return Optional.of(crossRate);
            }
        }
        return Optional.empty();
    }

    public Map<String, BigDecimal> getAllRates() {
        return Map.copyOf(rateCache);
    }

    // ─── Private fetch logic ──────────────────────────────────────────────────

    private void fetchRates() {
        if (!enabled) return;
        try {
            WebClient client = webClientBuilder.baseUrl(baseUrl).build();

            ExchangeRateResponse response = client.get()
                .uri("/USD")  // Fetch USD base
                .retrieve()
                .bodyToMono(ExchangeRateResponse.class)
                .block(java.time.Duration.ofSeconds(8));

            if (response == null || response.rates() == null) {
                log.warn("[ExchangeRateAPI] Null response received.");
                return;
            }

            int updated = 0;
            for (String toCurrency : TARGET_CURRENCIES) {
                BigDecimal rate = response.rates().get(toCurrency);
                if (rate == null) continue;

                String key = "USD/" + toCurrency;
                BigDecimal prev = rateCache.getOrDefault(key, rate);
                previousRates.put(key, prev);
                rateCache.put(key, rate);

                // Also store reverse
                if (rate.compareTo(BigDecimal.ZERO) != 0) {
                    rateCache.put(toCurrency + "/USD",
                        BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP));
                }

                // Persist to DB
                persistRate("USD", toCurrency, rate);

                // Broadcast tick to frontend
                broadcastTick(key, rate, prev);
                updated++;
            }
            log.info("[ExchangeRateAPI] Updated {} rates (base=USD).", updated);

        } catch (WebClientResponseException e) {
            log.warn("[ExchangeRateAPI] HTTP {}: {}", e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("[ExchangeRateAPI] Fetch failed: {}", e.getMessage());
        }
    }

    private void broadcastTick(String pairKey, BigDecimal rate, BigDecimal prev) {
        int cmp = rate.compareTo(prev);
        String direction = cmp > 0 ? "UP" : cmp < 0 ? "DOWN" : "FLAT";

        FxTickMessage tick = FxTickMessage.builder()
            .pair(pairKey)
            .price(rate)
            .previousPrice(prev)
            .direction(direction)
            .source("EXCHANGE_RATE_API")
            .timestamp(Instant.now().toEpochMilli())
            .build();

        try {
            messagingTemplate.convertAndSend("/topic/fx-ticks", tick);
        } catch (Exception e) {
            log.debug("[ExchangeRateAPI] STOMP broadcast failed: {}", e.getMessage());
        }
    }

    private void persistRate(String base, String quote, BigDecimal rate) {
        try {
            fxRateSnapshotRepository.save(FxRateSnapshot.builder()
                .baseCurrency(base)
                .quoteCurrency(quote)
                .rate(rate)
                .source(FxRateSource.EXCHANGE_RATE_API)
                .fetchedAt(LocalDateTime.now())
                .build());
        } catch (Exception e) {
            log.debug("[ExchangeRateAPI] DB persist skipped for USD/{}: {}", quote, e.getMessage());
        }
    }

    /** ExchangeRate-API /v6/latest/{base} response shape */
    public record ExchangeRateResponse(
        String result,
        String base_code,
        Map<String, BigDecimal> rates
    ) {}
}
