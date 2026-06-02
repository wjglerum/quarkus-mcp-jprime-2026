package nl.lunatech.jprime.chat.wiring;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import nl.lunatech.jprime.chat.llm.LlmToolPlanner;
import nl.lunatech.jprime.chat.web.QuickPrompts;
import nl.lunatech.jprime.chat.web.ToolDispatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WiringSmokeTest {

    @Inject
    ToolDispatcher dispatcher;

    @Inject
    LlmToolPlanner planner;

    @Inject
    Instance<ChatModel> chatModel;

    @Test
    void coreBeansResolve() {
        assertNotNull(dispatcher);
        assertNotNull(planner);
    }

    @Test
    void anthropicChatModelIsConfigured() {
        assertTrue(chatModel.isResolvable(), "the single Anthropic ChatModel bean must resolve");
    }

    @Test
    void quickPromptsDriveTheDemoScenarios() {
        assertEquals(10, QuickPrompts.all().size());
    }
}
