package nl.lunatech.jprime.chat.web;

import java.util.List;

/**
 * Demo-ready prompt suggestions shown in the sidebar. Each prompt hints at the
 * MCP tool the LLM is expected to pick and the access tier it exercises.
 */
public final class QuickPrompts {

    private QuickPrompts() {
    }

    public static List<QuickPrompt> all() {
        return List.of(
                new QuickPrompt("What's happening right now?", "whats_on_now", "public"),
                new QuickPrompt("What's coming up next?", "whats_next", "public"),
                new QuickPrompt("Find the Practical MCP talk", "list_sessions", "public"),
                new QuickPrompt("Bookmark the JSpecify talk for me", "bookmark_session", "attendee"),
                new QuickPrompt("Show me my agenda", "my_agenda", "attendee"),
                new QuickPrompt("Do I have any conflicts?", "my_conflicts", "attendee"),
                new QuickPrompt("Rate the MCP Security talk 5 stars with comment 'great use of caffeine'",
                        "rate_session", "attendee"),
                new QuickPrompt("Show feedback on my sessions", "my_session_feedback", "speaker"),
                new QuickPrompt("Who signed up for my Concurrency Crossroads deep dive?",
                        "view_session_attendees", "step-up"),
                new QuickPrompt("Cancel my deep dive, reason is I want to go home early",
                        "cancel_my_session", "step-up"));
    }

    public record QuickPrompt(String label, String suggestedTool, String tier) {
    }
}
