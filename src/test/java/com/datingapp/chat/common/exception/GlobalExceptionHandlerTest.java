package com.datingapp.chat.common.exception;

import com.datingapp.chat.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@Import(GlobalExceptionHandlerTest.ExceptionTestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/test/exceptions")
    static class ExceptionTestController {
        @GetMapping("/not-found")
        public ApiResponse<String> throwNotFound() {
            throw new ResourceNotFoundException("Test conversation not found", ErrorCode.CONVERSATION_NOT_FOUND);
        }

        @GetMapping("/forbidden")
        public ApiResponse<String> throwForbidden() {
            throw new ForbiddenException("Blocked user cannot send message", ErrorCode.USER_BLOCKED);
        }

        @GetMapping("/bad-request")
        public ApiResponse<String> throwBadRequest() {
            throw new BadRequestException("Message exceeds limit", ErrorCode.MESSAGE_TOO_LONG);
        }

        @GetMapping("/type-mismatch/{id}")
        public ApiResponse<Long> throwTypeMismatch(@PathVariable Long id) {
            return ApiResponse.success(id);
        }
    }

    @Test
    @DisplayName("ResourceNotFoundException should map to 404 with error envelope")
    void testResourceNotFound() throws Exception {
        mockMvc.perform(get("/test/exceptions/not-found")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONVERSATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Test conversation not found"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("ForbiddenException should map to 403 with error envelope")
    void testForbidden() throws Exception {
        mockMvc.perform(get("/test/exceptions/forbidden")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("USER_BLOCKED"))
                .andExpect(jsonPath("$.message").value("Blocked user cannot send message"));
    }

    @Test
    @DisplayName("BadRequestException should map to 400 with error envelope")
    void testBadRequest() throws Exception {
        mockMvc.perform(get("/test/exceptions/bad-request")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MESSAGE_TOO_LONG"));
    }

    @Test
    @DisplayName("Type mismatch parameter should map to 400 Bad Request")
    void testTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/exceptions/type-mismatch/not-a-number")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("Non-existent route should map to 404 Resource Not Found")
    void testUnmappedRoute() throws Exception {
        mockMvc.perform(get("/api/v1/non-existent-path")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
