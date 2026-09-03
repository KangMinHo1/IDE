package com.myide.backend.service.design;

import com.myide.backend.service.design.doctor.rules.ErdRules;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 설계에서 기계적으로 고칠 수 있는 것들을 고치는 규칙.
 *
 * 같은 규칙을 두 곳이 쓴다. AI 초안 후처리는 오류가 붙은 초안이 나오지
 * 않게 미리 고치고, 설계 점검의 "고치기" 버튼은 사람이 만든 문서를 고친다.
 * 둘이 따로 계산하면 <b>같은 문제를 서로 다르게 고치게 되고</b>, 판정하는
 * 쪽과 고치는 쪽이 어긋나면 눌러도 오류가 안 사라진다.
 *
 * 예약어 목록은 설계 점검 규칙(ErdRules.RESERVED)의 것을 그대로 쓴다.
 */
public final class DesignRepairs {

    /** 설계 점검(SCR_INVALID_ROUTE)이 받아들이는 경로 형식. */
    public static final Pattern ROUTE = Pattern.compile("^/[A-Za-z0-9\\-_/:\\[\\]]*$");

    /** 예약어를 사람이 읽기 좋은 이름으로 바꾼다. 목록에 없으면 뒤에 _value 를 붙인다. */
    private static final Map<String, String> RESERVED_RENAMES = Map.of(
            "order", "order_no",
            "group", "group_name",
            "key", "key_name",
            "desc", "description",
            "asc", "asc_order",
            "index", "index_no",
            "table", "table_name",
            "class", "class_name");

    private DesignRepairs() {
    }

    // ── 화면 경로 ───────────────────────────────────────────────────

    /** 이미 규칙에 맞는 경로인가. */
    public static boolean isValidRoute(String route) {
        return route != null && ROUTE.matcher(route).matches();
    }

    /**
     * 고칠 수 있으면 고치고, 규칙에 맞게 만들 수 없으면 null.
     *
     * AI 도 사람도 {@code /posts/{id}} 처럼 중괄호를 쓰거나 "주문 완료" 같은
     * 한글을 그대로 넣는다. 중괄호는 라우터가 아는 모양으로 바꿀 수 있지만
     * 한글은 살릴 수 없다.
     */
    public static String normalizeRoute(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String route = value.trim().replaceAll("\\{([A-Za-z0-9_]+)}", ":$1");

        if (!route.startsWith("/")) {
            route = "/" + route;
        }

        route = route.replaceAll("/{2,}", "/");

        if (route.length() > 1 && route.endsWith("/")) {
            route = route.substring(0, route.length() - 1);
        }

        return ROUTE.matcher(route).matches() ? route : null;
    }

    /**
     * 이름에서 경로를 만든다. 살릴 수 없는 경로를 대신할 때 쓴다.
     *
     * scr-order-done → /order-done
     */
    public static String routeFromKey(String key) {
        String value = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        value = value.replaceFirst("^scr[-_]", "").replaceAll("[^a-z0-9\\-_]+", "-");
        value = value.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");

        return value.isEmpty() ? "/screen" : "/" + value;
    }

    /**
     * 겹치지 않는 경로 하나를 고른다.
     *
     * @param used 이미 쓰이고 있는 경로(소문자). 고른 값이 여기에 더해진다.
     */
    public static String uniqueRoute(String raw, String key, Set<String> used) {
        String value = raw == null ? "" : raw.trim();

        // 경로를 아예 안 정한 화면은 그대로 둔다. 없는 경로를 지어내면
        // 팝업이나 외부 화면에 엉뚱한 주소가 붙는다.
        if (value.isEmpty()) {
            return "";
        }

        String route = normalizeRoute(value);
        if (route == null) {
            route = routeFromKey(key);
        }

        if (used.add(route.toLowerCase(Locale.ROOT))) {
            return route;
        }

        // 같은 경로를 쓰는 화면이 둘이면 뒤엣것은 영원히 열리지 않는다.
        String fromKey = routeFromKey(key);
        if (used.add(fromKey.toLowerCase(Locale.ROOT))) {
            return fromKey;
        }

        for (int suffix = 2; suffix < 100; suffix++) {
            String candidate = route + "-" + suffix;
            if (used.add(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }

        return route;
    }

    // ── 컬럼 이름 ───────────────────────────────────────────────────

    public static boolean isReservedColumnName(String name) {
        return name != null && ErdRules.RESERVED.contains(name.toLowerCase(Locale.ROOT));
    }

    /** order → order_no, desc → description. 목록에 없으면 뒤에 _value 를 붙인다. */
    public static String renameReservedColumn(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);

        if (!ErdRules.RESERVED.contains(key)) {
            return name;
        }

        return RESERVED_RENAMES.getOrDefault(key, key + "_value");
    }

    /** userName → user_name. 컬럼 이름은 소문자와 밑줄로 쓰는 것이 관례다. */
    public static String toSnakeCase(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }

        return name.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace("-", "_")
                .replaceAll("_{2,}", "_")
                .toLowerCase(Locale.ROOT);
    }
}
