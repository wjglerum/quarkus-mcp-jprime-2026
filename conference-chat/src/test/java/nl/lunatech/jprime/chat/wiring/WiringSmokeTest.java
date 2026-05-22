package nl.lunatech.jprime.chat.wiring;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.lunatech.jprime.chat.intent.IntentMatcher;
import nl.lunatech.jprime.chat.llm.LlmToolPlanner;
import nl.lunatech.jprime.chat.llm.ProviderRegistry;
import nl.lunatech.jprime.chat.web.ToolDispatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WiringSmokeTest {

    @Inject
    IntentMatcher matcher;

    @Inject
    ToolDispatcher dispatcher;

    @Inject
    ProviderRegistry providers;

    @Inject
    LlmToolPlanner planner;

    @Test
    void coreBeansResolve() {
        assertNotNull(matcher);
        assertNotNull(dispatcher);
        assertNotNull(providers);
        assertNotNull(planner);
    }

    @Test
    void scriptedProviderIsAlwaysAvailableAndDefault() {
        assertEquals(ProviderRegistry.SCRIPTED, providers.activeProvider());
        assertTrue(providers.isAvailable(ProviderRegistry.SCRIPTED));
        assertTrue(providers.list().stream()
                .anyMatch(p -> ProviderRegistry.SCRIPTED.equals(p.id()) && p.available()));
    }

    @Test
    void switchingToScriptedAlwaysSucceeds() {
        ProviderRegistry.SwitchResult r = providers.setActive(ProviderRegistry.SCRIPTED);
        assertTrue(r.ok());
        assertEquals(ProviderRegistry.SCRIPTED, r.active());
    }

    @Test
    void switchingToUnknownProviderIsRejected() {
        ProviderRegistry.SwitchResult r = providers.setActive("nope");
        assertFalse(r.ok());
        assertNotNull(r.error());
        assertTrue(r.error().contains("unknown provider"));
    }
}
