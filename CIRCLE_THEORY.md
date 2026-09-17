# BondCircle Live AI Conversation Assistant

BondCircle presents four simple English or Hinglish reply suggestions based on the active conversation and both profiles' interests. There are no language, tone, circle, or mode controls in the chat: the assistant infers what feels natural from the conversation. The five-circle model works quietly in the background.

## The five circles

1. **Right now (`DIRECT_REPLY`)** — uses a safe topic signal from the latest incoming message.
2. **Your history (`CALLBACK`)** — revisits recurring topics found in prior messages between the same participants.
3. **Shared interests (`COMMON_GROUND`)** — uses interests present in both profiles.
4. **Their world (`DISCOVERY`)** — explores a partner interest or uses balanced self-disclosure.
5. **Easy and playful (`LIGHT_TOUCH`)** — provides general, low-pressure prompts when closer signals are unavailable.

Each live result includes its circle, tone, language, explanation, topic, and relevance score. The model is required to return a strict JSON schema, and every generated message passes through BondCircle moderation before it reaches the browser.

## Context pipeline

The service reads the latest configurable message window, removes deleted messages, labels messages as `ME` or `THEM`, and combines the history with both profiles' interests. Conversation text is sent as untrusted quoted data, provider storage is disabled in the request, and the API key remains server-side.

If the provider is not configured or temporarily fails, the deterministic Circle engine supplies offline suggestions. Suggestions are always drafts: tapping one places it in the composer so the user can review or edit it before sending.

Configuration lives under `chat.icebreaker`:

```yaml
chat:
  icebreaker:
    history-lookback: 80
    default-suggestions: 4
    max-suggestions: 6
    quiet-after-hours: 6
  ai-assistant:
    enabled: true
    api-key: ${VERCEL_OIDC_TOKEN}
    base-url: https://ai-gateway.vercel.sh/v1
    model: openai/gpt-5.4-mini
    history-messages: 40
    max-suggestions: 6
```

On Vercel, the automatically injected `VERCEL_OIDC_TOKEN` authenticates the AI Gateway without storing another key. Elsewhere, set `AI_GATEWAY_API_KEY` or `AI_ASSISTANT_API_KEY`. All AI settings can be overridden with `AI_ASSISTANT_*` environment variables, including an alternate OpenResponses-compatible base URL and model.

## API

`GET /api/v1/chats/{conversationId}/ice-breakers?limit=4&variant=0`

- `limit`: requested result count, clamped to the configured maximum.
- `tone`: `ALL`, `CURIOUS`, `WARM`, `PLAYFUL`, or `THOUGHTFUL`.
- `language`: `AUTO`, `ENGLISH`, or `HINGLISH`.
- `mode`: `SUGGEST`, `WRITE_FOR_ME`, or `AUTOPILOT`.
- `variant`: asks the AI for a noticeably different angle and powers the Refresh button.

Only authenticated conversation participants can request replies. The browser automatically uses `AUTO` language, `ALL` tone, and suggestion mode. Advanced API parameters remain available for backward compatibility, but they are not exposed in the chat interface.
