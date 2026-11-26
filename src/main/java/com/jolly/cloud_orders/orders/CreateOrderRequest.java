package com.jolly.cloud_orders.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(

        @Email
        @NotBlank
        @Size(max = 255)
        String customerEmail,

        @NotEmpty
        @Size(max = 50)
        List<@Valid CreateOrderItemRequest> items

) {
}

