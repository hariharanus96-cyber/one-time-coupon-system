package com.nativewit.coupon.service;

import com.nativewit.coupon.dto.CouponApplyResponse;
import com.nativewit.coupon.dto.CouponRequest;
import com.nativewit.coupon.dto.CouponValidationResponse;
import com.nativewit.coupon.entity.Coupon;
import com.nativewit.coupon.entity.CouponEligibility;
import com.nativewit.coupon.entity.UserAccount;
import com.nativewit.coupon.exception.CouponException;
import com.nativewit.coupon.repository.CouponEligibilityRepository;
import com.nativewit.coupon.repository.CouponRedemptionRepository;
import com.nativewit.coupon.repository.CouponRepository;
import com.nativewit.coupon.repository.CouponTransactionRepository;
import com.nativewit.coupon.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.nativewit.coupon.CouponSystemApplication;

import java.math.BigDecimal;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = {
        CouponSystemApplication.class,
        CouponServiceIntegrationTest.TestConfig.class
})
@ActiveProfiles("test")
@Testcontainers
class CouponServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponEligibilityRepository couponEligibilityRepository;

    @Autowired
    private CouponRedemptionRepository couponRedemptionRepository;

    @Autowired
    private CouponTransactionRepository couponTransactionRepository;

    @Autowired
    private ControllableTransactionProcessor transactionProcessor;

    private UserAccount user123;
    private UserAccount user456;
    private Coupon welcomeCoupon;

    @BeforeEach
    void setUp() {

        couponRedemptionRepository.deleteAll();
        couponTransactionRepository.deleteAll();
        couponEligibilityRepository.deleteAll();
        couponRepository.deleteAll();
        userAccountRepository.deleteAll();

        user123 = userAccountRepository.save(
                UserAccount.builder()
                        .userId("user-123")
                        .build()
        );

        user456 = userAccountRepository.save(
                UserAccount.builder()
                        .userId("user-456")
                        .build()
        );

        welcomeCoupon = couponRepository.save(
                Coupon.builder()
                        .code("WELCOME100")
                        .active(true)
                        .discountPercentage(100)
                        .validFrom(LocalDateTime.now().minusDays(1))
                        .validUntil(LocalDateTime.now().plusDays(30))
                        .build()
        );

        couponEligibilityRepository.save(
                CouponEligibility.builder()
                        .user(user123)
                        .coupon(welcomeCoupon)
                        .build()
        );

        couponEligibilityRepository.save(
                CouponEligibility.builder()
                        .user(user456)
                        .coupon(welcomeCoupon)
                        .build()
        );

        transactionProcessor.setShouldFail(false);
    }

    @Test
    void shouldValidateValidCoupon() {

        CouponRequest request = CouponRequest.builder()
                .userId("user-123")
                .couponCode("WELCOME100")
                .amount(new BigDecimal("1000"))
                .build();

        CouponValidationResponse response =
                couponService.validateCoupon(request);

        assertTrue(response.isValid());

        assertEquals(
                0,
                new BigDecimal("1000")
                        .compareTo(response.getOriginalAmount())
        );

        assertEquals(
                0,
                new BigDecimal("1000")
                        .compareTo(response.getDiscount())
        );

        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(response.getFinalAmount())
        );
    }

    @Test
    void shouldRejectNonExistentCoupon() {

        CouponRequest request = CouponRequest.builder()
                .userId("user-123")
                .couponCode("DOES-NOT-EXIST")
                .amount(new BigDecimal("1000"))
                .build();

        CouponException exception = assertThrows(
                CouponException.class,
                () -> couponService.validateCoupon(request)
        );

        assertEquals(
                "Coupon not found",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInactiveCoupon() {

        Coupon inactiveCoupon = couponRepository.save(
                Coupon.builder()
                        .code("INACTIVE100")
                        .active(false)
                        .discountPercentage(100)
                        .build()
        );

        couponEligibilityRepository.save(
                CouponEligibility.builder()
                        .user(user123)
                        .coupon(inactiveCoupon)
                        .build()
        );

        CouponRequest request = CouponRequest.builder()
                .userId("user-123")
                .couponCode("INACTIVE100")
                .amount(new BigDecimal("1000"))
                .build();

        CouponException exception = assertThrows(
                CouponException.class,
                () -> couponService.validateCoupon(request)
        );

        assertEquals(
                "Coupon is inactive",
                exception.getMessage()
        );
    }

    @Test
    void shouldApplyCouponSuccessfully() {

        CouponRequest request = CouponRequest.builder()
                .userId("user-123")
                .couponCode("WELCOME100")
                .amount(new BigDecimal("1000"))
                .build();

        CouponApplyResponse response =
                couponService.applyCoupon(request);

        assertEquals("SUCCESS", response.getStatus());

        assertNotNull(response.getTransactionReference());

        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(response.getFinalAmount())
        );

        assertTrue(
                couponRedemptionRepository
                        .existsByUserIdAndCouponId(
                                user123.getId(),
                                welcomeCoupon.getId()
                        )
        );

        assertEquals(
                1,
                couponTransactionRepository.count()
        );
    }

    @Test
    void shouldRejectAlreadyUsedCoupon() {

        CouponRequest request = CouponRequest.builder()
                .userId("user-123")
                .couponCode("WELCOME100")
                .amount(new BigDecimal("1000"))
                .build();

        couponService.applyCoupon(request);

        CouponException exception = assertThrows(
                CouponException.class,
                () -> couponService.applyCoupon(request)
        );

        assertEquals(
                "Coupon has already been used by this user",
                exception.getMessage()
        );

        assertEquals(
                1,
                couponRedemptionRepository
                        .countByUserIdAndCouponId(
                                user123.getId(),
                                welcomeCoupon.getId()
                        )
        );
    }

    @Test
    void shouldAllowDifferentUsersToUseSameCoupon() {

        CouponRequest request1 = CouponRequest.builder()
                .userId("user-123")
                .couponCode("WELCOME100")
                .amount(new BigDecimal("1000"))
                .build();

        CouponRequest request2 = CouponRequest.builder()
                .userId("user-456")
                .couponCode("WELCOME100")
                .amount(new BigDecimal("500"))
                .build();

        CouponApplyResponse response1 =
                couponService.applyCoupon(request1);

        CouponApplyResponse response2 =
                couponService.applyCoupon(request2);

        assertEquals("SUCCESS", response1.getStatus());
        assertEquals("SUCCESS", response2.getStatus());

        assertEquals(
                1,
                couponRedemptionRepository
                        .countByUserIdAndCouponId(
                                user123.getId(),
                                welcomeCoupon.getId()
                        )
        );

        assertEquals(
                1,
                couponRedemptionRepository
                        .countByUserIdAndCouponId(
                                user456.getId(),
                                welcomeCoupon.getId()
                        )
        );
    }

    @Test
    void shouldNotConsumeCouponWhenTransactionFails() {

        CouponRequest request = CouponRequest.builder()
                .userId("user-123")
                .couponCode("WELCOME100")
                .amount(new BigDecimal("1000"))
                .build();

        // Simulate transaction/payment failure
        transactionProcessor.setShouldFail(true);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> couponService.applyCoupon(request)
        );

        assertEquals(
                "Transaction processing failed",
                exception.getMessage()
        );

        // Coupon must NOT have been consumed
        assertFalse(
                couponRedemptionRepository.existsByUserIdAndCouponId(
                        user123.getId(),
                        welcomeCoupon.getId()
                )
        );

        // Failed transaction must not be stored as successful
        assertEquals(0, couponTransactionRepository.count());

        // Turn transaction processing back on
        transactionProcessor.setShouldFail(false);

        // Coupon should still be valid because failed attempt did not consume it
        CouponValidationResponse validation =
                couponService.validateCoupon(request);

        assertTrue(validation.isValid());
    }

    @Test
    void shouldAllowOnlyOneConcurrentRedemptionForSameUserAndCoupon()
            throws Exception {

        int numberOfAttempts = 10;

        CouponRequest request = CouponRequest.builder()
                .userId("user-123")
                .couponCode("WELCOME100")
                .amount(new BigDecimal("1000"))
                .build();

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfAttempts);

        CountDownLatch ready =
                new CountDownLatch(numberOfAttempts);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < numberOfAttempts; i++) {

            results.add(executor.submit(() -> {

                ready.countDown();

                // All threads wait here
                start.await();

                try {
                    couponService.applyCoupon(request);
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            }));
        }

        // Wait until all 10 threads are ready
        assertTrue(
                ready.await(5, TimeUnit.SECONDS),
                "Threads were not ready in time"
        );

        // Release all threads at approximately the same time
        start.countDown();

        long successfulAttempts = 0;

        for (Future<Boolean> result : results) {
            if (result.get(15, TimeUnit.SECONDS)) {
                successfulAttempts++;
            }
        }

        executor.shutdown();

        assertTrue(
                executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor did not terminate in time"
        );

        // Only ONE request is allowed to succeed
        assertEquals(1, successfulAttempts);

        // Database must contain exactly ONE redemption
        assertEquals(
                1,
                couponRedemptionRepository.countByUserIdAndCouponId(
                        user123.getId(),
                        welcomeCoupon.getId()
                )
        );

        // And exactly ONE successful transaction
        assertEquals(
                1,
                couponTransactionRepository.count()
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        ControllableTransactionProcessor transactionProcessor() {
            return new ControllableTransactionProcessor();
        }
    }

    static class ControllableTransactionProcessor implements TransactionProcessor {

        private boolean shouldFail = false;

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        @Override
        public void process(BigDecimal amount) {
            if (shouldFail) {
                throw new RuntimeException("Transaction processing failed");
            }
        }
    }
}