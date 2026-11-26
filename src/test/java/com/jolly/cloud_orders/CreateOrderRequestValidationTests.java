package com.jolly.cloud_orders;

import com.jolly.cloud_orders.orders.CreateOrderItemRequest;
import com.jolly.cloud_orders.orders.CreateOrderRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTests {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    // --- customerEmail ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "bad-email"})
    void rejectsInvalidEmail(String email) {
        assertThat(violatedFields(requestWithEmail(email))).contains("customerEmail");
    }

    @Test
    void rejectsEmailLongerThanDatabaseColumn() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "."
                + "c".repeat(63) + "." + "d".repeat(61) + ".com";
        assertThat(email).hasSize(258);
        assertThat(violatedFields(requestWithEmail(email))).contains("customerEmail");
    }

    @Test
    void acceptsEmailAtDatabaseColumnLimit() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "."
                + "c".repeat(63) + "." + "d".repeat(58) + ".com";
        assertThat(email).hasSize(255);
        assertThat(violatedFields(requestWithEmail(email))).isEmpty();
    }

    // --- items list ---

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingOrEmptyItems(List<CreateOrderItemRequest> items) {
        var request = new CreateOrderRequest("validation@example.com", items);
        assertThat(violatedFields(request)).contains("items");
    }

    @Test
    void rejectsMoreThanFiftyItems() {
        var request = new CreateOrderRequest("validation@example.com",
                Collections.nCopies(51, validItem()));
        assertThat(violatedFields(request)).contains("items");
    }

    @Test
    void acceptsFiftyItems() {
        var request = new CreateOrderRequest("validation@example.com",
                Collections.nCopies(50, validItem()));
        assertThat(violatedFields(request)).isEmpty();
    }

    // --- item.sku ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsBlankSku(String sku) {
        var item = new CreateOrderItemRequest(sku, 1, new BigDecimal("12.34"));
        assertThat(violatedFields(requestWithItem(item))).contains("items[0].sku");
    }

    @Test
    void rejectsSkuLongerThanDatabaseColumn() {
        var item = new CreateOrderItemRequest("S".repeat(65), 1, new BigDecimal("12.34"));
        assertThat(violatedFields(requestWithItem(item))).contains("items[0].sku");
    }

    @Test
    void acceptsSkuAtDatabaseColumnLimit() {
        var item = new CreateOrderItemRequest("S".repeat(64), 1, new BigDecimal("12.34"));
        assertThat(violatedFields(requestWithItem(item))).isEmpty();
    }

    // --- item.quantity ---

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 1001})
    void rejectsQuantityOutsideAllowedRange(Integer quantity) {
        var item = new CreateOrderItemRequest("BOOK-1", quantity, new BigDecimal("12.34"));
        assertThat(violatedFields(requestWithItem(item))).contains("items[0].quantity");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000})
    void acceptsQuantityAtAllowedBounds(Integer quantity) {
        var item = new CreateOrderItemRequest("BOOK-1", quantity, new BigDecimal("12.34"));
        assertThat(violatedFields(requestWithItem(item))).isEmpty();
    }

    // --- item.unitPrice ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01", "0.001", "1.234", "10000000000.00"})
    void rejectsUnitPriceOutsideStorageContract(String price) {
        var item = new CreateOrderItemRequest("BOOK-1", 1,
                price == null ? null : new BigDecimal(price));
        assertThat(violatedFields(requestWithItem(item))).contains("items[0].unitPrice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "1.20", "9999999999.99"})
    void acceptsUnitPriceWithinStorageContract(String price) {
        var item = new CreateOrderItemRequest("BOOK-1", 1, new BigDecimal(price));
        assertThat(violatedFields(requestWithItem(item))).isEmpty();
    }

    // --- helpers ---

    private static CreateOrderItemRequest validItem() {
        return new CreateOrderItemRequest("BOOK-1", 1, new BigDecimal("12.34"));
    }

    private static CreateOrderRequest requestWithEmail(String email) {
        return new CreateOrderRequest(email, List.of(validItem()));
    }

    private static CreateOrderRequest requestWithItem(CreateOrderItemRequest item) {
        return new CreateOrderRequest("validation@example.com", List.of(item));
    }

    /** Paths of all violated fields, e.g. "customerEmail" or "items[0].unitPrice". */
    private static Set<String> violatedFields(CreateOrderRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
