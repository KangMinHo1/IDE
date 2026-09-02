package com.myide.backend.service.design.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.DoctorReport;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.dto.design.v2.TechStackV2;
import com.myide.backend.service.ai.GeminiHttpClient;
import com.myide.backend.service.design.doctor.DesignDoctorService;
import com.myide.backend.service.design.doctor.DesignRule;
import com.myide.backend.service.design.doctor.Finding;
import com.myide.backend.service.design.doctor.Severity;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 초안이 곧바로 코드 생성까지 갈 수 있어야 한다.
 *
 * 설계 점검에 오류가 하나라도 있으면 코드 생성이 막힌다. 그런데 오류를
 * 만들지 않는 일을 <b>프롬프트에 부탁하는 것으로</b> 처리해 두면, 부탁을
 * 안 듣는 날에는 사용자가 손으로 고칠 때까지 아무것도 못 한다. AI 는
 * 매번 다르게 답하고 점검 규칙은 매번 똑같이 판정하므로, 규칙이 검사하는
 * 것은 후처리가 <b>보장</b>해야 한다.
 *
 * 그래서 이 검사는 일부러 험한 응답을 넣는다. 실제 모델이 흔히 저지르는
 * 것들이다 — 경로에 중괄호를 쓰고, 같은 경로를 두 화면에 쓰고, 컬럼 이름을
 * order 라 짓고, 기본키를 빠뜨리고, 외래키 타입을 가리키는 기본키와 다르게
 * 적는다.
 */
class AiDraftPassesDoctorTest {

    private GeminiHttpClient gemini;
    private DesignDraftService service;
    private DesignDoctorService doctor;

    @BeforeEach
    void setUp() {
        gemini = Mockito.mock(GeminiHttpClient.class);
        service = new DesignDraftService(gemini, new ObjectMapper());

        List<DesignRule> rules = List.of(
                new RequirementRules(), new ScreenRules(), new ApiRules(),
                new ErdRules(), new TraceRules());
        doctor = new DesignDoctorService(rules);
    }

