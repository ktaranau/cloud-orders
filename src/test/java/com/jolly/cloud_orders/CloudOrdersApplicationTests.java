package com.jolly.cloud_orders;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.jolly.cloud_orders.orders.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CloudOrdersApplicationTests.Containers.class)
class CloudOrdersApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepository orderRepository;


    @BeforeEach
    void clearOrders() {
        orderRepository.deleteAll();
    }

    @Test
    void rejectsAnonymousOrdersRequest() throws Exception {
        mockMvc.perform(get("/orders")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsMinimumUnitPrice() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerEmail":"email@mail.com",
                                 "items":[{"sku":"BOOK-1","quantity":1,"unitPrice":0.01}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.totalAmount").value(0.01));

        assertThat(orderRepository.count()).isEqualTo(1L);

    }

    @Test
    void rejectsOrderWithoutItemsWithoutSavingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerEmail":"email@mail.com","items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("items")));

        assertThat(orderRepository.count()).isZero();
    }


    @Test
    void returns404ForMissingOrder() throws Exception {
        mockMvc.perform(get("/orders/{id}", "00000000-0000-0000-0000-000000000001")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsZeroUnitPriceWithoutSavingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerEmail":"email@mail.com",
                                 "items":[{"sku":"BOOK-1","quantity":1,"unitPrice":0}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(hasItem("items[0].unitPrice")));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void rejectsInvalidEmailWithoutSavingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerEmail":"bad-email",
                                 "items":[{"sku":"BOOK-1","quantity":1,"unitPrice":123.45}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(hasItem("customerEmail")));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void createsAndReadsOrder() throws Exception {
        String response = mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerEmail":"integration@example.com",
                                 "items":[
                                   {"sku":"BOOK-1","quantity":2,"unitPrice":10.50},
                                   {"sku":"PEN-7","quantity":1,"unitPrice":3.00}
                                 ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.totalAmount").value(24.00))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/orders/{id}", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerEmail").value("integration@example.com"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(24.00))
                .andExpect(jsonPath("$.items.length()").value(2))
                // The database does not guarantee item order, so check membership only.
                .andExpect(jsonPath("$.items[*].sku").value(containsInAnyOrder("BOOK-1", "PEN-7")));
    }

    @Test
    void rejectsEmptyEmailWithoutSavingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerEmail":"",
                                 "items":[{"sku":"BOOK-1","quantity":1,"unitPrice":12.34}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("customerEmail")));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void rejectsExcessFractionalDigitsWithoutSavingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerEmail":"validation@example.com",
                                 "items":[{"sku":"BOOK-1","quantity":1,"unitPrice":1.234}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("items[0].unitPrice")));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void rejectsOversizedAmountWithoutSavingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerEmail":"validation@example.com",
                                 "items":[{"sku":"BOOK-1","quantity":1,"unitPrice":10000000000.00}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("items[0].unitPrice")));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void rejectsInfoEvenForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_orders:read"),
                                new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAnonymousReadinessCheck() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void rejectsAnonymousPostWithoutSavingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "customerEmail": "security@example.com",
                                "items": [{"sku": "BOOK-1", "quantity": 1, "unitPrice": 123.45}]
                                }
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void rejectsMalformedBearerToken() throws Exception {
        mockMvc.perform(get("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsReadOnlyTokenWhenCreatingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_orders:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"customerEmail":"scope@example.com",
                             "items":[{"sku":"BOOK-1","quantity":1,"unitPrice":12.34}]}
                            """))
                .andExpect(status().isForbidden());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void rejectsWriteOnlyTokenWhenReadingOrders() throws Exception {
        mockMvc.perform(get("/orders")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTokenWithoutAuthoritiesWhenReadingOrders() throws Exception {
        mockMvc.perform(get("/orders")
                        .with(jwt().authorities(Collections.emptyList())))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTokenWithoutAuthoritiesWhenCreatingOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(Collections.emptyList()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"customerEmail":"scope@example.com",
                             "items":[{"sku":"BOOK-1","quantity":1,"unitPrice":12.34}]}
                            """))
                .andExpect(status().isForbidden());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void contextLoads() {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:16-alpine");
        }
    }
}
