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
 * 방마다 문서가 하나로 정해지는지 확인한다.
 *
 * 여기가 틀리면 사용자가 실제로 겪은 두 증상이 그대로 돌아온다 — 둘 다
 * 넣어서 <b>같은 내용이 두 번</b> 보이거나, 아무도 못 넣어서 <b>빈 화면</b>이
 * 된다. 그리고 그 상태로 저장하면 원본 파일이 망가진다.
 *
 * 예전 방식(허락만 주기)과의 결정적 차이는 <b>진 쪽도 쓸 것을 받는다</b>는
 * 점이다. 그래서 아무도 빈 문서에 갇히지 않는다.
 */
class CollaborationRoomDocTest {

    private static final String ROOM = "ws-1:shop:master:src/App.java";
    private static final String DOC_A = "AAAA-base64";
    private static final String DOC_B = "BBBB-base64";

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
    @DisplayName("처음 낸 문서만 채택된다")
    void onlyFirstSeedIsAccepted() {
        assertThat(handler.seedDoc(ROOM, DOC_A).accepted()).isTrue();
        assertThat(handler.seedDoc(ROOM, DOC_B).accepted()).isFalse();
    }

    @Test
    @DisplayName("진 쪽도 채택된 문서를 받는다")
    void loserGetsTheAcceptedDoc() {
        handler.seedDoc(ROOM, DOC_A);

        CollaborationWebSocketHandler.SeedOutcome outcome = handler.seedDoc(ROOM, DOC_B);

        // 이것이 빈 화면 문제의 핵심이다. 거절만 하고 끝내면 진 쪽은
        // 넣을 것도 받을 것도 없어 빈 문서에 갇힌다.
        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.yjsUpdate()).isEqualTo(DOC_A);
    }

    @Test
    @DisplayName("동시에 내도 채택은 하나뿐이고 모두 같은 것을 받는다")
    void onlyOneWinsUnderRace() throws Exception {
        int racers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(racers);

        try {
            List<Callable<CollaborationWebSocketHandler.SeedOutcome>> tasks = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                String candidate = "candidate-" + i;
                tasks.add(() -> handler.seedDoc(ROOM, candidate));
            }

            long accepted = 0;
            List<String> received = new ArrayList<>();

            for (Future<CollaborationWebSocketHandler.SeedOutcome> result : pool.invokeAll(tasks)) {
                CollaborationWebSocketHandler.SeedOutcome outcome = result.get();
                if (outcome.accepted()) {
                    accepted++;
                }
                received.add(outcome.yjsUpdate());
            }

            assertThat(accepted)
                    .as("여럿이 동시에 내도 채택되는 문서는 하나여야 한다")
                    .isEqualTo(1);

            assertThat(received)
                    .as("모두 같은 문서를 받아야 내용이 갈라지지 않는다")
                    .containsOnly(handler.getDoc(ROOM));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("방이 비면 보관하던 문서도 사라진다")
    void docIsDroppedWhenRoomEmpties() {
        WebSocketSession first = join("s1");
        handler.seedDoc(ROOM, DOC_A);

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);

        // 파일을 닫았다 다시 열면 디스크에서 새로 만들어 넣어야 한다.
        assertThat(handler.getDoc(ROOM)).isNull();
        assertThat(handler.seedDoc(ROOM, DOC_B).accepted()).isTrue();
    }

    @Test
    @DisplayName("사람이 남아 있으면 문서를 그대로 들고 있는다")
    void keepsDocWhileSomeoneIsInTheRoom() {
        join("s1");
        WebSocketSession second = join("s2");
        handler.seedDoc(ROOM, DOC_A);

        handler.afterConnectionClosed(second, CloseStatus.NORMAL);

        assertThat(handler.getDoc(ROOM)).isEqualTo(DOC_A);
    }

    @Test
    @DisplayName("저장 담당자가 올린 최신 상태로 갈아 끼운다")
    void saveDocOverwrites() {
        handler.seedDoc(ROOM, DOC_A);
        handler.saveDoc(ROOM, DOC_B);

        // 뒤늦게 들어온 사람이 최초 시드가 아니라 최신 상태를 받아야 한다.
        assertThat(handler.getDoc(ROOM)).isEqualTo(DOC_B);
    }

    @Test
    @DisplayName("방이 다르면 서로 영향을 주지 않는다")
    void roomsAreIndependent() {
        assertThat(handler.seedDoc(ROOM, DOC_A).accepted()).isTrue();
        assertThat(handler.seedDoc("ws-1:shop:master:src/Other.java", DOC_B).accepted()).isTrue();
    }

    @Test
    @DisplayName("방 이름이나 내용이 비면 받아들이지 않는다")
    void refusesBlankInput() {
        assertThat(handler.seedDoc(null, DOC_A).accepted()).isFalse();
        assertThat(handler.seedDoc("  ", DOC_A).accepted()).isFalse();
        assertThat(handler.seedDoc(ROOM, null).accepted()).isFalse();
        assertThat(handler.seedDoc(ROOM, "  ").accepted()).isFalse();

        assertThat(handler.getDoc(null)).isNull();
        assertThat(handler.getDoc(ROOM)).isNull();
    }
}
