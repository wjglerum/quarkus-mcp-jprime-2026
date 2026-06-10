package nl.lunatech.jprime.api.audit;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.domain.AuditEvent;
import nl.lunatech.jprime.api.security.Tokens;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@ApplicationScoped
public class AuditService {

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
        event.tokenAcr = Tokens.acr(jwt);
        event.tokenAmr = Tokens.amr(jwt);
        event.executedByClient = Tokens.azp(jwt);
        event.tokenIss = Tokens.iss(jwt);
        // Audit entries record when the action actually happened, in real time,
        // independent of the simulated conference clock used for the schedule.
        event.createdAt = OffsetDateTime.now(ZoneOffset.of("+03:00"));
        event.detail = detail;
        event.persist();
        Log.infof("AUDIT subject=%s action=%s target=%s acr=%s amr=%s client=%s iss=%s",
                event.attendeeSubject, event.action, event.target, event.tokenAcr, event.tokenAmr,
                event.executedByClient, event.tokenIss);
    }

    private String resolveSubject() {
        if (jwt != null) {
            // Prefer the human-readable username over the opaque "sub" UUID so the audit
            // trail attributes actions to a name (e.g. willem.jan), matching attendee records.
            Object preferredUsername = jwt.getClaim("preferred_username");
            if (preferredUsername != null && !String.valueOf(preferredUsername).isBlank()) {
                return String.valueOf(preferredUsername);
            }
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
