package com.routing.domain.entity;

import com.routing.domain.enums.FxRateSource;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot of an FX rate at a given point in time.
 *
 * ISO 20022 alignment: maps to ExchangeRate element in pacs.008 CdtTrfTxInf,
 * including UnitCcy (base) and XchgRate.
 *
 * DATA SOURCES:
 * - FRANKFURTER_API: Real rates fetched from https://api.frankfurter.app (free, ECB-based)
 * - MOCK_FALLBACK: Deterministic mock rates used when API is unavailable
 *
 * Note: Frankfurter provides ECB reference rates (not real-time market bid/ask).
 * The fxSpreadMargin on RoutingEdge is applied on top to simulate realistic
 * institutional pricing with bid-ask spread.
 */
@Entity
@Table(name = "fx_rate_snapshots",
       indexes = {
           @Index(name = "idx_fx_currencies", columnList = "base_currency, quote_currency"),
           @Index(name = "idx_fx_fetched_at", columnList = "fetched_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ISO 4217 base currency code (e.g. "USD") */
    @Column(nullable = false, length = 3)
    private String baseCurrency;

    /** ISO 4217 quote currency code (e.g. "INR") */
    @Column(nullable = false, length = 3)
    private String quoteCurrency;

    /**
     * Mid-market exchange rate: 1 unit of baseCurrency = rate units of quoteCurrency.
     * For FRANKFURTER_API: ECB reference rate.
     * For MOCK_FALLBACK: deterministic approximation.
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal rate;

    /** Data source classification */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FxRateSource source;

    /** Timestamp when this rate was fetched or generated */
    @Column(nullable = false)
    private LocalDateTime fetchedAt;
}
