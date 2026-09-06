package com.myide.backend.service.design.doctor;

/**
 * 설계에서 찾아낸 문제 하나.
 *
 * targetKind 와 targetId 가 있어야 화면이 그 항목으로 데려다줄 수 있다.
 * 닥터의 가치는 "여기가 문제다"에서 끝나지 않고 그 자리로 안내하는 데 있다.
 *
 * fix 는 기계적으로 고칠 수 있는 문제에만 담긴다. 화면은 이것이 있는 항목에만
 * "고치기" 버튼을 띄우고, 담긴 대로 적용한다.
 */
public record Finding(
        String ruleId,
        Severity severity,
        String targetKind,
        String targetId,
        String targetLabel,
        String message,
        String fixHint,
        Fix fix
) {
    public static Finding of(String ruleId, Severity severity, String targetKind,
                             String targetId, String targetLabel, String message) {
        return new Finding(ruleId, severity, targetKind, targetId, targetLabel, message, "", null);
    }

    public static Finding of(String ruleId, Severity severity, String targetKind,
                             String targetId, String targetLabel, String message, String fixHint) {
        return new Finding(ruleId, severity, targetKind, targetId, targetLabel, message,
                fixHint, null);
    }

    /** 같은 내용에 수정 방법만 붙인다. 규칙이 읽기 편하도록 체이닝으로 쓴다. */
    public Finding withFix(Fix value) {
        return new Finding(ruleId, severity, targetKind, targetId, targetLabel, message,
                fixHint, value);
    }
}
