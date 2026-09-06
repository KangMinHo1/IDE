package com.myide.backend.dto.design.v2;

import java.util.List;

/**
 * 설계 관리 v2 문서 모델.
 *
 * 프론트 src/features/design/model/schema.ts 와 1:1로 대응한다.
 * 필드명이 한쪽만 바뀌면 설계 닥터와 코드 생성이 통째로 실패하므로
 * 양쪽을 항상 같은 커밋에서 함께 고쳐야 한다.
 *
 * 서버는 이 모델을 Yjs 문서에서 직접 읽지 않는다. 클라이언트가 스냅샷을
 * 저장할 때 함께 보내는 평문 사본이 이 형태이며, AI 초안과 설계 닥터,
 * 코드 생성, 최종 보고서가 모두 이것을 입력으로 쓴다.
 */
public record DesignModelV2(
        int schemaVersion,
        DesignMetaV2 meta,
        List<RequirementV2> requirements,
        List<ScreenV2> screens,
        List<ScreenTransitionV2> screenTransitions,
        List<ApiSpecV2> apis,
        ErdV2 erd
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public DesignModelV2 {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        meta = meta == null ? DesignMetaV2.empty() : meta;
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        screens = screens == null ? List.of() : List.copyOf(screens);
        screenTransitions = screenTransitions == null ? List.of() : List.copyOf(screenTransitions);
        apis = apis == null ? List.of() : List.copyOf(apis);
        erd = erd == null ? ErdV2.empty() : erd;
    }

    public static DesignModelV2 empty() {
        return new DesignModelV2(
                CURRENT_SCHEMA_VERSION,
                DesignMetaV2.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ErdV2.empty()
        );
    }
}
