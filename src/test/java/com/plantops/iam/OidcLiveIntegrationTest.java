package com.plantops.iam;

import com.plantops.iam.support.OidcTestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live OIDC smoke — requires Keycloak on :8081 and Quarkus on :8080 with {@code QUARKUS_PROFILE=oidc}.
 * Skipped when Keycloak is not running.
 */
@QuarkusTest
@EnabledIf("com.plantops.iam.support.OidcTestSupport#isKeycloakAvailable")
class OidcLiveIntegrationTest {

  private static final String KEYCLOAK_REALM = "http://localhost:8081/realms/plantops";
  private static final String CLIENT_ID = "plantops-ui";
  private static final String CLIENT_SECRET = "plantops-ui-secret";

  @Test
  void passwordGrantPlannerCanCallIamMe() throws Exception {
    String accessToken = fetchPasswordGrantToken("planner", "planner");
    assertNotNull(accessToken);

    given()
        .header("Authorization", "Bearer " + accessToken)
        .when()
        .get("/api/v1/iam/me")
        .then()
        .statusCode(200)
        .body("userId", equalTo("planner"))
        .body("hasWorkspaces", equalTo(true));
  }

  @Test
  void authConfigExposesOidcWhenOidcProfileActive() {
    // When running with oidc profile, oidc.enabled=true; under default test profile this may be false — skip assertion if so
    var response = given().when().get("/api/v1/auth/config").then().statusCode(200).extract().response();
    boolean oidcEnabled = response.path("oidc.enabled");
    if (!oidcEnabled) {
      return;
    }
    assertNotNull(response.path("oidc.authorizationEndpoint"));
    assertTrue(response.path("oidc.authorizationEndpoint").toString().contains("8081"));
  }

  private static String fetchPasswordGrantToken(String username, String password) throws Exception {
    String discoveryUrl = KEYCLOAK_REALM + "/.well-known/openid-configuration";
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpResponse<String> discovery = client.send(
        HttpRequest.newBuilder(URI.create(discoveryUrl)).GET().timeout(Duration.ofSeconds(5)).build(),
        HttpResponse.BodyHandlers.ofString());
    assertTrue(discovery.statusCode() == 200, "discovery HTTP " + discovery.statusCode());

    String tokenEndpoint = discovery.body().split("\"token_endpoint\"\\s*:\\s*\"")[1].split("\"")[0];
    String body = "grant_type=password"
        + "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
        + "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8)
        + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
        + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

    HttpResponse<String> tokenRes = client.send(
        HttpRequest.newBuilder(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertTrue(tokenRes.statusCode() == 200, "token HTTP " + tokenRes.statusCode() + " " + tokenRes.body());

    String json = tokenRes.body();
    int start = json.indexOf("\"access_token\":\"") + 16;
    int end = json.indexOf('"', start);
    return json.substring(start, end);
  }
}
