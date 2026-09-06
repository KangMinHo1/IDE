package com.myide.backend.service.design.doctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.DoctorReport;
import com.myide.backend.dto.design.v2.ErdV2;
import com.myide.backend.dto.design.v2.RelationV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.ScreenTransitionV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.dto.design.v2.TableV2;
import com.myide.backend.service.design.DesignModelCodec;
import com.myide.backend.service.design.doctor.rules.ApiRules;
import com.myide.backend.service.design.doctor.rules.ErdRules;
import com.myide.backend.service.design.doctor.rules.RequirementRules;
import com.myide.backend.service.design.doctor.rules.ScreenRules;
import com.myide.backend.service.design.doctor.rules.TraceRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설계 점검이 실제로 맞는 것을 잡는지 확인한다.
 *
 * 이 검사 결과로 코드 생성이 막히므로, 없는 문제를 만들어 내면 사용자를
 * 가로막고 있는 문제를 놓치면 깨진 코드가 만들어진다. 양쪽 다 확인한다.
 */
class DesignDoctorServiceTest {

    private DesignDoctorService service;
    private DesignModelCodec codec;

    @BeforeEach
    void setUp() {
        service = new DesignDoctorService(List.of(
                new RequirementRules(),
                new ScreenRules(),
                new ApiRules(),
                new ErdRules(),
                new TraceRules()
        ));
        codec = new DesignModelCodec(new ObjectMapper());
    }

    private List<String> ruleIds(DoctorReport report) {
        return report.findings().stream().map(Finding::ruleId).toList();
    }

    private List<String> errorIds(DoctorReport report) {
        return report.findings().stream()
                .filter(finding -> finding.severity() == Severity.ERROR)
                .map(Finding::ruleId)
                .toList();
    }

    @Test
    @DisplayName("제대로 이어진 설계에는 오류를 만들어 내지 않는다")
    void cleanDesignHasNoErrors() throws Exception {
        String json;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("design/projection.json")) {
            assertThat(in).isNotNull();
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        DoctorReport report = service.inspect(codec.fromJson(json));

        assertThat(errorIds(report)).isEmpty();
        assertThat(report.codegenBlocked()).isFalse();
    }

    @Test
    @DisplayName("빈 문서는 아무 문제도 아니다")
    void emptyDesignIsFine() {
        DoctorReport report = service.inspect(DesignModelV2.empty());

        assertThat(report.findings()).isEmpty();
        assertThat(report.codegenBlocked()).isFalse();
    }

    @Test
    @DisplayName("기본키 없는 테이블과 컬럼 없는 테이블을 잡는다")
    void catchesTableProblems() {
        TableV2 noPk = new TableV2("tbl_a", "logs", "", "",
                List.of(column("col_a", "message", "TEXT", false)), null);
        TableV2 empty = new TableV2("tbl_b", "temp", "", "", List.of(), null);

        DoctorReport report = service.inspect(model(
                List.of(), List.of(), List.of(), List.of(),
                new ErdV2(List.of(noPk, empty), List.of())));

        assertThat(errorIds(report)).contains("TBL_NO_PK", "TBL_NO_COLUMN");
        assertThat(report.codegenBlocked()).isTrue();
    }

    @Test
    @DisplayName("예약어와 모르는 타입을 잡는다")
    void catchesColumnProblems() {
        TableV2 table = new TableV2("tbl_a", "orders", "", "", List.of(
                column("col_a", "id", "BIGINT", true),
                column("col_b", "order", "VARCHAR", false),
                column("col_c", "amount", "MONEY", false)
        ), null);

        DoctorReport report = service.inspect(model(
                List.of(), List.of(), List.of(), List.of(),
                new ErdV2(List.of(table), List.of())));

        assertThat(errorIds(report)).contains("COL_RESERVED_WORD", "COL_UNKNOWN_TYPE");
    }

    @Test
    @DisplayName("없는 테이블을 잇는 관계와 타입이 다른 외래키를 잡는다")
    void catchesRelationProblems() {
        TableV2 users = new TableV2("tbl_users", "users", "", "", List.of(
                column("col_uid", "id", "BIGINT", true)
        ), null);

        TableV2 posts = new TableV2("tbl_posts", "posts", "", "", List.of(
                column("col_pid", "id", "BIGINT", true),
                column("col_author", "author_id", "VARCHAR", false)
        ), null);

        RelationV2 dangling = new RelationV2("rel_x", "tbl_posts", "col_author",
                "tbl_ghost", "col_ghost", "1:N", "", "");

        RelationV2 mismatched = new RelationV2("rel_y", "tbl_posts", "col_author",
                "tbl_users", "col_uid", "1:N", "", "");

        DoctorReport report = service.inspect(model(
                List.of(), List.of(), List.of(), List.of(),
                new ErdV2(List.of(users, posts), List.of(dangling, mismatched))));

        assertThat(errorIds(report)).contains("REL_DANGLING", "REL_TYPE_MISMATCH");
    }

