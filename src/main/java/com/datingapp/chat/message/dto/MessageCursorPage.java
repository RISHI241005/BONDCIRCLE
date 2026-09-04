package com.datingapp.chat.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Message history cursor pagination response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Cursor-paginated message history")
public class MessageCursorPage {

    @Schema(description = "List of messages in current page ordered newest to oldest")
    private final List<MessageResponse> messages;

    @Schema(description = "Opaque cursor token for the next page of older messages", example = "eyJpZCI6MTIwLCJjcmVhdGVkQXQiOiIyMDI2LTA4LTI2VDE5OjEwOjAwWiJ9")
    private final String nextCursor;

    @Schema(description = "Indicates whether older messages exist in this conversation", example = "true")
    private final boolean hasMore;

    @Schema(description = "Requested page size limit", example = "30")
    private final int limit;

    public MessageCursorPage(List<MessageResponse> messages, String nextCursor, boolean hasMore, int limit) {
        this.messages = messages;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.limit = limit;
    }

    public List<MessageResponse> getMessages() {
        return messages;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public int getLimit() {
        return limit;
    }
}
