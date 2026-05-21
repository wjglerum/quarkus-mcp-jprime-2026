-- Initial schema for the jPrime 2026 conference companion.
-- Sequences are named to match Hibernate's default convention (<entity>_SEQ)
-- so we can hand IDs to Panache without further annotation.

CREATE SEQUENCE speaker_seq             START 1000 INCREMENT 50;
CREATE SEQUENCE conference_session_seq  START 1000 INCREMENT 50;
CREATE SEQUENCE attendee_seq            START 1000 INCREMENT 50;
CREATE SEQUENCE bookmark_seq            START 1000 INCREMENT 50;
CREATE SEQUENCE rating_seq              START 1000 INCREMENT 50;
CREATE SEQUENCE audit_event_seq         START 1000 INCREMENT 50;

CREATE TABLE speaker (
    id              BIGINT PRIMARY KEY DEFAULT nextval('speaker_seq'),
    external_id     VARCHAR(64),
    name            VARCHAR(255) NOT NULL,
    bio             TEXT,
    company         VARCHAR(255),
    twitter_handle  VARCHAR(64)
);

CREATE INDEX idx_speaker_external_id ON speaker(external_id);
CREATE INDEX idx_speaker_name        ON speaker(name);

CREATE TABLE conference_session (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('conference_session_seq'),
    external_id         VARCHAR(64),
    title               VARCHAR(512) NOT NULL,
    abstract_text       TEXT,
    track               VARCHAR(32)  NOT NULL,
    room                VARCHAR(64)  NOT NULL,
    level               VARCHAR(32),
    starts_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    day                 INT          NOT NULL,
    cancelled           BOOLEAN      NOT NULL DEFAULT FALSE,
    cancellation_reason TEXT
);

CREATE INDEX idx_session_external_id ON conference_session(external_id);
CREATE INDEX idx_session_starts_at   ON conference_session(starts_at);
CREATE INDEX idx_session_day_track   ON conference_session(day, track);

CREATE TABLE conference_session_speaker (
    session_id BIGINT NOT NULL REFERENCES conference_session(id) ON DELETE CASCADE,
    speaker_id BIGINT NOT NULL REFERENCES speaker(id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, speaker_id)
);

CREATE TABLE attendee (
    id           BIGINT PRIMARY KEY DEFAULT nextval('attendee_seq'),
    subject      VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    email        VARCHAR(255),
    is_speaker   BOOLEAN      NOT NULL DEFAULT FALSE,
    speaker_id   BIGINT REFERENCES speaker(id) ON DELETE SET NULL
);

CREATE INDEX idx_attendee_subject ON attendee(subject);

CREATE TABLE bookmark (
    id          BIGINT PRIMARY KEY DEFAULT nextval('bookmark_seq'),
    attendee_id BIGINT NOT NULL REFERENCES attendee(id) ON DELETE CASCADE,
    session_id  BIGINT NOT NULL REFERENCES conference_session(id) ON DELETE CASCADE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (attendee_id, session_id)
);

CREATE INDEX idx_bookmark_attendee ON bookmark(attendee_id);

CREATE TABLE rating (
    id          BIGINT PRIMARY KEY DEFAULT nextval('rating_seq'),
    attendee_id BIGINT NOT NULL REFERENCES attendee(id) ON DELETE CASCADE,
    session_id  BIGINT NOT NULL REFERENCES conference_session(id) ON DELETE CASCADE,
    stars       INT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (attendee_id, session_id)
);

CREATE INDEX idx_rating_session ON rating(session_id);

CREATE TABLE audit_event (
    id                BIGINT PRIMARY KEY DEFAULT nextval('audit_event_seq'),
    attendee_subject  VARCHAR(255) NOT NULL,
    action            VARCHAR(64)  NOT NULL,
    target            VARCHAR(255) NOT NULL,
    token_acr         VARCHAR(128),
    token_amr         TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    detail            TEXT
);

CREATE INDEX idx_audit_subject ON audit_event(attendee_subject);
CREATE INDEX idx_audit_created ON audit_event(created_at);
