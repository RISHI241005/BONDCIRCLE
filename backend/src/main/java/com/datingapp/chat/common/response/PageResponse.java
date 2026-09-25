package com.datingapp.chat.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Standard cursor-paginated payload container.
 *
 * @param <T> Item entity DTO type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Paginated list response")
public class PageResponse<T> {

    @Schema(description = "List of items in the current page")
    private final List<T> items;

    @Schema(description = "Pagination metadata")
    private final CursorPaginationMeta pagination;

    public PageResponse(List<T> items, CursorPaginationMeta pagination) {
        this.items = items;
        this.pagination = pagination;
    }

    public static <T> PageResponse<T> of(List<T> items, String nextCursor, boolean hasMore, int limit) {
        return new PageResponse<>(items, new CursorPaginationMeta(nextCursor, hasMore, limit));
    }

    public List<T> getItems() {
        return items;
    }

    public CursorPaginationMeta getPagination() {
        return pagination;
    }
}
