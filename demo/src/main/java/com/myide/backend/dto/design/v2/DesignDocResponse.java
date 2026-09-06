package com.myide.backend.dto.design.v2;

/**
 * 설계 문서를 열 때 클라이언트가 받는 것.
 *
 * needsSeed 가 true 면 아직 저장본이 없다는 뜻이고, 클라이언트는 예전
 * 데이터를 읽어 문서를 만든 뒤 seed 로 제출해야 한다.
 *
 * 중요한 순서: 클라이언트는 이 응답을 받아 시드를 확정한 다음에야
 * WebSocket 에 접속해야 한다. 먼저 접속하면 서버의 시드 1회 보장이
 * 무의미해진다. 진 쪽이 이미 자기 문서를 방에 뿌린 뒤이기 때문이다.
 */
public record DesignDocResponse(
        int schemaVersion,
        long revision,
        String yjsUpdate,
        DesignModelV2 projection,
        boolean needsSeed
) {
    public static DesignDocResponse seedRequired() {
        return new DesignDocResponse(
                DesignModelV2.CURRENT_SCHEMA_VERSION, 0L, null, DesignModelV2.empty(), true);
    }
}
