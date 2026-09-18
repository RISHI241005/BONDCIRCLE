package com.datingapp.chat.replycoach.entity;

/**
 * User interaction with a generated AI Reply Coach suggestion.
 */
public enum FeedbackAction {
    /** Suggestion was displayed to user (auto-recorded). */
    SHOWN,
    /** User tapped "Use" — suggestion inserted into composer. */
    USED,
    /** User tapped "Not this" / refresh — suggestion discarded. */
    REJECTED,
    /** User tapped a copy button (if present). */
    COPIED,
    /** User edited the suggestion before sending. */
    EDITED,
    /** User sent the (possibly edited) suggestion. */
    SENT
}
