package com.myide.backend.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최초 내용을 넣을 권한이 방마다 한 번만 나가는지 확인한다.
 *
 * 여기가 틀리면 사용자가 실제로 겪은 두 증상이 그대로 돌아온다 — 둘 다
 * 넣어서 <b>같은 내용이 두 번</b> 보이거나, 아무도 안 넣어서 <b>빈 화면</b>이
 * 된다. 그리고 그 상태로 저장하면 원본 파일이 망가진다.
 */
class CollaborationSeedClaimTest {

    private static final String ROOM = "ws-1:shop:master:src/App.java";

    private CollaborationWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CollaborationWebSocketHandler();
    }

    /** 방에 들어와 있는 척한다. 방 이름은 접속 URL 의 room 쿼리에서 읽는다. */
    private WebSocketSession join(String id) {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Mockito.when(session.getId()).thenReturn(id);
        Mockito.when(session.getUri())
                .thenReturn(URI.create("ws://localhost:8080/ws/collab?room="
                        + ROOM.replace("/", "%2F")));
        Mockito.when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        return session;
    }

    @Test
    @DisplayName("한 방에는 한 사람만 허락받는다")
    void onlyOneClaimPerRoom() {
        assertThat(handler.claimSeed(ROOM)).isTrue();
        assertThat(handler.claimSeed(ROOM)).isFalse();
        assertThat(handler.claimSeed(ROOM)).isFalse();
    }

    @Test
    @DisplayName("동시에 요청해도 한 사람만 허락받는다")
    void onlyOneWinsUnderRace() throws Exception {
        int racers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(racers);

        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                tasks.add(() -> handler.claimSeed(ROOM));
            }

            long granted = 0;
            for (Future<Boolean> result : pool.invokeAll(tasks)) {
                if (result.get()) {
                    granted++;
                }
            }

            assertThat(granted)
                    .as("여럿이 동시에 물어봐도 넣는 사람은 하나여야 한다")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("방이 비면 다음에 여는 사람이 다시 허락받는다")
    void claimResetsWhenRoomEmpties() {
        WebSocketSession first = join("s1");
        assertThat(handler.claimSeed(ROOM)).isTrue();

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);

        // 파일을 닫았다 다시 열면 디스크에서 새로 읽어 넣어야 한다.
        assertThat(handler.claimSeed(ROOM)).isTrue();
    }

    @Test
    @DisplayName("아직 사람이 남아 있으면 다시 허락하지 않는다")
    void keepsClaimWhileSomeoneIsInTheRoom() {
        join("s1");
        WebSocketSession second = join("s2");

        assertThat(handler.claimSeed(ROOM)).isTrue();

        handler.afterConnectionClosed(second, CloseStatus.NORMAL);

        // 한 명이 나갔을 뿐 문서는 남아 있는 사람이 들고 있다.
        assertThat(handler.claimSeed(ROOM)).isFalse();
    }

    @Test
    @DisplayName("방이 다르면 서로 영향을 주지 않는다")
    void roomsAreIndependent() {
        assertThat(handler.claimSeed(ROOM)).isTrue();
        assertThat(handler.claimSeed("ws-1:shop:master:src/Other.java")).isTrue();
    }

    @Test
    @DisplayName("방 이름이 없으면 허락하지 않는다")
    void refusesBlankRoom() {
        assertThat(handler.claimSeed(null)).isFalse();
        assertThat(handler.claimSeed("  ")).isFalse();
    }
}
