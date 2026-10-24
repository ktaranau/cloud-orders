package com.jolly.cloud_orders;

import com.jolly.cloud_orders.orders.CreateOrderRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "bad-email"})
    void rejectsInvalidEmail(String email) {
        var request = new CreateOrderRequest(email, new BigDecimal("12.34"));
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("customerEmail");
    }

    @Test
    void rejectsEmailLongerThanDatabaseColumn() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "."
                + "c".repeat(63) + "." + "d".repeat(61) + ".com";
        var request = new CreateOrderRequest(email, new BigDecimal("12.34"));
        assertThat(email).hasSize(258);
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("customerEmail");
    }

    @Test
    void acceptsEmailAtDatabaseColumnLimit() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "."
                + "c".repeat(63) + "." + "d".repeat(58) + ".com";
        var request = new CreateOrderRequest(email, new BigDecimal("12.34"));
        assertThat(email).hasSize(255);
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01", "0.001", "1.234", "10000000000.00"})
    void rejectsAmountOutsideStorageContract(String amount) {
        var request = new CreateOrderRequest("validation@example.com",
                amount == null ? null : new BigDecimal(amount));
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("totalAmount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "1.20", "9999999999.99"})
    void acceptsAmountWithinStorageContract(String amount) {
        var request = new CreateOrderRequest("validation@example.com", new BigDecimal(amount));
        assertThat(validator.validate(request)).isEmpty();
    }
}
