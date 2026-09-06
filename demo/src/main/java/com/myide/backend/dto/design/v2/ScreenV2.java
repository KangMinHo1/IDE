package com.myide.backend.dto.design.v2;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * key는 라우트 경로이며 React 코드 생성의 입력이 된다.
 * 비어 있으면 코드 생성 대상에서 빠지고 설계 닥터가 경고한다.
 *
 * boolean 성분에 JsonProperty를 붙인 이유: 레코드의 is 접두 성분을
 * Jackson이 접두사를 떼고 직렬화할 여지가 있어 프론트와 필드명이
 * 어긋나는 것을 원천 차단한다.
 */
public record ScreenV2(
        String id,
        String key,
        String name,
        String description,
        String role,
        @JsonProperty("isEntry") boolean isEntry,
        @JsonProperty("requiresAuth") boolean requiresAuth,
        List<String> requirementIds,
        List<String> apiIds,
        PointV2 layout
) {
    public ScreenV2 {
        key = key == null ? "" : key;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        role = role == null || role.isBlank() ? "page" : role;
        requirementIds = requirementIds == null ? List.of() : List.copyOf(requirementIds);
        apiIds = apiIds == null ? List.of() : List.copyOf(apiIds);
        layout = layout == null ? PointV2.origin() : layout;
    }
}
