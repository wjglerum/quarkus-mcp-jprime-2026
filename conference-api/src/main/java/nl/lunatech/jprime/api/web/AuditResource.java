package nl.lunatech.jprime.api.web;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.api.domain.AuditEvent;
import nl.lunatech.jprime.api.web.Dtos.AuditEventDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/audit")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"attendee", "speaker"})
@Tag(name = "audit", description = "Audit feed used by the talk's second-screen demo")
public class AuditResource {

    @Inject
    SecurityIdentity identity;

    @GET
    public List<AuditEventDto> tail(@QueryParam("limit") Integer limit, @QueryParam("subject") String subject) {
        int lim = limit == null ? 25 : Math.max(1, Math.min(limit, 200));
        String mySubject = identity.getPrincipal().getName();
        String filter = (subject == null || subject.isBlank()) ? mySubject : subject;
        return AuditEvent.<AuditEvent>find(
                        "attendeeSubject = ?1 order by createdAt desc", filter)
                .page(0, lim)
                .list()
                .stream()
                .map(AuditEventDto::of)
                .toList();
    }
}
