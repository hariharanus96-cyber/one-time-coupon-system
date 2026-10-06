package com.nativewit.coupon.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI couponSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("One-Time Coupon System API")
                        .version("1.0")
                        .description(
                                "API for validating and applying one-time " +
                                        "100% discount coupons per user."
                        ));
    }
}