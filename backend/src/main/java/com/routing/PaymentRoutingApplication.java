package com.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ISO 20022 Multi-Currency Payment Routing & Liquidity Engine
 * Main application entry point.
 */
@SpringBootApplication
@EnableScheduling
public class PaymentRoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentRoutingApplication.class, args);
    }
}
