package nl.lunatech.jprime.api.web;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.domain.Attendee;
import nl.lunatech.jprime.api.domain.Speaker;
import nl.lunatech.jprime.api.security.Tokens;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;

@ApplicationScoped
public class AttendeeService {

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    @Transactional
    public Attendee currentAttendee() {
        String subject = subjectFromJwt();
        Attendee a = Attendee.findBySubject(subject);
        if (a != null) {
            return refreshFromToken(a);
        }
        Attendee fresh = new Attendee();
        fresh.subject = subject;
        fresh.displayName = nameFromJwt();
        fresh.speaker = matchSpeaker(fresh);
        fresh.persist();
        return fresh;
    }

    private Speaker matchSpeaker(Attendee a) {
        if (!hasSpeakerRole()) return null;
        if ("willem.jan".equals(a.subject)) {
            return Speaker.find("name", "Willem Jan Glerum").firstResult();
        }
        return null;
    }

    private String subjectFromJwt() {
        return claim("preferred_username")
                .or(() -> claim("sub"))
                .orElseGet(() -> identity.getPrincipal().getName());
    }

    /** A non-blank string JWT claim, or empty if the token or claim is absent. */
    private Optional<String> claim(String name) {
        Object value = jwt == null ? null : jwt.getClaim(name);
        if (value == null) return Optional.empty();
        String s = String.valueOf(value);
        return s.isBlank() ? Optional.empty() : Optional.of(s);
    }

    public boolean hasSpeakerRole() {
        return identity.getRoles().contains("speaker");
    }

    private Attendee refreshFromToken(Attendee a) {
        boolean dirty = false;
        if (a.displayName == null) {
            a.displayName = nameFromJwt();
            dirty = true;
        }
        if (a.speaker == null && hasSpeakerRole()) {
            Speaker sp = matchSpeaker(a);
            if (sp != null) {
                a.speaker = sp;
                dirty = true;
            }
        }
        if (dirty) a.persist();
        return a;
    }

    private String nameFromJwt() {
        return claim("name")
                .or(() -> claim("preferred_username"))
                .orElseGet(() -> identity.getPrincipal().getName());
    }
}
