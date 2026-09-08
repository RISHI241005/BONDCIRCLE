package com.datingapp.chat.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ModerationCheckRequest {

    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 2000, message = "Message content must not exceed 2000 characters")
    private String content;

    public ModerationCheckRequest() {
    }

    public ModerationCheckRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
