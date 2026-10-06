package com.nativewit.coupon.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponApplyResponse {

    private String transactionReference;

    private String userId;

    private String couponCode;

    private BigDecimal originalAmount;

    private BigDecimal discount;

    private BigDecimal finalAmount;

    private String status;
}