package com.myide.backend.service.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.LegacyProjectionV2;
import com.myide.backend.dto.design.v2.RelationV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.ScreenTransitionV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.dto.design.v2.TableV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프론트(src/features/design/model)와 백엔드 v2 계약이 실제로 맞물리는지 확인한다.
 *
 * 고정 데이터는 프론트 코드가 직접 만들어 낸 것이며, 모든 필드가 기본값이
 * 아닌 값으로 채워져 있다. 한쪽에서 필드명을 바꾸면 그 값이 기본값으로
 * 떨어져 여기서 바로 실패한다. 설계 닥터와 코드 생성이 통째로 무너지는
 * 사고를 이 테스트가 앞에서 막는다.
 *
 * 스프링 컨텍스트를 띄우지 않는다. 코덱은 ObjectMapper 하나만 필요하고,
 * DB나 Gemini 키가 없는 환경에서도 계약 검증은 돌아가야 하기 때문이다.
 */
class DesignModelCodecContractTest {

    private ObjectMapper objectMapper;
    private DesignModelCodec codec;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        codec = new DesignModelCodec(objectMapper);
    }

    private String readResource(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("고정 데이터 %s 를 찾지 못했습니다", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("프론트가 만든 문서를 백엔드가 필드 손실 없이 읽는다")
    void deserializesFrontendProjection() throws Exception {
        DesignModelV2 model = codec.fromJson(readResource("design/projection.json"));

        assertThat(model.schemaVersion()).isEqualTo(2);
        assertThat(model.meta().projectSummary()).isEqualTo("중고 물품 거래 웹");
        assertThat(model.meta().techStack().backend()).isEqualTo("Spring Boot");
        assertThat(model.meta().techStack().frontend()).isEqualTo("React");
        assertThat(model.meta().techStack().db()).isEqualTo("MySQL");
        assertThat(model.meta().legacyFlow().nodesJson()).contains("옛 노드");
        assertThat(model.meta().legacyFlow().edgesJson()).contains("옛 연결");

        RequirementV2 requirement = model.requirements().get(0);
        assertThat(requirement.id()).isEqualTo("req_aaaaaaaa");
        assertThat(requirement.code()).isEqualTo("R-01");
        assertThat(requirement.category()).isEqualTo("회원");
        assertThat(requirement.name()).isEqualTo("로그인");
        assertThat(requirement.description()).isEqualTo("이메일과 비밀번호로 로그인한다");
        assertThat(requirement.priority()).isEqualTo("must");
        assertThat(requirement.screenIds()).containsExactly("scr_bbbbbbbb");
        assertThat(requirement.apiIds()).containsExactly("api_cccccccc");

        ScreenV2 loginScreen = model.screens().get(0);
        assertThat(loginScreen.id()).isEqualTo("scr_bbbbbbbb");
        assertThat(loginScreen.key()).isEqualTo("/login");
        assertThat(loginScreen.name()).isEqualTo("로그인 화면");
        assertThat(loginScreen.description()).isEqualTo("이메일 입력 폼");
        assertThat(loginScreen.role()).isEqualTo("modal");
        assertThat(loginScreen.isEntry()).isTrue();
        assertThat(loginScreen.requiresAuth()).isFalse();
        assertThat(loginScreen.requirementIds()).containsExactly("req_aaaaaaaa");
        assertThat(loginScreen.apiIds()).containsExactly("api_cccccccc");
        assertThat(loginScreen.layout().x()).isEqualTo(120.5);
        assertThat(loginScreen.layout().y()).isEqualTo(240);

        assertThat(model.screens().get(1).requiresAuth()).isTrue();
        assertThat(model.screens().get(1).isEntry()).isFalse();

        ScreenTransitionV2 transition = model.screenTransitions().get(0);
        assertThat(transition.id()).isEqualTo("trn_eeeeeeee");
        assertThat(transition.from()).isEqualTo("scr_bbbbbbbb");
        assertThat(transition.to()).isEqualTo("scr_dddddddd");
        assertThat(transition.trigger()).isEqualTo("로그인 버튼 클릭");
        assertThat(transition.kind()).isEqualTo("submit");
        assertThat(transition.condition()).isEqualTo("성공 시");
        assertThat(transition.apiIds()).containsExactly("api_cccccccc");

        ApiSpecV2 api = model.apis().get(0);
        assertThat(api.id()).isEqualTo("api_cccccccc");
        assertThat(api.method()).isEqualTo("POST");
        assertThat(api.endpoint()).isEqualTo("/api/auth/login");
        assertThat(api.description()).isEqualTo("로그인");
        assertThat(api.request()).contains("email");
        assertThat(api.response()).contains("accessToken");
        assertThat(api.auth()).isFalse();
        assertThat(api.crud()).isEqualTo("R");
        assertThat(api.requirementIds()).containsExactly("req_aaaaaaaa");
        assertThat(api.screenIds()).containsExactly("scr_bbbbbbbb");
        assertThat(api.tableIds()).containsExactly("tbl_ffffffff");

        TableV2 users = model.erd().tables().get(0);
        assertThat(users.id()).isEqualTo("tbl_ffffffff");
        assertThat(users.name()).isEqualTo("users");
        assertThat(users.entityName()).isEqualTo("User");
        assertThat(users.description()).isEqualTo("회원");
        assertThat(users.layout().x()).isEqualTo(60);

        ColumnV2 idColumn = users.columns().get(0);
        assertThat(idColumn.id()).isEqualTo("col_11111111");
        assertThat(idColumn.name()).isEqualTo("id");
        assertThat(idColumn.type()).isEqualTo("BIGINT");
        assertThat(idColumn.length()).isNull();
        assertThat(idColumn.nullable()).isFalse();
        assertThat(idColumn.isPk()).isTrue();
        assertThat(idColumn.isFk()).isFalse();
        assertThat(idColumn.comment()).isEqualTo("PK");

        ColumnV2 emailColumn = users.columns().get(1);
        assertThat(emailColumn.length()).isEqualTo(255);
        assertThat(emailColumn.defaultValue()).isEqualTo("''");
        assertThat(emailColumn.comment()).isEqualTo("로그인 아이디");

        assertThat(model.erd().tables().get(1).columns().get(1).isFk()).isTrue();

        RelationV2 relation = model.erd().relations().get(0);
        assertThat(relation.id()).isEqualTo("rel_88888888");
        assertThat(relation.fromTableId()).isEqualTo("tbl_99999999");
        assertThat(relation.fromColumnId()).isEqualTo("col_44444444");
        assertThat(relation.toTableId()).isEqualTo("tbl_ffffffff");
        assertThat(relation.toColumnId()).isEqualTo("col_11111111");
        assertThat(relation.cardinality()).isEqualTo("1:N");
        assertThat(relation.onDelete()).isEqualTo("CASCADE");
        assertThat(relation.note()).isEqualTo("회원이 상품을 소유한다");
    }

    @Test
    @DisplayName("역투영 결과가 프론트의 역투영과 같다")
    void producesSameLegacyProjectionAsFrontend() throws Exception {
        DesignModelV2 model = codec.fromJson(readResource("design/projection.json"));
        LegacyProjectionV2 actual = codec.toLegacy(model);

        JsonNode expected = objectMapper.readTree(readResource("design/expected-legacy.json"));

        JsonNode actualRequirements = objectMapper.valueToTree(actual.requirements());
        JsonNode actualApiSpecs = objectMapper.valueToTree(actual.apiSpecs());

        assertThat(actualRequirements).isEqualTo(expected.get("requirements"));
        assertThat(actualApiSpecs).isEqualTo(expected.get("apiSpecs"));

        // 다이어그램은 숫자 표기(100 과 100.0)가 언어마다 달라 값으로 비교한다.
        assertThat(numericSafe(objectMapper.readTree(actual.erdNodesJson())))
                .isEqualTo(numericSafe(objectMapper.readTree(expected.get("erdNodesJson").asText())));
        assertThat(numericSafe(objectMapper.readTree(actual.erdEdgesJson())))
                .isEqualTo(numericSafe(objectMapper.readTree(expected.get("erdEdgesJson").asText())));

        // 화면이 있으면 화면 흐름을 예전 형식으로 내보낸다.
        // 자료실이 사용자가 지금 관리하는 흐름을 보여주게 하기 위한 것이다.
        assertThat(numericSafe(objectMapper.readTree(actual.flowNodesJson())))
                .isEqualTo(numericSafe(objectMapper.readTree(expected.get("flowNodesJson").asText())));
        assertThat(numericSafe(objectMapper.readTree(actual.flowEdgesJson())))
                .isEqualTo(numericSafe(objectMapper.readTree(expected.get("flowEdgesJson").asText())));
    }

    @Test
    @DisplayName("빈 문서와 모르는 필드를 안전하게 다룬다")
    void handlesEmptyAndUnknownInput() {
        DesignModelV2 empty = codec.fromJson("");
        assertThat(empty.requirements()).isEmpty();
        assertThat(empty.erd().tables()).isEmpty();

        // 화면이 하나도 없으면 보관해 둔 예전 데이터 플로우를 그대로 돌려준다.
        // 아직 새 탭을 써 보지 않은 워크스페이스에서 자료실이 비어 보이면 안 된다.
        assertThat(codec.toLegacy(empty).flowNodesJson()).isEqualTo("[]");

        // 모르는 필드가 섞여 와도 문서 전체를 버리지 않는다.
        DesignModelV2 withUnknown = codec.fromJson(
                "{\"schemaVersion\":2,\"unknownField\":123,\"requirements\":[]}");
        assertThat(withUnknown.schemaVersion()).isEqualTo(2);

        assertThat(codec.toLegacy(null).erdNodesJson()).isEqualTo("[]");
    }

    /** 정수와 실수 표기 차이를 없애기 위해 모든 숫자를 double 로 통일한다. */
    private JsonNode numericSafe(JsonNode node) {
        if (node.isNumber()) {
            return new DoubleNode(node.asDouble());
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            node.forEach(child -> copy.add(numericSafe(child)));
            return copy;
        }
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry ->
                    copy.set(entry.getKey(), numericSafe(entry.getValue())));
            return copy;
        }
        return node;
    }
}
