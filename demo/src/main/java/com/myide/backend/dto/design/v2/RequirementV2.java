package com.myide.backend.dto.design.v2;

import java.util.List;

/**
 * id는 불변이며 참조에만 쓴다.
 * code는 사람이 읽는 표시용 번호라 재정렬로 바뀔 수 있고 참조에 쓰면 안 된다.
 */
public record RequirementV2(
        String id,
        String code,
        String category,
        String name,
        String description,
        String priority,
        List<String> screenIds,
        List<String> apiIds
) {
    public RequirementV2 {
        code = code == null ? "" : code;
        category = category == null || category.isBlank() ? "기본" : category;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        priority = priority == null || priority.isBlank() ? "should" : priority;
        screenIds = screenIds == null ? List.of() : List.copyOf(screenIds);
        apiIds = apiIds == null ? List.of() : List.copyOf(apiIds);
    }
}
