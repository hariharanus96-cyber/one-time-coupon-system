package com.nativewit.coupon.repository;

import com.nativewit.coupon.entity.CouponTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponTransactionRepository
        extends JpaRepository<CouponTransaction, Long> {
}