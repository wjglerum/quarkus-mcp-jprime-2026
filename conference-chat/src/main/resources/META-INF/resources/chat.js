(function () {
    'use strict';

    const transcript = document.getElementById('transcript');
    const composer = document.getElementById('composer');
    const promptInput = document.getElementById('prompt');
    const modeToggle = document.getElementById('mode-llm');
    const modePill = document.getElementById('mode-pill');
    const footerMode = document.getElementById('footer-mode');
    const modeHelp = document.getElementById('mode-help');
    const providerSelect = document.getElementById('provider-select');
    const providerHelp = document.getElementById('provider-help');
    const quickPromptsEl = document.getElementById('quick-prompts');

    const me = window.__ME__ || { subject: 'unknown', name: 'unknown', provider: 'scripted' };
    let activeProvider = me.provider || 'scripted';
    let llmEnabled = activeProvider !== 'scripted';

    const STEP_UP_TOOLS = new Set(['view_session_attendees', 'cancel_my_session']);
    const DESTRUCTIVE_TOOLS = new Set(['cancel_my_session']);

    function updateModePill() {
        const llm = modeToggle.checked && llmEnabled;
        const label = llm ? activeProvider : 'scripted';
        modePill.textContent = label;
        modePill.classList.toggle('llm', llm);
        if (footerMode) footerMode.textContent = label;
    }

    function refreshProviderUiFromSnapshot(snap) {
        const providers = snap.providers || [];
        activeProvider = snap.active || activeProvider;
        providerSelect.innerHTML = '';
        for (const p of providers) {
            const opt = document.createElement('option');
            opt.value = p.id;
            const tag = p.available ? '' : ' (unavailable)';
            const model = p.model ? ' / ' + p.model : '';
            opt.textContent = p.id + model + tag;
            opt.disabled = !p.available;
            if (p.id === activeProvider) opt.selected = true;
            providerSelect.appendChild(opt);
        }
        llmEnabled = activeProvider !== 'scripted';
        modeToggle.disabled = !llmEnabled;
        if (!llmEnabled) modeToggle.checked = false;
        else modeToggle.checked = true;
        updateModePill();
    }

    providerSelect.addEventListener('change', async () => {
        const choice = providerSelect.value;
        const res = await fetch('/api/chat/provider', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ provider: choice })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            providerHelp.textContent = 'Switch failed: ' + (err.error || res.status);
            return;
        }
        const snap = await res.json();
        providerHelp.textContent = 'Active provider: ' + (snap.active || choice) + '.';
        refreshProviderUiFromSnapshot(snap);
    });

    modeToggle.addEventListener('change', () => {
        updateModePill();
        const llm = modeToggle.checked && llmEnabled;
        modeHelp.textContent = llm
            ? activeProvider + ' decides which tool to call. The MCP tools are passed in the request.'
            : 'Default. Deterministic intent matcher.';
    });

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
                body: JSON.stringify({
                    prompt: text,
                    mode: modeToggle.checked && llmEnabled ? 'llm' : 'scripted'
                })
            });
            if (res.status === 503) {
                const body = await res.json().catch(() => ({}));
                if (body && body.error === 'provider_unavailable') {
                    addAssistantBubble({
                        note: 'LLM provider unavailable. Switch back to scripted mode to continue.',
                        result: { error: 'provider_unavailable', active: body.active }
                    });
                    return;
                }
            }
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

    updateModePill();
})();
