package com.myide.backend.service.design.doctor.rules;

import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.service.design.doctor.DesignIndex;
import com.myide.backend.service.design.doctor.DesignRule;
import com.myide.backend.service.design.doctor.Finding;
import com.myide.backend.service.design.doctor.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RequirementRules implements DesignRule {

    private static final int SHORT_DESCRIPTION = 10;

    @Override
    public List<Finding> check(DesignModelV2 model, DesignIndex index) {
        List<Finding> findings = new ArrayList<>();
        Map<String, Integer> nameCount = new HashMap<>();

        for (RequirementV2 requirement : model.requirements()) {
            String key = requirement.name().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                nameCount.merge(key, 1, Integer::sum);
            }
        }

        for (RequirementV2 requirement : model.requirements()) {
            String label = requirement.name().isBlank() ? requirement.code() : requirement.name();

            if (requirement.name().isBlank()) {
                findings.add(Finding.of("REQ_EMPTY_NAME", Severity.WARNING,
                        "requirement", requirement.id(), label,
                        "기능명이 비어 있습니다.",
                        "무엇을 할 수 있어야 하는지 한 줄로 적어 주세요."));
            }

            if (requirement.screenIds().isEmpty()) {
                findings.add(Finding.of("REQ_NO_SCREEN", Severity.WARNING,
                        "requirement", requirement.id(), label,
                        "이 요구사항이 어느 화면에서 이루어지는지 연결되어 있지 않습니다.",
                        "화면 흐름 탭에서 해당 화면을 만든 뒤 연결해 주세요."));
            }

            if (requirement.apiIds().isEmpty()) {
                findings.add(Finding.of("REQ_NO_API", Severity.WARNING,
                        "requirement", requirement.id(), label,
                        "이 요구사항을 담당하는 API가 없습니다.",
                        "이 기능이 서버와 주고받을 것이 있다면 API를 만들어 연결해 주세요."));
            }

            if (requirement.description().trim().length() < SHORT_DESCRIPTION) {
                findings.add(Finding.of("REQ_EMPTY_DESC", Severity.INFO,
                        "requirement", requirement.id(), label,
                        "설명이 너무 짧습니다.",
                        "팀원이 읽고 같은 그림을 그릴 수 있을 만큼 적어 주세요."));
            }

            String key = requirement.name().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && nameCount.getOrDefault(key, 0) > 1) {
                findings.add(Finding.of("REQ_DUP_NAME", Severity.WARNING,
                        "requirement", requirement.id(), label,
                        "같은 이름의 요구사항이 두 개 이상 있습니다."));
            }

            for (String screenId : requirement.screenIds()) {
                if (!index.hasScreen(screenId)) {
                    findings.add(Finding.of("REQ_ORPHAN_LINK", Severity.ERROR,
                            "requirement", requirement.id(), label,
                            "존재하지 않는 화면을 참조하고 있습니다.",
                            "연결을 지웠다가 다시 걸어 주세요."));
                }
            }

            for (String apiId : requirement.apiIds()) {
                if (!index.hasApi(apiId)) {
                    findings.add(Finding.of("REQ_ORPHAN_LINK", Severity.ERROR,
                            "requirement", requirement.id(), label,
                            "존재하지 않는 API를 참조하고 있습니다.",
                            "연결을 지웠다가 다시 걸어 주세요."));
                }
            }
        }

        return findings;
    }
}
