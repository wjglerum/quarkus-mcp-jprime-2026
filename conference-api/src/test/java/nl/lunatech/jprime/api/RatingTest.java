package nl.lunatech.jprime.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(RatingTest.RehearsalClockProfile.class)
class RatingTest {

    public static class RehearsalClockProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("demo.now", "2026-06-03T13:30:00+03:00");
        }
    }

    private static int sessionId(String query) {
        return given().queryParam("q", query)
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .extract().jsonPath().getInt("[0].id");
    }

    @Test
    @TestSecurity(user = "rater-1", roles = {"attendee"})
    void rateSessionThatHasStartedSucceeds() {
        int id = sessionId("Practical MCP Security");
        given().contentType("application/json")
                .body("{\"stars\":5,\"comment\":\"great use of caffeine\"}")
                .when().post("/api/v1/sessions/" + id + "/ratings")
                .then().statusCode(201)
                .body("stars", equalTo(5));
    }

    @Test
    @TestSecurity(user = "rater-2", roles = {"attendee"})
    void rateSessionThatHasNotStartedReturns422() {
        int id = sessionId("Beyond the LLM API");
        given().contentType("application/json")
                .body("{\"stars\":4,\"comment\":\"too early\"}")
                .when().post("/api/v1/sessions/" + id + "/ratings")
                .then().statusCode(422)
                .body("error", equalTo("session_not_started"));
    }
}
