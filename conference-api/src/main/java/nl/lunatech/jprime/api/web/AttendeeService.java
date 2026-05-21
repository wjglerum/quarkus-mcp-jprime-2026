package nl.lunatech.jprime.api.web;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.domain.Attendee;
import nl.lunatech.jprime.api.domain.Speaker;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

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
        fresh.email = jwt.getClaim("email");
        fresh.isSpeaker = hasSpeakerRole();
        fresh.speaker = matchSpeaker(fresh);
        fresh.persist();
        return fresh;
    }

    private Speaker matchSpeaker(Attendee a) {
        if (!a.isSpeaker) return null;
        if (a.displayName != null) {
            Speaker byName = Speaker.find("lower(name) = ?1", a.displayName.toLowerCase()).firstResult();
            if (byName != null) return byName;
        }
        if (a.email != null) {
            Speaker byHandle = Speaker.find("twitterHandle = ?1", a.email).firstResult();
            if (byHandle != null) return byHandle;
        }
        // Pragmatic demo shortcut: subject "willem.jan" -> Willem Jan speaker.
        if ("willem.jan".equals(a.subject)) {
            return Speaker.find("lower(name) = ?1", "willem jan glerum").firstResult();
        }
        return null;
    }

    private String subjectFromJwt() {
        if (jwt != null) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) return sub;
        }
        return identity.getPrincipal().getName();
    }

    public boolean hasAttendeeRole() {
        return jwt.getGroups().contains("attendee");
    }

    public boolean hasSpeakerRole() {
        return jwt.getGroups().contains("speaker");
    }

    public boolean hasStrongAcr() {
        Object acr = jwt.getClaim("acr");
        if (acr != null && ("2".equals(acr.toString())
                || "urn:mace:incommon:iap:silver".equals(acr.toString()))) {
            return true;
        }
        Object amr = jwt.getClaim("amr");
        if (amr instanceof Iterable<?> it) {
            for (Object o : it) {
                if ("mfa".equals(String.valueOf(o)) || "otp".equals(String.valueOf(o))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Attendee refreshFromToken(Attendee a) {
        boolean dirty = false;
        if (a.displayName == null) {
            a.displayName = nameFromJwt();
            dirty = true;
        }
        if (a.email == null && jwt.containsClaim("email")) {
            a.email = jwt.getClaim("email");
            dirty = true;
        }
        boolean speakerRole = hasSpeakerRole();
        if (a.isSpeaker != speakerRole) {
            a.isSpeaker = speakerRole;
            dirty = true;
        }
        if (dirty) a.persist();
        return a;
    }

    private String nameFromJwt() {
        if (jwt != null) {
            String name = jwt.getClaim("name");
            if (name != null && !name.isBlank()) return name;
            String preferred = jwt.getClaim("preferred_username");
            if (preferred != null && !preferred.isBlank()) return preferred;
        }
        return identity.getPrincipal().getName();
    }

    public Set<String> roles() {
        return jwt.getGroups();
    }
}
