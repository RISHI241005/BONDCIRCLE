package com.datingapp.chat.websocket.service.impl;

import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.websocket.dto.event.WsEvent;
import com.datingapp.chat.websocket.dto.event.WsEventType;
import com.datingapp.chat.websocket.service.WebSocketBroadcastService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;

/** Transactional outbox: each active instance forwards committed events to its sockets. */
@Service
@Profile("vercel")
public class PostgresBroadcastService implements WebSocketBroadcastService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final SimpMessagingTemplate sockets;
    private final SimpUserRegistry users;
    private final Map<Long, Long> seen = new HashMap<>();

    public PostgresBroadcastService(JdbcTemplate db, ObjectMapper json,
            SimpMessagingTemplate sockets, SimpUserRegistry users) {
        this.db = db; this.json = json; this.sockets = sockets; this.users = users;
    }

    private void enqueue(List<Long> recipients, String destination, WsEvent<?> event) {
        try {
            String payload = json.writeValueAsString(event);
            for (Long recipient : recipients) {
                db.update("INSERT INTO realtime_events(recipient,destination,payload) VALUES (?,?,?)",
                        recipient, destination, payload);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize realtime event", e);
        }
    }

    @Override
    public void broadcastNewMessage(String conversationId, MessageResponse message, List<Long> recipients) {
        enqueue(recipients, "/queue/messages", WsEvent.of(WsEventType.NEW_MESSAGE, message));
    }
    @Override
    public void broadcastTyping(String conversationId, Long senderId, boolean typing, List<Long> recipients) {
        enqueue(recipients, "/queue/typing", WsEvent.typing(conversationId, senderId, typing));
    }
    @Override
    public void broadcastMessageStatusUpdate(String conversationId, String messageId,
            MessageStatus status, Long updatedBy, List<Long> recipients) {
        enqueue(recipients, "/queue/messages", WsEvent.statusUpdate(conversationId, messageId, status.name(), updatedBy));
    }
    @Override
    public void sendErrorMessageToUser(Long userId, String code, String message) {
        sockets.convertAndSendToUser(userId.toString(), "/queue/errors", WsEvent.error(code, message));
    }

    @Scheduled(fixedDelay = 750)
    public void forwardCommittedEvents() {
        if (users.getUserCount() == 0) { seen.clear(); return; }
        long now = System.currentTimeMillis();
        seen.entrySet().removeIf(entry -> now - entry.getValue() > 180000);
        // Overlap the query window: sequence IDs alone can skip transactions committed out of order.
        db.query("SELECT id,recipient,destination,payload FROM realtime_events "
                + "WHERE created_at > clock_timestamp() - interval '2 minutes' ORDER BY id", rs -> {
            long id = rs.getLong("id");
            if (seen.containsKey(id)) return;
            String recipient = Long.toString(rs.getLong("recipient"));
            if (users.getUser(recipient) != null) {
                try {
                    sockets.convertAndSendToUser(recipient, rs.getString("destination"),
                            json.readValue(rs.getString("payload"), WsEvent.class));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Invalid stored realtime event", e);
                }
            }
            seen.put(id, now);
        });
    }

    @Scheduled(fixedDelay = 300000)
    public void removeExpiredEvents() {
        db.update("DELETE FROM realtime_events WHERE created_at < clock_timestamp() - interval '10 minutes'");
    }
}
