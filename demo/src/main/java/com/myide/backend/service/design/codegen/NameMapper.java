package com.myide.backend.service.design.codegen;

import java.util.Set;

/**
 * 설계에 적힌 이름을 각 언어에서 쓸 수 있는 이름으로 바꾼다.
 *
 * 여기서 한 글자만 틀려도 생성된 코드가 컴파일되지 않으므로 규칙을 한곳에
 * 모아 둔다. 설계에는 사람이 읽기 좋은 이름(테이블 users, 라우트 /post/:id)이
 * 들어 있고, 코드에는 그 언어의 관례를 따르는 이름(User, PostDetailPage)이
 * 필요하다.
 */
public final class NameMapper {

    /** 자바에서 식별자로 쓸 수 없는 단어. 뒤에 밑줄을 붙여 피한다. */
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "record", "var", "yield");

    private NameMapper() {
    }

    /** users → User, order_items → OrderItem. 엔티티 이름이 비어 있을 때만 쓴다. */
    public static String toPascalCase(String raw) {
        StringBuilder builder = new StringBuilder();

        for (String word : splitWords(raw)) {
            builder.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }

        String result = builder.toString();
        return result.isEmpty() ? "Unnamed" : result;
    }

    /** created_at → createdAt. */
    public static String toCamelCase(String raw) {
        String pascal = toPascalCase(raw);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /** 자바에서 그대로 쓸 수 없는 예약어인가. */
    public static boolean isJavaKeyword(String value) {
        return JAVA_KEYWORDS.contains(value);
    }

    /** 자바 필드 이름. 예약어면 뒤에 밑줄을 붙인다. */
    public static String toFieldName(String raw) {
        String camel = toCamelCase(raw);
        return JAVA_KEYWORDS.contains(camel) ? camel + "_" : camel;
    }

    /** 테이블 이름에서 엔티티 이름을 정한다. 설계에 적혀 있으면 그것을 존중한다. */
    public static String toEntityName(String tableName, String declaredEntityName) {
        if (declaredEntityName != null && !declaredEntityName.isBlank()) {
            return toPascalCase(declaredEntityName);
        }

        return toPascalCase(singularize(tableName));
    }

    /**
     * users → user. 영어 복수형만 아주 얕게 처리한다.
     *
     * 완벽한 단수화는 불가능하고 필요하지도 않다. 설계에 entityName 을 적어
     * 두면 그것이 이긴다.
     */
    public static String singularize(String raw) {
        String lower = raw == null ? "" : raw.trim();

        if (lower.length() > 3 && lower.endsWith("ies")) {
            return lower.substring(0, lower.length() - 3) + "y";
        }
        if (lower.length() > 3 && (lower.endsWith("ses") || lower.endsWith("xes")
                || lower.endsWith("zes") || lower.endsWith("ches") || lower.endsWith("shes"))) {
            return lower.substring(0, lower.length() - 2);
        }
        if (lower.length() > 1 && lower.endsWith("s") && !lower.endsWith("ss")) {
            return lower.substring(0, lower.length() - 1);
        }

        return lower;
    }

    /**
     * 라우트에서 React 컴포넌트 이름을 만든다.
     *
     * /            → HomePage
     * /login       → LoginPage
     * /posts/:id   → PostsDetailPage
     */
    public static String toComponentName(String route, String fallbackName) {
        String path = route == null ? "" : route.trim();

        if (path.isBlank() || "/".equals(path)) {
            return "HomePage";
        }

        StringBuilder builder = new StringBuilder();
        boolean sawParam = false;

        for (String segment : path.split("/")) {
            if (segment.isBlank()) {
                continue;
            }

            if (segment.startsWith(":") || segment.startsWith("{")) {
                sawParam = true;
                continue;
            }

            builder.append(toPascalCase(segment));
        }

        if (sawParam) {
            builder.append("Detail");
        }

        String base = builder.toString();

        if (base.isEmpty()) {
            base = toPascalCase(fallbackName == null ? "" : fallbackName);
        }

        return base.isEmpty() || "Unnamed".equals(base) ? "HomePage" : base + "Page";
    }

    /**
     * API 하나를 부르는 함수 이름.
     *
     * GET /api/posts/{id} → getPostsById, POST /api/posts → createPosts.
     * 이름이 겹칠 수 있으므로 부르는 쪽에서 중복을 확인한다.
     */
    public static String toFunctionName(String method, String endpoint) {
        String verb = switch (method == null ? "" : method.toUpperCase()) {
            case "POST" -> "create";
            case "PUT", "PATCH" -> "update";
            case "DELETE" -> "remove";
            default -> "get";
        };

        StringBuilder builder = new StringBuilder(verb);
        boolean sawParam = false;

        for (String segment : (endpoint == null ? "" : endpoint).split("/")) {
            if (segment.isBlank() || "api".equalsIgnoreCase(segment)) {
                continue;
            }

            if (segment.startsWith("{") || segment.startsWith(":")) {
                sawParam = true;
                continue;
            }

            builder.append(toPascalCase(segment));
        }

        if (sawParam) {
            builder.append("ById");
        }

        return builder.toString();
    }

    /** 엔드포인트에서 담당 컨트롤러를 정할 때 쓰는 첫 자원 이름. */
    public static String resourceOf(String endpoint) {
        for (String segment : (endpoint == null ? "" : endpoint).split("/")) {
            if (segment.isBlank() || "api".equalsIgnoreCase(segment)) {
                continue;
            }
            if (segment.startsWith("{") || segment.startsWith(":")) {
                continue;
            }
            return segment;
        }

        return "common";
    }

    /** 경로 변수 이름을 자바 파라미터 이름으로. */
    public static String toParamName(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[{}:]", "");
        return toFieldName(cleaned);
    }

    private static String[] splitWords(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }

        // 밑줄·하이픈·공백으로 끊고, camelCase 로 붙어 있는 것도 끊는다.
        String spaced = raw.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim();

        if (spaced.isEmpty()) {
            return new String[0];
        }

        String[] words = spaced.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            words[i] = word.toUpperCase().equals(word) && word.length() > 1
                    ? word.toLowerCase()
                    : Character.toLowerCase(word.charAt(0)) + word.substring(1);
        }

        return words;
    }
}
