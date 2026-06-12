package com.routing.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LiquidityDto {
    private String currency;
    private BigDecimal availableAmount;
    private BigDecimal reservedAmount;
    private BigDecimal minimumRequiredBalance;
    private LocalDateTime updatedAt;
}
