package com.routing.domain.dto;

import com.routing.domain.enums.FxRateSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Live FX quote returned by the FxPricingEngine.
 * Carries rate + provenance metadata for full audit trail.
 *
 * ISO 20022: maps to ExchangeRate element in pacs.008 CdtTrfTxInf.
 */
@Data
@Builder
public class LiveFxQuote {

    /** ISO 4217 base currency (e.g., "USD") */
    private String baseCurrency;

    /** ISO 4217 quote currency (e.g., "INR") */
    private String quoteCurrency;

    /** Mid-market rate: 1 base = rate quote */
    private BigDecimal midRate;

    /** Which tier/source provided this rate */
    private FxRateSource source;

    /** Human-readable source label for UI display */
    private String sourceLabel;

    /** When this rate was last updated */
    private Instant fetchedAt;

    /** Whether this is a live tick (true) or a periodic refresh (false) */
    private boolean isLiveTick;
}
