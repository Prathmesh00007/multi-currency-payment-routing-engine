package com.routing.controller;

import com.routing.service.fx.ExchangeRateApiService;
import com.routing.service.fx.FinnhubWebSocketService;
import com.routing.service.fx.FrankfurterScheduledService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST + WebSocket controller for live FX market data.
 *
 * Endpoints:
 *   GET  /api/fx/rates        — Snapshot of all current rates from all tiers
 *   GET  /api/fx/rate/{pair}  — Single pair rate (e.g., /api/fx/rate/USD-INR)
 *   GET  /api/fx/sources      — Which tier is providing each pair
 */
@RestController
@RequestMapping("/api/fx")
@RequiredArgsConstructor
@Slf4j
public class FxTickerController {

    private final FinnhubWebSocketService finnhubService;
    private final ExchangeRateApiService exchangeRateService;
    private final FrankfurterScheduledService frankfurterService;

    /**
     * Returns a snapshot of all currently known FX rates across all tiers.
     * Used by the frontend on initial load to populate the ticker.
     *
     * Response shape:
     * {
     *   "USD/INR": { "rate": 83.45, "source": "EXCHANGE_RATE_API" },
     *   "USD/MXN": { "rate": 17.25, "source": "FINNHUB_WS" },
     *   ...
     * }
     */
    @GetMapping("/rates")
    public Map<String, Object> getAllRates() {
        Map<String, Object> result = new HashMap<>();
        String[] pairs = {"USD/INR", "USD/MXN", "USD/KES", "EUR/USD", "GBP/USD"};

        for (String pair : pairs) {
            String[] parts = pair.split("/");
            if (parts.length != 2) continue;
            String from = parts[0];
            String to = parts[1];

            Map<String, Object> entry = new HashMap<>();

            // Check Finnhub first
            BigDecimal finnhubRate = finnhubService.getLatestTick(from, to);
            if (finnhubRate != null) {
                entry.put("rate", finnhubRate);
                entry.put("source", "FINNHUB_WS");
                entry.put("live", true);
            } else {
                // Try ExchangeRate-API
                Optional<BigDecimal> erRate = exchangeRateService.getRate(from, to);
                if (erRate.isPresent()) {
                    entry.put("rate", erRate.get());
                    entry.put("source", "ER-API");
                    entry.put("live", false);
                } else {
                    // Frankfurter fallback
                    Optional<BigDecimal> fRate = frankfurterService.getRate(from, to);
                    if (fRate.isPresent()) {
                        entry.put("rate", fRate.get());
                        entry.put("source", "FRANKFURTER");
                        entry.put("live", false);
                    } else {
                        entry.put("rate", null);
                        entry.put("source", "UNAVAILABLE");
                        entry.put("live", false);
                    }
                }
            }

            result.put(pair, entry);
        }

        log.debug("[FxTicker] Returning rate snapshot for {} pairs", result.size());
        return result;
    }

    /**
     * Single pair rate lookup.
     * Path param format: USD-INR (hyphen-separated, not slash, for URL safety)
     */
    @GetMapping("/rate/{pair}")
    public Map<String, Object> getPairRate(@PathVariable String pair) {
        String normalised = pair.replace("-", "/").toUpperCase();
        String[] parts = normalised.split("/");
        if (parts.length != 2) {
            return Map.of("error", "Invalid pair format. Use: USD-INR");
        }
        String from = parts[0];
        String to = parts[1];

        BigDecimal rate = finnhubService.getLatestTick(from, to);
        String source = "FINNHUB_WS";

        if (rate == null) {
            var er = exchangeRateService.getRate(from, to);
            if (er.isPresent()) { rate = er.get(); source = "ER-API"; }
        }
        if (rate == null) {
            var fr = frankfurterService.getRate(from, to);
            if (fr.isPresent()) { rate = fr.get(); source = "FRANKFURTER"; }
        }

        return Map.of(
            "pair", normalised,
            "rate", rate != null ? rate : "UNAVAILABLE",
            "source", source
        );
    }
}
