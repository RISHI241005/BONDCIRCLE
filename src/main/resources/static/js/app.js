const API_PREFIX = "/api/v1";
const TOKEN_KEY = "bondcircle.token";
const PROFILE_KEY = "bondcircle.profile";

const state = {
    token: localStorage.getItem(TOKEN_KEY),
    profile: readStoredProfile(),
    conversations: [],
    activeId: null,
    messages: new Map(),
    socket: null,
    stompConnected: false,
    stompBuffer: "",
    reconnectTimer: null,
    reconnectAttempt: 0,
    heartbeatTimer: null,
    typingTimer: null,
    sentTyping: false,
    moderationChecking: false,
    pendingModeratedMessage: null,
    replyCoach: {
        visible: false,
        loading: false,
        suggestions: [],
        rejectedIds: [],
        rejectedTexts: [],
        activeConversationId: null
    },
};

const $ = (id) => document.getElementById(id);
const authView = $("authView");
const chatView = $("chatView");
const workspace = document.querySelector(".workspace");
let toastTimer;

function readStoredProfile() {
    try {
        return JSON.parse(localStorage.getItem(PROFILE_KEY) || "null");
    } catch {
        return null;
    }
}

function initials(name) {
    return String(name || "BC")
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase() || "")
        .join("") || "BC";
}

function normalizePhone(value) {
    const trimmed = String(value || "").trim();
    const digits = trimmed.replace(/\D/g, "");
    if (digits.length < 10 || digits.length > 15) {
        throw new Error("Enter a phone number with 10 to 15 digits.");
    }
    return trimmed.startsWith("+") ? `+${digits}` : digits;
}

function parseInterests(value) {
    const seen = new Set();
    const interests = [];
    for (const raw of String(value || "").split(",")) {
        const interest = raw.trim().replace(/\s+/g, " ");
        const key = interest.toLowerCase();
        if (!interest || seen.has(key)) continue;
        if (interest.length > 40) throw new Error("Each interest must be 40 characters or less.");
        seen.add(key);
        interests.push(interest);
    }
    if (interests.length > 10) throw new Error("Add no more than 10 interests.");
    return interests;
}

