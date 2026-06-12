package com.routing.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Fee breakdown for a single routing edge hop.
 */
@Data
@Builder
public class FeeBreakdownDto {

    private String fromBank;
    private String toBank;
    private String corridorCurrencyPair;

    /** Base correspondent fee percentage */
    private BigDecimal baseFeePercentage;

    /** FX spread margin percentage */
    private BigDecimal fxSpreadMarginPercentage;

    /** Base fee in absolute source currency amount */
    private BigDecimal baseFeeAbsolute;

    /** FX spread in absolute source currency amount */
    private BigDecimal fxSpreadAbsolute;

    /** Latency for this hop */
    private Integer latencyMs;
}
