package nl.lunatech.jprime.chat.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.chat.security.CurrentUser;

import java.util.List;

@Path("/")
@Authenticated
public class ChatPageResource {

    @Inject
    CurrentUser me;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance chat(CurrentUser me, List<QuickPrompts.QuickPrompt> quickPrompts);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return Templates.chat(me, QuickPrompts.all());
    }
}
