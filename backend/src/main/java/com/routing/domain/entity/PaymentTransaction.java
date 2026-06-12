package com.routing.domain.entity;

import com.routing.domain.enums.OptimizationPreference;
import com.routing.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents an ISO 20022-aligned payment transaction.
 *
 * ISO 20022 alignment: this entity models the core fields from:
 *   - pacs.008 (FIToFICustmrCdtTrf) - Financial Institution Credit Transfer
 *   - CdtTrfTxInf block: IntrBkSttlmAmt, Cdtr, CdtrAgt, Dbtr, DbtrAgt
 *
 * Named "PaymentTransaction" (not "Transaction") to avoid SQL reserved keyword conflict.
 */
@Entity
@Table(name = "payment_transactions",
       indexes = {
           @Index(name = "idx_txn_status", columnList = "status"),
           @Index(name = "idx_txn_created", columnList = "created_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ISO 20022: DbtrAgt currency - source currency (ISO 4217)
     * e.g. "USD"
     */
    @Column(nullable = false, length = 3)
    private String sourceCurrency;

    /**
     * ISO 20022: CdtrAgt currency / IntrBkSttlmAmt Ccy - target currency (ISO 4217)
     * e.g. "INR", "MXN", "KES"
     */
    @Column(nullable = false, length = 3)
    private String targetCurrency;

    /**
     * ISO 20022: IntrBkSttlmAmt - the instructed amount
     */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    /**
     * Routing optimization preference selected by the initiating party.
     * Determines Dijkstra edge weight function.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OptimizationPreference optimizationPreference;

    /** Current processing status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    /**
     * JSON-serialized ordered list of routing node IDs and names.
     * Stored as text for simplicity; deserialized by service layer.
     * Example: [{"nodeId":1,"bankName":"Bank of America"},{"nodeId":5,"bankName":"HDFC Bank India"}]
     */
    @Column(columnDefinition = "TEXT")
    private String selectedPath;

    /** Total correspondent fees across all hops (as percentage of amount) */
    @Column(precision = 20, scale = 6)
    private BigDecimal totalFee;

    /** Total FX impact cost including spread (absolute amount in source currency) */
    @Column(precision = 20, scale = 6)
    private BigDecimal totalFxImpact;

    /** Total estimated execution time in milliseconds (sum of hop latencies) */
    private Long estimatedExecutionTimeMs;

    /** Machine-readable failure reason if status = FAILED */
    @Column(length = 500)
    private String failureReason;

    /**
     * ISO 20022: GrpHdr.CreDtTm - creation date/time of the instruction
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
