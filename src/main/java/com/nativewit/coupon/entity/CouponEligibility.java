package com.nativewit.coupon.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "coupon_eligibilities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_coupon_eligibility_user_coupon",
                        columnNames = {"user_id", "coupon_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;
}