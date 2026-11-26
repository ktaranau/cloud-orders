package com.jolly.cloud_orders.orders;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        BigDecimal totalSum = request.items().stream().map(item ->
                        item.unitPrice()
                                .multiply(BigDecimal.valueOf(
                                item.quantity()
                        ))
                ).reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = new Order(
                UUID.randomUUID(),
                request.customerEmail(),
                OrderStatus.CREATED,
                totalSum,
                OffsetDateTime.now()
        );
        request.items().forEach(item -> {
            order.addItem(new OrderItem(
                    UUID.randomUUID(),
                    item.sku(),
                    item.quantity(),
                    item.unitPrice()
            ));
        });
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAll()
                .stream()
                .map(OrderResponse::from)
                .toList();
    }
}

