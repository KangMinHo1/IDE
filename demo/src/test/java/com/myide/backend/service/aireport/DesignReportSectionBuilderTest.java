package com.myide.backend.service.aireport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.domain.design.DesignDocSnapshot;
import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignMetaV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.ErdV2;
import com.myide.backend.dto.design.v2.PointV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.ScreenTransitionV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.dto.design.v2.TableV2;
import com.myide.backend.repository.design.DesignDocSnapshotRepository;
import com.myide.backend.service.design.DesignModelCodec;
import com.myide.backend.service.design.doctor.DesignDoctorService;
import com.myide.backend.service.design.doctor.DesignRule;
import com.myide.backend.service.design.doctor.rules.ApiRules;
import com.myide.backend.service.design.doctor.rules.ErdRules;
import com.myide.backend.service.design.doctor.rules.RequirementRules;
import com.myide.backend.service.design.doctor.rules.ScreenRules;
import com.myide.backend.service.design.doctor.rules.TraceRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최종 보고서에 들어갈 설계 부분을 확인한다.
 *
 * 화면이 보내 주는 값 대신 저장된 설계를 읽게 바꾼 이유가 여기서 드러나야
 * 한다. 화면 흐름과 설계 점검 결과, 그리고 요구사항이 실제로 구현까지
 * 이어졌는지가 글에 들어 있어야 의미가 있다.
 */
class DesignReportSectionBuilderTest {

    private static final String WORKSPACE = "ws-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DesignModelCodec codec = new DesignModelCodec(objectMapper);

    private DesignDocSnapshotRepository snapshotRepository;
    private DesignReportSectionBuilder builder;

    @BeforeEach
    void setUp() {
        snapshotRepository = Mockito.mock(DesignDocSnapshotRepository.class);

        List<DesignRule> rules = List.of(
                new RequirementRules(), new ScreenRules(), new ApiRules(),
                new ErdRules(), new TraceRules());

        builder = new DesignReportSectionBuilder(
                snapshotRepository, codec, new DesignDoctorService(rules));
    }

    @Test
    @DisplayName("저장된 설계가 없으면 예전 경로를 쓰도록 null 을 돌려준다")
    void returnsNullWithoutSnapshot() {
        Mockito.when(snapshotRepository.findByWorkspace_Uuid(WORKSPACE))
                .thenReturn(Optional.empty());

        assertThat(builder.build(WORKSPACE)).isNull();
    }

    @Test
    @DisplayName("저장은 있는데 내용이 비었으면 굳이 빈 글을 넣지 않는다")
    void returnsNullForEmptyDesign() {
        givenSnapshot(codec.toJson(DesignModelV2.empty()));

        assertThat(builder.build(WORKSPACE)).isNull();
    }

    @Test
    @DisplayName("망가진 저장본이면 보고서를 막지 않고 예전 경로로 넘긴다")
    void survivesBrokenSnapshot() {
        givenSnapshot("{ 이건 JSON 이 아니다");

        assertThat(builder.build(WORKSPACE)).isNull();
    }

    @Test
    @DisplayName("화면 흐름과 점검 결과, 추적성이 함께 들어간다")
    void includesFlowDoctorAndTrace() {
        givenSnapshot(codec.toJson(sample()));

        String text = builder.build(WORKSPACE);

        assertThat(text).isNotNull();

        // 예전에는 화면이 보내는 값에 화면 흐름 자체가 없었다.
        assertThat(text).contains("[화면 흐름]");
        assertThat(text).contains("로그인 화면 (/login)");
        assertThat(text).contains("[시작 화면]");
        assertThat(text).contains("이동: 로그인 버튼 클릭 → 글 상세");

        assertThat(text).contains("[설계 점검 결과]");
        assertThat(text).contains("[추적성");

        // 근거와 연결이 보고서에 드러나야 한다.
        assertThat(text).contains("근거 요구사항: R-01 로그인");
        assertThat(text).contains("쓰는 표: users");
        assertThat(text).contains("id BIGINT [PK]");
    }

    @Test
    @DisplayName("사슬이 끊긴 요구사항은 어디서 끊겼는지 이름과 함께 적는다")
    void reportsBrokenTrace() {
        DesignModelV2 model = sample();

        // 화면도 API도 없는 요구사항을 하나 더한다.
        List<RequirementV2> requirements = new java.util.ArrayList<>(model.requirements());
        requirements.add(new RequirementV2("req_9", "R-09", "알림", "알림 받기",
                "새 글이 올라오면 알린다", "should", List.of(), List.of()));

        givenSnapshot(codec.toJson(new DesignModelV2(model.schemaVersion(), model.meta(),
                requirements, model.screens(), model.screenTransitions(), model.apis(),
                model.erd())));

        String text = builder.build(WORKSPACE);

        assertThat(text).contains("R-09 알림 받기");
        assertThat(text).contains("담당 화면 없음");
        assertThat(text).contains("담당 API 없음");
        assertThat(text).contains("요구사항 2개 중 1개가 화면·API·표까지 모두 이어져 있습니다.");
    }

    private void givenSnapshot(String projectionJson) {
        DesignDocSnapshot snapshot = Mockito.mock(DesignDocSnapshot.class);
        Mockito.when(snapshot.getProjectionJson()).thenReturn(projectionJson);
        Mockito.when(snapshotRepository.findByWorkspace_Uuid(WORKSPACE))
                .thenReturn(Optional.of(snapshot));
    }

    private DesignModelV2 sample() {
        ColumnV2 id = new ColumnV2("col_1", "id", "BIGINT", null,
                false, true, false, "", "");
        ColumnV2 email = new ColumnV2("col_2", "email", "VARCHAR", 255,
                false, false, false, "", "로그인 이메일");

        TableV2 users = new TableV2("tbl_user", "users", "User", "서비스를 쓰는 사람",
                List.of(id, email), new PointV2(0, 0));

        RequirementV2 login = new RequirementV2("req_1", "R-01", "회원", "로그인",
                "이메일로 로그인한다", "must", List.of("scr_login"), List.of("api_login"));

        ScreenV2 loginScreen = new ScreenV2("scr_login", "/login", "로그인 화면",
                "이메일과 비밀번호를 넣는다", "page", true, false,
                List.of("req_1"), List.of("api_login"), new PointV2(0, 0));

        ScreenV2 detailScreen = new ScreenV2("scr_detail", "/posts", "글 상세",
                "", "page", false, false, List.of(), List.of(), new PointV2(300, 0));

        ScreenTransitionV2 transition = new ScreenTransitionV2("trn_1", "scr_login",
                "scr_detail", "로그인 버튼 클릭", "submit", "", List.of());

        ApiSpecV2 loginApi = new ApiSpecV2("api_login", "POST", "/api/users/login",
                "로그인한다", "", "", false, "R",
                List.of("req_1"), List.of("scr_login"), List.of("tbl_user"));

        return new DesignModelV2(2, DesignMetaV2.empty(),
                List.of(login), List.of(loginScreen, detailScreen), List.of(transition),
                List.of(loginApi), new ErdV2(List.of(users), List.of()));
    }
}
