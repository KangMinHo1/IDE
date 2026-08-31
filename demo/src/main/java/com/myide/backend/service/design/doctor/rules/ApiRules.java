package com.myide.backend.service.design.doctor.rules;

import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.service.design.doctor.DesignIndex;
import com.myide.backend.service.design.doctor.DesignRule;
import com.myide.backend.service.design.doctor.Finding;
import com.myide.backend.service.design.doctor.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiRules implements DesignRule {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH");

    @Override
    public List<Finding> check(DesignModelV2 model, DesignIndex index) {
        List<Finding> findings = new ArrayList<>();
        Map<String, Integer> endpointCount = new HashMap<>();

        for (ApiSpecV2 api : model.apis()) {
            String key = api.method() + " " + api.endpoint().trim().toLowerCase(Locale.ROOT);
            endpointCount.merge(key, 1, Integer::sum);
        }

        for (ApiSpecV2 api : model.apis()) {
            String label = api.method() + " " + api.endpoint();

            String key = api.method() + " " + api.endpoint().trim().toLowerCase(Locale.ROOT);
            if (endpointCount.getOrDefault(key, 0) > 1) {
                findings.add(Finding.of("API_DUP_ENDPOINT", Severity.ERROR,
                        "api", api.id(), label,
                        "같은 메서드와 경로를 쓰는 API가 또 있습니다.",
                        "코드 생성 시 같은 자리를 두 번 만들게 되어 컴파일되지 않습니다."));
            }

            if (api.endpoint().isBlank()) {
                findings.add(Finding.of("API_NO_ENDPOINT", Severity.ERROR,
                        "api", api.id(), label,
                        "경로가 비어 있습니다."));
            }

            if (api.requirementIds().isEmpty()) {
                findings.add(Finding.of("API_NO_REQ", Severity.WARNING,
                        "api", api.id(), label,
                        "이 API가 어떤 요구사항 때문에 필요한지 연결되어 있지 않습니다.",
                        "근거 없는 API는 만들다 만 것이거나 필요 없는 것일 수 있습니다."));
            }

            if (!index.isApiUsedByScreen(api.id())) {
                findings.add(Finding.of("API_ORPHAN_SCREEN", Severity.INFO,
                        "api", api.id(), label,
                        "이 API를 호출하는 화면이 없습니다.",
                        "화면 없이 쓰이는 API라면 그대로 두어도 됩니다."));
            }

            if (api.tableIds().isEmpty()) {
                findings.add(Finding.of("API_NO_TABLE", Severity.WARNING,
                        "api", api.id(), label,
                        "이 API가 어떤 테이블을 다루는지 연결되어 있지 않습니다.",
                        "연결해 두면 코드 생성 시 본문 뼈대까지 만들어집니다."));
            }

            for (String tableId : api.tableIds()) {
                if (!index.hasTable(tableId)) {
                    findings.add(Finding.of("API_ORPHAN_LINK", Severity.ERROR,
                            "api", api.id(), label,
                            "존재하지 않는 테이블을 참조하고 있습니다.",
                            "연결을 지웠다가 다시 걸어 주세요."));
                }
            }

            if (WRITE_METHODS.contains(api.method()) && api.request().isBlank()) {
                findings.add(Finding.of("API_WRITE_NO_BODY", Severity.WARNING,
                        "api", api.id(), label,
                        api.method() + " 인데 보낼 내용이 적혀 있지 않습니다.",
                        "요청 본문 예시를 적어 두면 프론트와 백엔드가 어긋나지 않습니다."));
            }

            if (api.response().isBlank()) {
                findings.add(Finding.of("API_NO_RESPONSE", Severity.INFO,
                        "api", api.id(), label,
                        "응답 예시가 비어 있습니다."));
            }

            checkPathParams(api, index, label, findings);
            checkConvention(api, label, findings);
        }

        return findings;
    }

    /** 경로에 쓴 변수가 연결된 테이블의 컬럼에 실제로 있는지 본다. */
    private void checkPathParams(ApiSpecV2 api, DesignIndex index, String label,
                                 List<Finding> findings) {
        Matcher matcher = PATH_PARAM.matcher(api.endpoint());
        Set<String> columnNames = new HashSet<>();

        api.tableIds().forEach(tableId -> {
            var table = index.table(tableId);
            if (table == null) {
                return;
            }
            table.columns().stream()
                    .map(ColumnV2::name)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .forEach(columnNames::add);
        });

        while (matcher.find()) {
            String param = matcher.group(1).toLowerCase(Locale.ROOT);

            // id 는 어느 테이블에나 있는 이름이라 굳이 트집 잡지 않는다.
            if (param.equals("id") || columnNames.isEmpty()) {
                continue;
            }

            if (!columnNames.contains(param)) {
                findings.add(Finding.of("API_PATH_PARAM_UNKNOWN", Severity.WARNING,
                        "api", api.id(), label,
                        "경로 변수 " + matcher.group(1) + " 와 이름이 같은 컬럼이 연결된 테이블에 없습니다."));
            }
        }
    }

    /** 학생 팀이 주 사용자라 관례도 배울 거리로 알려 준다. 막지는 않는다. */
    private void checkConvention(ApiSpecV2 api, String label, List<Finding> findings) {
        String endpoint = api.endpoint();

        if (!endpoint.isBlank() && !endpoint.startsWith("/api")) {
            findings.add(Finding.of("API_PATH_CONVENTION", Severity.INFO,
                    "api", api.id(), label,
                    "경로가 /api 로 시작하지 않습니다.",
                    "프로젝트 안에서 규칙을 통일해 두면 나중에 찾기 쉽습니다."));
        }

        if (endpoint.matches(".*[A-Z_].*")) {
            findings.add(Finding.of("API_PATH_CONVENTION", Severity.INFO,
                    "api", api.id(), label,
                    "경로에 대문자나 밑줄이 있습니다.",
                    "소문자와 하이픈으로 쓰는 것이 일반적입니다."));
        }

        if (endpoint.matches(".*/(get|create|update|delete|remove)[A-Za-z]*(/.*)?$")) {
            findings.add(Finding.of("API_PATH_CONVENTION", Severity.INFO,
                    "api", api.id(), label,
                    "경로에 동사가 들어 있습니다.",
                    "무엇을 하는지는 메서드로 나타내고 경로에는 대상만 적습니다."));
        }
    }
}
