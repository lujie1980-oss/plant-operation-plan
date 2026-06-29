package com.plantops.iam.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Gate live OIDC tests on Keycloak availability (port 8081). */
public final class OidcTestSupport {

    private OidcTestSupport() {}

    public static boolean isKeycloakAvailable() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://localhost:8081/realms/plantops/.well-known/openid-configuration"))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("token_endpoint");
        } catch (Exception e) {
            return false;
        }
    }
}
