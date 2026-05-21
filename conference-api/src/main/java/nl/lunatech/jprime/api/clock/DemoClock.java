package nl.lunatech.jprime.api.clock;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

@ApplicationScoped
public class DemoClock {

    public static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");

    @ConfigProperty(name = "demo.now")
    Optional<String> demoNow;

    public OffsetDateTime now() {
        return demoNow
                .filter(s -> !s.isBlank())
                .map(OffsetDateTime::parse)
                .orElseGet(() -> OffsetDateTime.now(SOFIA));
    }

    public OffsetDateTime at(String iso) {
        return iso == null || iso.isBlank() ? now() : OffsetDateTime.parse(iso);
    }
}
