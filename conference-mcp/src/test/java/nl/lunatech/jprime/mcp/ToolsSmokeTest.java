package nl.lunatech.jprime.mcp;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import nl.lunatech.jprime.mcp.tools.AttendeeTools;
import nl.lunatech.jprime.mcp.tools.PublicTools;
import nl.lunatech.jprime.mcp.tools.SpeakerTools;
import nl.lunatech.jprime.mcp.tools.StepUpTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ToolsSmokeTest {

    @Test
    void allToolBeansAreWired() {
        assertNotNull(Arc.container().instance(PublicTools.class).get());
        assertNotNull(Arc.container().instance(AttendeeTools.class).get());
        assertNotNull(Arc.container().instance(SpeakerTools.class).get());
        assertNotNull(Arc.container().instance(StepUpTools.class).get());
    }
}