function showToast(message, type = "info") {
    const toast = $("toast");
    toast.textContent = message;
    toast.classList.toggle("is-error", type === "error");
    toast.classList.add("is-visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove("is-visible"), 3600);
}

function getErrorMessage(payload, fallback) {
    if (Array.isArray(payload?.details) && payload.details[0]?.message) {
        return payload.details[0].message;
    }
    return payload?.message || fallback;
}

async function api(path, options = {}) {
    const headers = { Accept: "application/json", ...(options.headers || {}) };
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    if (state.token) headers.Authorization = `Bearer ${state.token}`;

    let response;
    try {
        response = await fetch(`${API_PREFIX}${path}`, { ...options, headers });
    } catch {
        throw new Error("The server is unreachable. Check your connection and try again.");
    }

    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json") ? await response.json() : null;
    if (!response.ok) {
        if (response.status === 401 && state.token) logout(false);
        const error = new Error(getErrorMessage(payload, `Request failed (${response.status}).`));
        error.status = response.status;
        error.code = payload?.errorCode;
        throw error;
    }
    return payload?.data ?? payload;
}

function setFormBusy(form, busy, busyText) {
    const button = form.querySelector('button[type="submit"]');
    if (!button) return;
    if (busy) {
        button.dataset.label = button.querySelector("span")?.textContent || button.textContent;
        const label = button.querySelector("span");
        if (label) label.textContent = busyText;
    } else {
        const label = button.querySelector("span");
        if (label && button.dataset.label) label.textContent = button.dataset.label;
    }
    button.disabled = busy;
}

function switchAuthTab(tab) {
    const login = tab === "login";
    $("loginForm").hidden = !login;
    $("registerForm").hidden = login;
    $("loginTab").classList.toggle("is-active", login);
    $("registerTab").classList.toggle("is-active", !login);
    $("loginTab").setAttribute("aria-selected", String(login));
    $("registerTab").setAttribute("aria-selected", String(!login));
    $(login ? "loginPhone" : "registerName").focus();
}

async function handleLogin(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const error = $("loginError");
    error.textContent = "";
    try {
        const phone = normalizePhone($("loginPhone").value);
        const password = $("loginPassword").value;
        if (!password) throw new Error("Enter your password.");
        setFormBusy(form, true, "Signing in…");
        const profile = await api("/users/login", {
            method: "POST",
            body: JSON.stringify({ phone, password }),
        });
        state.token = profile.token;
        state.profile = profile;
        localStorage.setItem(TOKEN_KEY, profile.token);
        localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
        await enterChat();
    } catch (err) {
        error.textContent = err.message;
    } finally {
        setFormBusy(form, false);
    }
}

async function handleRegister(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const error = $("registerError");
    error.textContent = "";
    try {
        const fullName = $("registerName").value.trim();
        const phone = normalizePhone($("registerPhone").value);
        const email = $("registerEmail").value.trim();
        const password = $("registerPassword").value;
        const confirmPassword = $("registerConfirm").value;
        const interests = parseInterests($("registerInterests").value);
        if (!fullName) throw new Error("Enter your full name.");
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error("Enter a valid email address.");
        if (!/^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[!@#$%^&*]).{8,128}$/.test(password)) {
            throw new Error("Use 8+ characters with uppercase, lowercase, a number, and a special character.");
        }
        if (password !== confirmPassword) throw new Error("The passwords do not match.");
        setFormBusy(form, true, "Creating account…");
        await api("/users/register", {
            method: "POST",
            body: JSON.stringify({ fullName, phone, email, password, confirmPassword, interests }),
        });
        const profile = await api("/users/login", {
            method: "POST",
            body: JSON.stringify({ phone, password }),
        });
        state.token = profile.token;
        state.profile = profile;
        localStorage.setItem(TOKEN_KEY, profile.token);
        localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
        await enterChat();
        showToast("Your account is ready.");
    } catch (err) {
        error.textContent = err.message;
    } finally {
        setFormBusy(form, false);
    }
}

async function enterChat() {
    authView.hidden = true;
    chatView.hidden = false;
    $("profileName").textContent = state.profile?.fullName || "Your account";
    $("profilePhone").textContent = state.profile?.phone || "";
    $("profileAvatar").textContent = initials(state.profile?.fullName);
    connectRealtime();
    await loadConversations();

    try {
        const freshProfile = await api("/users/me");
        if (freshProfile) {
            state.profile = { ...state.profile, ...freshProfile };
            localStorage.setItem(PROFILE_KEY, JSON.stringify(state.profile));
            $("profileName").textContent = state.profile?.fullName || "Your account";
            $("profilePhone").textContent = state.profile?.phone || "";
            $("profileAvatar").textContent = initials(state.profile?.fullName);
        }
    } catch (err) {
        if (err.status === 401) {
            logout(false);
            showToast("Your session expired. Please sign in again.", "error");
        }
    }
}

function logout(notify = true) {
    clearTimeout(state.reconnectTimer);
    clearInterval(state.heartbeatTimer);
    if (state.socket && state.socket.readyState === WebSocket.OPEN) {
        try { sendFrame("DISCONNECT", { receipt: "logout" }); } catch { /* connection is already closing */ }
        state.socket.close(1000, "Signed out");
    }
    state.token = null;
    state.profile = null;
    state.socket = null;
    state.stompConnected = false;
    state.conversations = [];
    state.messages.clear();
    state.activeId = null;
    state.coachLoadedFor.clear();
    state.coachResponse = null;
    state.coachVariant = 0;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(PROFILE_KEY);
    chatView.hidden = true;
    authView.hidden = false;
    $("loginForm").reset();
    switchAuthTab("login");
    if (notify) showToast("You have signed out.");
}

async function loadConversations(selectId = null) {
    const list = $("conversationList");
    try {
        state.conversations = await api("/chats") || [];
        renderConversations();
        const target = selectId || state.activeId;
        if (target) {
            const conversation = state.conversations.find((item) => item.conversationId === target);
            if (conversation) await openConversation(conversation, false);
        }
    } catch (err) {
        list.textContent = "";
        const message = document.createElement("p");
        message.className = "list-empty";
        message.textContent = err.message;
        list.appendChild(message);
    }
}

function renderConversations() {
    const list = $("conversationList");
    list.textContent = "";
    $("conversationCount").textContent = `${state.conversations.length} ${state.conversations.length === 1 ? "chat" : "chats"}`;
    if (!state.conversations.length) {
        const empty = document.createElement("p");
        empty.className = "list-empty";
        empty.textContent = "No conversations yet. Search for a phone number to begin.";
        list.appendChild(empty);
        return;
    }

    for (const conversation of state.conversations) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "conversation-item";
        button.classList.toggle("is-active", conversation.conversationId === state.activeId);

        const avatar = document.createElement("span");
        avatar.className = "avatar";
        avatar.textContent = initials(conversation.otherUserName);

        const copy = document.createElement("span");
        copy.className = "conversation-copy";
        const line = document.createElement("span");
        line.className = "conversation-line";
        const name = document.createElement("strong");
        name.textContent = conversation.otherUserName || conversation.otherUserPhone || "Conversation";
        const time = document.createElement("time");
        time.textContent = relativeTime(conversation.lastMessageAt || conversation.updatedAt);
        line.append(name, time);
        const preview = document.createElement("p");
        preview.textContent = conversation.lastMessageContent || conversation.otherUserPhone || "Start the conversation";
        copy.append(line, preview);
        button.append(avatar, copy);

        if (conversation.unreadCount > 0) {
            const unread = document.createElement("span");
            unread.className = "unread";
            unread.textContent = conversation.unreadCount > 99 ? "99+" : String(conversation.unreadCount);
            button.appendChild(unread);
        }
        button.addEventListener("click", () => openConversation(conversation));
        list.appendChild(button);
    }
}

async function openConversation(conversation, moveToChat = true) {
    state.activeId = conversation.conversationId;
    conversation.unreadCount = 0;
    $("emptyConversation").hidden = true;
    $("activeConversation").hidden = false;
    $("chatName").textContent = conversation.otherUserName || conversation.otherUserPhone || "Conversation";
    $("chatAvatar").textContent = initials(conversation.otherUserName);
    const presence = String(conversation.otherUserOnlineStatus || "OFFLINE").toLowerCase();
    $("chatPresence").textContent = presence === "online" ? "Online now" : "Offline";
    $("chatPresence").classList.toggle("is-online", presence === "online");
    if (moveToChat) workspace.classList.add("is-chat-open");
    renderConversations();
    $("messageList").textContent = "";
    closeReplyCoach();
    if (state.replyCoach) {
        state.replyCoach.suggestions = [];
        state.replyCoach.rejectedIds = [];
        state.replyCoach.rejectedTexts = [];
    }

    try {
        const page = await api(`/chats/${encodeURIComponent(state.activeId)}/messages?limit=50`);
        const messages = [...(page?.messages || [])].reverse();
        state.messages.set(state.activeId, messages);
        renderMessages();
        acknowledgeLatestIncoming(messages);
    } catch (err) {
        showToast(err.message, "error");
    }
}

function renderMessages() {
    const list = $("messageList");
    list.textContent = "";
    const messages = state.messages.get(state.activeId) || [];
    if (!messages.length) {
        const empty = document.createElement("p");
        empty.className = "list-empty";
        empty.textContent = "This is the beginning of your conversation.";
        list.appendChild(empty);
        return;
    }

    const divider = document.createElement("div");
    divider.className = "day-divider";
    divider.textContent = "Messages";
    list.appendChild(divider);

    for (const message of messages) {
        const mine = Number(message.senderId) === Number(state.profile?.userId);
        const row = document.createElement("article");
        row.className = `message ${mine ? "is-mine" : "is-theirs"}`;
        row.classList.toggle("is-pending", Boolean(message.pending));
        const bubble = document.createElement("div");
        bubble.className = "message-bubble";
        bubble.textContent = message.deleted ? "This message was deleted." : message.content;
        bubble.setAttribute("data-raw-content", bubble.textContent);
        const meta = document.createElement("div");
        meta.className = "message-meta";
        const time = document.createElement("time");
        time.dateTime = message.createdAt || "";
        time.textContent = clockTime(message.createdAt);
        meta.appendChild(time);
        if (mine) {
            const status = document.createElement("span");
            const statusKey = message.pending ? "sending" : String(message.status || "SENT").toLowerCase();
            status.className = `message-status status-${statusKey}`;
            status.textContent = message.pending ? "Sending…" : formatStatus(message.status);
            meta.appendChild(status);
        }
        row.append(bubble, meta);
        list.appendChild(row);
    }
    requestAnimationFrame(() => { list.scrollTop = list.scrollHeight; });
}

function upsertMessage(message) {
    const conversationId = message.conversationId;
    const messages = state.messages.get(conversationId) || [];
    let index = messages.findIndex((item) => item.id === message.id);
    if (index < 0 && message.clientMessageId) {
        index = messages.findIndex((item) => item.clientMessageId === message.clientMessageId);
    }
    if (index >= 0) messages[index] = { ...messages[index], ...message, pending: false };
    else messages.push(message);
    messages.sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0));
    state.messages.set(conversationId, messages);

    const conversation = state.conversations.find((item) => item.conversationId === conversationId);
    if (conversation) {
        conversation.lastMessageContent = message.content;
        conversation.lastMessageAt = message.createdAt;
        if (conversationId !== state.activeId && Number(message.senderId) !== Number(state.profile?.userId)) {
            conversation.unreadCount = (conversation.unreadCount || 0) + 1;
        }
    }
    renderConversations();
    if (conversationId === state.activeId) renderMessages();
}

