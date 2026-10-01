package com.jolly.cloud_orders;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CloudOrdersApplicationTests.Containers.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JwtSecurityTests {

    private static final String AUDIENCE = "cloud-orders-api";
    private static final String KEY_ID = "test-key";
    private static final RSAKey TRUSTED_KEY = newKey();
    private static final RSAKey OTHER_KEY = newKey();
    private static final HttpServer JWKS_SERVER = startJwksServer();
    private static final String ISSUER = "http://127.0.0.1:"
            + JWKS_SERVER.getAddress().getPort() + "/test-issuer";

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:"
                        + JWKS_SERVER.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Test
    void acceptsValidSignedToken() throws Exception {
        String token = sign(validClaims().build(), TRUSTED_KEY);

        requestOrders(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void rejectsTokenSignedByAnotherKey() throws Exception {
        String token = sign(validClaims().build(), OTHER_KEY);

        requestOrders(token).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        String token = sign(validClaims()
                .issuer("https://other-issuer.example")
                .build(), TRUSTED_KEY);

        requestOrders(token).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        String token = sign(validClaims()
                .audience("another-api")
                .build(), TRUSTED_KEY);

        requestOrders(token).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = sign(validClaims()
                .expirationTime(Date.from(Instant.now().minusSeconds(300)))
                .build(), TRUSTED_KEY);

        requestOrders(token).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenNotYetValid() throws Exception {
        String token = sign(validClaims()
                .notBeforeTime(Date.from(Instant.now().plusSeconds(300)))
                .build(), TRUSTED_KEY);

        requestOrders(token).andExpect(status().isUnauthorized());
    }

    private ResultActions requestOrders(String token) throws Exception {
        return mockMvc.perform(get("/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("test-service-account")
                .audience(AUDIENCE)
                .issueTime(Date.from(now.minusSeconds(600)))
                .notBeforeTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.plusSeconds(600)));
    }

    private static String sign(JWTClaimsSet claims, RSAKey key)
            throws JOSEException {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(KEY_ID)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Cannot generate test RSA key", exception);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = new JWKSet(TRUSTED_KEY.toPublicJWK())
                    .toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                try {
                    exchange.getResponseHeaders()
                            .set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start test JWKS server", exception);
        }
    }
}