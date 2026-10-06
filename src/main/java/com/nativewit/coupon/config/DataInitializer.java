package com.nativewit.coupon.config;

import com.nativewit.coupon.entity.Coupon;
import com.nativewit.coupon.entity.CouponEligibility;
import com.nativewit.coupon.entity.UserAccount;
import com.nativewit.coupon.repository.CouponEligibilityRepository;
import com.nativewit.coupon.repository.CouponRepository;
import com.nativewit.coupon.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.seed-data",
        havingValue = "true",
        matchIfMissing = true
)
public class DataInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final CouponRepository couponRepository;
    private final CouponEligibilityRepository couponEligibilityRepository;

    @Override
    public void run(String... args) {

        UserAccount user123 = userAccountRepository
                .findByUserId("user-123")
                .orElseGet(() ->
                        userAccountRepository.save(
                                UserAccount.builder()
                                        .userId("user-123")
                                        .build()
                        )
                );

        UserAccount user456 = userAccountRepository
                .findByUserId("user-456")
                .orElseGet(() ->
                        userAccountRepository.save(
                                UserAccount.builder()
                                        .userId("user-456")
                                        .build()
                        )
                );

        Coupon welcomeCoupon = couponRepository
                .findByCode("WELCOME100")
                .orElseGet(() ->
                        couponRepository.save(
                                Coupon.builder()
                                        .code("WELCOME100")
                                        .active(true)
                                        .discountPercentage(100)
                                        .validFrom(LocalDateTime.now().minusDays(1))
                                        .validUntil(LocalDateTime.now().plusDays(30))
                                        .build()
                        )
                );

        Coupon inactiveCoupon = couponRepository
                .findByCode("INACTIVE100")
                .orElseGet(() ->
                        couponRepository.save(
                                Coupon.builder()
                                        .code("INACTIVE100")
                                        .active(false)
                                        .discountPercentage(100)
                                        .build()
                        )
                );

        createEligibilityIfMissing(user123, welcomeCoupon);
        createEligibilityIfMissing(user456, welcomeCoupon);
        createEligibilityIfMissing(user123, inactiveCoupon);
    }

    private void createEligibilityIfMissing(
            UserAccount user,
            Coupon coupon) {

        boolean exists = couponEligibilityRepository
                .findByUserIdAndCouponId(
                        user.getId(),
                        coupon.getId()
                )
                .isPresent();

        if (!exists) {
            couponEligibilityRepository.save(
                    CouponEligibility.builder()
                            .user(user)
                            .coupon(coupon)
                            .build()
            );
        }
    }
}