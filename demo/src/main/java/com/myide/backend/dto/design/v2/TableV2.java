package com.myide.backend.dto.design.v2;

import java.util.List;

/** name은 DB 물리명, entityName은 코드 생성용 클래스명이다. */
public record TableV2(
        String id,
        String name,
        String entityName,
        String description,
        List<ColumnV2> columns,
        PointV2 layout
) {
    public TableV2 {
        name = name == null ? "" : name;
        entityName = entityName == null ? "" : entityName;
        description = description == null ? "" : description;
        columns = columns == null ? List.of() : List.copyOf(columns);
        layout = layout == null ? PointV2.origin() : layout;
    }
}
