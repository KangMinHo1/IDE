package com.myide.backend.service.design;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기계적으로 고칠 수 있는 것들을 실제로 고치는지 확인한다.
 *
 * 이 규칙을 AI 초안 후처리와 설계 점검의 "고치기" 버튼이 함께 쓴다.
 * 여기서 틀리면 초안에 오류가 붙어 나오고, 버튼을 눌러도 오류가 안 사라진다.
 */
class DesignRepairsTest {

    @Test
    @DisplayName("중괄호 경로는 라우터가 아는 모양으로 바꾼다")
    void convertsBraceParams() {
        assertThat(DesignRepairs.normalizeRoute("/posts/{id}")).isEqualTo("/posts/:id");
        assertThat(DesignRepairs.normalizeRoute("posts")).isEqualTo("/posts");
        assertThat(DesignRepairs.normalizeRoute("/posts//new/")).isEqualTo("/posts/new");
    }

    @Test
    @DisplayName("살릴 수 없는 경로는 null 을 준다")
    void givesUpOnUnusableRoute() {
        assertThat(DesignRepairs.normalizeRoute("주문 완료")).isNull();
        assertThat(DesignRepairs.normalizeRoute("")).isNull();
    }

    @Test
    @DisplayName("고친 경로는 점검 규칙을 통과한다")
    void repairedRouteIsValid() {
        assertThat(DesignRepairs.isValidRoute(DesignRepairs.normalizeRoute("/posts/{id}"))).isTrue();
        assertThat(DesignRepairs.isValidRoute(DesignRepairs.routeFromKey("scr-order-done"))).isTrue();
        assertThat(DesignRepairs.isValidRoute("주문 완료")).isFalse();
    }

    @Test
    @DisplayName("이름에서 경로를 만들 때 앞의 scr- 는 뗀다")
    void buildsRouteFromKey() {
        assertThat(DesignRepairs.routeFromKey("scr-order-done")).isEqualTo("/order-done");
        assertThat(DesignRepairs.routeFromKey("주문 완료")).isEqualTo("/screen");
    }

    @Test
    @DisplayName("이미 쓰이는 경로와 겹치지 않게 고른다")
    void avoidsUsedRoutes() {
        Set<String> used = new HashSet<>(Set.of("/products"));

        String first = DesignRepairs.uniqueRoute("/products", "scr-mine", used);
        assertThat(first).isEqualTo("/mine");

        String second = DesignRepairs.uniqueRoute("/products", "scr-mine", used);
        assertThat(second).isNotEqualTo("/products").isNotEqualTo("/mine");
        assertThat(DesignRepairs.isValidRoute(second)).isTrue();
    }

    @Test
    @DisplayName("경로를 안 정한 화면에는 경로를 지어내지 않는다")
    void keepsEmptyRouteEmpty() {
        assertThat(DesignRepairs.uniqueRoute("", "scr-popup", new HashSet<>())).isEmpty();
    }

    @Test
    @DisplayName("예약어는 뜻이 통하는 이름으로 바꾼다")
    void renamesReservedWords() {
        assertThat(DesignRepairs.isReservedColumnName("order")).isTrue();
        assertThat(DesignRepairs.renameReservedColumn("order")).isEqualTo("order_no");
        assertThat(DesignRepairs.renameReservedColumn("desc")).isEqualTo("description");
        assertThat(DesignRepairs.renameReservedColumn("select")).isEqualTo("select_value");

        // 고친 이름이 다시 예약어면 안 된다.
        assertThat(DesignRepairs.isReservedColumnName(
                DesignRepairs.renameReservedColumn("order"))).isFalse();
    }

    @Test
    @DisplayName("예약어가 아닌 이름은 그대로 둔다")
    void leavesNormalNamesAlone() {
        assertThat(DesignRepairs.renameReservedColumn("email")).isEqualTo("email");
        assertThat(DesignRepairs.renameReservedColumn("order_no")).isEqualTo("order_no");
    }

    @Test
    @DisplayName("컬럼 이름을 소문자와 밑줄로 바꾼다")
    void convertsToSnakeCase() {
        assertThat(DesignRepairs.toSnakeCase("userName")).isEqualTo("user_name");
        assertThat(DesignRepairs.toSnakeCase("created-At")).isEqualTo("created_at");
        assertThat(DesignRepairs.toSnakeCase("email")).isEqualTo("email");
    }
}
