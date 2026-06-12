package com.routing.repository;

import com.routing.domain.entity.LiquidityBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiquidityBalanceRepository extends JpaRepository<LiquidityBalance, Long> {

    Optional<LiquidityBalance> findByNodeIdAndCurrency(Long nodeId, String currency);

    List<LiquidityBalance> findByNodeId(Long nodeId);

    @Query("SELECT lb FROM LiquidityBalance lb WHERE lb.node.id = :nodeId AND lb.currency = :currency " +
           "AND (lb.availableAmount - :amount) >= lb.minimumRequiredBalance")
    Optional<LiquidityBalance> findSufficientLiquidity(Long nodeId, String currency, BigDecimal amount);

    @Modifying
    @Transactional
    @Query("UPDATE LiquidityBalance lb SET lb.availableAmount = lb.availableAmount - :amount " +
           "WHERE lb.node.id = :nodeId AND lb.currency = :currency")
    int deductLiquidity(Long nodeId, String currency, BigDecimal amount);
}
