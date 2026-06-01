package nl.lunatech.jprime.chat.intent;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class IntentMatcher {

    private static final Pattern RATING_INLINE = Pattern.compile(
            "rate\\s+(?:session\\s+)?(?<sid>\\d+)?\\s*(?:.*)?\\b(?<stars>[1-5])\\s*stars?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_QUOTED = Pattern.compile(
            "(?:comment|note|saying)\\s+['\"](?<c>[^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SESSION_TITLE_AFTER = Pattern.compile(
            "(?:bookmark|add|register for|sign me up for|signed up for|going to|find|cancel|kill|drop)"
                    + "\\s+(?:the\\s+|my\\s+)?(?<t>[A-Za-z][^,?!]+?)"
                    + "(?:\\s+(?:deep\\s+dive|talk|session|keynote|workshop)|\\?|\\.|$)",
            Pattern.CASE_INSENSITIVE);

    public Intent match(String raw) {
        if (raw == null) return Intent.none();
        String prompt = raw.trim();
        String lower = prompt.toLowerCase(Locale.ENGLISH);

        if (lower.matches(".*\\b(now|right now|currently|on now)\\b.*")
                && lower.matches(".*\\b(what|which|happening|on)\\b.*")) {
            return new Intent("whats_on_now", Map.of(), "Looking up sessions running right now.");
        }
        if (lower.matches(".*\\b(next|coming up|after)\\b.*")
                && !lower.contains("cancel")) {
            return new Intent("whats_next", Map.of("limit", 3), "Pulling up the next three sessions.");
        }
        if (lower.contains("my agenda") || lower.contains("my schedule")
                || (lower.contains("agenda") && lower.contains("me"))) {
            return new Intent("my_agenda", Map.of(), "Fetching your saved agenda.");
        }
        if (lower.contains("conflict") || lower.contains("overlap")) {
            return new Intent("my_conflicts", Map.of(), "Checking for overlapping sessions on your agenda.");
        }
        if (lower.contains("my rating") || lower.contains("ratings i") || lower.contains("rated")) {
            return new Intent("my_ratings", Map.of(), "Listing the ratings you have submitted.");
        }
        if (lower.contains("feedback") && (lower.contains("my") || lower.contains("speaker"))) {
            return new Intent("my_session_feedback", Map.of(),
                    "Fetching the ratings attendees have left on your sessions.");
        }
        boolean asksAboutAttendees = lower.contains("attend")
                || lower.contains("signed up")
                || lower.contains("signing up")
                || lower.contains("registered for")
                || lower.contains("is going to")
                || lower.contains("are going to")
                || lower.contains("attendee list");
        if (lower.contains("who") && asksAboutAttendees) {
            String title = extractGroup(SESSION_TITLE_AFTER, prompt, "t");
            return new Intent("view_session_attendees",
                    title == null ? Map.of() : Map.of("session_query", title),
                    "Asking for the attendee list. This is sensitive and needs step-up auth.");
        }
        if (lower.startsWith("cancel ") || lower.contains("cancel my ") || lower.contains("kill my session")) {
            String reason = extractGroup(Pattern.compile(
                    "(?:because|reason\\s+is|the reason is|since)\\s+(?<r>.+)$",
                    Pattern.CASE_INSENSITIVE), prompt, "r");
            String subject = cancelSubject(prompt);
            return new Intent("cancel_my_session",
                    Map.of(
                            "session_query", subject,
                            "reason", reason == null ? "demo cancellation" : reason),
                    "Cancelling. This is destructive and needs step-up auth.");
        }
        Matcher rate = RATING_INLINE.matcher(prompt);
        boolean rateFound = rate.find();
        if (rateFound || lower.contains("rate")) {
            int stars = 5;
            if (rateFound) {
                try { stars = Integer.parseInt(rate.group("stars")); }
                catch (Exception ignore) { /* default */ }
            }
            String comment = extractGroup(COMMENT_QUOTED, prompt, "c");
            String title = extractGroup(SESSION_TITLE_AFTER, prompt, "t");
            return new Intent("rate_session",
                    Map.of(
                            "session_query", title == null ? "current" : title,
                            "stars", stars,
                            "comment", comment == null ? "" : comment),
                    "Rating with " + stars + " stars" + (comment == null ? "" : " and a comment") + ".");
        }
        if (lower.startsWith("bookmark") || lower.startsWith("add ") || lower.contains("sign me up")
                || lower.contains("register for")) {
            String title = extractGroup(SESSION_TITLE_AFTER, prompt, "t");
            return new Intent("bookmark_session",
                    title == null ? Map.of() : Map.of("session_query", title),
                    "Bookmarking session.");
        }
        boolean mentionsSession = lower.contains("session")
                || lower.contains("talk")
                || lower.contains("keynote")
                || lower.contains("schedule")
                || lower.contains("deep dive")
                || lower.contains("workshop");
        if (mentionsSession) {
            String title = extractGroup(SESSION_TITLE_AFTER, prompt, "t");
            Map<String, Object> args = title == null
                    ? Map.of("query", prompt)
                    : Map.of("query", title);
            return new Intent("list_sessions", args, "Searching the schedule.");
        }
        return Intent.none();
    }

    public static List<QuickPrompt> quickPrompts() {
        return List.of(
                new QuickPrompt("What's happening right now?",     "whats_on_now",         "public"),
                new QuickPrompt("What's coming up next?",          "whats_next",           "public"),
                new QuickPrompt("Find the Practical MCP talk",     "list_sessions",        "public"),
                new QuickPrompt("Bookmark the JSpecify talk for me",   "bookmark_session", "attendee"),
                new QuickPrompt("Show me my agenda",               "my_agenda",            "attendee"),
                new QuickPrompt("Do I have any conflicts?",        "my_conflicts",         "attendee"),
                new QuickPrompt("Rate the MCP Security talk 5 stars with comment 'great use of caffeine'",
                        "rate_session", "attendee"),
                new QuickPrompt("Show feedback on my sessions",    "my_session_feedback",  "speaker"),
                new QuickPrompt("Who signed up for my Concurrency Crossroads deep dive?",
                        "view_session_attendees", "step-up"),
                new QuickPrompt("Cancel my deep dive, reason is I want to go home early",
                        "cancel_my_session", "step-up")
        );
    }

    private static String cancelSubject(String prompt) {
        String subject = prompt.replaceFirst("(?i)^.*?\\bcancel\\s+", "");
        subject = subject.replaceFirst(
                "(?i)[,\\s]*(?:because|reason\\s+is|the reason is|since)\\b.*$", "");
        subject = subject.replaceFirst("(?i)^(?:my|the)\\s+", "");
        subject = subject.replaceAll("[\\s,?.!]+$", "").trim();
        return subject.isEmpty() ? "deep dive" : subject;
    }

    private static String extractGroup(Pattern p, String input, String group) {
        Matcher m = p.matcher(input);
        if (!m.find()) return null;
        try {
            return m.group(group);
        } catch (Exception e) {
            return null;
        }
    }

    public record Intent(String tool, Map<String, Object> args, String note) {
        public static Intent none() {
            return new Intent(null, Map.of(),
                    "I'm not sure which conference tool to use. Try one of the quick prompts.");
        }
        public boolean matched() { return tool != null; }
    }

    public record QuickPrompt(String label, String suggestedTool, String tier) {}
}
