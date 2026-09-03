// 경로: src/main/java/com/myide/backend/handler/CollaborationWebSocketHandler.java
package com.myide.backend.handler;

import com.myide.backend.config.WebSocketConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class CollaborationWebSocketHandler extends BinaryWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 컨테이너 기본값과 별개로 스프링 쪽 세션 한도가 따로 걸린다.
        // 한쪽만 올려 두면 큰 변경이 여전히 막힌다.
        session.setBinaryMessageSizeLimit(WebSocketConfig.MAX_MESSAGE_BYTES);
        session.setTextMessageSizeLimit(WebSocketConfig.MAX_MESSAGE_BYTES);

        String room = getRoomName(session);
        rooms.computeIfAbsent(room, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("🤝 [Collab] 동시 편집 접속: 세션 ID = {}, 방 = {}", session.getId(), room);
    }

    /**
     * 전송에 실패해도 방 전체를 멈추지 않는다.
     *
     * 한 사람의 연결이 막혔다고 나머지에게도 안 보내면, 한 명 때문에 팀
     * 전체의 동시 편집이 멈춘다. 실패한 연결은 어차피 곧 정리된다.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("⚠️ [Collab] 전송 오류: 세션 ID = {}, 이유 = {}",
                session.getId(), exception.getMessage());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String room = getRoomName(session);
        Set<WebSocketSession> roomSessions = rooms.get(room);

        if (roomSessions != null) {
            for (WebSocketSession s : roomSessions) {
                if (s.isOpen() && !s.getId().equals(session.getId())) {
                    s.sendMessage(message);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String room = getRoomName(session);
        Set<WebSocketSession> roomSessions = rooms.get(room);
        if (roomSessions != null) {
            roomSessions.remove(session);
            if (roomSessions.isEmpty()) {
                rooms.remove(room);
                log.info("💥 [Collab] 빈 방 삭제됨: {}", room);
            }
        }
        log.info("👋 [Collab] 동시 편집 퇴장: 세션 ID = {}, 방 = {}", session.getId(), room);
    }

    // 💡 [핵심 해결] 정확하게 쿼리 파라미터(?room=...)에서 방 이름을 뽑아냅니다!
    private String getRoomName(WebSocketSession session) {
        // 핸드셰이크에서 이미 뽑아 둔 값이 있으면 그것을 쓴다.
        // 토큰 같은 다른 쿼리 파라미터가 붙어도 방 이름이 흔들리지 않는다.
        Object cached = session.getAttributes().get("COLLAB_ROOM");
        if (cached instanceof String cachedRoom && !cachedRoom.isBlank()) {
            return cachedRoom;
        }

        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            String[] params = uri.getQuery().split("&");
            for (String param : params) {
                if (param.startsWith("room=")) {
                    String rawRoom = param.substring(5);
                    return URLDecoder.decode(rawRoom, StandardCharsets.UTF_8);
                }
            }
        }
        return "default-room";
    }
}