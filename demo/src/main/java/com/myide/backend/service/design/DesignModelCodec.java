package com.myide.backend.service.design;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.LegacyProjectionV2;
import com.myide.backend.dto.design.v2.RelationV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.TableV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 설계 문서의 평문 사본을 읽고 쓰는 유일한 통로.
 *
 * 서버는 Yjs 바이너리를 해석하지 못하므로, 클라이언트가 스냅샷과 함께
 * 보내는 평문 사본을 이 코덱으로 읽어 AI 초안과 설계 닥터, 코드 생성,
 * 최종 보고서에 넘긴다.
 *
 * 역투영은 예전 화면들을 살려 두기 위한 것이다. 자세한 배경은
 * LegacyProjectionV2 주석 참고.
 */
@Slf4j
@Component
public class DesignModelCodec {

    private final ObjectMapper objectMapper;
    private final ObjectReader lenientReader;

    public DesignModelCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 구버전 클라이언트나 AI 응답이 모르는 필드를 섞어 보내도
        // 문서 전체를 버리지 않도록 관대하게 읽는다.
        this.lenientReader = objectMapper
                .reader()
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .forType(DesignModelV2.class);
    }

    public DesignModelV2 fromJson(String projectionJson) {
        if (projectionJson == null || projectionJson.isBlank()) {
            return DesignModelV2.empty();
        }

        try {
            DesignModelV2 model = lenientReader.readValue(projectionJson);
            return model == null ? DesignModelV2.empty() : model;
        } catch (Exception e) {
            log.warn("⚠️ 설계 문서 평문 사본을 해석하지 못했습니다: {}", e.getMessage());
            throw new IllegalArgumentException("설계 문서 형식이 올바르지 않습니다.", e);
        }
    }

    public String toJson(DesignModelV2 model) {
        try {
            return objectMapper.writeValueAsString(model == null ? DesignModelV2.empty() : model);
        } catch (Exception e) {
            throw new IllegalStateException("설계 문서를 직렬화하지 못했습니다.", e);
        }
    }

    /**
     * v2 문서를 예전 형식으로 되돌린다.
     * 프론트의 modelToLegacy 와 결과가 같아야 하므로 한쪽만 고치면 안 된다.
     */
    public LegacyProjectionV2 toLegacy(DesignModelV2 model) {
        DesignModelV2 safe = model == null ? DesignModelV2.empty() : model;

        List<LegacyProjectionV2.RequirementRow> requirements = new ArrayList<>();
        for (RequirementV2 requirement : safe.requirements()) {
            requirements.add(new LegacyProjectionV2.RequirementRow(
                    requirement.id(),
                    requirement.category(),
                    requirement.name(),
                    requirement.description()
            ));
        }

        List<LegacyProjectionV2.ApiSpecRow> apiSpecs = new ArrayList<>();
        for (ApiSpecV2 api : safe.apis()) {
            apiSpecs.add(new LegacyProjectionV2.ApiSpecRow(
                    api.id(),
                    api.method(),
                    api.endpoint(),
                    api.description(),
                    api.request(),
                    api.response()
            ));
        }

        return new LegacyProjectionV2(
                requirements,
                apiSpecs,
                writeJson(buildErdNodes(safe)),
                writeJson(buildErdEdges(safe)),
                safe.meta().legacyFlow().nodesJson(),
                safe.meta().legacyFlow().edgesJson()
        );
    }

    private List<Map<String, Object>> buildErdNodes(DesignModelV2 model) {
        List<Map<String, Object>> nodes = new ArrayList<>();

        for (TableV2 table : model.erd().tables()) {
            List<Map<String, Object>> columns = new ArrayList<>();
            table.columns().forEach(column -> {
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapped.put("id", column.id());
                mapped.put("name", column.name());
                mapped.put("type", column.type());
                mapped.put("isPk", column.isPk());
                mapped.put("isFk", column.isFk());
                columns.add(mapped);
            });

            Map<String, Object> position = new LinkedHashMap<>();
            position.put("x", table.layout().x());
            position.put("y", table.layout().y());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", table.name());
            data.put("columns", columns);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", table.id());
            node.put("type", "tableNode");
            node.put("position", position);
            node.put("data", data);

            nodes.add(node);
        }

        return nodes;
    }

    private List<Map<String, Object>> buildErdEdges(DesignModelV2 model) {
        List<Map<String, Object>> edges = new ArrayList<>();

        for (RelationV2 relation : model.erd().relations()) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", relation.id());
            edge.put("source", relation.fromTableId());
            edge.put("target", relation.toTableId());
            edge.put("type", "smoothstep");
            edge.put("label", relation.note());
            edges.add(edge);
        }

        return edges;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("설계 다이어그램을 직렬화하지 못했습니다.", e);
        }
    }
}
