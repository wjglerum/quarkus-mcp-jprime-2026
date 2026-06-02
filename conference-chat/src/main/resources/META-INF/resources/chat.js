(function () {
    'use strict';

    const transcript = document.getElementById('transcript');
    const composer = document.getElementById('composer');
    const promptInput = document.getElementById('prompt');
    const quickPromptsEl = document.getElementById('quick-prompts');

    const me = window.__ME__ || { subject: 'unknown', name: 'unknown' };

    const STEP_UP_TOOLS = new Set(['view_session_attendees', 'cancel_my_session']);
    const DESTRUCTIVE_TOOLS = new Set(['cancel_my_session']);

    quickPromptsEl.addEventListener('click', (ev) => {
        const btn = ev.target.closest('.quick-prompt');
        if (!btn) return;
        const label = btn.dataset.label || btn.textContent.trim();
        promptInput.value = label;
        composer.requestSubmit();
    });

    composer.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const text = promptInput.value.trim();
        if (!text) return;
        clearHero();
        addUserBubble(text);
        promptInput.value = '';
        try {
            const res = await fetch('/api/chat/send', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt: text })
            });
            if (!res.ok) {
                addAssistantBubble({
                    note: 'Backend returned HTTP ' + res.status + '.',
                    result: { error: 'request_failed' }
                });
                return;
            }
            const turn = await res.json();
            addAssistantBubble(turn);
        } catch (e) {
            addAssistantBubble({
                note: 'Network error: ' + e.message,
                result: { error: 'network' }
            });
        }
    });

    function clearHero() {
        const hero = transcript.querySelector('.hero-empty');
        if (hero) hero.remove();
    }

    function addUserBubble(text) {
        const tmpl = document.getElementById('user-bubble');
        const node = tmpl.content.cloneNode(true);
        node.querySelector('[data-bind="who"]').textContent = me.name || me.subject;
        node.querySelector('[data-bind="time"]').textContent = nowHHMMSS();
        node.querySelector('[data-bind="text"]').textContent = text;
        transcript.appendChild(node);
        scrollDown();
    }

    function addAssistantBubble(turn) {
        const tmpl = document.getElementById('assistant-bubble');
        const node = tmpl.content.cloneNode(true);
        node.querySelector('[data-bind="time"]').textContent = nowHHMMSS();
        node.querySelector('[data-bind="note"]').textContent = turn.note || '';

        const result = turn.result;
        const card = node.querySelector('[data-bind-card]');
        const errorBox = node.querySelector('[data-bind-error]');
        const stepUpBox = node.querySelector('[data-bind-stepup]');

        if (!result || !result.tool) {
            card.hidden = true;
            errorBox.hidden = true;
            stepUpBox.hidden = true;
            transcript.appendChild(node);
            scrollDown();
            return;
        }

        const article = card;
        if (STEP_UP_TOOLS.has(result.tool)) article.classList.add('step-up');
        if (DESTRUCTIVE_TOOLS.has(result.tool)) article.classList.add('destructive');

        node.querySelector('[data-bind="toolName"]').textContent = result.tool;
        node.querySelector('[data-bind="toolTier"]').textContent = tierOf(result.tool);
        node.querySelector('[data-bind="toolArgs"]').textContent =
            JSON.stringify(result.args || {}, null, 2);

        if (result.error) {
            errorBox.textContent = result.error;
            errorBox.hidden = false;
        } else {
            errorBox.hidden = true;
        }

        if (result.stepUpRequired) {
            stepUpBox.hidden = false;
            node.querySelector('[data-bind="toolResult"]').textContent =
                JSON.stringify({ error: 'insufficient_user_authentication' }, null, 2);
        } else {
            stepUpBox.hidden = true;
            node.querySelector('[data-bind="toolResult"]').textContent =
                result.result === null || result.result === undefined
                    ? (result.error ? '(no result)' : 'null')
                    : JSON.stringify(result.result, null, 2);
        }

        const sessions = (result.error || result.stepUpRequired)
            ? [] : collectSessions(result.result);
        if (sessions.length) {
            card.parentNode.insertBefore(buildSessionSummary(sessions), card);
        }

        transcript.appendChild(node);
        scrollDown();
    }

    // Sessions arrive embedded in several result shapes: bare SessionDto(s),
    // bookmarks ({session: ...}), and feedback ({session: ..., ratingCount: ...}).
    function collectSessions(result) {
        const items = Array.isArray(result) ? result : [result];
        const out = [];
        for (const it of items) {
            if (!it || typeof it !== 'object') continue;
            if (it.startsAt && it.title) {
                out.push({ s: it });
            } else if (it.session && it.session.startsAt) {
                let badge = null;
                if (typeof it.ratingCount === 'number') {
                    badge = it.ratingCount + ' rating' + (it.ratingCount === 1 ? '' : 's')
                        + (it.ratingCount ? ', avg ' + Number(it.averageStars).toFixed(1) + '★' : '');
                }
                out.push({ s: it.session, badge });
            }
        }
        return out;
    }

    function buildSessionSummary(entries) {
        const wrap = document.createElement('div');
        wrap.className = 'session-summary';
        const sorted = entries.slice().sort((a, b) =>
            (a.s.startsAt || '').localeCompare(b.s.startsAt || '')
            || (a.s.room || '').localeCompare(b.s.room || ''));
        const tracks = new Set(sorted.map(e => e.s.room).filter(Boolean));
        const head = document.createElement('div');
        head.className = 'session-summary-head';
        head.textContent = sorted.length + ' session' + (sorted.length === 1 ? '' : 's')
            + (tracks.size > 1 ? ' across ' + tracks.size + ' tracks' : '');
        wrap.appendChild(head);
        for (const e of sorted) wrap.appendChild(buildSessionRow(e));
        return wrap;
    }

    function buildSessionRow(e) {
        const s = e.s;
        const row = document.createElement('div');
        row.className = 'session-row' + (s.cancelled ? ' cancelled' : '');

        const meta = document.createElement('div');
        meta.className = 'session-meta';
        const time = document.createElement('span');
        time.className = 'session-time';
        time.textContent = formatRange(s.startsAt, s.endsAt);
        meta.appendChild(time);
        if (s.room) {
            const room = document.createElement('span');
            room.className = 'session-room';
            room.textContent = s.room;
            meta.appendChild(room);
        }
        row.appendChild(meta);

        const title = document.createElement('div');
        title.className = 'session-title';
        title.textContent = s.title || '(untitled)';
        row.appendChild(title);

        const bits = [];
        if (s.speaker && s.speaker.name) bits.push(s.speaker.name);
        if (e.badge) bits.push(e.badge);
        if (s.cancelled) bits.push('cancelled' + (s.cancellationReason ? ': ' + s.cancellationReason : ''));
        if (bits.length) {
            const sub = document.createElement('div');
            sub.className = 'session-sub';
            sub.textContent = bits.join('  ·  ');
            row.appendChild(sub);
        }
        return row;
    }

    // Always render in the conference's local time so the presenter's laptop
    // timezone never misleads the audience.
    const VENUE_TZ = 'Europe/Sofia';
    const DAY_FMT = new Intl.DateTimeFormat('en-GB',
        { weekday: 'short', day: 'numeric', month: 'short', timeZone: VENUE_TZ });
    const TIME_FMT = new Intl.DateTimeFormat('en-GB',
        { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: VENUE_TZ });

    function formatRange(startIso, endIso) {
        if (!startIso) return '';
        const start = new Date(startIso);
        if (isNaN(start.getTime())) return startIso;
        const day = DAY_FMT.format(start);
        const startT = TIME_FMT.format(start);
        if (!endIso) return day + ' · ' + startT;
        const end = new Date(endIso);
        const endT = isNaN(end.getTime()) ? '' : TIME_FMT.format(end);
        return day + ' · ' + startT + (endT ? '–' + endT : '');
    }

    function tierOf(tool) {
        if (STEP_UP_TOOLS.has(tool)) return 'step-up';
        if (tool === 'my_session_feedback') return 'speaker';
        if (tool && tool.startsWith('my_')) return 'attendee';
        if (tool === 'bookmark_session' || tool === 'unbookmark_session'
            || tool === 'rate_session') return 'attendee';
        return 'public';
    }

    function nowHHMMSS() {
        const d = new Date();
        return [d.getHours(), d.getMinutes(), d.getSeconds()]
            .map(n => String(n).padStart(2, '0')).join(':');
    }

    function scrollDown() {
        requestAnimationFrame(() => {
            transcript.scrollTop = transcript.scrollHeight;
        });
    }
})();