    @Test
    @DisplayName("험한 응답을 받아도 초안에 코드 생성을 막는 오류가 남지 않는다")
    void draftHasNoBlockingErrors() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any())).thenReturn(SKELETON);
        DesignModelV2 skeleton = service.generateSkeleton("중고 거래", stack(), null);

        // 2단계 응답은 1단계에서 정해진 진짜 id를 그대로 되받아 적는다.
        // 실제 호출도 그렇게 도는데, 지어낸 id를 쓰면 연결이 전부 버려져
        // 검사가 아무것도 확인하지 못한다.
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any()))
                .thenReturn(detailFor(skeleton));

        DesignModelV2 model = service.generateDetail(skeleton, null);

        DoctorReport report = doctor.inspect(model);

        List<String> errors = report.findings().stream()
                .filter(finding -> finding.severity() == Severity.ERROR)
                .map(finding -> finding.ruleId() + " — " + finding.message())
                .toList();

        assertThat(errors)
                .as("코드 생성을 막는 오류가 남아 있으면 초안을 만든 의미가 없다")
                .isEmpty();
    }

    @Test
    @DisplayName("고칠 때 뜻이 통하게 고친다")
    void repairsAreReadable() {
        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any())).thenReturn(SKELETON);
        DesignModelV2 skeleton = service.generateSkeleton("중고 거래", stack(), null);

        Mockito.when(gemini.generate(Mockito.anyString(), Mockito.any()))
                .thenReturn(detailFor(skeleton));
        DesignModelV2 model = service.generateDetail(skeleton, null);

        List<String> routes = model.screens().stream().map(ScreenV2::key).toList();

        // 중괄호는 라우터가 아는 모양으로 바꾼다.
        assertThat(routes).contains("/products/:id");

        // 같은 경로를 두 번 쓰면 뒤엣것은 영원히 열리지 않는다. 화면 키에서
        // 새 경로를 만든다.
        assertThat(routes).contains("/mine");

        // 한글 경로는 살릴 수 없으므로 화면 키에서 만든다.
        assertThat(routes).contains("/done");
        assertThat(routes).doesNotContain("주문 완료");

        // 경로가 겹치는 화면이 하나도 없어야 한다.
        assertThat(routes).doesNotHaveDuplicates();

        List<String> orderColumns = model.erd().tables().stream()
                .filter(table -> table.name().equals("orders"))
                .flatMap(table -> table.columns().stream())
                .map(ColumnV2::name)
                .toList();

        // 예약어는 사람이 읽을 수 있는 이름으로 바꾼다. order_col 같은 이름을
        // 붙이면 고쳐 놓고도 결국 손대게 된다.
        assertThat(orderColumns).contains("order_no");
        assertThat(orderColumns).doesNotContain("order");

        // 외래키는 가리키는 기본키와 같은 타입이어야 한다.
        ColumnV2 productId = model.erd().tables().stream()
                .filter(table -> table.name().equals("orders"))
                .flatMap(table -> table.columns().stream())
                .filter(column -> column.name().equals("product_id"))
                .findFirst()
                .orElseThrow();

        assertThat(productId.type()).isEqualTo("BIGINT");
        assertThat(productId.isFk()).isTrue();
    }

    private TechStackV2 stack() {
        return new TechStackV2("Spring Boot", "React", "MySQL");
    }

    /** 경로에 중괄호를 쓰고, 같은 경로를 두 번 쓰고, 시작 화면을 빠뜨린 응답. */
    private static final String SKELETON = """
            {
              "requirements": [
                {"key":"req-list","category":"상품","name":"상품 목록 보기",
                 "description":"팔린 물건을 목록으로 본다","priority":"must",
                 "screenKeys":["scr-list","scr-detail"]},
                {"key":"req-order","category":"거래","name":"주문하기",
                 "description":"마음에 드는 물건을 산다","priority":"must",
                 "screenKeys":["scr-detail"]}
              ],
              "screens": [
                {"key":"scr-list","route":"/products","name":"상품 목록","description":"",
                 "role":"page","isEntry":false,"requiresAuth":false,
                 "requirementKeys":["req-list"]},
                {"key":"scr-detail","route":"/products/{id}","name":"상품 상세","description":"",
                 "role":"page","isEntry":false,"requiresAuth":false,
                 "requirementKeys":["req-list","req-order"]},
                {"key":"scr-mine","route":"/products","name":"내 상품","description":"",
                 "role":"page","isEntry":false,"requiresAuth":true,
                 "requirementKeys":["req-order"]},
                {"key":"scr-done","route":"주문 완료","name":"주문 완료","description":"",
                 "role":"page","isEntry":false,"requiresAuth":true,
                 "requirementKeys":["req-order"]}
              ],
              "transitions": [
                {"fromKey":"scr-list","toKey":"scr-detail","trigger":"상품 클릭",
                 "kind":"navigate","condition":""},
                {"fromKey":"scr-detail","toKey":"scr-done","trigger":"주문 버튼 클릭",
                 "kind":"submit","condition":""},
                {"fromKey":"scr-list","toKey":"scr-mine","trigger":"내 상품 보기",
                 "kind":"navigate","condition":""}
              ]
            }
            """;

    /**
     * 예약어 컬럼, 같은 이름 컬럼, 기본키 없는 표, 이름이 겹치는 표,
     * 외래키와 기본키의 타입이 어긋난 관계가 모두 들어 있다.
     */
    private String detailFor(DesignModelV2 skeleton) {
        String reqList = skeleton.requirements().get(0).id();
        String reqOrder = skeleton.requirements().get(1).id();
        String scrList = skeleton.screens().get(0).id();
        String scrDetail = skeleton.screens().get(1).id();

        return """
                {
                  "tables": [
                    {"name":"products","description":"파는 물건",
                     "columns":[
                       {"name":"id","type":"BIGINT","nullable":false,"isPk":true,"comment":""},
                       {"name":"name","type":"VARCHAR","length":100,"nullable":false,"isPk":false,"comment":""},
                       {"name":"name","type":"VARCHAR","length":100,"nullable":true,"isPk":false,"comment":"겹치는 이름"},
                       {"name":"desc","type":"TEXT","nullable":true,"isPk":false,"comment":"예약어"}
                     ]},
                    {"name":"orders","description":"주문",
                     "columns":[
                       {"name":"id","type":"BIGINT","nullable":false,"isPk":true,"comment":""},
                       {"name":"order","type":"VARCHAR","length":50,"nullable":false,"isPk":false,"comment":"예약어"},
                       {"name":"product_id","type":"VARCHAR","length":50,"nullable":false,"isPk":false,"comment":"타입이 어긋남"}
                     ]},
                    {"name":"orders","description":"이름이 겹치는 표",
                     "columns":[
                       {"name":"amount","type":"MONEY","nullable":false,"isPk":false,"comment":"모르는 타입"}
                     ]},
                    {"name":"reviews","description":"기본키가 없는 표",
                     "columns":[
                       {"name":"body","type":"TEXT","nullable":true,"isPk":false,"comment":""}
                     ]},
                    {"name":"","description":"이름도 컬럼도 없는 표","columns":[]}
                  ],
                  "relations": [
                    {"fromTable":"orders","fromColumn":"product_id",
                     "toTable":"products","toColumn":"id","cardinality":"1:N"},
                    {"fromTable":"orders","fromColumn":"없는컬럼",
                     "toTable":"products","toColumn":"id","cardinality":"1:N"}
                  ],
                  "apis": [
                    {"method":"GET","endpoint":"/api/products","description":"상품 목록",
                     "request":"","response":"[{\\"id\\":1}]","auth":false,
                     "requirementIds":["%s"],"screenIds":["%s"],"tableNames":["products"]},
                    {"method":"GET","endpoint":"/api/products/{id}","description":"상품 하나",
                     "request":"","response":"{\\"id\\":1}","auth":false,
                     "requirementIds":["%s"],"screenIds":["%s"],"tableNames":["products"]},
                    {"method":"POST","endpoint":"","description":"주소가 빠진 API",
                     "request":"{}","response":"{}","auth":true,
                     "requirementIds":["%s"],"screenIds":["%s"],"tableNames":["orders"]},
                    {"method":"POST","endpoint":"/api/orders","description":"주문한다",
                     "request":"{\\"productId\\":1}","response":"{\\"id\\":1}","auth":true,
                     "requirementIds":["%s"],"screenIds":["%s"],"tableNames":["orders","reviews"]},
                    {"method":"POST","endpoint":"/api/orders","description":"똑같은 주소를 또 냈다",
                     "request":"{}","response":"{}","auth":true,
                     "requirementIds":["%s"],"screenIds":["%s"],"tableNames":["orders"]}
                  ]
                }
                """.formatted(reqList, scrList, reqList, scrDetail, reqOrder, scrDetail,
                reqOrder, scrDetail, reqOrder, scrDetail);
    }
}
