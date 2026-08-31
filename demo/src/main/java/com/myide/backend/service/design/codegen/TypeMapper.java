package com.myide.backend.service.design.codegen;

import com.myide.backend.dto.design.v2.ColumnV2;

import java.util.Map;

/**
 * 설계에 적힌 컬럼 타입을 각 언어의 타입으로 옮긴다.
 *
 * 설계 점검(COL_UNKNOWN_TYPE)이 모르는 타입을 미리 걸러 주므로 여기까지
 * 오는 값은 이미 아는 타입이다. 그래도 기본값을 둔다 — 코드 생성이 예외로
 * 멈추는 것보다 String 으로라도 나오는 편이 낫다.
 */
public final class TypeMapper {

    private static final Map<String, String> JAVA_TYPES = Map.ofEntries(
            Map.entry("BIGINT", "Long"),
            Map.entry("INT", "Integer"),
            Map.entry("SMALLINT", "Integer"),
            Map.entry("TINYINT", "Integer"),
            Map.entry("BOOLEAN", "Boolean"),
            Map.entry("DECIMAL", "java.math.BigDecimal"),
            Map.entry("DOUBLE", "Double"),
            Map.entry("FLOAT", "Float"),
            Map.entry("VARCHAR", "String"),
            Map.entry("CHAR", "String"),
            Map.entry("TEXT", "String"),
            Map.entry("LONGTEXT", "String"),
            Map.entry("JSON", "String"),
            Map.entry("BLOB", "byte[]"),
            Map.entry("DATE", "java.time.LocalDate"),
            Map.entry("DATETIME", "java.time.LocalDateTime"),
            Map.entry("TIMESTAMP", "java.time.LocalDateTime"),
            Map.entry("TIME", "java.time.LocalTime")
    );

    private TypeMapper() {
    }

    public static String toJavaType(ColumnV2 column) {
        return JAVA_TYPES.getOrDefault(normalize(column.type()), "String");
    }

    /** DDL 에 쓸 실제 컬럼 타입. 길이가 있으면 붙인다. */
    public static String toSqlType(ColumnV2 column) {
        String type = normalize(column.type());

        if ("VARCHAR".equals(type) || "CHAR".equals(type)) {
            int length = column.length() == null || column.length() <= 0 ? 255 : column.length();
            return type + "(" + length + ")";
        }

        if ("DECIMAL".equals(type)) {
            return "DECIMAL(19, 2)";
        }

        return type;
    }

    /**
     * JPA 의 length 속성을 붙일지 여부.
     *
     * 문자열이 아닌 컬럼에 length 를 붙이면 쓸모없는 잡음이 되고,
     * TEXT 계열에 붙이면 오히려 컬럼 정의가 어긋난다.
     */
    public static boolean hasJpaLength(ColumnV2 column) {
        String type = normalize(column.type());
        return ("VARCHAR".equals(type) || "CHAR".equals(type))
                && column.length() != null && column.length() > 0;
    }

    /** TEXT 계열은 JPA 에서 columnDefinition 으로 알려 줘야 VARCHAR(255) 로 안 만들어진다. */
    public static String columnDefinition(ColumnV2 column) {
        String type = normalize(column.type());
        return switch (type) {
            case "TEXT", "LONGTEXT", "JSON" -> type;
            default -> "";
        };
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "VARCHAR";
        }

        String upper = raw.trim().toUpperCase();
        int paren = upper.indexOf('(');

        return paren > 0 ? upper.substring(0, paren).trim() : upper;
    }
}
