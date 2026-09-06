package com.myide.backend.service.design.codegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * API 명세에 적어 둔 요청/응답 예시 JSON 을 DTO record 로 옮긴다.
 *
 * 명세의 request/response 는 자유 서술 칸이라 무엇이든 들어올 수 있다.
 * 그래서 <b>확실히 옮길 수 있을 때만</b> record 를 만들고, 조금이라도
 * 애매하면 Map 으로 물러선다. 컴파일되지 않는 DTO 를 만드는 것보다
 * 밋밋한 Map 이 낫다.
 */
public final class JsonStubShaper {

    /** javaType 은 항상 채워지고, recordSource 는 record 를 만들 수 있을 때만 있다. */
    public record Shape(String javaType, String recordName, String recordSource) {
        public boolean hasRecord() {
            return recordSource != null && !recordSource.isBlank();
        }
    }

    private static final Shape MAP_SHAPE =
            new Shape("Map<String, Object>", null, null);

    private JsonStubShaper() {
    }

    public static Shape mapShape() {
        return MAP_SHAPE;
    }

    public static Shape shape(ObjectMapper mapper, String rawJson, String recordName,
                              String basePackage, String description) {
        if (rawJson == null || rawJson.isBlank()) {
            return MAP_SHAPE;
        }

        JsonNode root;
        try {
            root = mapper.readTree(rawJson.trim());
        } catch (Exception e) {
            // 예시가 JSON 이 아니라 설명 문장인 경우다. 흔한 일이라 조용히 넘어간다.
            return MAP_SHAPE;
        }

        boolean list = root.isArray();
        JsonNode object = list ? (root.isEmpty() ? null : root.get(0)) : root;

        if (object == null || !object.isObject() || object.isEmpty()) {
            return MAP_SHAPE;
        }

        List<String> fields = new ArrayList<>();
        boolean needsList = false;

        Iterator<Map.Entry<String, JsonNode>> it = object.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();

            // 필드 이름을 바꾸면 JSON 과 어긋난다. 그대로 쓸 수 없는 이름이면
            // record 를 포기한다.
            if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]*") || NameMapper.isJavaKeyword(key)) {
                return MAP_SHAPE;
            }

            String type = javaTypeOf(entry.getValue());
            if (type.startsWith("List<")) {
                needsList = true;
            }

            fields.add("        " + type + " " + key);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(basePackage).append(".dto;\n\n");

        if (needsList) {
            builder.append("import java.util.List;\n\n");
        }

        builder.append("/**\n");
        builder.append(" * ").append(description == null || description.isBlank()
                ? recordName
                : description.replace("*/", "*")).append("\n");
        builder.append(" *\n");
        builder.append(" * 설계 관리의 API 명세에 적힌 예시에서 만들어졌습니다.\n");
        builder.append(" */\n");
        builder.append("public record ").append(recordName).append("(\n");
        builder.append(String.join(",\n", fields));
        builder.append("\n) {\n}\n");

        return new Shape(list ? "List<" + recordName + ">" : recordName, recordName, builder.toString());
    }

    private static String javaTypeOf(JsonNode value) {
        if (value.isTextual()) {
            return "String";
        }
        if (value.isIntegralNumber()) {
            return "Long";
        }
        if (value.isFloatingPointNumber()) {
            return "Double";
        }
        if (value.isBoolean()) {
            return "Boolean";
        }
        if (value.isArray()) {
            return "List<Object>";
        }
        return "Object";
    }
}
