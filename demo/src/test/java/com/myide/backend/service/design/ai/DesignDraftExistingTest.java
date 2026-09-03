package com.myide.backend.service.design.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignMetaV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.ErdV2;
import com.myide.backend.dto.design.v2.PointV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.dto.design.v2.TableV2;
import com.myide.backend.dto.design.v2.TechStackV2;
import com.myide.backend.service.ai.GeminiHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 이미 내용이 있는 문서에 초안을 더할 때를 확인한다.
 *
 * "결제 관련 요구사항만 추가해 줘" 같은 요청이 실제 쓰임새인데, AI 에게
 * 지금 문서에 무엇이 있는지 알려 주지 않으면 이미 있는 것을 다시 내놓는다.
 * 그러면 같은 경로를 쓰는 화면과 같은 이름의 표가 생겨 설계 점검 오류가
 * 붙고 코드 생성이 막힌다.
 *
 * 프롬프트로 부탁하는 것만으로는 부족하다는 것을 이미 겪었으므로, 여기서는
 * <b>겹치는 것이 실제로 버려지는지</b>까지 본다.
 */
class DesignDraftExistingTest {

    private GeminiHttpClient gemini;
    private DesignDraftService service;

    @BeforeEach
    void setUp() {
        gemini = Mockito.mock(GeminiHttpClient.class);
        service = new DesignDraftService(gemini, new ObjectMapper());
    }

    @Test
    @DisplayName("이미 있는 요구사항과 화면을 프롬프트에 적어 준다")
    void tellsTheModelWhatAlreadyExists() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any())).thenReturn(SKELETON);

        service.generateSkeleton("중고 거래", stack(), null, existing());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        Mockito.verify(gemini).generate(prompt.capture(), Mockito.any());

        assertThat(prompt.getValue()).contains("이미 문서에 있는 것");
        assertThat(prompt.getValue()).contains("로그인");
        assertThat(prompt.getValue()).contains("/login");
        assertThat(prompt.getValue()).contains("users");
    }

    @Test
    @DisplayName("이미 쓰이는 경로는 새 화면에 주지 않는다")
    void doesNotReuseExistingRoutes() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any())).thenReturn(SKELETON);

        DesignModelV2 draft = service.generateSkeleton("중고 거래", stack(), null, existing());

        // AI 가 /login 을 또 내놓았지만 그대로 쓰면 화면 둘이 같은 경로가 된다.
        assertThat(draft.screens()).extracting(ScreenV2::key).doesNotContain("/login");
        assertThat(draft.screens()).extracting(ScreenV2::key).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("이미 있는 표는 다시 만들지 않는다")
    void doesNotRecreateExistingTables() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any())).thenReturn(DETAIL);

        DesignModelV2 draft = service.generateDetail(skeleton(), null, existing());

        assertThat(draft.erd().tables()).extracting(TableV2::name)
                .doesNotContain("users")
                .contains("payments");
    }

    @Test
    @DisplayName("응답이 깨져 있으면 한 번 고쳐 달라고 다시 부른다")
    void repairsBrokenResponseOnce() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any()))
                .thenReturn("이건 JSON 이 아니다", SKELETON);

        DesignModelV2 draft = service.generateSkeleton("중고 거래", stack(), null, null);

        assertThat(draft.screens()).isNotEmpty();
        Mockito.verify(gemini, Mockito.times(2)).generate(Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("고쳐 달라고 해도 안 되면 이유를 알려 준다")
    void givesUpAfterOneRepair() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any()))
                .thenReturn("깨진 응답", "여전히 깨진 응답");

        Throwable error = catchThrowable(
                () -> service.generateSkeleton("중고 거래", stack(), null, null));

        assertThat(error).hasMessageContaining("해석하지 못했습니다");

        // 두 번까지만 부른다. 더 불러도 성공률은 거의 오르지 않는다.
        Mockito.verify(gemini, Mockito.times(2)).generate(Mockito.anyString(), Mockito.any());
    }

    // ── 도우미 ──────────────────────────────────────────────────────

    private TechStackV2 stack() {
        return new TechStackV2("Spring Boot", "React", "MySQL");
    }

    /** 이미 문서에 들어 있는 내용. */
    private DesignModelV2 existing() {
        RequirementV2 login = new RequirementV2("req_1", "R-01", "회원", "로그인",
                "이메일로 로그인한다", "must", List.of("scr_login"), List.of());

        ScreenV2 loginScreen = new ScreenV2("scr_login", "/login", "로그인 화면", "",
                "page", true, false, List.of("req_1"), List.of(), new PointV2(0, 0));

        TableV2 users = new TableV2("tbl_user", "users", "User", "사용자",
                List.of(new ColumnV2("col_1", "id", "BIGINT", null,
                        false, true, false, "", "")), new PointV2(0, 0));

        return new DesignModelV2(2, DesignMetaV2.empty(), List.of(login),
                List.of(loginScreen), List.of(), List.of(),
                new ErdV2(List.of(users), List.of()));
    }

    /** 2단계 입력으로 쓸 최소한의 1단계 결과. */
    private DesignModelV2 skeleton() {
        ScreenV2 screen = new ScreenV2("scr_pay", "/pay", "결제 화면", "",
                "page", true, false, List.of(), List.of(), new PointV2(0, 0));

        return new DesignModelV2(2, DesignMetaV2.empty(), List.of(),
                List.of(screen), List.of(), List.of(), ErdV2.empty());
    }

    /** 이미 있는 /login 을 또 내놓는 응답. */
    private static final String SKELETON = """
            {
              "requirements": [
                {"key":"req-pay","category":"결제","name":"결제하기",
                 "description":"물건 값을 낸다","priority":"must","screenKeys":["scr-pay"]}
              ],
              "screens": [
                {"key":"scr-login","route":"/login","name":"로그인 화면","description":"",
                 "role":"page","isEntry":false,"requiresAuth":false,"requirementKeys":["req-pay"]},
                {"key":"scr-pay","route":"/pay","name":"결제 화면","description":"",
                 "role":"page","isEntry":true,"requiresAuth":true,"requirementKeys":["req-pay"]}
              ],
              "transitions": []
            }
            """;

    /** 이미 있는 users 표를 또 내놓는 응답. */
    private static final String DETAIL = """
            {
              "tables": [
                {"name":"users","description":"또 만든 사용자 표",
                 "columns":[{"name":"id","type":"BIGINT","nullable":false,"isPk":true,"comment":""}]},
                {"name":"payments","description":"결제",
                 "columns":[{"name":"id","type":"BIGINT","nullable":false,"isPk":true,"comment":""}]}
              ],
              "relations": [],
              "apis": [
                {"method":"POST","endpoint":"/api/payments","description":"결제한다",
                 "request":"{}","response":"{}","auth":true,
                 "requirementIds":[],"screenIds":["scr_pay"],"tableNames":["payments"]}
              ]
            }
            """;
}
