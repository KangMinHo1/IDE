package com.myide.backend.service.design.doctor.rules;

import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.ScreenTransitionV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.service.design.doctor.DesignIndex;
import com.myide.backend.service.design.doctor.DesignRule;
import com.myide.backend.service.design.DesignRepairs;
import com.myide.backend.service.design.doctor.Finding;
import com.myide.backend.service.design.doctor.Fix;
import com.myide.backend.service.design.doctor.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ScreenRules implements DesignRule {

    /** 라우트로 쓸 수 있는 모양. 코드 생성이 이 값을 파일 경로로 쓴다. */
    private static final Pattern ROUTE = Pattern.compile("^/[A-Za-z0-9\\-_/:\\[\\]]*$");

    @Override
    public List<Finding> check(DesignModelV2 model, DesignIndex index) {
        List<Finding> findings = new ArrayList<>();

        if (!model.screens().isEmpty()
                && model.screens().stream().noneMatch(ScreenV2::isEntry)) {
            findings.add(Finding.of("SCR_NO_ENTRY", Severity.ERROR,
                    "screen", "", "화면 흐름",
                    "시작 화면이 지정되어 있지 않습니다.",
                    "사용자가 처음 보게 될 화면 하나를 시작 화면으로 표시해 주세요."));
        }

        Map<String, Integer> routeCount = new HashMap<>();
        for (ScreenV2 screen : model.screens()) {
            String key = screen.key().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                routeCount.merge(key, 1, Integer::sum);
            }
        }

        boolean hasLoginLike = model.screens().stream()
                .anyMatch(screen -> screen.name().contains("로그인")
                        || screen.key().toLowerCase(Locale.ROOT).contains("login"))
                || model.apis().stream()
                .anyMatch(api -> api.endpoint().toLowerCase(Locale.ROOT).contains("login"));

        // 고칠 경로를 정할 때 이미 쓰이는 것과 겹치면 안 된다. 먼저 나온
        // 화면이 그 경로를 가져가고, 뒤엣것에만 다른 경로를 제안한다.
        Set<String> claimedRoutes = new HashSet<>();

        for (ScreenV2 screen : model.screens()) {
            String label = screen.name().isBlank() ? screen.key() : screen.name();

            if (screen.key().isBlank()) {
                // 오류가 아니라 경고로 둔다. 화면을 막 만든 직후에는 비어 있는 것이
                // 자연스러운데, 그때마다 붉은 오류가 뜨면 사용자를 재촉하게 된다.
                findings.add(Finding.of("SCR_NO_ROUTE", Severity.WARNING,
                        "screen", screen.id(), label,
                        "라우트 경로가 비어 있습니다.",
                        "코드 생성 대상이 되려면 /login 처럼 경로를 정해야 합니다."));
            } else if (!ROUTE.matcher(screen.key()).matches()) {
                findings.add(Finding.of("SCR_INVALID_ROUTE", Severity.ERROR,
                        "screen", screen.id(), label,
                        "라우트 경로 형식이 올바르지 않습니다: " + screen.key(),
                        "슬래시로 시작하고 공백이나 한글이 없어야 합니다."
                ).withFix(Fix.setScreenRoute(screen.id(),
                        DesignRepairs.uniqueRoute(screen.key(), label, claimedRoutes))));
            }

            String routeKey = screen.key().trim().toLowerCase(Locale.ROOT);
            if (!routeKey.isEmpty() && routeCount.getOrDefault(routeKey, 0) > 1) {
                Finding dup = Finding.of("SCR_DUP_ROUTE", Severity.ERROR,
                        "screen", screen.id(), label,
                        "같은 라우트 경로를 쓰는 화면이 또 있습니다: " + screen.key());

                // 먼저 나온 화면은 그대로 두고 뒤엣것만 옮긴다. 둘 다 옮기면
                // 사용자가 기억하던 경로가 아무 이유 없이 사라진다.
                findings.add(claimedRoutes.add(routeKey)
                        ? dup
                        : dup.withFix(Fix.setScreenRoute(screen.id(),
                                DesignRepairs.uniqueRoute(screen.key(), label, claimedRoutes))));
            } else if (!routeKey.isEmpty()) {
                claimedRoutes.add(routeKey);
            }

            if (!screen.isEntry() && !index.isScreenReachable(screen.id())) {
                findings.add(Finding.of("SCR_UNREACHABLE", Severity.WARNING,
                        "screen", screen.id(), label,
                        "시작 화면에서 이 화면으로 갈 수 있는 경로가 없습니다.",
                        "다른 화면에서 이 화면으로 오는 화살표를 그어 주세요."));
            }

            if (!index.hasOutgoing(screen.id())) {
                findings.add(Finding.of("SCR_DEAD_END", Severity.INFO,
                        "screen", screen.id(), label,
                        "이 화면에서 나가는 흐름이 없습니다.",
                        "마지막 화면이 맞다면 그대로 두어도 됩니다."));
            }

            if (screen.requirementIds().isEmpty()) {
                findings.add(Finding.of("SCR_NO_REQ", Severity.WARNING,
                        "screen", screen.id(), label,
                        "이 화면이 어떤 요구사항 때문에 필요한지 연결되어 있지 않습니다.",
                        "근거가 없는 화면은 만들다 만 화면일 수 있습니다."));
            }

            if (screen.requiresAuth() && !hasLoginLike) {
                findings.add(Finding.of("SCR_AUTH_NO_LOGIN", Severity.WARNING,
                        "screen", screen.id(), label,
                        "로그인이 필요한 화면인데 로그인 화면이나 로그인 API가 없습니다."));
            }
        }

        for (ScreenTransitionV2 transition : model.screenTransitions()) {
            if (!index.isTransitionValid(transition)) {
                findings.add(Finding.of("TRN_DANGLING", Severity.ERROR,
                        "transition", transition.id(), "화면 이동",
                        "존재하지 않는 화면을 잇는 흐름이 있습니다.",
                        "이 화살표를 지워 주세요."
                ).withFix(Fix.deleteTransition(transition.id())));
                continue;
            }

            if (transition.trigger().isBlank()) {
                findings.add(Finding.of("TRN_NO_TRIGGER", Severity.INFO,
                        "transition", transition.id(), "화면 이동",
                        "무엇을 했을 때 이동하는지 적혀 있지 않습니다.",
                        "발표 자료로 쓰려면 사용자의 행동을 적어 두는 편이 좋습니다."));
            }
        }

        return findings;
    }
}
