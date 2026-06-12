package com.routing.repository;

import com.routing.domain.entity.FxRateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FxRateSnapshotRepository extends JpaRepository<FxRateSnapshot, Long> {

    /** Get the most recent rate for a currency pair */
    @Query("SELECT f FROM FxRateSnapshot f WHERE f.baseCurrency = :base AND f.quoteCurrency = :quote " +
           "ORDER BY f.fetchedAt DESC LIMIT 1")
    Optional<FxRateSnapshot> findLatestRate(String base, String quote);
}
