package com.datingapp.chat.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Metadata for cursor-based pagination.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Cursor pagination metadata")
public class CursorPaginationMeta {

    @Schema(description = "Opaque cursor token for the next page of results", example = "eyJpZCI6MTIwLCJjcmVhdGVkQXQiOiIyMDI2LTA4LTI2VDE5OjEwOjAwWiJ9")
    private final String nextCursor;

    @Schema(description = "Indicates whether additional records exist beyond current page", example = "true")
    private final boolean hasMore;

    @Schema(description = "Number of items returned in the current page", example = "30")
    private final int limit;

    public CursorPaginationMeta(String nextCursor, boolean hasMore, int limit) {
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.limit = limit;
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