    @Test
    @DisplayName("시작 화면이 없거나 도달할 수 없는 화면을 잡는다")
    void catchesScreenProblems() {
        ScreenV2 entry = screen("scr_a", "/home", "홈", true);
        ScreenV2 island = screen("scr_b", "/lost", "외딴 화면", false);

        DoctorReport report = service.inspect(model(
                List.of(), List.of(entry, island), List.of(), List.of(), ErdV2.empty()));

        assertThat(ruleIds(report)).contains("SCR_UNREACHABLE");
        assertThat(errorIds(report)).doesNotContain("SCR_NO_ENTRY");

        // 시작 화면을 지우면 그때는 오류다.
        DoctorReport noEntry = service.inspect(model(
                List.of(), List.of(screen("scr_b", "/lost", "외딴 화면", false)),
                List.of(), List.of(), ErdV2.empty()));

        assertThat(errorIds(noEntry)).contains("SCR_NO_ENTRY");
    }

    @Test
    @DisplayName("라우트 경로가 겹치거나 형식이 틀린 것을 잡는다")
    void catchesRouteProblems() {
        ScreenV2 a = screen("scr_a", "/list", "목록", true);
        ScreenV2 b = screen("scr_b", "/list", "목록 복사본", false);
        ScreenV2 c = screen("scr_c", "로그인 화면", "한글 경로", false);

        DoctorReport report = service.inspect(model(
                List.of(), List.of(a, b, c), List.of(), List.of(), ErdV2.empty()));

        assertThat(errorIds(report)).contains("SCR_DUP_ROUTE", "SCR_INVALID_ROUTE");
    }

    @Test
    @DisplayName("경로가 비어 있는 것은 오류가 아니라 경고다")
    void emptyRouteIsWarningNotError() {
        ScreenV2 fresh = screen("scr_a", "", "방금 만든 화면", true);

        DoctorReport report = service.inspect(model(
                List.of(), List.of(fresh), List.of(), List.of(), ErdV2.empty()));

        assertThat(ruleIds(report)).contains("SCR_NO_ROUTE");
        assertThat(report.codegenBlocked()).isFalse();
    }

    @Test
    @DisplayName("같은 엔드포인트가 두 번 있으면 잡는다")
    void catchesDuplicateEndpoint() {
        ApiSpecV2 first = api("api_a", "GET", "/api/users");
        ApiSpecV2 second = api("api_b", "GET", "/api/users");

        DoctorReport report = service.inspect(model(
                List.of(), List.of(), List.of(), List.of(first, second), ErdV2.empty()));

        assertThat(errorIds(report)).contains("API_DUP_ENDPOINT");
    }

    @Test
    @DisplayName("아무도 쓰지 않는 테이블과 담당 API 없는 요구사항을 잡는다")
    void catchesOrphans() {
        RequirementV2 requirement = new RequirementV2("req_a", "R-01", "회원", "로그인",
                "이메일로 로그인한다", "must", List.of(), List.of());

        TableV2 unused = new TableV2("tbl_a", "reviews", "", "", List.of(
                column("col_a", "id", "BIGINT", true)
        ), null);

        DoctorReport report = service.inspect(model(
                List.of(requirement), List.of(), List.of(), List.of(),
                new ErdV2(List.of(unused), List.of())));

        assertThat(ruleIds(report)).contains("REQ_NO_API", "REQ_NO_SCREEN", "TBL_ORPHAN");
    }

    @Test
    @DisplayName("요구사항에서 API까지는 이어졌는데 테이블이 없으면 교차 검사가 잡는다")
    void catchesBrokenChain() {
        RequirementV2 requirement = new RequirementV2("req_a", "R-01", "회원", "로그인",
                "이메일로 로그인한다", "must", List.of("scr_a"), List.of("api_a"));

        ScreenV2 loginScreen = new ScreenV2("scr_a", "/login", "로그인 화면", "", "page",
                true, false, List.of("req_a"), List.of("api_a"), null);

        // 테이블을 하나도 다루지 않는 API
        ApiSpecV2 loginApi = new ApiSpecV2("api_a", "POST", "/api/login", "로그인",
                "{}", "{}", false, "", List.of("req_a"), List.of("scr_a"), List.of());

        DoctorReport report = service.inspect(model(
                List.of(requirement), List.of(loginScreen), List.of(),
                List.of(loginApi), ErdV2.empty()));

        assertThat(ruleIds(report)).contains("TRACE_NO_STORAGE");
    }

    // ── 만들기 도우미 ───────────────────────────────────────────────

    private DesignModelV2 model(List<RequirementV2> requirements,
                                List<ScreenV2> screens,
                                List<ScreenTransitionV2> transitions,
                                List<ApiSpecV2> apis,
                                ErdV2 erd) {
        return new DesignModelV2(2, null, requirements, screens, transitions, apis, erd);
    }

    private ColumnV2 column(String id, String name, String type, boolean isPk) {
        return new ColumnV2(id, name, type, null, !isPk, isPk, false, "", "");
    }

    private ScreenV2 screen(String id, String key, String name, boolean isEntry) {
        return new ScreenV2(id, key, name, "", "page", isEntry, false,
                List.of(), List.of(), null);
    }

    private ApiSpecV2 api(String id, String method, String endpoint) {
        return new ApiSpecV2(id, method, endpoint, "", "", "", false, "",
                List.of(), List.of(), List.of());
    }
}
