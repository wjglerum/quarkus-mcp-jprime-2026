package nl.lunatech.jprime.api.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
import nl.lunatech.jprime.api.domain.Bookmark;
import nl.lunatech.jprime.api.domain.Rating;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.dto.AttendeeDto;
import nl.lunatech.jprime.api.dto.BookmarkDto;
import nl.lunatech.jprime.api.dto.CreateBookmarkRequest;
import nl.lunatech.jprime.api.dto.RatingDto;
import nl.lunatech.jprime.api.dto.SessionDto;
import nl.lunatech.jprime.api.dto.SessionFeedbackDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("attendee")
@Tag(name = "me", description = "Endpoints for the current authenticated attendee")
public class MeResource {

    @Inject
    AttendeeService attendees;

    @Inject
    AuditService audit;

    @Inject
    DemoClock clock;

    @GET
    @Transactional
    public AttendeeDto me() {
        return AttendeeDto.of(attendees.currentAttendee());
    }

    @GET
    @Path("/agenda")
    @Transactional
    public List<BookmarkDto> myAgenda() {
        Attendee me = attendees.currentAttendee();
        return Bookmark.listByAttendee(me.id).stream().map(BookmarkDto::of).toList();
    }

    @POST
    @Path("/agenda")
    @Transactional
    public BookmarkDto addToAgenda(CreateBookmarkRequest req) {
        if (req == null || req.sessionId() == null) {
            throw new WebApplicationException("session_id is required", 400);
        }
        Attendee me = attendees.currentAttendee();
        Session session = Session.findById(req.sessionId());
        if (session == null) throw new NotFoundException("session " + req.sessionId());
        Bookmark existing = Bookmark.findOne(me.id, session.id);
        if (existing != null) return BookmarkDto.of(existing);
        Bookmark b = new Bookmark();
        b.attendee = me;
        b.session = session;
        b.createdAt = clock.now();
        b.persist();
        audit.record("BOOKMARK_ADD", "session:" + session.id, session.title);
        return BookmarkDto.of(b);
    }

    @DELETE
    @Path("/agenda/{sessionId}")
    @Transactional
    public Response removeFromAgenda(@PathParam("sessionId") Long sessionId) {
        Attendee me = attendees.currentAttendee();
        Bookmark b = Bookmark.findOne(me.id, sessionId);
        if (b == null) return Response.noContent().build();
        b.delete();
        audit.record("BOOKMARK_REMOVE", "session:" + sessionId, null);
        return Response.noContent().build();
    }

    @GET
    @Path("/conflicts")
    @Transactional
    public List<SessionDto> conflicts() {
        Attendee me = attendees.currentAttendee();
        List<Session> sessions = Bookmark.listByAttendee(me.id).stream()
                .map(b -> b.session)
                .toList();
        Set<Long> overlapping = new LinkedHashSet<>();
        for (int i = 0; i < sessions.size(); i++) {
            for (int j = i + 1; j < sessions.size(); j++) {
                Session a = sessions.get(i);
                Session b = sessions.get(j);
                if (a.overlaps(b)) {
                    overlapping.add(a.id);
                    overlapping.add(b.id);
                }
            }
        }
        List<SessionDto> out = new ArrayList<>();
        for (Session s : sessions) {
            if (overlapping.contains(s.id)) out.add(SessionDto.of(s));
        }
        return out;
    }

    @GET
    @Path("/ratings")
    @Transactional
    public List<RatingDto> myRatings() {
        Attendee me = attendees.currentAttendee();
        return Rating.listForAttendee(me.id).stream().map(RatingDto::of).toList();
    }

    @GET
    @Path("/sessions/feedback")
    @RolesAllowed("speaker")
    @Transactional
    public List<SessionFeedbackDto> mySessionFeedback() {
        Attendee me = attendees.currentAttendee();
        if (me.speaker == null) return List.of();
        List<Session> mine = Session.list(
                "from Session s where s.speaker.id = ?1 order by s.startsAt",
                me.speaker.id);
        List<SessionFeedbackDto> out = new ArrayList<>();
        for (Session s : mine) {
            out.add(SessionFeedbackDto.of(s, Rating.listForSession(s.id)));
        }
        return out;
    }
}
