package com.nativewit.coupon.controller;

import com.nativewit.coupon.dto.CouponRequest;
import com.nativewit.coupon.dto.CouponValidationResponse;
import com.nativewit.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.nativewit.coupon.dto.CouponApplyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "Coupon API",
        description = "Validate and redeem one-time coupons"
)
public class CouponController {

    private final CouponService couponService;

    @Operation(
            summary = "Validate a coupon",
            description = """
                Checks whether the coupon exists, is active,
                is valid for the user, and has not already been used.
                This operation does not modify coupon state.
                """
    )
    @PostMapping("/validate-coupon")
    public ResponseEntity<CouponValidationResponse> validateCoupon(
            @Valid @RequestBody CouponRequest request) {

        return ResponseEntity.ok(
                couponService.validateCoupon(request)
        );
    }

    @Operation(
            summary = "Apply a coupon",
            description = """
                Revalidates and applies the one-time coupon.
                Creates the successful transaction and records redemption.
                Concurrent redemption attempts for the same user and coupon
                are protected using database-level locking.
                """
    )
    @PostMapping("/apply-coupon")
    public ResponseEntity<CouponApplyResponse> applyCoupon(
            @Valid @RequestBody CouponRequest request) {

        return ResponseEntity.ok(
                couponService.applyCoupon(request)
        );
    }
}