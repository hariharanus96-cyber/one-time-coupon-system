package com.nativewit.coupon.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponValidationResponse {

    private boolean valid;

    private BigDecimal originalAmount;

    private BigDecimal discount;

    private BigDecimal finalAmount;
}