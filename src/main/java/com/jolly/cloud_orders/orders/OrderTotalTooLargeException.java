package com.jolly.cloud_orders.orders;

import java.math.BigDecimal;

public class OrderTotalTooLargeException extends RuntimeException {

    public OrderTotalTooLargeException(BigDecimal total) {
        super("Order total " + total + " exceeds the maximum of 9999999999.99");
    }

}
