package com.routing.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a directed routing edge between two correspondent banking nodes.
 * Each edge represents a bilateral correspondent banking relationship with
 * associated fees, FX spread, and latency characteristics.
 *
 * ISO 20022 alignment: maps to IntermediaryAgent routing hops in pacs.008.
 *
 * NOTE: All fee, spread, latency, and corridor data is SYNTHETIC MOCK DATA.
 * Real interbank fee and corridor data is not publicly available.
 */
@Entity
@Table(name = "routing_edges",
       indexes = {
           @Index(name = "idx_edge_source", columnList = "source_node_id"),
           @Index(name = "idx_edge_target", columnList = "target_node_id"),
           @Index(name = "idx_edge_active", columnList = "active")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source node of this directed edge */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_node_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CorrespondentNode sourceNode;

    /** Target node of this directed edge */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_node_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CorrespondentNode targetNode;

    /**
     * Base correspondent banking fee as a percentage of transaction amount.
     * MOCK DATA - realistic range: 0.001 (0.1%) to 0.005 (0.5%)
     */
    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal baseFee;

    /**
     * FX spread margin applied on top of market mid-rate.
     * MOCK DATA - realistic range: 0.001 (0.1%) to 0.008 (0.8%)
     */
    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal fxSpreadMargin;

    /**
     * Estimated end-to-end latency for this hop in milliseconds.
     * MOCK DATA - realistic range: 50ms (same-currency) to 2000ms (cross-border)
     */
    @Column(nullable = false)
    private Integer latencyMs;

    /** Whether this corridor/edge is currently active */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Currency pair supported on this corridor, e.g. "USD/INR", "EUR/MXN".
     * ISO 20022 alignment: CdtTrfTxInf.IntrBkSttlmAmt currency context.
     */
    @Column(nullable = false, length = 7)
    private String corridorCurrencyPair;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
