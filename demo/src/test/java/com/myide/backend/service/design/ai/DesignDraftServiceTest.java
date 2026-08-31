package com.myide.backend.service.design.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.TableV2;
import com.myide.backend.dto.design.v2.TechStackV2;
import com.myide.backend.service.ai.GeminiHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI가 엉뚱한 것을 내놓아도 문서가 깨지지 않는지 확인한다.
 *
 * 실제 Gemini 는 부르지 않는다. 값어치가 있는 부분은 호출이 아니라 후처리이고,
 * 후처리가 무너지면 AI 결과가 그대로 팀 문서로 들어가기 때문이다.
 */
class DesignDraftServiceTest {

    private GeminiHttpClient gemini;
    private DesignDraftService service;

    @BeforeEach
    void setUp() {
        gemini = Mockito.mock(GeminiHttpClient.class);
        service = new DesignDraftService(gemini, new ObjectMapper());
    }

    private void willReturn(String json) {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any())).thenReturn(json);
    }

    @Test
    @DisplayName("임시 키를 진짜 id로 바꾸고 양쪽 연결을 채운다")
    void assignsIdsAndFillsBothSides() {
        willReturn("""
                {
                  "requirements": [
                    {"key":"req-login","category":"회원","name":"로그인","description":"이메일로 로그인",
                     "priority":"must","screenKeys":["scr-login"]}
                  ],
                  "screens": [
                    {"key":"scr-login","route":"/login","name":"로그인 화면","description":"",
                     "role":"page","isEntry":true,"requiresAuth":false,"requirementKeys":["req-login"]},
                    {"key":"scr-home","route":"/home","name":"홈","description":"",
                     "role":"page","isEntry":false,"requiresAuth":false,"requirementKeys":["req-login"]}
                  ],
                  "transitions": [
                    {"fromKey":"scr-login","toKey":"scr-home","trigger":"로그인 성공","kind":"submit","condition":""},
                    {"fromKey":"scr-login","toKey":"scr-ghost","trigger":"없는 화면으로","kind":"navigate","condition":""}
                  ]
                }
                """);

        DesignModelV2 model = service.generateSkeleton("중고 거래", stack(), null);

        assertThat(model.requirements()).hasSize(1);
        assertThat(model.requirements().get(0).id()).startsWith("req_");
        assertThat(model.requirements().get(0).code()).isEqualTo("R-01");
        assertThat(model.screens()).hasSize(2);
        assertThat(model.screens().get(0).id()).startsWith("scr_");

        // 요구사항과 화면이 서로를 가리켜야 한다.
        String screenId = model.screens().get(0).id();
        assertThat(model.requirements().get(0).screenIds()).contains(screenId);
        assertThat(model.screens().get(0).requirementIds())
                .contains(model.requirements().get(0).id());

        // 없는 화면을 가리키는 이동은 버린다. 끊어진 화살표를 만들지 않는다.
        assertThat(model.screenTransitions()).hasSize(1);
        assertThat(model.screenTransitions().get(0).trigger()).isEqualTo("로그인 성공");

        // 화면이 겹치지 않게 자리를 잡아야 한다.
        assertThat(model.screens().get(0).layout())
                .isNotEqualTo(model.screens().get(1).layout());
    }

    @Test
    @DisplayName("시작 화면을 빠뜨리면 첫 화면을 시작으로 삼는다")
    void ensuresEntryScreen() {
        willReturn("""
                {
                  "requirements": [],
                  "screens": [
                    {"key":"scr-a","route":"/a","name":"화면 A","description":"",
                     "role":"page","isEntry":false,"requiresAuth":false,"requirementKeys":[]}
                  ],
                  "transitions": []
                }
                """);

        DesignModelV2 model = service.generateSkeleton("무언가", stack(), null);

        assertThat(model.screens().get(0).isEntry()).isTrue();
    }

    @Test
    @DisplayName("기본키를 빠뜨린 표에 id를 붙이고 모르는 타입을 고친다")
    void fixesTables() {
        willReturn("""
                {
                  "tables": [
                    {"name":"products","description":"상품",
                     "columns":[
                       {"name":"title","type":"MONEY","nullable":false,"isPk":false,"comment":""},
                       {"name":"price","type":"DECIMAL(10,2)","nullable":false,"isPk":false,"comment":""}
                     ]}
                  ],
                  "relations": [],
                  "apis": [
                    {"method":"GET","endpoint":"/api/products","description":"목록",
                     "request":"","response":"[]","auth":false,
                     "requirementIds":["req_keep"],"screenIds":["scr_keep"],"tableNames":["products"]}
                  ]
                }
                """);

        DesignModelV2 model = service.generateDetail(skeleton(), null);
        TableV2 products = model.erd().tables().get(0);

        // 기본키가 없으면 코드 생성이 아예 막히므로 앞에 붙여 준다.
        assertThat(products.columns().get(0).name()).isEqualTo("id");
        assertThat(products.columns().get(0).isPk()).isTrue();

        // 모르는 타입은 VARCHAR 로, 길이 표기는 떼어 낸다.
        assertThat(typeOf(products, "title")).isEqualTo("VARCHAR");
        assertThat(typeOf(products, "price")).isEqualTo("DECIMAL");
    }

    @Test
    @DisplayName("없는 요구사항이나 화면을 가리키는 참조는 버린다")
    void dropsUnknownReferences() {
        willReturn("""
                {
                  "tables": [
                    {"name":"users","description":"",
                     "columns":[{"name":"id","type":"BIGINT","nullable":false,"isPk":true,"comment":""}]}
                  ],
                  "relations": [],
                  "apis": [
                    {"method":"GET","endpoint":"/api/users","description":"목록",
                     "request":"","response":"[]","auth":false,
                     "requirementIds":["req_keep","req_ghost"],
                     "screenIds":["scr_ghost"],
                     "tableNames":["users","ghosts"]}
                  ]
                }
                """);

        DesignModelV2 model = service.generateDetail(skeleton(), null);

        assertThat(model.apis().get(0).requirementIds()).containsExactly("req_keep");
        assertThat(model.apis().get(0).screenIds()).isEmpty();
        assertThat(model.apis().get(0).tableIds()).hasSize(1);
    }

    @Test
    @DisplayName("같은 엔드포인트를 두 번 내놓으면 하나만 남긴다")
    void dropsDuplicateEndpoints() {
        willReturn("""
                {
                  "tables": [],
                  "relations": [],
                  "apis": [
                    {"method":"GET","endpoint":"/api/users","description":"첫 번째",
                     "request":"","response":"","auth":false,
                     "requirementIds":[],"screenIds":[],"tableNames":[]},
                    {"method":"GET","endpoint":"/api/users","description":"중복",
                     "request":"","response":"","auth":false,
                     "requirementIds":[],"screenIds":[],"tableNames":[]}
                  ]
                }
                """);

        DesignModelV2 model = service.generateDetail(skeleton(), null);

        assertThat(model.apis()).hasSize(1);
        assertThat(model.apis().get(0).description()).isEqualTo("첫 번째");
    }

    @Test
    @DisplayName("가리키는 컬럼이 없는 관계는 만들지 않고, 만든 관계의 컬럼은 외래키로 표시한다")
    void handlesRelations() {
        willReturn("""
                {
                  "tables": [
                    {"name":"users","description":"",
                     "columns":[{"name":"id","type":"BIGINT","nullable":false,"isPk":true,"comment":""}]},
                    {"name":"orders","description":"",
                     "columns":[
                       {"name":"id","type":"BIGINT","nullable":false,"isPk":true,"comment":""},
                       {"name":"user_id","type":"BIGINT","nullable":false,"isPk":false,"comment":""}
                     ]}
                  ],
                  "relations": [
                    {"fromTable":"orders","fromColumn":"user_id","toTable":"users","toColumn":"id","cardinality":"1:N"},
                    {"fromTable":"orders","fromColumn":"nowhere","toTable":"users","toColumn":"id","cardinality":"1:N"}
                  ],
                  "apis": []
                }
                """);

        DesignModelV2 model = service.generateDetail(skeleton(), null);

        assertThat(model.erd().relations()).hasSize(1);

        TableV2 orders = model.erd().tables().stream()
                .filter(table -> table.name().equals("orders"))
                .findFirst()
                .orElseThrow();

        assertThat(orders.columns().stream()
                .filter(column -> column.name().equals("user_id"))
                .findFirst()
                .orElseThrow()
                .isFk()).isTrue();
    }

    @Test
    @DisplayName("AI가 응답을 잘리게 내놓으면 이유를 알려 준다")
    void reportsTruncation() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any()))
                .thenThrow(new GeminiHttpClient.ResponseTruncatedException("잘림"));

        assertThat(catchMessage(() -> service.generateSkeleton("무언가", stack(), null)))
                .contains("너무 길어");
    }

    @Test
    @DisplayName("AI를 쓸 수 없으면 편집을 막지 않고 이유만 알려 준다")
    void reportsUnavailable() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any()))
                .thenThrow(new GeminiHttpClient.GeminiUnavailableException("없음"));

        assertThat(catchMessage(() -> service.generateSkeleton("무언가", stack(), null)))
                .contains("AI를 쓸 수 없습니다");
    }

    // ── 도우미 ──────────────────────────────────────────────────────

    private TechStackV2 stack() {
        return new TechStackV2("Spring Boot", "React", "MySQL");
    }

    /** 2단계 입력으로 쓸 최소한의 1단계 결과. */
    private DesignModelV2 skeleton() {
        var requirement = new com.myide.backend.dto.design.v2.RequirementV2(
                "req_keep", "R-01", "기본", "요구사항", "설명", "must", java.util.List.of(), java.util.List.of());

        var screen = new com.myide.backend.dto.design.v2.ScreenV2(
                "scr_keep", "/keep", "화면", "", "page", true, false,
                java.util.List.of("req_keep"), java.util.List.of(), null);

        return new DesignModelV2(2,
                new com.myide.backend.dto.design.v2.DesignMetaV2("요약", stack(), null),
                java.util.List.of(requirement), java.util.List.of(screen),
                java.util.List.of(), java.util.List.of(),
                com.myide.backend.dto.design.v2.ErdV2.empty());
    }

    private String typeOf(TableV2 table, String columnName) {
        return table.columns().stream()
                .filter(column -> column.name().equals(columnName))
                .map(ColumnV2::type)
                .findFirst()
                .orElse("");
    }

    private String catchMessage(Runnable runnable) {
        try {
            runnable.run();
            return "";
        } catch (Exception e) {
            return e.getMessage() == null ? "" : e.getMessage();
        }
    }
}
