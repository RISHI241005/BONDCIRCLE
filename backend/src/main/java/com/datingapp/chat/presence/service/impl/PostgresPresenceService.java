package com.datingapp.chat.presence.service.impl;

import com.datingapp.chat.presence.dto.UserPresenceResponse;
import com.datingapp.chat.presence.model.PresenceStatus;
import com.datingapp.chat.presence.service.PresenceService;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@Profile("vercel")
public class PostgresPresenceService implements PresenceService {
    private final JdbcTemplate db;
    private final String instanceId = UUID.randomUUID().toString();
    public PostgresPresenceService(JdbcTemplate db) { this.db = db; }

    public void registerSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null) return;
        db.update("INSERT INTO realtime_sessions(session_id,instance_id,user_id) VALUES (?,?,?) "
                + "ON CONFLICT(session_id) DO UPDATE SET connected=true,touched_at=clock_timestamp()",
                sessionId, instanceId, userId);
    }
    public void unregisterSession(String sessionId) {
        if (sessionId == null) return;
        db.update("UPDATE realtime_sessions SET connected=false,touched_at=clock_timestamp() WHERE session_id=?",
                sessionId);
    }
    public boolean isUserOnline(Long userId) {
        if (userId == null) return false;
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM realtime_sessions "
                + "WHERE user_id=? AND connected AND touched_at > clock_timestamp() - interval '60 seconds')",
                Boolean.class, userId));
    }
    public UserPresenceResponse getUserPresence(Long userId, Long requesterId) {
        if (isUserOnline(userId)) return new UserPresenceResponse(userId, PresenceStatus.ONLINE, null);
        java.sql.Timestamp last = db.queryForObject(
                "SELECT max(touched_at) FROM realtime_sessions WHERE user_id=?", java.sql.Timestamp.class, userId);
        return new UserPresenceResponse(userId, PresenceStatus.OFFLINE, last == null ? null : last.toInstant());
    }
    @Scheduled(fixedDelay = 20000)
    public void renewLeases() {
        db.update("UPDATE realtime_sessions SET touched_at=clock_timestamp() WHERE instance_id=? AND connected", instanceId);
    }
    @Scheduled(fixedDelay = 3600000)
    public void removeOldSessions() {
        db.update("DELETE FROM realtime_sessions WHERE touched_at < clock_timestamp() - interval '7 days'");
    }
}