/* ==========================================================================
   BOND CIRCLE — AI REPLY COACH
   ========================================================================== */

function toggleReplyCoach() {
    if (state.replyCoach.visible) {
        closeReplyCoach();
    } else {
        openReplyCoach();
    }
}

function closeReplyCoach() {
    state.replyCoach.visible = false;
    const panel = $("aiReplyCoachPanel");
    if (panel) {
        panel.hidden = true;
    }
    $("iceBreakerButton")?.setAttribute("aria-expanded", "false");
    $("iceBreakerButton")?.classList.remove("is-active");
    $("aiReplyComposerBtn")?.classList.remove("is-active");
}

async function openReplyCoach() {
    const conversationId = state.activeId;
    if (!conversationId) {
        showToast("Please select a conversation first", "info");
        return;
    }

    state.replyCoach.visible = true;
    state.replyCoach.activeConversationId = conversationId;

    const panel = $("aiReplyCoachPanel");
    if (panel) {
        panel.hidden = false;
    }
    $("iceBreakerButton")?.setAttribute("aria-expanded", "true");
    $("iceBreakerButton")?.classList.add("is-active");
    $("aiReplyComposerBtn")?.classList.add("is-active");

    await fetchReplySuggestions();
}

async function fetchReplySuggestions() {
    const conversationId = state.activeId;
    if (!conversationId) return;

    renderReplyCoachLoading("Listening to conversation and drafting replies…");

    try {
        const res = await api("/api/ai/reply-suggestions", {
            method: "POST",
            body: {
                conversationId: conversationId,
                limit: 3
            }
        });
        if (state.activeId !== conversationId) return;
        const payload = res?.data || res;
        state.replyCoach.suggestions = payload?.suggestions || [];
        renderReplyCoachSuggestions(state.replyCoach.suggestions, payload?.conversationState);
    } catch (err) {
        renderReplyCoachError(err.message || "Couldn't generate suggestions right now.");
    }
}

async function regenerateReplySuggestions() {
    const conversationId = state.activeId;
    if (!conversationId) return;

    // Gather rejected suggestions
    const currentSuggestions = state.replyCoach.suggestions || [];
    for (const sug of currentSuggestions) {
        if (sug.id && !state.replyCoach.rejectedIds.includes(sug.id)) {
            state.replyCoach.rejectedIds.push(sug.id);
        }
        if (sug.text && !state.replyCoach.rejectedTexts.includes(sug.text)) {
            state.replyCoach.rejectedTexts.push(sug.text);
        }
        recordReplyFeedback(sug.id, conversationId, "REJECTED", sug.text);
    }

    renderReplyCoachLoading("Exploring different angles…");

    try {
        const res = await api("/api/ai/reply-suggestions/regenerate", {
            method: "POST",
            body: {
                conversationId: conversationId,
                rejectedSuggestionIds: state.replyCoach.rejectedIds,
                rejectedTexts: state.replyCoach.rejectedTexts,
                limit: 3
            }
        });
        if (state.activeId !== conversationId) return;
        const payload = res?.data || res;
        state.replyCoach.suggestions = payload?.suggestions || [];
        renderReplyCoachSuggestions(state.replyCoach.suggestions, payload?.conversationState);
    } catch (err) {
        renderReplyCoachError(err.message || "Couldn't regenerate suggestions.");
    }
}

function renderReplyCoachLoading(message) {
    const list = $("replyCoachSuggestions");
    if (!list) return;
    list.innerHTML = `
        <div class="reply-coach-loading">
            <span class="reply-coach-loading-spinner" aria-hidden="true"></span>
            <span>${escapeHtml(message)}</span>
        </div>
    `;
}

function renderReplyCoachError(errMsg) {
    const list = $("replyCoachSuggestions");
    if (!list) return;
    list.innerHTML = `
        <div class="reply-coach-empty">
            <p>${escapeHtml(errMsg)}</p>
            <button type="button" class="reply-coach-action-btn" id="replyCoachRetryBtn">Try Again</button>
        </div>
    `;
    $("replyCoachRetryBtn")?.addEventListener("click", () => fetchReplySuggestions());
}

function renderReplyCoachSuggestions(suggestions, convState) {
    const list = $("replyCoachSuggestions");
    if (!list) return;
    list.textContent = "";

    const badge = $("replyCoachTopicBadge");
    if (badge) {
        if (convState?.topic && convState.topic !== "Chat") {
            badge.textContent = convState.topic;
            badge.hidden = false;
        } else {
            badge.hidden = true;
        }
    }

    if (!suggestions || !suggestions.length) {
        list.innerHTML = `
            <div class="reply-coach-empty">
                <p>No new suggestions available right now.</p>
                <button type="button" class="reply-coach-action-btn" id="replyCoachFreshAngleBtn">Try Different Angle</button>
            </div>
        `;
        $("replyCoachFreshAngleBtn")?.addEventListener("click", () => regenerateReplySuggestions());
        return;
    }

    for (const sug of suggestions) {
        const card = document.createElement("div");
        card.className = "reply-coach-card";
        card.setAttribute("data-id", sug.id || "");

        const textDiv = document.createElement("div");
        textDiv.className = "reply-coach-card-text";
        textDiv.textContent = `"${sug.text}"`;

        const actionsDiv = document.createElement("div");
        actionsDiv.className = "reply-coach-card-actions";

        const useBtn = document.createElement("button");
        useBtn.type = "button";
        useBtn.className = "reply-coach-use-btn";
        useBtn.textContent = "Use";
        useBtn.title = "Insert into message composer";
        useBtn.addEventListener("click", () => handleUseSuggestion(sug));

        const dismissBtn = document.createElement("button");
        dismissBtn.type = "button";
        dismissBtn.className = "reply-coach-dismiss-btn";
        dismissBtn.textContent = "✕";
        dismissBtn.title = "Not this reply";
        dismissBtn.addEventListener("click", () => handleDismissSuggestion(sug, card));

        actionsDiv.append(useBtn, dismissBtn);
        card.append(textDiv, actionsDiv);
        list.appendChild(card);
    }
}

function handleUseSuggestion(sug) {
    const input = $("messageInput");
    if (input) {
        input.value = sug.text;
        input.dispatchEvent(new Event("input", { bubbles: true }));
        resizeComposer();
        input.focus();
    }
    closeReplyCoach();
    showToast("Reply inserted into composer — edit or send!", "info");

    recordReplyFeedback(sug.id, state.activeId, "USED", sug.text);
}

function handleDismissSuggestion(sug, cardEl) {
    if (cardEl) {
        cardEl.style.opacity = "0.25";
        cardEl.style.pointerEvents = "none";
    }
    if (sug.id && !state.replyCoach.rejectedIds.includes(sug.id)) {
        state.replyCoach.rejectedIds.push(sug.id);
    }
    if (sug.text && !state.replyCoach.rejectedTexts.includes(sug.text)) {
        state.replyCoach.rejectedTexts.push(sug.text);
    }
    recordReplyFeedback(sug.id, state.activeId, "REJECTED", sug.text);
}

