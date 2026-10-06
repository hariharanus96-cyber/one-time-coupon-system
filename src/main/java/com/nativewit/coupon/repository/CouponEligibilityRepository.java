package com.nativewit.coupon.repository;

import com.nativewit.coupon.entity.CouponEligibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponEligibilityRepository
        extends JpaRepository<CouponEligibility, Long> {

    Optional<CouponEligibility>
    findByUserIdAndCouponId(Long userId, Long couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ce
            from CouponEligibility ce
            where ce.user.id = :userId
              and ce.coupon.id = :couponId
            """)
    Optional<CouponEligibility> findByUserIdAndCouponIdForUpdate(
            @Param("userId") Long userId,
            @Param("couponId") Long couponId
    );
}