package nl.lunatech.jprime.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class PublicEndpointsTest {

    @Test
    void listsSessions() {
        given()
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .body("size()", greaterThan(0))
                .body("title", hasItem("Practical MCP Security in Action"));
    }

    @Test
    void filtersByQuery() {
        given().queryParam("q", "Concurrency Crossroads")
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .body("size()", greaterThan(0))
                .body("title[0]", org.hamcrest.Matchers.containsString("Concurrency Crossroads"));
    }

    @Test
    void filtersBySpeakerId() {
        Integer speakerId = given().when().get("/api/v1/speakers")
                .then().statusCode(200)
                .extract().jsonPath()
                .getInt("find { it.name == 'Willem Jan Glerum' }.id");
        given().queryParam("speaker_id", speakerId)
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .body("size()", greaterThan(0))
                .body("title", hasItem("Practical MCP Security in Action"));
    }

    @Test
    void getsSingleSession() {
        Integer id = given().queryParam("q", "Practical MCP Security")
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .extract().jsonPath().getInt("[0].id");
        given().when().get("/api/v1/sessions/{id}", id)
                .then().statusCode(200)
                .body("title", notNullValue())
                .body("speaker", notNullValue());
    }

    @Test
    void currentSessionsAtRehearsalClock() {
        given().queryParam("at", "2026-06-03T10:15:00+03:00")
                .when().get("/api/v1/sessions/current")
                .then().statusCode(200)
                .body("title", hasItem("Practical MCP Security in Action"));
    }

    @Test
    void nextSessionsRespectsLimit() {
        given().queryParam("at", "2026-06-03T09:55:00+03:00")
                .queryParam("limit", 2)
                .when().get("/api/v1/sessions/next")
                .then().statusCode(200)
                .body("size()", org.hamcrest.Matchers.lessThanOrEqualTo(2));
    }

    @Test
    void listsSpeakers() {
        given().when().get("/api/v1/speakers")
                .then().statusCode(200)
                .body("name", hasItem("Willem Jan Glerum"))
                .body("find { it.name == 'Willem Jan Glerum' }.sessions.size()", greaterThan(0));
    }
}
