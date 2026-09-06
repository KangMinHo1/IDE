package com.myide.backend.service.design.doctor.rules;

import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.service.design.doctor.DesignIndex;
import com.myide.backend.service.design.doctor.DesignRule;
import com.myide.backend.service.design.doctor.Finding;
import com.myide.backend.service.design.doctor.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 축을 가로지르는 검사.
 *
 * 이 기능이 종이나 노션보다 나은 유일한 이유가 여기 있다. 요구사항 하나가
 * 화면을 거쳐 API를 지나 테이블까지 이어지는지는 사람이 눈으로 좇기 어렵지만,
 * 연결이 데이터로 남아 있으면 기계가 한 번에 짚어 준다.
 */
@Component
public class TraceRules implements DesignRule {

    @Override
    public List<Finding> check(DesignModelV2 model, DesignIndex index) {
        List<Finding> findings = new ArrayList<>();

        Map<String, ApiSpecV2> apiById = model.apis().stream()
                .collect(Collectors.toMap(ApiSpecV2::id, Function.identity(), (a, b) -> a));

        checkRequirementChains(model, apiById, findings);
        checkScreenApiRelevance(model, apiById, findings);

        return findings;
    }

    /**
     * 요구사항 → 화면 → API → 테이블 사슬이 중간에 끊겼는지.
     *
     * 아무것도 연결되지 않은 요구사항은 다른 규칙이 이미 짚으므로 건너뛴다.
     * 여기서는 절반쯤 이어 놓고 멈춘 경우만 알린다. 그런 것이 가장 놓치기 쉽다.
     */
    private void checkRequirementChains(DesignModelV2 model,
                                        Map<String, ApiSpecV2> apiById,
                                        List<Finding> findings) {
        for (RequirementV2 requirement : model.requirements()) {
            if (requirement.screenIds().isEmpty() && requirement.apiIds().isEmpty()) {
                continue;
            }

            String label = requirement.name().isBlank() ? requirement.code() : requirement.name();

            if (!requirement.apiIds().isEmpty()) {
                boolean touchesTable = requirement.apiIds().stream()
                        .map(apiById::get)
                        .filter(api -> api != null)
                        .anyMatch(api -> !api.tableIds().isEmpty());

                if (!touchesTable) {
                    findings.add(Finding.of("TRACE_NO_STORAGE", Severity.WARNING,
                            "requirement", requirement.id(), label,
                            "요구사항에서 API까지는 이어졌는데, 그 API가 다루는 테이블이 없습니다.",
                            "저장하거나 읽을 데이터가 정말 없는 기능인지 확인해 주세요."));
                }
            }

            if (requirement.screenIds().isEmpty() && !requirement.apiIds().isEmpty()) {
                findings.add(Finding.of("TRACE_NO_SCREEN_PATH", Severity.INFO,
                        "requirement", requirement.id(), label,
                        "API는 정해졌는데 사용자가 이 기능을 어디서 쓰는지가 비어 있습니다."));
            }
        }
    }

    /**
     * 화면이 부르는 API가 그 화면의 요구사항과 아무 관련이 없는 경우.
     *
     * 연결을 잘못 건 흔한 실수라, 알려 주면 대개 바로 고친다.
     */
    private void checkScreenApiRelevance(DesignModelV2 model,
                                         Map<String, ApiSpecV2> apiById,
                                         List<Finding> findings) {
        for (ScreenV2 screen : model.screens()) {
            if (screen.requirementIds().isEmpty() || screen.apiIds().isEmpty()) {
                continue;
            }

            Set<String> screenRequirements = new HashSet<>(screen.requirementIds());
            String label = screen.name().isBlank() ? screen.key() : screen.name();

            for (String apiId : screen.apiIds()) {
                ApiSpecV2 api = apiById.get(apiId);
                if (api == null || api.requirementIds().isEmpty()) {
                    continue;
                }

                boolean shares = api.requirementIds().stream().anyMatch(screenRequirements::contains);

                if (!shares) {
                    findings.add(Finding.of("TRACE_SCREEN_API_MISMATCH", Severity.INFO,
                            "screen", screen.id(), label,
                            api.method() + " " + api.endpoint()
                                    + " 는 이 화면의 요구사항과 무관한 요구사항에만 걸려 있습니다.",
                            "연결을 잘못 걸었는지 확인해 주세요."));
                }
            }
        }
    }
}
