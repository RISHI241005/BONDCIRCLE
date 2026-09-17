# BondCircle Conversation Circles

BondCircle ranks draft messages by how close they are to the current conversation. The engine uses deterministic rules, so it works without sending private chats to an external AI provider.

## The five circles

1. **Right now (`DIRECT_REPLY`)** — uses a safe topic signal from the latest incoming message.
2. **Your history (`CALLBACK`)** — revisits recurring topics found in prior messages between the same participants.
3. **Shared interests (`COMMON_GROUND`)** — uses interests present in both profiles.
4. **Their world (`DISCOVERY`)** — explores a partner interest or uses balanced self-disclosure.
5. **Easy and playful (`LIGHT_TOUCH`)** — provides general, low-pressure prompts when closer signals are unavailable.

The API balances results across available circles instead of returning many near-duplicate variations from a single topic. Each draft includes its circle, tone, explanation, topic, and relevance score.

## Context pipeline

The service reads the latest configurable message window, removes deleted messages, identifies the latest incoming topic, counts recurring safe topics, and combines those signals with both profiles' interests. Extracted terms pass through the existing language moderation service before they can appear in a generated draft.

Configuration lives under `chat.icebreaker`:

```yaml
chat:
  icebreaker:
    history-lookback: 80
    default-suggestions: 12
    max-suggestions: 20
    quiet-after-hours: 6
```

All values can be overridden through the corresponding `ICEBREAKER_*` environment variables.

## API

`GET /api/v1/chats/{conversationId}/ice-breakers?limit=16&tone=ALL&variant=0`

- `limit`: requested result count, clamped to the configured maximum.
- `tone`: `ALL`, `CURIOUS`, `WARM`, `PLAYFUL`, or `THOUGHTFUL`.
- `variant`: changes ordering within similarly relevant drafts and powers the refresh button.

Only authenticated conversation participants can request suggestions. Selecting a suggestion copies it into the composer; it is never sent automatically.
