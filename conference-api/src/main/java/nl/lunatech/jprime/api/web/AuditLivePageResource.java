package nl.lunatech.jprime.api.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/audit-live")
@PermitAll
public class AuditLivePageResource {

    @CheckedTemplate
    static class Templates {
        public static native TemplateInstance audit(int pollIntervalSeconds);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return Templates.audit(2);
    }
}
