package com.jolly.cloud_orders.orders;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @Email
        @NotNull
        String customerEmail,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal totalAmount
) {
}

