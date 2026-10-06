package com.nativewit.coupon.repository;

import com.nativewit.coupon.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository
        extends JpaRepository<CouponRedemption, Long> {

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    long countByUserIdAndCouponId(Long userId, Long couponId);
}