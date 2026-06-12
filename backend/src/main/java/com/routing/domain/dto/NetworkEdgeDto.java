package com.routing.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class NetworkEdgeDto {
    private Long id;
    private Long sourceNodeId;
    private Long targetNodeId;
    private String targetBankName;
    private BigDecimal baseFee;
    private BigDecimal fxSpreadMargin;
    private Integer latencyMs;
    private boolean active;
    private String corridorCurrencyPair;
}
