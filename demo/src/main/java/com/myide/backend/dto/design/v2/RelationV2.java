package com.myide.backend.dto.design.v2;

/** note는 기존 ERD가 관계선마다 갖고 있던 자유 설명이 옮겨오는 자리다. */
public record RelationV2(
        String id,
        String fromTableId,
        String fromColumnId,
        String toTableId,
        String toColumnId,
        String cardinality,
        String onDelete,
        String note
) {
    public RelationV2 {
        fromTableId = fromTableId == null ? "" : fromTableId;
        fromColumnId = fromColumnId == null ? "" : fromColumnId;
        toTableId = toTableId == null ? "" : toTableId;
        toColumnId = toColumnId == null ? "" : toColumnId;
        cardinality = cardinality == null || cardinality.isBlank() ? "1:N" : cardinality;
        onDelete = onDelete == null ? "" : onDelete;
        note = note == null ? "" : note;
    }
}
