package com.myide.backend.dto.design.v2;

import com.fasterxml.jackson.annotation.JsonProperty;

/** length가 null이면 타입의 기본 길이를 쓴다는 뜻이다. */
public record ColumnV2(
        String id,
        String name,
        String type,
        Integer length,
        @JsonProperty("nullable") boolean nullable,
        @JsonProperty("isPk") boolean isPk,
        @JsonProperty("isFk") boolean isFk,
        String defaultValue,
        String comment
) {
    public ColumnV2 {
        name = name == null ? "" : name;
        type = type == null || type.isBlank() ? "VARCHAR" : type;
        defaultValue = defaultValue == null ? "" : defaultValue;
        comment = comment == null ? "" : comment;
    }
}
