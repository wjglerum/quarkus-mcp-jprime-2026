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
    void filtersByDay() {
        given().queryParam("day", 2)
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .body("day", hasItem(2))
                .body("day.unique()", hasItem(2));
    }

    @Test
    void filtersByQuery() {
        given().queryParam("q", "Concurrency")
                .when().get("/api/v1/sessions")
                .then().statusCode(200)
                .body("size()", greaterThan(0))
                .body("title", hasItem("Concurrency Crossroads: Virtual Threads, Loom and Beyond"));
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
                .body("speakers.size()", greaterThan(0));
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
                .body("name", hasItem("Willem Jan Glerum"));
    }

    @Test
    void roomsListSurfacesAtLeastOneRoom() {
        given().when().get("/api/v1/rooms")
                .then().statusCode(200)
                .body("size()", greaterThan(0));
    }
}