async function recordReplyFeedback(suggestionId, conversationId, action, suggestionText) {
    if (!suggestionId || !conversationId) return;
    try {
        await api("/api/ai/reply-suggestions/feedback", {
            method: "POST",
            body: {
                suggestionId: String(suggestionId),
                conversationId: String(conversationId),
                action: action,
                suggestionText: suggestionText || ""
            }
        });
    } catch {
        // Feedback recording is best-effort background
    }
}

function openInterests() {
    $("interestsInput").value = (state.profile?.interests || []).join(", ");
    $("interestsModal").hidden = false;
    document.body.style.overflow = "hidden";
    $("interestsInput").focus();
}

function closeInterests() {
    $("interestsModal").hidden = true;
    document.body.style.overflow = "";
}

async function saveInterests(event) {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        const interests = parseInterests($("interestsInput").value);
        setFormBusy(form, true, "Saving…");
        const response = await api("/users/me/interests", {
            method: "PUT",
            body: JSON.stringify({ interests }),
        });
        state.profile.interests = response.interests || interests;
        localStorage.setItem(PROFILE_KEY, JSON.stringify(state.profile));
        state.coachLoadedFor.clear();
        closeInterests();
        showToast("Your interests have been updated.");
    } catch (err) {
        showToast(err.message, "error");
    } finally {
        setFormBusy(form, false);
    }
}

async function handleSearch(event) {
    event.preventDefault();
    const result = $("searchResult");
    result.textContent = "";
    try {
        const phone = normalizePhone($("peopleSearchInput").value);
        const person = await api(`/users/search?phone=${encodeURIComponent(phone)}`);
        renderSearchResult(person);
    } catch (err) {
        const message = document.createElement("p");
        message.className = "search-message is-error";
        message.textContent = err.message;
        result.appendChild(message);
    }
}

function renderSearchResult(person) {
    const result = $("searchResult");
    result.textContent = "";
    const card = document.createElement("div");
    card.className = "person-result";
    const avatar = document.createElement("span");
    avatar.className = "avatar";
    avatar.textContent = initials(person.fullName);
    const copy = document.createElement("span");
    copy.className = "person-result-copy";
    const name = document.createElement("strong");
    name.textContent = person.fullName;
    const phone = document.createElement("span");
    phone.textContent = person.phone;
    copy.append(name, phone);
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Message";
    button.addEventListener("click", () => startConversation(person, button));
    card.append(avatar, copy, button);
    result.appendChild(card);
}

async function startConversation(person, button) {
    button.disabled = true;
    button.textContent = "Opening…";
    try {
        const detail = await api("/chats", {
            method: "POST",
            body: JSON.stringify({ participantId: person.userId }),
        });
        $("searchResult").textContent = "";
        $("peopleSearchInput").value = "";
        await loadConversations(detail.conversationId);
        const conversation = state.conversations.find((item) => item.conversationId === detail.conversationId) || {
            conversationId: detail.conversationId,
            otherParticipantId: person.userId,
            otherUserName: person.fullName,
            otherUserPhone: person.phone,
            otherUserOnlineStatus: person.status,
            unreadCount: 0,
        };
        await openConversation(conversation);
    } catch (err) {
        showToast(err.message, "error");
        button.disabled = false;
        button.textContent = "Message";
    }
}

async function sendMessage(event) {
    event.preventDefault();
    const input = $("messageInput");
    const content = input.value.trim();
    if (!content || !state.activeId || state.moderationChecking) return;

    clearModerationWarning();
    state.moderationChecking = true;
    $("sendButton").disabled = true;
    try {
        const result = await api("/moderation/check", {
            method: "POST",
            body: JSON.stringify({ content }),
        });
        if (input.value.trim() !== content) return;
        if (result?.flagged) {
            showModerationWarning(content, result.matchedTerms || []);
            return;
        }
        await performSend(content, false);
    } catch (err) {
        showToast(err.message, "error");
    } finally {
        state.moderationChecking = false;
        resizeComposer();
    }
}

async function performSend(content, moderationOverride) {
    if (!content || !state.activeId) return;
    const clientMessageId = crypto.randomUUID ? crypto.randomUUID() : `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const optimistic = {
        id: `pending-${clientMessageId}`,
        conversationId: state.activeId,
        senderId: state.profile.userId,
        clientMessageId,
        content,
        status: "SENT",
        createdAt: new Date().toISOString(),
        pending: true,
    };
    upsertMessage(optimistic);
    const input = $("messageInput");
    if (input.value.trim() === content) input.value = "";
    clearModerationWarning();
    resizeComposer();
    sendTyping(false);

    const payload = { conversationId: state.activeId, content, clientMessageId, type: "TEXT", moderationOverride };
    try {
        if (state.stompConnected) {
            sendApplicationMessage("/app/chat.send", payload);
        } else {
            const saved = await api(`/chats/${encodeURIComponent(state.activeId)}/messages`, {
                method: "POST",
                body: JSON.stringify({ content, clientMessageId, type: "TEXT", moderationOverride }),
            });
            upsertMessage(saved);
        }
    } catch (err) {
        optimistic.pending = false;
        optimistic.status = "FAILED";
        renderMessages();
        showToast(err.message, "error");
    }
}

function showModerationWarning(content, matchedTerms) {
    state.pendingModeratedMessage = content;
    $("messageForm").classList.add("is-flagged");
    $("moderationHint").hidden = false;
    const terms = $("moderationTerms");
    terms.textContent = "";
    for (const term of matchedTerms.slice(0, 6)) {
        const chip = document.createElement("span");
        chip.textContent = term;
        terms.appendChild(chip);
    }
    $("moderationModal").hidden = false;
    document.body.style.overflow = "hidden";
    $("moderationEdit").focus();
}

function closeModerationWarning() {
    state.pendingModeratedMessage = null;
    $("moderationModal").hidden = true;
    document.body.style.overflow = "";
    $("messageInput").focus();
}

function clearModerationWarning() {
    $("messageForm").classList.remove("is-flagged");
    $("moderationHint").hidden = true;
}

async function sendModeratedMessage() {
    const content = state.pendingModeratedMessage;
    if (!content) return;
    closeModerationWarning();
    await performSend(content, true);
}

function acknowledgeLatestIncoming(messages) {
    const latest = [...messages].reverse().find((message) => Number(message.senderId) !== Number(state.profile?.userId));
    if (!latest?.id) return;
    if (state.stompConnected) {
        sendApplicationMessage("/app/chat.delivered", { conversationId: state.activeId, messageId: latest.id });
        sendApplicationMessage("/app/chat.read", { conversationId: state.activeId, messageId: latest.id });
    } else {
        api(`/chats/${encodeURIComponent(state.activeId)}/read`, {
            method: "POST",
            body: JSON.stringify({ messageId: latest.id }),
        }).catch(() => {});
    }
}

function resizeComposer() {
    const input = $("messageInput");
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 140)}px`;
    $("characterCount").textContent = `${input.value.length} / 2000`;
    const hasText = Boolean(input.value.trim());
    const sendBtn = $("sendButton");
    const voiceBtn = $("voiceNoteButton");
    if (sendBtn) {
        sendBtn.disabled = !hasText;
        sendBtn.hidden = !hasText;
    }
    if (voiceBtn) {
        voiceBtn.hidden = hasText;
    }
}

