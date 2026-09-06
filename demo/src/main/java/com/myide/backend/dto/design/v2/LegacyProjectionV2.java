package com.myide.backend.dto.design.v2;

import java.util.List;

/**
 * v2 문서를 예전 형식으로 되돌린 결과(역투영).
 *
 * 자료실과 마이페이지는 설계 데이터를 예전 엔드포인트 세 개
 * (/design/requirements, /design/apis, /design/document)에서 각각 읽는다.
 * 스냅샷을 저장할 때마다 이 형식으로 되써 두면 그 화면들을 한 줄도
 * 고치지 않고 새 편집기로 전환할 수 있다.
 *
 * 데이터 플로우 두 필드는 새로 그리는 것이 아니라 시드 때 보관해 둔
 * 원본을 그대로 돌려주는 값이다.
 */
public record LegacyProjectionV2(
        List<RequirementRow> requirements,
        List<ApiSpecRow> apiSpecs,
        String erdNodesJson,
        String erdEdgesJson,
        String flowNodesJson,
        String flowEdgesJson
) {
    public record RequirementRow(
            String id,
            String category,
            String name,
            String description
    ) {}

    public record ApiSpecRow(
            String id,
            String method,
            String endpoint,
            String description,
            String request,
            String response
    ) {}
}
