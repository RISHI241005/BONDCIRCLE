package com.datingapp.chat.icebreaker.service;

import com.datingapp.chat.icebreaker.dto.IceBreakerResponse;

public interface IceBreakerService {
    IceBreakerResponse getSuggestions(String conversationId, Long userId);

    IceBreakerResponse getSuggestions(String conversationId, Long userId, int limit, String tone, int variant);
}
