package com.myide.backend.service.design.doctor;

import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignMetaV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.DoctorReport;
import com.myide.backend.dto.design.v2.ErdV2;
import com.myide.backend.dto.design.v2.PointV2;
import com.myide.backend.dto.design.v2.RelationV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.ScreenTransitionV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.dto.design.v2.TableV2;
import com.myide.backend.dto.design.v2.TechStackV2;
import com.myide.backend.service.design.doctor.rules.ApiRules;
import com.myide.backend.service.design.doctor.rules.ErdRules;
import com.myide.backend.service.design.doctor.rules.RequirementRules;
import com.myide.backend.service.design.doctor.rules.ScreenRules;
import com.myide.backend.service.design.doctor.rules.TraceRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고치기 버튼이 진짜로 문제를 없애는지 확인한다.
 *
 * 버튼을 누를 수 있다는 것만으로는 부족하다. 눌렀는데 오류가 그대로 남아
 * 있으면 사용자는 도구를 믿지 않게 된다. 그래서 여기서는 <b>수정을 실제로
 * 적용한 뒤 다시 점검해서</b> 그 오류가 사라졌는지 본다.
 */
class DesignDoctorFixTest {

    private final DesignDoctorService doctor = new DesignDoctorService(List.of(
            new RequirementRules(), new ScreenRules(), new ApiRules(),
            new ErdRules(), new TraceRules()));

    private static final Set<String> FIXABLE = Set.of(
            "COL_RESERVED_WORD", "COL_NAMING", "TBL_NO_PK", "SCR_INVALID_ROUTE",
            "SCR_DUP_ROUTE", "REL_TYPE_MISMATCH", "REL_DANGLING", "TRN_DANGLING");

    @Test
    @DisplayName("기계적으로 고칠 수 있는 문제에는 수정 방법이 담긴다")
    void fixableFindingsCarryAFix() {
        List<Finding> findings = doctor.inspect(broken()).findings();

        assertThat(kindOf(findings, "COL_RESERVED_WORD")).isEqualTo(Fix.Kind.RENAME_COLUMN);
        assertThat(kindOf(findings, "COL_NAMING")).isEqualTo(Fix.Kind.RENAME_COLUMN);
        assertThat(kindOf(findings, "TBL_NO_PK")).isEqualTo(Fix.Kind.ADD_PK_COLUMN);
        assertThat(kindOf(findings, "SCR_INVALID_ROUTE")).isEqualTo(Fix.Kind.SET_SCREEN_ROUTE);
        assertThat(kindOf(findings, "SCR_DUP_ROUTE")).isEqualTo(Fix.Kind.SET_SCREEN_ROUTE);
        assertThat(kindOf(findings, "REL_TYPE_MISMATCH")).isEqualTo(Fix.Kind.ALIGN_FK_TYPE);
        assertThat(kindOf(findings, "TRN_DANGLING")).isEqualTo(Fix.Kind.DELETE_TRANSITION);
    }

    @Test
    @DisplayName("담긴 대로 고치면 그 오류가 사라진다")
    void applyingFixesClearsTheProblems() {
        DesignModelV2 model = broken();

        for (int round = 0; round < 10; round++) {
            List<Finding> fixable = doctor.inspect(model).findings().stream()
                    .filter(finding -> finding.fix() != null)
                    .toList();

            if (fixable.isEmpty()) {
                break;
            }

            // 한 번에 하나씩 적용한다. 화면에서도 버튼을 하나씩 누른다.
            model = apply(model, fixable.get(0).fix());
        }

        DoctorReport report = doctor.inspect(model);

        assertThat(report.findings().stream().map(Finding::ruleId).toList())
                .doesNotContainAnyElementsOf(FIXABLE);
    }

    @Test
    @DisplayName("같은 경로를 쓰는 두 화면 중 먼저 것은 그대로 둔다")
    void firstScreenKeepsItsRoute() {
        List<Finding> dup = doctor.inspect(broken()).findings().stream()
                .filter(finding -> "SCR_DUP_ROUTE".equals(finding.ruleId()))
                .toList();

        assertThat(dup).hasSize(2);
        assertThat(dup.get(0).fix()).isNull();
        assertThat(dup.get(1).fix()).isNotNull();
    }

    @Test
    @DisplayName("고칠 수 없는 문제에는 수정 방법을 달지 않는다")
    void unfixableFindingsHaveNoFix() {
        assertThat(doctor.inspect(broken()).findings())
                .filteredOn(finding -> finding.fix() != null)
                .allMatch(finding -> FIXABLE.contains(finding.ruleId()));
    }

    // ── 도우미 ──────────────────────────────────────────────────────

    private Fix.Kind kindOf(List<Finding> findings, String ruleId) {
        return findings.stream()
                .filter(finding -> ruleId.equals(finding.ruleId()))
                .map(Finding::fix)
                .filter(fix -> fix != null)
                .map(Fix::kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError(ruleId + " 에 수정 방법이 없습니다."));
    }

