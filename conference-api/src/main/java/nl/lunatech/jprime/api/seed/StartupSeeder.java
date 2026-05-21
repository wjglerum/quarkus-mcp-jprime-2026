package nl.lunatech.jprime.api.seed;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import nl.lunatech.jprime.api.importer.JsoupImporter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class StartupSeeder {

    @Inject
    JsoupImporter importer;

    @Inject
    StaticScheduleSeeder staticSeeder;

    @Inject
    DemoDataSeeder demoSeeder;

    @ConfigProperty(name = "jprime.importer.enabled", defaultValue = "true")
    boolean importerEnabled;

    void onStart(@Observes StartupEvent ev) {
        try {
            staticSeeder.seedIfEmpty();
        } catch (Exception e) {
            Log.warnf(e, "Static schedule seeding failed; continuing with empty schedule");
        }
        if (importerEnabled) {
            try {
                importer.importAgenda();
            } catch (Exception e) {
                Log.warnf(e, "jPrime importer failed at startup; ignoring");
            }
        } else {
            Log.info("jPrime importer disabled by config");
        }
        try {
            demoSeeder.seedIfEmpty();
        } catch (Exception e) {
            Log.warnf(e, "Demo data seeding failed");
        }
    }
}
