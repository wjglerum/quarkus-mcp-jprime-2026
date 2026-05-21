package nl.lunatech.jprime.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class AuditDashboardTest {

    @Test
    void recentEndpointIsOpenForTheSecondScreen() {
        given().when().get("/api/v1/audit/recent")
                .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "dashboard-tester", roles = {"attendee"})
    void recentSurfacesEventsWrittenByOthers() {
        int sessionId = given().queryParam("q", "Keynote")
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .extract().jsonPath().getInt("[0].id");

        given().contentType("application/json")
                .body("{\"sessionId\":" + sessionId + "}")
                .when().post("/api/v1/me/agenda")
                .then().statusCode(200);

        given().queryParam("limit", 5)
                .when().get("/api/v1/audit/recent")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("[0].attendeeSubject", equalTo("dashboard-tester"))
                .body("action", hasItem("BOOKMARK_ADD"));
    }

    @Test
    void liveAuditPageIsServedAsStaticAsset() {
        given().when().get("/audit-live/")
                .then().statusCode(200)
                .body(org.hamcrest.Matchers.containsString("Practical MCP Security in Action"));
    }
}
