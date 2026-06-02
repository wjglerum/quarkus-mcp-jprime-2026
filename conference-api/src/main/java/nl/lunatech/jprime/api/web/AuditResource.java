package nl.lunatech.jprime.api.web;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.api.domain.AuditEvent;
import nl.lunatech.jprime.api.dto.AuditEventDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/audit")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "audit", description = "Audit feed used by the talk's second-screen demo")
public class AuditResource {

    @GET
    @Path("/recent")
    @PermitAll
    public List<AuditEventDto> recent(@QueryParam("limit") Integer limit) {
        int lim = limit == null ? 30 : Math.max(1, Math.min(limit, 100));
        // id desc as a tiebreak keeps newest-first deterministic even when many
        // events share a timestamp.
        return AuditEvent.<AuditEvent>find("order by createdAt desc, id desc")
                .page(0, lim)
                .list()
                .stream()
                .map(AuditEventDto::of)
                .toList();
    }
}
