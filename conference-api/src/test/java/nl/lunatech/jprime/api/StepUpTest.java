package nl.lunatech.jprime.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class StepUpTest {

    private static int wjgSessionId() {
        return given().queryParam("q", "Practical MCP Security")
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .extract().jsonPath().getInt("[0].id");
    }

    @Test
    @TestSecurity(user = "willem.jan", roles = {"attendee", "speaker"})
    void attendeesEndpointRequiresStepUp() {
        int id = wjgSessionId();
        given().when().get("/api/v1/sessions/" + id + "/attendees")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("insufficient_user_authentication"));
    }

    @Test
    @TestSecurity(user = "willem.jan", roles = {"attendee", "speaker"})
    @OidcSecurity(claims = {@Claim(key = "acr", value = "urn:mace:incommon:iap:silver")})
    void attendeesEndpointAllowsAfterStepUp() {
        int id = wjgSessionId();
        given().when().get("/api/v1/sessions/" + id + "/attendees")
                .then().statusCode(200)
                .body("$", notNullValue());
    }

    @Test
    @TestSecurity(user = "attendee-only", roles = {"attendee"})
    void attendeesEndpointForbidsNonSpeakers() {
        int id = wjgSessionId();
        given().when().get("/api/v1/sessions/" + id + "/attendees")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "willem.jan", roles = {"attendee", "speaker"})
    @OidcSecurity(claims = {@Claim(key = "acr", value = "urn:mace:incommon:iap:silver")})
    void cancelSessionIsReversible() {
        int id = wjgSessionId();
        given().contentType("application/json")
                .body("{\"reason\":\"going home early\"}")
                .when().post("/api/v1/sessions/" + id + "/cancel")
                .then().statusCode(200)
                .body("cancelled", org.hamcrest.Matchers.equalTo(true));
        given().contentType("application/json")
                .body("{\"reason\":\"changed mind\"}")
                .when().post("/api/v1/sessions/" + id + "/cancel")
                .then().statusCode(200)
                .body("cancelled", org.hamcrest.Matchers.equalTo(false));
    }
}
