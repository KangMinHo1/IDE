package com.myide.backend.dto.design.v2;

/**
 * 시드와 스냅샷 저장이 함께 쓰는 요청 형태.
 *
 * yjsUpdate 는 Y.encodeStateAsUpdate 결과의 base64 이고 서버는 해석하지
 * 않는다. projection 은 같은 문서의 평문 사본으로, 한 요청에서 둘을 함께
 * 받기 때문에 서로 어긋날 수 없다.
 */
public record DesignDocWriteRequest(
        int schemaVersion,
        String yjsUpdate,
        DesignModelV2 projection
) {}
