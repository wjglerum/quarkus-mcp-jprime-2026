package nl.lunatech.jprime.api;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Step-up is enforced by Quarkus' {@code @AuthenticationContext} during token verification,
 * so these tests use real signed tokens against the wiremock OIDC server instead of
 * {@code @TestSecurity}, which bypasses that layer.
 */
@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class StepUpTest {

    private static final String MFA_ACR = "urn:jprime:mfa";

    private static String token(String user, Set<String> roles, String acr) {
        JwtClaimsBuilder claims = Jwt.preferredUserName(user)
                .issuer("https://server.example.com")
                .audience("https://service.example.com")
                .claim("realm_access", Map.of("roles", roles));
        if (acr != null) {
            claims.claim("acr", acr);
        }
        return claims.jws().keyId("1").sign("privateKey.jwk");
    }

    private static int wjgSessionId() {
        return given().queryParam("q", "Practical MCP Security")
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .extract().jsonPath().getInt("[0].id");
    }

    @Test
    void attendeesEndpointRequiresStepUp() {
        int id = wjgSessionId();
        given().auth().oauth2(token("willem.jan", Set.of("attendee", "speaker"), null))
                .when().get("/api/v1/sessions/" + id + "/attendees")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("insufficient_user_authentication"))
                .header("WWW-Authenticate", containsString(MFA_ACR));
    }

    @Test
    void attendeesEndpointAllowsAfterStepUp() {
        int id = wjgSessionId();
        given().auth().oauth2(token("willem.jan", Set.of("attendee", "speaker"), MFA_ACR))
                .when().get("/api/v1/sessions/" + id + "/attendees")
                .then().statusCode(200)
                .body("$", notNullValue());
    }

    @Test
    void attendeesEndpointForbidsNonSpeakers() {
        int id = wjgSessionId();
        given().auth().oauth2(token("attendee-only", Set.of("attendee"), MFA_ACR))
                .when().get("/api/v1/sessions/" + id + "/attendees")
                .then().statusCode(403);
    }

    @Test
    void cancelSessionIsReversibleAndAuditsBothDirections() {
        int id = wjgSessionId();
        String token = token("willem.jan", Set.of("attendee", "speaker"), MFA_ACR);
        given().auth().oauth2(token)
                .contentType("application/json")
                .body("{\"reason\":\"going home early\"}")
                .when().post("/api/v1/sessions/" + id + "/cancel")
                .then().statusCode(200)
                .body("cancelled", equalTo(true));
        given().auth().oauth2(token)
                .contentType("application/json")
                .body("{\"reason\":\"changed mind\"}")
                .when().post("/api/v1/sessions/" + id + "/cancel")
                .then().statusCode(200)
                .body("cancelled", equalTo(false));

        given().queryParam("limit", 30)
                .when().get("/api/v1/audit/recent")
                .then().statusCode(200)
                .body("action", hasItem("CANCEL_SESSION"))
                .body("action", hasItem("CANCEL_SESSION_UNDONE"));
    }
}
