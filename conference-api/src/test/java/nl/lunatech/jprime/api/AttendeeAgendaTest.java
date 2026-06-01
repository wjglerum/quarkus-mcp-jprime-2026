package nl.lunatech.jprime.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class AttendeeAgendaTest {

    private static int bookmarkableSessionId() {
        return given().queryParam("q", "Practical MCP Security")
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .extract().jsonPath().getInt("[0].id");
    }

    @Test
    @TestSecurity(user = "attendee-test", roles = {"attendee"})
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "attendee-test"),
            @Claim(key = "name", value = "Test Attendee")
    })
    void agendaCrud() {
        int sessionId = bookmarkableSessionId();

        given().when().get("/api/v1/me")
                .then().statusCode(200)
                .body("subject", equalTo("attendee-test"));

        given().contentType("application/json")
                .body("{\"sessionId\":" + sessionId + "}")
                .when().post("/api/v1/me/agenda")
                .then().statusCode(200)
                .body("sessionId", equalTo(sessionId));

        given().when().get("/api/v1/me/agenda")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));

        given().queryParam("limit", 30)
                .when().get("/api/v1/audit/recent")
                .then().statusCode(200)
                .body("action", hasItem("BOOKMARK_ADD"));

        given().when().delete("/api/v1/me/agenda/" + sessionId)
                .then().statusCode(204);
    }

    @Test
    void anonymousUserCannotAccessMe() {
        given().when().get("/api/v1/me").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "attendee-without-role", roles = {})
    void userWithoutAttendeeRoleIsForbidden() {
        given().when().get("/api/v1/me").then().statusCode(403);
    }
}
