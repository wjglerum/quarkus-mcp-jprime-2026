package nl.lunatech.jprime.mcp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.security.StepUp;
import nl.lunatech.jprime.mcp.tools.AttendeeTools;
import nl.lunatech.jprime.mcp.tools.PublicTools;
import nl.lunatech.jprime.mcp.tools.SpeakerTools;
import nl.lunatech.jprime.mcp.tools.StepUpTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ToolsSmokeTest {

    @Inject
    PublicTools publicTools;

    @Inject
    AttendeeTools attendeeTools;

    @Inject
    SpeakerTools speakerTools;

    @Inject
    StepUpTools stepUpTools;

    @Inject
    StepUp stepUp;

    @Test
    void allToolBeansAreInjectable() {
        assertNotNull(publicTools);
        assertNotNull(attendeeTools);
        assertNotNull(speakerTools);
        assertNotNull(stepUpTools);
        assertNotNull(stepUp);
    }
}
