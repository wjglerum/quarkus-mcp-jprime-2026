(function () {
    'use strict';

    const REFRESH_MS = 2000;
    const ENDPOINT = '/api/v1/audit/recent?limit=30';

    const eventsEl = document.getElementById('events');
    const emptyEl = document.getElementById('empty');
    const countEl = document.getElementById('count');
    const template = document.getElementById('event-template');

    let lastIds = new Set();

    function classifyAction(action) {
        if (!action) return '';
        const a = action.toUpperCase();
        if (a.includes('CANCEL_SESSION') && !a.includes('ATTEMPT') && !a.includes('UNDONE')) return 'destructive';
        if (a.includes('REJECTED')) return 'step-up';
        if (a.includes('ATTEMPT')) return 'step-up';
        if (a.includes('VIEW_SESSION_ATTENDEES')) return 'step-up';
        return '';
    }

    function fmtTime(iso) {
        if (!iso) return '';
        try {
            const d = new Date(iso);
            const hh = String(d.getHours()).padStart(2, '0');
            const mm = String(d.getMinutes()).padStart(2, '0');
            const ss = String(d.getSeconds()).padStart(2, '0');
            return hh + ':' + mm + ':' + ss;
        } catch (e) {
            return iso;
        }
    }

    function render(events) {
        if (!events || events.length === 0) {
            emptyEl.style.display = '';
            return;
        }
        emptyEl.style.display = 'none';

        const nextIds = new Set(events.map(e => e.id));
        if (nextIds.size === lastIds.size && [...nextIds].every(id => lastIds.has(id))) {
            countEl.textContent = events.length + ' events';
            return;
        }
        lastIds = nextIds;

        eventsEl.innerHTML = '';
        for (const e of events) {
            const node = template.content.cloneNode(true);
            const article = node.querySelector('.event');
            const cls = classifyAction(e.action);
            if (cls) article.classList.add(cls);

            node.querySelector('[data-bind="time"]').textContent = fmtTime(e.createdAt);
            node.querySelector('[data-bind="action"]').textContent = e.action || '';
            node.querySelector('[data-bind="target"]').textContent = e.target || '';
            node.querySelector('[data-bind="subject"]').textContent = e.attendeeSubject || '';
            node.querySelector('[data-bind="client"]').textContent = e.executedByClient || '-';
            node.querySelector('[data-bind="iss"]').textContent = e.tokenIss || '-';
            node.querySelector('[data-bind="acr"]').textContent = e.tokenAcr || '-';
            node.querySelector('[data-bind="amr"]').textContent = e.tokenAmr || '-';
            node.querySelector('[data-bind="detail"]').textContent = e.detail || '';

            eventsEl.appendChild(node);
        }

        countEl.textContent = events.length + ' events';
    }

    async function tick() {
        try {
            const res = await fetch(ENDPOINT, { cache: 'no-store' });
            if (!res.ok) throw new Error('http ' + res.status);
            const events = await res.json();
            render(events);
        } catch (err) {
            console.warn('audit fetch failed', err);
        }
    }

    tick();
    setInterval(tick, REFRESH_MS);
})();