    /** 화면이 하는 일을 그대로 흉내 낸다. */
    private DesignModelV2 apply(DesignModelV2 model, Fix fix) {
        List<ScreenV2> screens = new ArrayList<>(model.screens());
        List<ScreenTransitionV2> transitions = new ArrayList<>(model.screenTransitions());
        List<TableV2> tables = new ArrayList<>(model.erd().tables());
        List<RelationV2> relations = new ArrayList<>(model.erd().relations());

        switch (fix.kind()) {
            case SET_SCREEN_ROUTE -> screens.replaceAll(screen ->
                    screen.id().equals(fix.targetId())
                            ? new ScreenV2(screen.id(), fix.value(), screen.name(),
                            screen.description(), screen.role(), screen.isEntry(),
                            screen.requiresAuth(), screen.requirementIds(), screen.apiIds(),
                            screen.layout())
                            : screen);

            case DELETE_TRANSITION ->
                    transitions.removeIf(item -> item.id().equals(fix.targetId()));

            case DELETE_RELATION ->
                    relations.removeIf(item -> item.id().equals(fix.targetId()));

            case RENAME_COLUMN, ALIGN_FK_TYPE -> tables.replaceAll(table -> {
                if (!table.id().equals(fix.targetId())) {
                    return table;
                }

                List<ColumnV2> columns = new ArrayList<>(table.columns());
                columns.replaceAll(column -> {
                    if (!column.id().equals(fix.columnId())) {
                        return column;
                    }

                    return fix.kind() == Fix.Kind.RENAME_COLUMN
                            ? new ColumnV2(column.id(), fix.value(), column.type(),
                            column.length(), column.nullable(), column.isPk(), column.isFk(),
                            column.defaultValue(), column.comment())
                            : new ColumnV2(column.id(), column.name(), fix.value(),
                            fix.length(), column.nullable(), column.isPk(), column.isFk(),
                            column.defaultValue(), column.comment());
                });

                return new TableV2(table.id(), table.name(), table.entityName(),
                        table.description(), columns, table.layout());
            });

            case ADD_PK_COLUMN -> tables.replaceAll(table -> {
                if (!table.id().equals(fix.targetId())) {
                    return table;
                }

                List<ColumnV2> columns = new ArrayList<>();
                columns.add(new ColumnV2("col_new_pk", "id", "BIGINT", null,
                        false, true, false, "", ""));
                columns.addAll(table.columns());

                return new TableV2(table.id(), table.name(), table.entityName(),
                        table.description(), columns, table.layout());
            });
        }

        return new DesignModelV2(model.schemaVersion(), model.meta(), model.requirements(),
                screens, transitions, model.apis(), new ErdV2(tables, relations));
    }

    /** 고칠 수 있는 문제를 종류별로 하나씩 심어 둔 설계. */
    private DesignModelV2 broken() {
        ColumnV2 userId = new ColumnV2("col_u1", "id", "BIGINT", null,
                false, true, false, "", "");
        ColumnV2 userName = new ColumnV2("col_u2", "userName", "VARCHAR", 100,
                false, false, false, "", "");

        ColumnV2 orderId = new ColumnV2("col_o1", "id", "BIGINT", null,
                false, true, false, "", "");
        ColumnV2 orderWord = new ColumnV2("col_o2", "order", "VARCHAR", 50,
                false, false, false, "", "");
        ColumnV2 orderUser = new ColumnV2("col_o3", "user_id", "VARCHAR", 50,
                false, false, true, "", "");

        TableV2 users = new TableV2("tbl_user", "users", "User", "사용자",
                List.of(userId, userName), new PointV2(0, 0));
        TableV2 orders = new TableV2("tbl_order", "orders", "Order", "주문",
                List.of(orderId, orderWord, orderUser), new PointV2(300, 0));
        TableV2 logs = new TableV2("tbl_log", "logs", "Log", "기본키가 없는 표",
                List.of(new ColumnV2("col_l1", "body", "TEXT", null,
                        true, false, false, "", "")), new PointV2(600, 0));

        RelationV2 mismatch = new RelationV2("rel_1", "tbl_order", "col_o3",
                "tbl_user", "col_u1", "1:N", "", "");

        RequirementV2 requirement = new RequirementV2("req_1", "R-01", "주문", "주문하기",
                "물건을 산다", "must", List.of("scr_list"), List.of("api_1"));

        ScreenV2 list = new ScreenV2("scr_list", "/products", "상품 목록", "",
                "page", true, false, List.of("req_1"), List.of("api_1"), new PointV2(0, 0));
        ScreenV2 mine = new ScreenV2("scr_mine", "/products", "내 상품", "",
                "page", false, false, List.of("req_1"), List.of(), new PointV2(300, 0));
        ScreenV2 detail = new ScreenV2("scr_detail", "/products/{id}", "상품 상세", "",
                "page", false, false, List.of("req_1"), List.of(), new PointV2(600, 0));

        ScreenTransitionV2 toMine = new ScreenTransitionV2("trn_1", "scr_list", "scr_mine",
                "내 상품", "navigate", "", List.of());
        ScreenTransitionV2 toNowhere = new ScreenTransitionV2("trn_2", "scr_list", "scr_ghost",
                "없는 화면", "navigate", "", List.of());
        ScreenTransitionV2 toDetail = new ScreenTransitionV2("trn_3", "scr_list", "scr_detail",
                "상품 클릭", "navigate", "", List.of());

        ApiSpecV2 api = new ApiSpecV2("api_1", "GET", "/api/products", "상품 목록",
                "", "", false, "R", List.of("req_1"), List.of("scr_list"),
                List.of("tbl_user", "tbl_order", "tbl_log"));

        return new DesignModelV2(2,
                new DesignMetaV2("주문", new TechStackV2("Spring Boot", "React", "MySQL"), null),
                List.of(requirement),
                List.of(list, mine, detail),
                List.of(toMine, toNowhere, toDetail),
                List.of(api),
                new ErdV2(List.of(users, orders, logs), List.of(mismatch)));
    }
}
