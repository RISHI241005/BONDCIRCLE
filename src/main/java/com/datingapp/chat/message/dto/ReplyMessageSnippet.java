package com.datingapp.chat.message.dto;

import com.datingapp.chat.message.entity.MessageType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Snippet representing a replied-to message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Replied message summary snippet")
public class ReplyMessageSnippet {

    @Schema(description = "Public UUID of replied message", example = "7b8d1b32-8df2-4f1e-9273-df16a7f34c22")
    private final String id;

    @Schema(description = "Sender user ID of replied message", example = "204")
    private final Long senderId;

    @Schema(description = "Snippet content of replied message", example = "Are we meeting at 7?")
    private final String content;

    @Schema(description = "Message type of replied message", example = "TEXT")
    private final MessageType type;

    public ReplyMessageSnippet(String id, Long senderId, String content, MessageType type) {
        this.id = id;
        this.senderId = senderId;
        this.content = content;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public MessageType getType() {
        return type;
    }
}
