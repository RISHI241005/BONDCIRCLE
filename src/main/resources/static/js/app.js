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
    coachLoadedFor: new Set(),
    coachResponse: null,
    coachVariant: 0,
    autoReplyFor: new Set(),
    autoReplyHandled: new Set(),
    autoReplyRequest: 0,
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
    state.autoReplyFor.clear();
    state.autoReplyHandled.clear();
    state.autoReplyRequest += 1;
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
    state.coachResponse = null;
    state.coachVariant = 0;
    $("aiAutoReply").checked = state.autoReplyFor.has(state.activeId);
    closeIceBreakers();

    try {
        const page = await api(`/chats/${encodeURIComponent(state.activeId)}/messages?limit=50`);
        const messages = [...(page?.messages || [])].reverse();
        state.messages.set(state.activeId, messages);
        renderMessages();
        acknowledgeLatestIncoming(messages);
        maybeSurfaceIceBreakers(messages);
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
        const meta = document.createElement("div");
        meta.className = "message-meta";
        const time = document.createElement("time");
        time.dateTime = message.createdAt || "";
        time.textContent = clockTime(message.createdAt);
        meta.appendChild(time);
        if (mine) {
            const status = document.createElement("span");
            status.className = "message-status";
            status.textContent = message.pending ? "Sending" : formatStatus(message.status);
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

function shouldSurfaceIceBreakers(messages) {
    if (!messages.length) return true;
    const lastMessageAt = new Date(messages[messages.length - 1]?.createdAt || 0).getTime();
    if (lastMessageAt && Date.now() - lastMessageAt > 6 * 60 * 60 * 1000) return true;
    if (messages.length < 3) return false;
    return messages.slice(-4).filter((message) => String(message.content || "").trim().length <= 24).length >= 3;
}

function maybeSurfaceIceBreakers(messages) {
    if (!shouldSurfaceIceBreakers(messages) || state.coachLoadedFor.has(state.activeId)) return;
    state.coachLoadedFor.add(state.activeId);
    loadIceBreakers(true);
}

async function loadIceBreakers(automatic = false, modeOverride = null) {
    const conversationId = state.activeId;
    if (!conversationId) return;
    const panel = $("iceBreakerPanel");
    const suggestions = $("iceBreakerSuggestions");
    panel.hidden = false;
    $("iceBreakerButton").setAttribute("aria-expanded", "true");
    suggestions.textContent = "";
    const loading = document.createElement("p");
    loading.className = "coach-loading";
    loading.textContent = "Reading the conversation and writing fresh replies…";
    suggestions.appendChild(loading);
    try {
        const language = $("assistantLanguage").value;
        const mode = modeOverride || $("assistantMode").value;
        const limit = mode === "AUTOPILOT" ? 1 : 12;
        const params = new URLSearchParams({
            limit: String(limit),
            variant: String(state.coachVariant),
            language,
            mode,
        });
        const response = await api(`/chats/${encodeURIComponent(conversationId)}/ice-breakers?${params}`);
        if (state.activeId !== conversationId) return;
        renderIceBreakers(response);
    } catch (err) {
        closeIceBreakers();
        if (!automatic) showToast(err.message, "error");
    }
}

function renderIceBreakers(response) {
    state.coachResponse = response;
    const panel = $("iceBreakerPanel");
    panel.classList.toggle("is-live", Boolean(response?.generatedLive));
    panel.classList.toggle("is-fallback", !response?.generatedLive);
    $("iceBreakerGuidance").textContent = response?.guidance || "Choose a reply that sounds like you.";
    const circleFilter = $("iceBreakerCircleFilter");
    const selectedCircle = circleFilter.value;
    circleFilter.textContent = "";
    const allOption = document.createElement("option");
    allOption.value = "ALL";
    allOption.textContent = "All circles";
    circleFilter.appendChild(allOption);
    for (const circle of response?.circles || []) {
        const option = document.createElement("option");
        option.value = circle.code;
        option.textContent = circle.label;
        circleFilter.appendChild(option);
    }
    circleFilter.value = [...circleFilter.options].some((option) => option.value === selectedCircle) ? selectedCircle : "ALL";
    renderFilteredIceBreakers();
    if (response?.generatedLive && response?.mode === "WRITE_FOR_ME" && response.suggestions?.[0]?.text) {
        $("messageInput").value = response.suggestions[0].text;
        resizeComposer();
        showToast("AI wrote a draft. Review it before sending.");
    }
}

function renderFilteredIceBreakers() {
    const container = $("iceBreakerSuggestions");
    container.textContent = "";
    const circle = $("iceBreakerCircleFilter").value;
    const tone = $("iceBreakerToneFilter").value;
    const visibleSuggestions = (state.coachResponse?.suggestions || []).filter((suggestion) =>
        (circle === "ALL" || suggestion.circle === circle) &&
        (tone === "ALL" || suggestion.tone === tone));
    if (!visibleSuggestions.length) {
        const empty = document.createElement("p");
        empty.className = "coach-loading";
        empty.textContent = "No ideas match these filters. Try another circle or tone.";
        container.appendChild(empty);
        return;
    }
    for (const suggestion of visibleSuggestions) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "coach-suggestion";
        button.title = suggestion.reason || "Use this suggestion";
        const meta = document.createElement("div");
        meta.className = "coach-suggestion-meta";
        const circleLabel = document.createElement("span");
        circleLabel.textContent = suggestion.circleLabel || suggestion.topic || "Conversation idea";
        const toneLabel = document.createElement("span");
        toneLabel.textContent = String(suggestion.tone || "").toLowerCase();
        const languageLabel = document.createElement("span");
        languageLabel.textContent = String(suggestion.language || "English").toLowerCase();
        meta.append(circleLabel, toneLabel, languageLabel);
        const text = document.createElement("strong");
        text.textContent = suggestion.text;
        const reason = document.createElement("small");
        reason.textContent = suggestion.reason || "";
        button.append(meta, text, reason);
        button.addEventListener("click", () => {
            $("messageInput").value = suggestion.text;
            resizeComposer();
            closeIceBreakers();
            $("messageInput").focus();
        });
        container.appendChild(button);
    }
}

function toggleAutoReply() {
    if (!state.activeId) {
        $("aiAutoReply").checked = false;
        return;
    }
    if ($("aiAutoReply").checked) {
        state.autoReplyFor.add(state.activeId);
        showToast("AI auto-reply is on for this open chat.");
        const messages = state.messages.get(state.activeId) || [];
        const latest = messages[messages.length - 1];
        if (latest && Number(latest.senderId) !== Number(state.profile?.userId)) {
            scheduleAutoReply(latest);
        }
    } else {
        state.autoReplyFor.delete(state.activeId);
        state.autoReplyRequest += 1;
        showToast("AI auto-reply is off.");
    }
}

function scheduleAutoReply(message) {
    const conversationId = message?.conversationId || state.activeId;
    const messageId = message?.id;
    if (!conversationId || !messageId
            || conversationId !== state.activeId
            || !state.autoReplyFor.has(conversationId)
            || state.autoReplyHandled.has(messageId)
            || Number(message.senderId) === Number(state.profile?.userId)) return;

    state.autoReplyHandled.add(messageId);
    const requestId = ++state.autoReplyRequest;
    setTimeout(async () => {
        if (requestId !== state.autoReplyRequest
                || conversationId !== state.activeId
                || !state.autoReplyFor.has(conversationId)) return;
        const messages = state.messages.get(conversationId) || [];
        const latest = messages[messages.length - 1];
        if (!latest || Number(latest.senderId) === Number(state.profile?.userId)) return;
        try {
            const params = new URLSearchParams({
                limit: "1",
                variant: String(Date.now()),
                language: $("assistantLanguage").value,
                mode: "AUTOPILOT",
            });
            const response = await api(`/chats/${encodeURIComponent(conversationId)}/ice-breakers?${params}`);
            if (requestId !== state.autoReplyRequest
                    || conversationId !== state.activeId
                    || !state.autoReplyFor.has(conversationId)) return;
            const current = state.messages.get(conversationId) || [];
            const currentLatest = current[current.length - 1];
            if (!currentLatest || Number(currentLatest.senderId) === Number(state.profile?.userId)) return;
            if (!response?.generatedLive) {
                state.autoReplyFor.delete(conversationId);
                $("aiAutoReply").checked = false;
                throw new Error("Live AI is not configured, so auto-reply was switched off.");
            }
            const reply = response.suggestions?.[0]?.text?.trim();
            if (!reply) throw new Error("AI could not create a safe reply.");
            await performSend(reply, false);
            showToast("AI sent a reply for you.");
        } catch (err) {
            showToast(err.message, "error");
        }
    }, 1400);
}

function closeIceBreakers() {
    $("iceBreakerPanel").hidden = true;
    $("iceBreakerButton").setAttribute("aria-expanded", "false");
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
    $("sendButton").disabled = !input.value.trim();
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
                scheduleAutoReply(data);
            }
        }
    } else if (event?.eventType === "MESSAGE_STATUS_UPDATE" && data) {
        const messages = state.messages.get(data.conversationId) || [];
        const message = messages.find((item) => item.id === data.messageId);
        if (message) message.status = data.status;
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
    const labels = { SENT: "Sent", DELIVERED: "Delivered", READ: "Read", EDITED: "Edited", DELETED: "Deleted", FAILED: "Failed" };
    return labels[status] || "Sent";
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
$("peopleSearchForm").addEventListener("submit", handleSearch);
$("focusSearchButton").addEventListener("click", focusPhoneSearch);
$("emptySearchButton").addEventListener("click", focusPhoneSearch);
$("mobileBackButton").addEventListener("click", () => workspace.classList.remove("is-chat-open"));
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
$("iceBreakerButton").addEventListener("click", () => {
    if ($("iceBreakerPanel").hidden) loadIceBreakers(false);
    else closeIceBreakers();
});
$("refreshIceBreakers").addEventListener("click", () => {
    state.coachVariant += 1;
    loadIceBreakers(false);
});
$("closeIceBreakers").addEventListener("click", closeIceBreakers);
$("iceBreakerCircleFilter").addEventListener("change", renderFilteredIceBreakers);
$("iceBreakerToneFilter").addEventListener("change", renderFilteredIceBreakers);
$("assistantLanguage").addEventListener("change", () => {
    if (!$("iceBreakerPanel").hidden) loadIceBreakers(false);
});
$("assistantMode").addEventListener("change", () => {
    if (!$("iceBreakerPanel").hidden) loadIceBreakers(false);
});
$("aiAutoReply").addEventListener("change", toggleAutoReply);
$("interestsButton").addEventListener("click", openInterests);
$("interestsClose").addEventListener("click", closeInterests);
$("interestsForm").addEventListener("submit", saveInterests);
$("interestsModal").addEventListener("click", (event) => {
    if (event.target === $("interestsModal")) closeInterests();
});
document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !$("moderationModal").hidden) closeModerationWarning();
    if (event.key === "Escape" && !$("interestsModal").hidden) closeInterests();
});

if (state.token && state.profile) enterChat();
else switchAuthTab("login");
