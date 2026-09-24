package com.jolly.cloud_orders;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.jolly.cloud_orders.orders.OrderRepository;
import org.junit.jupiter.api.BeforeEach;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
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
void acceptsMinimumAmount() throws Exception {
    mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"customerEmail":"email@mail.com","totalAmount":0.02}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.totalAmount").value(0.01));
                    
    assertThat(orderRepository.count()).isEqualTo(1L);
	
}


@Test
void returns404ForMissingOrder() throws Exception {
    mockMvc.perform(get("/orders/{id}", "00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
}

@Test
void rejectsZeroAmountWithoutSavingOrder() throws Exception {
    mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"customerEmail":"email@mail.com","totalAmount":0}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field")
                    .value(hasItem("totalAmount")));

    assertThat(orderRepository.count()).isZero();
}

@Test
void rejectsInvalidEmailWithoutSavingOrder() throws Exception {
    mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"customerEmail":"bad-email","totalAmount":123.45}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field")
                    .value(hasItem("customerEmail")));

    assertThat(orderRepository.count()).isZero();
}

@Test
void createsAndReadsOrder() throws Exception {
    String response = mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"customerEmail":"integration@example.com","totalAmount":123.45}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

    String id = JsonPath.read(response, "$.id");

    mockMvc.perform(get("/orders/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.customerEmail").value("integration@example.com"))
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.totalAmount").value(123.45));
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
