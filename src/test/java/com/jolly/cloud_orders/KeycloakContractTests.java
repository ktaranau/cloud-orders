package com.jolly.cloud_orders;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class KeycloakContractTests {

    private static final String WORKER_SECRET = "test-only-worker-secret";
    private static final String READER_SECRET = "test-only-reader-secret";

    @Container
    private static final GenericContainer<?> KEYCLOAK =
            new GenericContainer<>(DockerImageName.parse(
                    "quay.io/keycloak/keycloak:26.7.4"))
                    .withEnv("KC_DB", "dev-file")
                    .withEnv("KC_HOSTNAME_STRICT", "false")
                    .withEnv("ORDERS_WORKER_SECRET", WORKER_SECRET)
                    .withEnv("ORDERS_READER_SECRET", READER_SECRET)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(
                                    "infra/keycloak/cloud-orders-realm.json"),
                            "/opt/keycloak/data/import/cloud-orders-realm.json")
                    .withCommand("start-dev", "--import-realm")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp(
                                    "/realms/cloud-orders/.well-known/openid-configuration")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(4)));

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @AfterAll
    static void closeHttpClient() {
        HTTP.close();
    }

    @Test
    void workerCanRequestReadAndWrite() throws Exception {
        HttpResponse<String> response = requestToken(
                "orders-worker", WORKER_SECRET, "orders:read orders:write");

        JWTClaimsSet claims = readSuccessfulToken(response);

        assertThat(scopes(claims)).containsExactlyInAnyOrder(
                "orders:read", "orders:write");
    }

    @Test
    void workerCanRequestOnlyRead() throws Exception {
        HttpResponse<String> response = requestToken(
                "orders-worker", WORKER_SECRET, "orders:read");
        JWTClaimsSet claims = readSuccessfulToken(response);
        assertThat(scopes(claims)).containsExactly("orders:read");
    }

    @Test
    void readerCanRequestRead() throws Exception {
        HttpResponse<String> response = requestToken(
                "orders-reader", READER_SECRET, "orders:read");

        JWTClaimsSet claims = readSuccessfulToken(response);

        assertThat(scopes(claims)).containsExactly("orders:read");
    }

    @Test
    void readerWithoutRequestedScopeHasNoScopes() throws Exception {
        HttpResponse<String> response = requestToken(
                "orders-reader", READER_SECRET, null);

        JWTClaimsSet claims = readSuccessfulToken(response);

        assertThat(scopes(claims)).isEmpty();
    }

    @Test
    void rejectsWriteScopeForReader() throws Exception {
        HttpResponse<String> response = requestToken(
                "orders-reader", READER_SECRET, "orders:write");

        assertInvalidScope(response);
    }

    @Test
    void rejectsMixedScopesForReader() throws Exception {
        HttpResponse<String> response = requestToken(
                "orders-reader", READER_SECRET, "orders:read orders:write");

        assertInvalidScope(response);
    }

    @Test
    void rejectsWrongClientSecret() throws Exception {
        HttpResponse<String> response = requestToken(
                "orders-reader", "incorrect-test-secret", "orders:read");

        assertThat(response.statusCode()).isEqualTo(401);
        Map<String, Object> body = JsonPath.read(response.body(), "$");
        assertThat(body).doesNotContainKey("access_token");
        assertThat((String) body.get("error")).isNotBlank();
    }

    private static HttpResponse<String> requestToken(
            String clientId, String secret, String requestedScope) throws Exception {
        String form = "grant_type=client_credentials";
        if (requestedScope != null) {
            form += "&scope=" + URLEncoder.encode(
                    requestedScope, StandardCharsets.UTF_8);
        }

        String credentials = clientId + ":" + secret;
        String basic = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuer() + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JWTClaimsSet readSuccessfulToken(HttpResponse<String> response)
            throws ParseException {
        assertThat(response.statusCode()).isEqualTo(200);

        String tokenType = JsonPath.read(response.body(), "$.token_type");
        String accessToken = JsonPath.read(response.body(), "$.access_token");
        assertThat(tokenType).isEqualTo("Bearer");
        assertThat(accessToken).isNotBlank();

        // Parsing claims here is not signature verification.
        JWTClaimsSet claims = SignedJWT.parse(accessToken).getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(issuer());
        assertThat(claims.getAudience()).contains("cloud-orders-api");
        return claims;
    }

    private static void assertInvalidScope(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(400);
        Map<String, Object> body = JsonPath.read(response.body(), "$");
        assertThat(body.get("error")).isEqualTo("invalid_scope");
        assertThat(body).doesNotContainKey("access_token");
    }

    private static Set<String> scopes(JWTClaimsSet claims) throws ParseException {
        String scope = claims.getStringClaim("scope");
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scope.trim().split("\\s+"))
                .collect(Collectors.toSet());
    }

    private static String issuer() {
        return "http://" + KEYCLOAK.getHost() + ":"
                + KEYCLOAK.getMappedPort(8080) + "/realms/cloud-orders";
    }
}