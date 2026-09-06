// 경로: src/main/java/com/myide/backend/config/WebSocketConfig.java
package com.myide.backend.config;

import com.myide.backend.handler.*;
import com.myide.backend.security.CollabHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    /** 한 메시지의 최대 크기. 자세한 이유는 아래 createWebSocketContainer 주석 참고. */
    public static final int MAX_MESSAGE_BYTES = 1024 * 1024;

    private final RunWebSocketHandler runWebSocketHandler;
    private final DebugWebSocketHandler debugWebSocketHandler;
    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final CollaborationWebSocketHandler collaborationWebSocketHandler;
    private final WorkspaceEventWebSocketHandler workspaceEventWebSocketHandler;
    private final CollabHandshakeInterceptor collabHandshakeInterceptor;

    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "http://127.0.0.1:*",

            // 같은 와이파이/핫스팟에서 접속할 때 사용하는 사설 IP 대역
            "http://192.168.*.*:*",
            "http://10.*.*.*:*",
            "http://172.16.*.*:*",
            "http://172.17.*.*:*",
            "http://172.18.*.*:*",
            "http://172.19.*.*:*",
            "http://172.20.*.*:*",
            "http://172.21.*.*:*",
            "http://172.22.*.*:*",
            "http://172.23.*.*:*",
            "http://172.24.*.*:*",
            "http://172.25.*.*:*",
            "http://172.26.*.*:*",
            "http://172.27.*.*:*",
            "http://172.28.*.*:*",
            "http://172.29.*.*:*",
            "http://172.30.*.*:*",
            "http://172.31.*.*:*"
    };

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(runWebSocketHandler, "/ws/run")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);

        registry.addHandler(debugWebSocketHandler, "/ws/debug")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);

        registry.addHandler(terminalWebSocketHandler, "/ws/terminal")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);

        // 설계 문서와 코드 동시편집이 같은 엔드포인트를 쓴다.
        // 핸드셰이크 단계에서 JWT와 워크스페이스 멤버십을 확인한다.
        registry.addHandler(collaborationWebSocketHandler, "/ws/collab")
                .addInterceptors(collabHandshakeInterceptor)
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);

        // 파일 트리 변경 이벤트용
        registry.addHandler(workspaceEventWebSocketHandler, "/ws/workspace-events")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
    }

    /**
     * 한 번에 주고받을 수 있는 메시지 크기.
     *
     * 이 설정이 없으면 톰캣 기본값인 8KB 가 적용된다. 그 한도로는 동시 편집이
     * 반쪽만 돈다 — 이름 한 줄을 고치는 작은 변경은 통과하지만, <b>AI 초안을
     * 적용하거나 되돌리기를 하면 문서 전체가 한 덩어리로 나가면서</b> 한도를
     * 넘고, 서버가 그 연결을 끊어 버린다(1009). 그래서 팀원 화면에는 아무것도
     * 안 나타나고 새로고침해야 보였다. 초안이 들어간 뒤에는 접속할 때 주고받는
     * 최초 동기화마저 8KB를 넘어 아예 못 붙는다.
     *
     * 코드 에디터도 같은 엔드포인트를 쓰므로 큰 파일에서 같은 문제를 겪는다.
     *
     * 1MB 로 잡은 이유는 지금 설계 문서 전체의 Yjs 바이너리가 수십 KB 수준이라
     * 여유가 충분하고, 버퍼는 연결마다 잡히므로 무한정 키울 것은 아니기
     * 때문이다. 이 한도마저 넘길 만큼 커지면 그때는 Node y-websocket 사이드카로
     * 옮긴다 — 방 이름 규약을 지켜 두었으므로 프론트는 그대로 두고 주소만
     * 바꾸면 된다.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);

        return container;
    }
}