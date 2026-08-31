package com.myide.backend.service.design.doctor;

/**
 * 설계에서 찾아낸 문제 하나.
 *
 * targetKind 와 targetId 가 있어야 화면이 그 항목으로 데려다줄 수 있다.
 * 닥터의 가치는 "여기가 문제다"에서 끝나지 않고 그 자리로 안내하는 데 있다.
 */
public record Finding(
        String ruleId,
        Severity severity,
        String targetKind,
        String targetId,
        String targetLabel,
        String message,
        String fixHint
) {
    public static Finding of(String ruleId, Severity severity, String targetKind,
                             String targetId, String targetLabel, String message) {
        return new Finding(ruleId, severity, targetKind, targetId, targetLabel, message, "");
    }

    public static Finding of(String ruleId, Severity severity, String targetKind,
                             String targetId, String targetLabel, String message, String fixHint) {
        return new Finding(ruleId, severity, targetKind, targetId, targetLabel, message, fixHint);
    }
}
