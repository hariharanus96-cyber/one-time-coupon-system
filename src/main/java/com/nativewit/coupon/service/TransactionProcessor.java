package com.nativewit.coupon.service;

import java.math.BigDecimal;

public interface TransactionProcessor {

    void process(BigDecimal amount);
}