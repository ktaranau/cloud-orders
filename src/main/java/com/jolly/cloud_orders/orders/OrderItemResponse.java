package com.jolly.cloud_orders.orders;

import java.math.BigDecimal;

public record OrderItemResponse(

        String sku,

        int quantity,

        BigDecimal unitPrice

) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getSku(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }

}