function handleComposerInput() {
    clearModerationWarning();
    resizeComposer();
    if (!state.activeId || !state.stompConnected) return;
    if (!state.sentTyping && $("messageInput").value.trim()) sendTyping(true);
    clearTimeout(state.typingTimer);
    state.typingTimer = setTimeout(() => sendTyping(false), 1200);
}

function sendTyping(typing) {
    clearTimeout(state.typingTimer);
    if (state.stompConnected && state.activeId && state.sentTyping !== typing) {
        sendApplicationMessage("/app/chat.typing", { conversationId: state.activeId, typing });
    }
    state.sentTyping = typing;
}

function connectRealtime() {
    if (!state.token || state.socket?.readyState === WebSocket.OPEN || state.socket?.readyState === WebSocket.CONNECTING) return;
    clearTimeout(state.reconnectTimer);
    updateLiveState("connecting");
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(`${protocol}//${location.host}/ws`);
    state.socket = socket;
    state.stompBuffer = "";

    socket.addEventListener("open", () => {
        sendFrame("CONNECT", {
            "accept-version": "1.2",
            "heart-beat": "10000,10000",
            Authorization: `Bearer ${state.token}`,
        });
    });
    socket.addEventListener("message", (event) => consumeStompData(String(event.data)));
    socket.addEventListener("error", () => updateLiveState("error"));
    socket.addEventListener("close", () => {
        state.stompConnected = false;
        clearInterval(state.heartbeatTimer);
        if (state.token) scheduleReconnect();
    });
}

function scheduleReconnect() {
    const delay = Math.min(1000 * 2 ** state.reconnectAttempt, 15000);
    state.reconnectAttempt += 1;
    updateLiveState("reconnecting");
    clearTimeout(state.reconnectTimer);
    state.reconnectTimer = setTimeout(connectRealtime, delay);
}

function consumeStompData(chunk) {
    if (chunk === "\n" || chunk === "\r\n") return;
    state.stompBuffer += chunk;
    let boundary;
    while ((boundary = state.stompBuffer.indexOf("\0")) >= 0) {
        const rawFrame = state.stompBuffer.slice(0, boundary).replace(/^[\r\n]+/, "");
        state.stompBuffer = state.stompBuffer.slice(boundary + 1);
        if (rawFrame) handleStompFrame(parseStompFrame(rawFrame));
    }
}

function parseStompFrame(raw) {
    const separator = raw.search(/\r?\n\r?\n/);
    const head = separator >= 0 ? raw.slice(0, separator) : raw;
    const body = separator >= 0 ? raw.slice(separator + (raw.slice(separator).startsWith("\r\n\r\n") ? 4 : 2)) : "";
    const lines = head.split(/\r?\n/);
    const command = lines.shift();
    const headers = {};
    for (const line of lines) {
        const index = line.indexOf(":");
        if (index > 0) headers[line.slice(0, index)] = line.slice(index + 1).replace(/\\c/g, ":").replace(/\\n/g, "\n").replace(/\\r/g, "\r").replace(/\\\\/g, "\\");
    }
    return { command, headers, body };
}

function handleStompFrame(frame) {
    if (frame.command === "CONNECTED") {
        state.stompConnected = true;
        state.reconnectAttempt = 0;
        updateLiveState("online");
        sendFrame("SUBSCRIBE", { id: "messages", destination: "/user/queue/messages", ack: "auto" });
        sendFrame("SUBSCRIBE", { id: "typing", destination: "/user/queue/typing", ack: "auto" });
        sendFrame("SUBSCRIBE", { id: "errors", destination: "/user/queue/errors", ack: "auto" });
        clearInterval(state.heartbeatTimer);
        state.heartbeatTimer = setInterval(() => {
            if (state.socket?.readyState === WebSocket.OPEN) state.socket.send("\n");
        }, 10000);
        return;
    }
    if (frame.command === "MESSAGE") {
        try { handleRealtimeEvent(JSON.parse(frame.body)); }
        catch { showToast("A real-time update could not be read.", "error"); }
        return;
    }
    if (frame.command === "ERROR") {
        showToast(frame.headers.message || "The real-time connection reported an error.", "error");
    }
}

function handleRealtimeEvent(event) {
    const data = event?.data;
    if (event?.eventType === "NEW_MESSAGE" && data) {
        upsertMessage(data);
        if (Number(data.senderId) !== Number(state.profile?.userId)) {
            sendApplicationMessage("/app/chat.delivered", { conversationId: data.conversationId, messageId: data.id });
            if (data.conversationId === state.activeId) {
                sendApplicationMessage("/app/chat.read", { conversationId: data.conversationId, messageId: data.id });
            }
        }
    } else if (event?.eventType === "MESSAGE_STATUS_UPDATE" && data) {
        const messages = state.messages.get(data.conversationId) || [];
        const message = messages.find((item) => item.id === data.messageId || item.publicId === data.messageId || item.clientMessageId === data.messageId);
        if (message) {
            message.status = data.status;
            message.pending = false;
        }
        if (data.conversationId === state.activeId) renderMessages();
    } else if (event?.eventType === "USER_TYPING" && data && data.conversationId === state.activeId && Number(data.userId) !== Number(state.profile?.userId)) {
        $("typingIndicator").hidden = !data.typing;
    } else if (event?.eventType === "ERROR") {
        showToast(data?.message || "A message could not be sent.", "error");
        markLatestPendingFailed();
    }
}

function markLatestPendingFailed() {
    const messages = state.messages.get(state.activeId) || [];
    const pending = [...messages].reverse().find((message) => message.pending);
    if (pending) {
        pending.pending = false;
        pending.status = "FAILED";
        renderMessages();
    }
}

function sendApplicationMessage(destination, payload) {
    sendFrame("SEND", { destination, "content-type": "application/json" }, JSON.stringify(payload));
}

function sendFrame(command, headers = {}, body = "") {
    if (!state.socket || state.socket.readyState !== WebSocket.OPEN) throw new Error("Real-time connection is not ready.");
    const finalHeaders = { ...headers };
    if (body) finalHeaders["content-length"] = new TextEncoder().encode(body).length;
    const headerLines = Object.entries(finalHeaders).map(([key, value]) => `${key}:${escapeStompHeader(String(value))}`);
    state.socket.send(`${command}\n${headerLines.join("\n")}\n\n${body}\0`);
}

