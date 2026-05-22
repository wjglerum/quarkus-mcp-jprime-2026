package nl.lunatech.jprime.api.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.lunatech.jprime.api.audit.AuditService;
import nl.lunatech.jprime.api.clock.DemoClock;
import nl.lunatech.jprime.api.domain.Attendee;
import nl.lunatech.jprime.api.domain.Rating;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.dto.CreateRatingRequest;
import nl.lunatech.jprime.api.dto.RatingDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/sessions/{id}/ratings")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("attendee")
@Tag(name = "ratings", description = "Attendee ratings for sessions")
public class RatingResource {

    @Inject
    AttendeeService attendees;

    @Inject
    AuditService audit;

    @Inject
    DemoClock clock;

    @POST
    @Transactional
    public Response rate(@PathParam("id") Long sessionId, @Valid CreateRatingRequest req) {
        if (req == null) throw new WebApplicationException("body required", 400);
        if (req.stars() < 1 || req.stars() > 5) {
            throw new WebApplicationException("stars must be between 1 and 5", 422);
        }
        Session session = Session.findById(sessionId);
        if (session == null) throw new NotFoundException("session " + sessionId);
        if (session.startsAt.isAfter(clock.now())) {
            audit.record("RATE_SESSION_REJECTED_NOT_STARTED", "session:" + sessionId,
                    "stars=" + req.stars());
            return Response.status(422)
                    .entity("{\"error\":\"session_not_started\",\"description\":"
                            + "\"You cannot rate a session before it has started.\"}")
                    .build();
        }
        Attendee me = attendees.currentAttendee();
        Rating r = Rating.findOne(me.id, session.id);
        if (r == null) {
            r = new Rating();
            r.attendee = me;
            r.session = session;
            r.createdAt = clock.now();
        }
        r.stars = req.stars();
        r.comment = req.comment();
        r.persist();
        audit.record("RATE_SESSION", "session:" + sessionId,
                "stars=" + req.stars() + " comment=" + (req.comment() == null ? "" : req.comment()));
        return Response.status(201).entity(RatingDto.of(r)).build();
    }
}
