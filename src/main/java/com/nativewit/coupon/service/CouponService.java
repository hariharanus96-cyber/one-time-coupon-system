package com.nativewit.coupon.service;

import com.nativewit.coupon.dto.CouponRequest;
import com.nativewit.coupon.dto.CouponValidationResponse;
import com.nativewit.coupon.entity.Coupon;
import com.nativewit.coupon.entity.UserAccount;
import com.nativewit.coupon.exception.CouponException;
import com.nativewit.coupon.repository.CouponEligibilityRepository;
import com.nativewit.coupon.repository.CouponRedemptionRepository;
import com.nativewit.coupon.repository.CouponRepository;
import com.nativewit.coupon.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nativewit.coupon.dto.CouponApplyResponse;
import com.nativewit.coupon.entity.CouponRedemption;
import com.nativewit.coupon.entity.CouponTransaction;
import com.nativewit.coupon.entity.TransactionStatus;
import com.nativewit.coupon.repository.CouponTransactionRepository;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final UserAccountRepository userAccountRepository;
    private final CouponRepository couponRepository;
    private final CouponEligibilityRepository couponEligibilityRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CouponTransactionRepository couponTransactionRepository;
    private final TransactionProcessor transactionProcessor;

    @Transactional(readOnly = true)
    public CouponValidationResponse validateCoupon(CouponRequest request) {

        UserAccount user = userAccountRepository
                .findByUserId(request.getUserId())
                .orElseThrow(() ->
                        new CouponException("User not found"));

        Coupon coupon = couponRepository
                .findByCode(request.getCouponCode())
                .orElseThrow(() ->
                        new CouponException("Coupon not found"));

        validateCouponActive(coupon);

        boolean eligible = couponEligibilityRepository
                .findByUserIdAndCouponId(user.getId(), coupon.getId())
                .isPresent();

        if (!eligible) {
            throw new CouponException(
                    "Coupon is not valid for this user"
            );
        }

        boolean alreadyUsed = couponRedemptionRepository
                .existsByUserIdAndCouponId(
                        user.getId(),
                        coupon.getId()
                );

        if (alreadyUsed) {
            throw new CouponException(
                    "Coupon has already been used by this user"
            );
        }

        BigDecimal originalAmount = request.getAmount();

        BigDecimal discount = originalAmount
                .multiply(BigDecimal.valueOf(coupon.getDiscountPercentage()))
                .divide(BigDecimal.valueOf(100));

        BigDecimal finalAmount =
                originalAmount.subtract(discount);

        return CouponValidationResponse.builder()
                .valid(true)
                .originalAmount(originalAmount)
                .discount(discount)
                .finalAmount(finalAmount)
                .build();
    }

    private void validateCouponActive(Coupon coupon) {

        if (!Boolean.TRUE.equals(coupon.getActive())) {
            throw new CouponException("Coupon is inactive");
        }

        LocalDateTime now = LocalDateTime.now();

        if (coupon.getValidFrom() != null
                && now.isBefore(coupon.getValidFrom())) {

            throw new CouponException(
                    "Coupon is not active yet"
            );
        }

        if (coupon.getValidUntil() != null
                && now.isAfter(coupon.getValidUntil())) {

            throw new CouponException(
                    "Coupon has expired"
            );
        }
    }

    @Transactional
    public CouponApplyResponse applyCoupon(CouponRequest request) {

        UserAccount user = userAccountRepository
                .findByUserId(request.getUserId())
                .orElseThrow(() ->
                        new CouponException("User not found"));

        Coupon coupon = couponRepository
                .findByCode(request.getCouponCode())
                .orElseThrow(() ->
                        new CouponException("Coupon not found"));

        validateCouponActive(coupon);

        couponEligibilityRepository
                .findByUserIdAndCouponIdForUpdate(
                        user.getId(),
                        coupon.getId()
                )
                .orElseThrow(() ->
                        new CouponException(
                                "Coupon is not valid for this user"
                        ));

        boolean alreadyUsed = couponRedemptionRepository
                .existsByUserIdAndCouponId(
                        user.getId(),
                        coupon.getId()
                );

        if (alreadyUsed) {
            throw new CouponException(
                    "Coupon has already been used by this user"
            );
        }

        BigDecimal originalAmount = request.getAmount();

        BigDecimal discount = originalAmount
                .multiply(
                        BigDecimal.valueOf(
                                coupon.getDiscountPercentage()
                        )
                )
                .divide(BigDecimal.valueOf(100));

        BigDecimal finalAmount =
                originalAmount.subtract(discount);

        /*
         * Transaction processing happens BEFORE the
         * coupon redemption is created.
         *
         * If this throws an exception, @Transactional
         * rolls everything back.
         */
        transactionProcessor.process(finalAmount);

        CouponTransaction transaction =
                CouponTransaction.builder()
                        .transactionReference(
                                UUID.randomUUID().toString()
                        )
                        .user(user)
                        .coupon(coupon)
                        .originalAmount(originalAmount)
                        .discount(discount)
                        .finalAmount(finalAmount)
                        .status(TransactionStatus.SUCCESS)
                        .createdAt(LocalDateTime.now())
                        .build();

        transaction =
                couponTransactionRepository.save(transaction);

        CouponRedemption redemption =
                CouponRedemption.builder()
                        .user(user)
                        .coupon(coupon)
                        .transaction(transaction)
                        .redeemedAt(LocalDateTime.now())
                        .build();

        couponRedemptionRepository.save(redemption);

        return CouponApplyResponse.builder()
                .transactionReference(
                        transaction.getTransactionReference()
                )
                .userId(user.getUserId())
                .couponCode(coupon.getCode())
                .originalAmount(originalAmount)
                .discount(discount)
                .finalAmount(finalAmount)
                .status(transaction.getStatus().name())
                .build();
    }
}