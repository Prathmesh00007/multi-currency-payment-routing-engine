package com.routing.domain.dto;

import com.routing.domain.enums.OptimizationPreference;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ISO 20022-aligned payment instruction request.
 *
 * Modeled after key fields from pacs.008 FIToFICustmrCdtTrf:
 * - GrpHdr.IntrBkSttlmDt (settlement preferences)
 * - CdtTrfTxInf.IntrBkSttlmAmt (Ccy + amount)
 * - CdtTrfTxInf.PmtTpInf (payment type / optimization preference)
 * - CdtTrfTxInf.Dbtr / CdtTrfTxInf.Cdtr (parties)
 */
@Data
public class PaymentInstructionRequest {

    /**
     * ISO 20022: DbtrAgt settlement currency (ISO 4217).
     * Currently only USD is supported as source.
     */
    @NotBlank(message = "Source currency is required")
    @Size(min = 3, max = 3, message = "Source currency must be ISO 4217 (3 characters)")
    private String sourceCurrency = "USD";

    /**
     * ISO 20022: IntrBkSttlmAmt Ccy - target currency (ISO 4217).
     * Supported: MXN, KES, INR
     */
    @NotBlank(message = "Target currency is required")
    @Size(min = 3, max = 3, message = "Target currency must be ISO 4217 (3 characters)")
    private String targetCurrency;

    /**
     * ISO 20022: IntrBkSttlmAmt - instructed amount in source currency.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "100000000", message = "Amount exceeds maximum single-instruction limit")
    private BigDecimal amount;

    /**
     * Routing optimization preference.
     * COST = minimize total fees; SPEED = minimize latency.
     * ISO 20022: maps to PmtTpInf.SvcLvl concept.
     */
    @NotNull(message = "Optimization preference is required")
    private OptimizationPreference optimizationPreference;

    /** Optional: Debtor party name (ISO 20022: Dbtr.Nm) */
    @Size(max = 140, message = "Debtor name too long")
    private String debtorName;

    /** Optional: Creditor party name (ISO 20022: Cdtr.Nm) */
    @Size(max = 140, message = "Creditor name too long")
    private String creditorName;

    /** Optional: End-to-end reference (ISO 20022: CdtTrfTxInf.EndToEndId) */
    @Size(max = 35, message = "End-to-end ID too long")
    private String endToEndId;
}
