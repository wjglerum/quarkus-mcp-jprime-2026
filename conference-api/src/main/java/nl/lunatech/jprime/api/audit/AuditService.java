package nl.lunatech.jprime.api.audit;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.clock.DemoClock;
import nl.lunatech.jprime.api.domain.AuditEvent;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class AuditService {

    @Inject
    DemoClock clock;

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    @Transactional
    public void record(String action, String target, String detail) {
        AuditEvent event = new AuditEvent();
        event.attendeeSubject = resolveSubject();
        event.action = action;
        event.target = target;
        event.tokenAcr = jwt == null ? null : jwt.getClaim("acr");
        Object amr = jwt == null ? null : jwt.getClaim("amr");
        if (amr instanceof Iterable<?> it) {
            event.tokenAmr = StreamSupport.stream(it.spliterator(), false)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        } else if (amr != null) {
            event.tokenAmr = amr.toString();
        }
        event.createdAt = clock.now();
        event.detail = detail;
        event.persist();
        Log.infof("AUDIT subject=%s action=%s target=%s acr=%s amr=%s",
                event.attendeeSubject, event.action, event.target, event.tokenAcr, event.tokenAmr);
    }

    private String resolveSubject() {
        if (jwt != null) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) return sub;
        }
        if (identity != null && identity.getPrincipal() != null) {
            String name = identity.getPrincipal().getName();
            if (name != null && !name.isBlank()) return name;
        }
        return "anonymous";
    }
}