function escapeStompHeader(value) {
    return value.replace(/\\/g, "\\\\").replace(/\r/g, "\\r").replace(/\n/g, "\\n").replace(/:/g, "\\c");
}

function updateLiveState(status) {
    const labels = { connecting: "Connecting", reconnecting: "Reconnecting", online: "Live", error: "Connection issue" };
    $("liveLabel").textContent = labels[status] || "Offline";
    $("liveDot").classList.toggle("is-online", status === "online");
    $("liveDot").classList.toggle("is-error", status === "error" || status === "reconnecting");
}

function relativeTime(value) {
    if (!value) return "";
    const date = new Date(value);
    const diff = Date.now() - date.getTime();
    if (diff < 60000) return "now";
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}h`;
    return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function clockTime(value) {
    const date = value ? new Date(value) : new Date();
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function formatStatus(status) {
    const labels = {
        SENT: "✓",
        DELIVERED: "✓✓",
        READ: "✓✓",
        EDITED: "Edited",
        DELETED: "Deleted",
        FAILED: "⚠ Failed"
    };
    return labels[status] || "✓";
}

function focusPhoneSearch() {
    workspace?.classList.remove("is-chat-open");
    $("peopleSearchInput").focus();
}

$("loginTab").addEventListener("click", () => switchAuthTab("login"));
$("registerTab").addEventListener("click", () => switchAuthTab("register"));
$("loginForm").addEventListener("submit", handleLogin);
$("registerForm").addEventListener("submit", handleRegister);
$("logoutButton").addEventListener("click", () => logout());
function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str || "";
    return div.innerHTML;
}

// -----------------------------------------------------------------------------
// WHATSAPP & TELEGRAM SERVICES & CONTROLS
// -----------------------------------------------------------------------------

function handleGoBack() {
    if (window.innerWidth <= 680 || workspace?.classList.contains("is-chat-open")) {
        workspace?.classList.remove("is-chat-open");
    } else {
        // Desktop back button: close the active chat and show start screen
        state.activeId = null;
        $("emptyConversation").hidden = false;
        $("activeConversation").hidden = true;
        workspace?.classList.remove("is-chat-open");
        renderConversations();
        closeReplyCoach();
        closeChatSearch();
        closeChatDropdown();
    }
}

// In-Chat Search
function toggleChatSearch() {
    const overlay = $("chatSearchOverlay");
    if (!overlay) return;
    overlay.hidden = !overlay.hidden;
    if (!overlay.hidden) {
        $("chatSearchInput").value = "";
        $("chatSearchMatches").textContent = "";
        $("chatSearchInput").focus();
    } else {
        closeChatSearch();
    }
}

function closeChatSearch() {
    const overlay = $("chatSearchOverlay");
    if (overlay) overlay.hidden = true;
    const input = $("chatSearchInput");
    if (input) input.value = "";
    const matches = $("chatSearchMatches");
    if (matches) matches.textContent = "";
    renderMessages();
}

function handleInChatSearch() {
    const query = ($("chatSearchInput")?.value || "").trim().toLowerCase();
    if (!query) {
        $("chatSearchMatches").textContent = "";
        renderMessages();
        return;
    }
    const messageEls = $("messageList").querySelectorAll(".message-bubble");
    let matchCount = 0;
    let firstMatchEl = null;

    messageEls.forEach((bubble) => {
        const text = bubble.getAttribute("data-raw-content") || bubble.textContent || "";
        const lower = text.toLowerCase();
        if (lower.includes(query)) {
            matchCount++;
            const regex = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, "gi");
            bubble.innerHTML = escapeHtml(text).replace(regex, '<mark class="chat-highlight">$1</mark>');
            bubble.closest(".message").style.display = "";
            if (!firstMatchEl) firstMatchEl = bubble;
        } else {
            bubble.textContent = text;
            bubble.closest(".message").style.display = "none";
        }
    });

    $("chatSearchMatches").textContent = `${matchCount} ${matchCount === 1 ? "match" : "matches"}`;
    if (firstMatchEl) {
        firstMatchEl.scrollIntoView({ behavior: "smooth", block: "center" });
    }
}

// Audio / Video Calling
let callTimerInterval = null;
let callDurationSec = 0;

function startCall(isVideo = false) {
    const activeConv = state.conversations.find((c) => c.conversationId === state.activeId);
    const name = activeConv?.otherUserName || activeConv?.otherUserPhone || $("chatName").textContent || "User";

    $("callContactName").textContent = name;
    $("callAvatar").textContent = initials(name);
    $("callTypeIcon").textContent = isVideo ? "📹" : "📞";
    $("callTypeLabel").textContent = isVideo ? "BondCircle Video" : "BondCircle Audio";
    $("callStatusText").textContent = "Ringing…";
    $("callModal").hidden = false;

    clearInterval(callTimerInterval);
    callDurationSec = 0;

    // Simulate connection after 2.2 seconds
    callTimerInterval = setTimeout(() => {
        $("callStatusText").textContent = "Connected · 00:00";
        callTimerInterval = setInterval(() => {
            callDurationSec++;
            const mins = String(Math.floor(callDurationSec / 60)).padStart(2, "0");
            const secs = String(callDurationSec % 60).padStart(2, "0");
            $("callStatusText").textContent = `Connected · ${mins}:${secs}`;
        }, 1000);
    }, 2200);
}

function endCall() {
    clearInterval(callTimerInterval);
    $("callStatusText").textContent = "Call ended";
    setTimeout(() => {
        $("callModal").hidden = true;
        showToast("Call ended", "info");
    }, 500);
}

// Header Menu Dropdown & Contact Info
function toggleChatDropdown() {
    const dropdown = $("chatMenuDropdown");
    if (dropdown) dropdown.hidden = !dropdown.hidden;
}

function closeChatDropdown() {
    const dropdown = $("chatMenuDropdown");
    if (dropdown) dropdown.hidden = true;
}

function openContactInfo() {
    const activeConv = state.conversations.find((c) => c.conversationId === state.activeId);
    const name = activeConv?.otherUserName || activeConv?.otherUserPhone || $("chatName").textContent || "Conversation";
    $("infoContactName").textContent = name;
    $("infoContactAvatar").textContent = initials(name);
    $("infoContactPhone").textContent = activeConv?.otherUserPhone || "Private number";
    $("contactInfoModal").hidden = false;
    closeChatDropdown();
}

function closeContactInfo() {
    $("contactInfoModal").hidden = true;
}

// Quick Emoji Drawer
function toggleEmojiDrawer() {
    const drawer = $("emojiDrawer");
    if (!drawer) return;
    drawer.hidden = !drawer.hidden;
    $("attachmentMenu").hidden = true;
}

function insertEmoji(emoji) {
    const input = $("messageInput");
    const start = input.selectionStart || input.value.length;
    const end = input.selectionEnd || input.value.length;
    input.value = input.value.substring(0, start) + emoji + input.value.substring(end);
    input.selectionStart = input.selectionEnd = start + emoji.length;
    input.focus();
    handleComposerInput();
}

// Attachment Menu
function toggleAttachmentMenu() {
    const menu = $("attachmentMenu");
    if (!menu) return;
    menu.hidden = !menu.hidden;
    $("emojiDrawer").hidden = true;
}

function handleAttachment(type) {
    $("attachmentMenu").hidden = true;
    if (type === "photo" || type === "document") {
        const fileInput = document.createElement("input");
        fileInput.type = "file";
        if (type === "photo") fileInput.accept = "image/*,video/*";
        fileInput.onchange = () => {
            if (fileInput.files?.length) {
                const file = fileInput.files[0];
                const placeholder = type === "photo" ? `📷 [Photo: ${file.name}]` : `📄 [Document: ${file.name}]`;
                $("messageInput").value += ($("messageInput").value ? " " : "") + placeholder;
                handleComposerInput();
                showToast(`Attached ${file.name}`);
            }
        };
        fileInput.click();
    } else if (type === "contact") {
        $("messageInput").value += ($("messageInput").value ? " " : "") + `👤 [Contact: ${$("chatName").textContent}]`;
        handleComposerInput();
    } else if (type === "poll") {
        $("messageInput").value += ($("messageInput").value ? " " : "") + `📊 [Poll: What time works best?]`;
        handleComposerInput();
    }
}

// Voice Note Recording Simulation
let isRecordingVoice = false;
function handleVoiceNote() {
    if (!isRecordingVoice) {
        isRecordingVoice = true;
        showToast("🎙️ Recording audio message... Tap again to send", "info");
        $("voiceNoteButton").style.background = "linear-gradient(180deg, #ef4444, #dc2626)";
    } else {
        isRecordingVoice = false;
        $("voiceNoteButton").style.background = "";
        const seconds = Math.floor(Math.random() * 8) + 3;
        performSend(`🎙️ Voice message (0:0${seconds})`, false);
        showToast("Voice message sent!", "info");
    }
}



$("loginTab").addEventListener("click", () => switchAuthTab("login"));
$("registerTab").addEventListener("click", () => switchAuthTab("register"));
$("loginForm").addEventListener("submit", handleLogin);
$("registerForm").addEventListener("submit", handleRegister);
$("logoutButton").addEventListener("click", () => logout());
$("peopleSearchForm").addEventListener("submit", handleSearch);
$("focusSearchButton").addEventListener("click", focusPhoneSearch);
$("emptySearchButton").addEventListener("click", focusPhoneSearch);

// Go Back button (Desktop and Mobile)
$("chatBackButton")?.addEventListener("click", handleGoBack);
$("mobileBackButton")?.addEventListener("click", handleGoBack);

// In-chat search
$("chatSearchBtn")?.addEventListener("click", toggleChatSearch);
$("chatSearchInput")?.addEventListener("input", handleInChatSearch);
$("closeChatSearch")?.addEventListener("click", closeChatSearch);

// Audio & Video calls
$("voiceCallBtn")?.addEventListener("click", () => startCall(false));
$("videoCallBtn")?.addEventListener("click", () => startCall(true));
$("endCallBtn")?.addEventListener("click", endCall);
$("callToggleMute")?.addEventListener("click", (e) => e.currentTarget.classList.toggle("is-active"));
$("callToggleSpeaker")?.addEventListener("click", (e) => e.currentTarget.classList.toggle("is-active"));

// Header options dropdown & contact info
$("chatMenuBtn")?.addEventListener("click", (e) => {
    e.stopPropagation();
    toggleChatDropdown();
});
$("menuContactInfo")?.addEventListener("click", openContactInfo);
$("contactInfoClose")?.addEventListener("click", closeContactInfo);
$("closeContactInfoBtn")?.addEventListener("click", closeContactInfo);
$("chatAvatar")?.addEventListener("click", openContactInfo);
$("chatName")?.addEventListener("click", openContactInfo);

$("menuMute")?.addEventListener("click", () => {
    closeChatDropdown();
    showToast("Notifications muted for this chat", "info");
});
$("menuClearChat")?.addEventListener("click", () => {
    closeChatDropdown();
    if (state.activeId) {
        state.messages.set(state.activeId, []);
        renderMessages();
        showToast("Chat cleared", "info");
    }
});
$("menuBlock")?.addEventListener("click", () => {
    closeChatDropdown();
    showToast("User blocked", "error");
});

// Composer tools
$("emojiButton")?.addEventListener("click", (e) => {
    e.stopPropagation();
    toggleEmojiDrawer();
});
$("closeEmojiDrawer")?.addEventListener("click", () => {
    const drawer = $("emojiDrawer");
    if (drawer) drawer.hidden = true;
});
document.querySelectorAll(".emoji-btn").forEach((btn) => {
    btn.addEventListener("click", () => insertEmoji(btn.getAttribute("data-emoji")));
});

$("attachButton")?.addEventListener("click", (e) => {
    e.stopPropagation();
    toggleAttachmentMenu();
});
document.querySelectorAll(".attach-item").forEach((btn) => {
    btn.addEventListener("click", () => handleAttachment(btn.getAttribute("data-type")));
});

$("voiceNoteButton")?.addEventListener("click", handleVoiceNote);

$("messageForm").addEventListener("submit", sendMessage);
$("messageInput").addEventListener("input", handleComposerInput);
$("messageInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        $("messageForm").requestSubmit();
    }
});

$("moderationClose").addEventListener("click", closeModerationWarning);
$("moderationEdit").addEventListener("click", closeModerationWarning);
$("moderationSendAnyway").addEventListener("click", () => sendModeratedMessage().catch((err) => showToast(err.message, "error")));
$("moderationModal").addEventListener("click", (event) => {
    if (event.target === $("moderationModal")) closeModerationWarning();
});

$("iceBreakerButton")?.addEventListener("click", toggleReplyCoach);
$("aiReplyComposerBtn")?.addEventListener("click", toggleReplyCoach);
$("replyCoachRegenBtn")?.addEventListener("click", regenerateReplySuggestions);
$("replyCoachCloseBtn")?.addEventListener("click", closeReplyCoach);

$("interestsButton").addEventListener("click", openInterests);
$("interestsClose").addEventListener("click", closeInterests);
$("interestsForm").addEventListener("submit", saveInterests);
$("interestsModal").addEventListener("click", (event) => {
    if (event.target === $("interestsModal")) closeInterests();
});

// Close popups on outside click
document.addEventListener("click", (event) => {
    const menuDropdown = $("chatMenuDropdown");
    if (menuDropdown && !menuDropdown.hidden && !event.target.closest(".chat-menu-wrapper")) {
        closeChatDropdown();
    }
    const emojiDrawer = $("emojiDrawer");
    if (emojiDrawer && !emojiDrawer.hidden && !event.target.closest("#emojiDrawer") && !event.target.closest("#emojiButton")) {
        emojiDrawer.hidden = true;
    }
    const attachMenu = $("attachmentMenu");
    if (attachMenu && !attachMenu.hidden && !event.target.closest("#attachmentMenu") && !event.target.closest("#attachButton")) {
        attachMenu.hidden = true;
    }
});

// ==========================================
// AI Settings & BYOK Modal Controller
// ==========================================

function openAiSettingsModal() {
    const modal = $("aiSettingsModal");
    if (!modal) return;
    const storedProvider = localStorage.getItem("bondcircle.ai_provider") || "gemini";
    const storedKey = localStorage.getItem("bondcircle.ai_key") || "";
    const storedModel = localStorage.getItem("bondcircle.ai_model") || "";

    const providerSelect = $("aiProviderSelect");
    const keyInput = $("aiApiKeyInput");
    const modelInput = $("aiModelInput");

    if (providerSelect) providerSelect.value = storedProvider;
    if (keyInput) keyInput.value = storedKey;
    if (modelInput) modelInput.value = storedModel;

    updateAiProviderHelp();
    updateAiSettingsStatusCard(Boolean(storedKey), storedProvider);

    modal.hidden = false;
    if (keyInput) keyInput.focus();
}

function closeAiSettingsModal() {
    const modal = $("aiSettingsModal");
    if (modal) modal.hidden = true;
}

function updateAiProviderHelp() {
    const provider = $("aiProviderSelect")?.value || "gemini";
    const helpEl = $("aiKeyHelpText");
    const modelInput = $("aiModelInput");
    const keyInput = $("aiApiKeyInput");

    if (provider === "gemini") {
        if (helpEl) helpEl.innerHTML = `💡 Get a 100% free key with no credit card at <a href="https://aistudio.google.com/app/apikey" target="_blank" rel="noopener noreferrer">Google AI Studio ↗</a>`;
        if (modelInput) modelInput.placeholder = "Default: gemini-2.0-flash";
        if (keyInput) keyInput.placeholder = "AIzaSy...";
    } else if (provider === "groq") {
        if (helpEl) helpEl.innerHTML = `💡 Obtain a high-speed free tier key at <a href="https://console.groq.com/keys" target="_blank" rel="noopener noreferrer">Groq Console ↗</a>`;
        if (modelInput) modelInput.placeholder = "Default: llama-3.3-70b-versatile";
        if (keyInput) keyInput.placeholder = "gsk_...";
    } else {
        if (helpEl) helpEl.innerHTML = `💡 Obtain your API key from <a href="https://platform.openai.com/api-keys" target="_blank" rel="noopener noreferrer">OpenAI Platform ↗</a>`;
        if (modelInput) modelInput.placeholder = "Default: gpt-4o-mini";
        if (keyInput) keyInput.placeholder = "sk-proj-...";
    }
}

function updateAiSettingsStatusCard(hasKey, provider) {
    const card = $("aiSettingsStatusCard");
    const title = $("aiStatusTitle");
    const desc = $("aiStatusDesc");
    if (!card || !title || !desc) return;

    if (hasKey) {
        card.classList.add("is-live");
        const provName = provider === "gemini" ? "Google Gemini" : provider === "groq" ? "Groq Llama" : "OpenAI ChatGPT";
        title.textContent = `Connected to ${provName}`;
        desc.textContent = "Your custom key is active. Suggestions will stream in real-time from the live LLM!";
    } else {
        card.classList.remove("is-live");
        title.textContent = "Offline Smart Engine Active";
        desc.textContent = "Using onboard smart rules, intent detection, and Hinglish banter. Connect a key for live generative LLM power!";
    }
}

function saveAiSettings() {
    const provider = $("aiProviderSelect")?.value || "gemini";
    const key = $("aiApiKeyInput")?.value?.trim() || "";
    const model = $("aiModelInput")?.value?.trim() || "";

    if (key) {
        localStorage.setItem("bondcircle.ai_key", key);
        localStorage.setItem("bondcircle.ai_provider", provider);
        if (model) localStorage.setItem("bondcircle.ai_model", model);
        else localStorage.removeItem("bondcircle.ai_model");
        showToast("Connected to live AI provider!", "info");
    } else {
        localStorage.removeItem("bondcircle.ai_key");
        localStorage.removeItem("bondcircle.ai_provider");
        localStorage.removeItem("bondcircle.ai_model");
        showToast("Switched to smart offline engine.", "info");
    }

    closeAiSettingsModal();
    if (state.activeId && state.replyCoach?.visible) {
        fetchReplySuggestions();
    }
}

function disconnectAiSettings() {
    localStorage.removeItem("bondcircle.ai_key");
    localStorage.removeItem("bondcircle.ai_provider");
    localStorage.removeItem("bondcircle.ai_model");

    if ($("aiApiKeyInput")) $("aiApiKeyInput").value = "";
    if ($("aiModelInput")) $("aiModelInput").value = "";
    updateAiSettingsStatusCard(false, "gemini");

    showToast("Disconnected key. Smart offline engine restored.", "info");
    closeAiSettingsModal();
    if (state.activeId && state.replyCoach?.visible) {
        fetchReplySuggestions();
    }
}

$("aiSettingsButton")?.addEventListener("click", openAiSettingsModal);
$("closeAiSettingsModal")?.addEventListener("click", closeAiSettingsModal);
$("cancelAiSettingsBtn")?.addEventListener("click", closeAiSettingsModal);
$("saveAiSettingsBtn")?.addEventListener("click", saveAiSettings);
$("disconnectAiKeyBtn")?.addEventListener("click", disconnectAiSettings);
$("aiProviderSelect")?.addEventListener("change", updateAiProviderHelp);
$("toggleAiKeyVisibility")?.addEventListener("click", () => {
    const keyInput = $("aiApiKeyInput");
    const toggleBtn = $("toggleAiKeyVisibility");
    if (!keyInput || !toggleBtn) return;
    if (keyInput.type === "password") {
        keyInput.type = "text";
        toggleBtn.textContent = "🙈 Hide";
    } else {
        keyInput.type = "password";
        toggleBtn.textContent = "👁️ Show";
    }
});
$("aiSettingsModal")?.addEventListener("click", (event) => {
    if (event.target === $("aiSettingsModal")) closeAiSettingsModal();
});

document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !$("moderationModal").hidden) closeModerationWarning();
    if (event.key === "Escape" && !$("interestsModal").hidden) closeInterests();
    if (event.key === "Escape" && !$("callModal").hidden) endCall();
    if (event.key === "Escape" && !$("contactInfoModal").hidden) closeContactInfo();
    if (event.key === "Escape" && !$("chatSearchOverlay").hidden) closeChatSearch();
    if (event.key === "Escape" && $("aiSettingsModal") && !$("aiSettingsModal").hidden) closeAiSettingsModal();
});

if (state.token && state.profile) enterChat();
else switchAuthTab("login");
