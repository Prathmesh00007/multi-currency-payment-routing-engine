package com.routing.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a correspondent banking node in the global payment network.
 * Each node is a financial institution that can hold liquidity and route payments.
 *
 * ISO 20022 alignment: maps to FinancialInstitutionIdentification (FinInstnId)
 * within Agent elements in pacs.008 messages.
 *
 * NOTE: liquidityBalances is a @OneToMany relationship stored in a separate table.
 * This entity uses SYNTHETIC/MOCK connectivity data.
 */
@Entity
@Table(name = "correspondent_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrespondentNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ISO 20022: FinInstnId.Nm - Financial institution name */
    @Column(nullable = false, length = 100)
    private String bankName;

    /** ISO 3166-1 alpha-2 country code */
    @Column(nullable = false, length = 2)
    private String country;

    /** ISO 4217 currency code - primary settlement currency of this node */
    @Column(nullable = false, length = 3)
    private String baseCurrency;

    /** Whether this node is currently active in the routing network.
     *  Inactive nodes are excluded from all route calculations. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Outgoing routing edges from this node (SYNTHETIC mock data) */
    @OneToMany(mappedBy = "sourceNode", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RoutingEdge> outgoingEdges;

    /** Liquidity balances held by this node across supported currencies */
    @OneToMany(mappedBy = "node", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LiquidityBalance> liquidityBalances;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
