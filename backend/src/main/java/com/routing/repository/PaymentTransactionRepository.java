package com.routing.repository;

import com.routing.domain.entity.PaymentTransaction;
import com.routing.domain.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByStatus(TransactionStatus status);

    List<PaymentTransaction> findBySourceCurrencyAndTargetCurrency(
        String sourceCurrency, String targetCurrency);
}
