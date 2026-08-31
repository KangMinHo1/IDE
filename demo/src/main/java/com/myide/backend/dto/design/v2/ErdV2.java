package com.myide.backend.dto.design.v2;

import java.util.List;

public record ErdV2(List<TableV2> tables, List<RelationV2> relations) {

    public ErdV2 {
        tables = tables == null ? List.of() : List.copyOf(tables);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    public static ErdV2 empty() {
        return new ErdV2(List.of(), List.of());
    }
}
