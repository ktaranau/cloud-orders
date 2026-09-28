package com.jolly.cloud_orders.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                .permitAll()
                                .requestMatchers("/orders", "/orders/**")
                                .authenticated()
                                .anyRequest().denyAll()
                        )
                .httpBasic(Customizer.withDefaults())
                .csrf(Customizer.withDefaults());
        return http.build();
    }

}