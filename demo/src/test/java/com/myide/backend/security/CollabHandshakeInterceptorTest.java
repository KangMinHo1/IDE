package com.myide.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /ws/collab 은 설계 문서만 쓰는 것이 아니다.
 * 방 이름 해석이 틀리면 잘 돌아가던 코드 에디터 동시편집과 워크스페이스
 * 전역 동기화가 접속 단계에서 끊긴다. 세 형식을 모두 고정해 둔다.
 */
class CollabHandshakeInterceptorTest {

    @Test
    @DisplayName("설계 문서 방에서 워크스페이스를 뽑는다")
    void extractsFromDesignRoom() {
        assertThat(CollabHandshakeInterceptor.extractWorkspaceId("design:ws-1234"))
                .isEqualTo("ws-1234");
    }

    @Test
    @DisplayName("코드 에디터 방에서 첫 구간을 워크스페이스로 읽는다")
    void extractsFromEditorRoom() {
        assertThat(CollabHandshakeInterceptor
                .extractWorkspaceId("ws-1234:myproject:master:src/App.jsx"))
                .isEqualTo("ws-1234");
    }

    @Test
    @DisplayName("전역 동기화 방에서 워크스페이스를 뽑는다")
    void extractsFromGlobalRoom() {
        assertThat(CollabHandshakeInterceptor
                .extractWorkspaceId("global-workspace-room-ws-1234"))
                .isEqualTo("ws-1234");
    }

    @Test
    @DisplayName("구분자가 없으면 방 이름 전체를 워크스페이스로 본다")
    void fallsBackToWholeRoom() {
        assertThat(CollabHandshakeInterceptor.extractWorkspaceId("ws-1234"))
                .isEqualTo("ws-1234");
    }

    @Test
    @DisplayName("방 이름이 없으면 워크스페이스도 없다")
    void returnsNullForBlankRoom() {
        assertThat(CollabHandshakeInterceptor.extractWorkspaceId(null)).isNull();
        assertThat(CollabHandshakeInterceptor.extractWorkspaceId("  ")).isNull();
    }
}
