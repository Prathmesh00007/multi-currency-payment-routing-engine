package com.routing.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents the liquidity position of a correspondent node for a specific currency.
 *
 * ISO 20022 alignment: maps to concepts within camt.052/camt.053 (account reporting)
 * for intraday liquidity monitoring.
 *
 * NOTE: All liquidity values are SYNTHETIC MOCK DATA representing realistic
 * institutional liquidity positions. Real interbank liquidity data is proprietary.
 */
@Entity
@Table(name = "liquidity_balances",
       uniqueConstraints = @UniqueConstraint(columnNames = {"node_id", "currency"}),
       indexes = @Index(name = "idx_liquidity_node", columnList = "node_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiquidityBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The correspondent node holding this balance */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CorrespondentNode node;

    /** ISO 4217 currency code for this balance */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Available liquidity for outgoing transfers.
     * MOCK DATA: seeded with realistic institutional amounts (millions).
     */
    @Column(nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal availableAmount = BigDecimal.ZERO;

    /**
     * Amount reserved for in-flight transactions (not available for new routing).
     * MOCK DATA.
     */
    @Column(nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    /**
     * Minimum required balance that must always be maintained.
     * Routing is blocked if availableAmount would drop below this threshold.
     * MOCK DATA: typically 10-20% of total position.
     */
    @Column(nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal minimumRequiredBalance = BigDecimal.ZERO;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
