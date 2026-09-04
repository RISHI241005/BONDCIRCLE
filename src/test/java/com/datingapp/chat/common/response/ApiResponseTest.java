package com.datingapp.chat.common.response;

import com.datingapp.chat.common.util.CursorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    @DisplayName("Should construct standard success ApiResponse with data and timestamp")
    void testSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("test-payload", "Success message");

        assertTrue(response.isSuccess());
        assertEquals("test-payload", response.getData());
        assertEquals("Success message", response.getMessage());
        assertNotNull(response.getTimestamp());
        assertNotNull(response.getRequestId());
    }

    @Test
    @DisplayName("Should construct standard ApiError response with error code")
    void testErrorResponse() {
        ApiError error = ApiError.of("Something went wrong", "RESOURCE_NOT_FOUND");

        assertFalse(error.isSuccess());
        assertNull(error.getData());
        assertEquals("Something went wrong", error.getMessage());
        assertEquals("RESOURCE_NOT_FOUND", error.getErrorCode());
        assertNotNull(error.getTimestamp());
        assertNotNull(error.getRequestId());
    }

    @Test
    @DisplayName("Should construct PageResponse with pagination metadata")
    void testPageResponse() {
        List<String> items = List.of("msg1", "msg2");
        PageResponse<String> page = PageResponse.of(items, "next-cursor-token", true, 2);

        assertEquals(2, page.getItems().size());
        assertEquals("next-cursor-token", page.getPagination().getNextCursor());
        assertTrue(page.getPagination().isHasMore());
        assertEquals(2, page.getPagination().getLimit());
    }

    @Test
    @DisplayName("Should encode and decode cursor payload accurately")
    void testCursorUtils() {
        Instant now = Instant.ofEpochMilli(1756230000000L);
        Long id = 42L;

        String encoded = CursorUtils.encodeCursor(now, id);
        assertNotNull(encoded);

        CursorUtils.CursorPayload payload = CursorUtils.decodeCursor(encoded);
        assertNotNull(payload);
        assertEquals(now, payload.getCreatedAt());
        assertEquals(id, payload.getId());
    }
}
