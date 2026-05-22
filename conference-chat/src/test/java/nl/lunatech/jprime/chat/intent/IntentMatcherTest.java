package nl.lunatech.jprime.chat.intent;

import nl.lunatech.jprime.chat.intent.IntentMatcher.Intent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the scripted IntentMatcher. The matcher is the demo's safety
 * net so each quick prompt must map to the right MCP tool deterministically.
 */
class IntentMatcherTest {

    private final IntentMatcher matcher = new IntentMatcher();

    @Test
    void quickPromptsAreNonEmpty() {
        assertEquals(10, IntentMatcher.quickPrompts().size(),
                "quick prompts list drives the three demo scenarios");
    }

    @Test
    void whatsOnNowMaps() {
        Intent i = matcher.match("What's happening right now?");
        assertTrue(i.matched());
        assertEquals("whats_on_now", i.tool());
    }

    @Test
    void whatsNextMaps() {
        Intent i = matcher.match("What's coming up next?");
        assertTrue(i.matched());
        assertEquals("whats_next", i.tool());
        assertEquals(3, i.args().get("limit"));
    }

    @Test
    void listSessionsForTitleMaps() {
        Intent i = matcher.match("Find the Practical MCP talk");
        assertTrue(i.matched());
        assertEquals("list_sessions", i.tool());
        assertNotNull(i.args().get("query"));
    }

    @Test
    void bookmarkMapsToBookmarkSession() {
        Intent i = matcher.match("Bookmark the JSpecify talk for me");
        assertTrue(i.matched());
        assertEquals("bookmark_session", i.tool());
        assertNotNull(i.args().get("session_query"));
    }

    @Test
    void agendaMaps() {
        Intent i = matcher.match("Show me my agenda");
        assertTrue(i.matched());
        assertEquals("my_agenda", i.tool());
    }

    @Test
    void conflictsMaps() {
        Intent i = matcher.match("Do I have any conflicts?");
        assertTrue(i.matched());
        assertEquals("my_conflicts", i.tool());
    }

    @Test
    void rateExtractsStarsAndComment() {
        Intent i = matcher.match(
                "Rate the MCP Security talk 5 stars with comment 'great use of caffeine'");
        assertTrue(i.matched());
        assertEquals("rate_session", i.tool());
        assertEquals(5, i.args().get("stars"));
        assertEquals("great use of caffeine", i.args().get("comment"));
    }

    @Test
    void myFeedbackMaps() {
        Intent i = matcher.match("Show feedback on my sessions");
        assertTrue(i.matched());
        assertEquals("my_session_feedback", i.tool());
    }

    @Test
    void whoAttendsMaps() {
        Intent i = matcher.match("Who signed up for my Concurrency Crossroads deep dive?");
        assertTrue(i.matched());
        assertEquals("view_session_attendees", i.tool());
    }

    @Test
    void cancelMaps() {
        Intent i = matcher.match("Cancel my deep dive, reason is I want to go home early");
        assertTrue(i.matched());
        assertEquals("cancel_my_session", i.tool());
        assertNotNull(i.args().get("reason"));
    }

    @Test
    void noMatchFallsBackGracefully() {
        Intent i = matcher.match("Tell me a joke about kubernetes");
        assertFalse(i.matched());
        assertNotNull(i.note());
    }
}
