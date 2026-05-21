(function () {
    'use strict';

    const transcript = document.getElementById('transcript');
    const composer = document.getElementById('composer');
    const promptInput = document.getElementById('prompt');
    const quickPromptsEl = document.getElementById('quick-prompts');
    const whoEl = document.getElementById('who');
    const introNameEl = document.getElementById('intro-name');
    const identityEl = document.getElementById('identity');
    const modeToggle = document.getElementById('mode-llm');
    const modePill = document.getElementById('mode-pill');
    const modeHelp = document.getElementById('mode-help');

    let me = null;
    let llmEnabled = false;

    const STEP_UP_TOOLS = new Set(['view_session_attendees', 'cancel_my_session']);
    const DESTRUCTIVE_TOOLS = new Set(['cancel_my_session']);

    async function loadMe() {
        const res = await fetch('/api/chat/me', { credentials: 'same-origin' });
        if (!res.ok) throw new Error('not authenticated');
        me = await res.json();
        whoEl.textContent = me.name || me.subject;
        if (introNameEl) introNameEl.textContent = me.name || me.subject;
        renderIdentity();
        llmEnabled = !!me.llmAvailable;
        modeToggle.disabled = !llmEnabled;
        if (!llmEnabled) {
            modeHelp.textContent = 'Set ANTHROPIC_API_KEY and CHAT_LLM_ENABLED=true to enable Claude.';
        }
    }

    function renderIdentity() {
        identityEl.innerHTML = '';
        const rows = [
            ['subject', me.subject],
            ['name', me.name],
            ['roles', (me.roles || []).join(', ') || '(none)'],
            ['acr', String(me.acr)],
            ['amr', Array.isArray(me.amr) ? me.amr.join(', ') : String(me.amr)]
        ];
        for (const [k, v] of rows) {
            const dt = document.createElement('dt'); dt.textContent = k;
            const dd = document.createElement('dd'); dd.textContent = v;
            if (k === 'subject' || k === 'name') dd.classList.add('amber');
            identityEl.appendChild(dt);
            identityEl.appendChild(dd);
        }
    }

    async function loadQuickPrompts() {
        const res = await fetch('/api/chat/quick-prompts', { credentials: 'same-origin' });
        if (!res.ok) return;
        const list = await res.json();
        quickPromptsEl.innerHTML = '';
        for (const q of list) {
            const btn = document.createElement('button');
            btn.className = 'quick-prompt';
            btn.dataset.tier = q.tier;
            btn.type = 'button';
            const main = document.createElement('span');
            main.textContent = q.label;
            const tier = document.createElement('span');
            tier.className = 'tier';
            tier.textContent = q.suggestedTool + ' / ' + q.tier;
            btn.appendChild(main);
            btn.appendChild(tier);
            btn.addEventListener('click', () => {
                promptInput.value = q.label;
                composer.requestSubmit();
            });
            quickPromptsEl.appendChild(btn);
        }
    }

    modeToggle.addEventListener('change', () => {
        const llm = modeToggle.checked && llmEnabled;
        modePill.textContent = llm ? 'llm' : 'scripted';
        modePill.classList.toggle('llm', llm);
        modeHelp.textContent = llm
            ? 'Claude decides which tool to call. The MCP tools are passed in the request.'
            : 'Default. Deterministic intent matcher.';
    });

    composer.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const text = promptInput.value.trim();
        if (!text) return;
        addUserBubble(text);
        promptInput.value = '';
        try {
            const res = await fetch('/api/chat/send', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    prompt: text,
                    mode: modeToggle.checked && llmEnabled ? 'llm' : 'scripted'
                })
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

    function addUserBubble(text) {
        const tmpl = document.getElementById('user-bubble');
        const node = tmpl.content.cloneNode(true);
        node.querySelector('[data-bind="who"]').textContent = me ? (me.name || me.subject) : 'me';
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

        transcript.appendChild(node);
        scrollDown();
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

    (async () => {
        try {
            await loadMe();
            await loadQuickPrompts();
        } catch (e) {
            whoEl.textContent = 'not logged in';
        }
    })();
})();
