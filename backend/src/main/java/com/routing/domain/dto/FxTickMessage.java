package com.routing.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a live forex tick from the Finnhub WebSocket stream.
 * Broadcast to frontend via STOMP /topic/fx-ticks.
 */
@Data
@Builder
public class FxTickMessage {

    /** Currency pair key, e.g. "USD/INR" */
    private String pair;

    /** Latest trade price */
    private BigDecimal price;

    /** Previous price (for green/red flash in UI) */
    private BigDecimal previousPrice;

    /** Direction: "UP", "DOWN", "FLAT" */
    private String direction;

    /** Source: "FINNHUB", "EXCHANGE_RATE_API", "FRANKFURTER" */
    private String source;

    /** Unix epoch milliseconds of the tick */
    private long timestamp;
}
