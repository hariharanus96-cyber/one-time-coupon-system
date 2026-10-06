package com.nativewit.coupon.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class DefaultTransactionProcessor implements TransactionProcessor {

    @Override
    public void process(BigDecimal amount) {
        // Simulates successful transaction processing.
        // A real application could call a payment/order service here.
    }
}