package com.myide.backend.dto.design.v2;

/**
 * 폐기된 데이터 플로우 탭의 원본 JSON을 그대로 보관한다.
 *
 * 변환하지 않는 이유: 화면으로 승격되는 것은 client 노드뿐이고
 * server/db/external 노드와 그 사이 연결선은 새 모델에 담을 자리가 없다.
 * 원본을 들고 있어야 자료실 역투영이 예전 다이어그램을 근사치가 아니라
 * 정확히 재현한다.
 */
public record LegacyFlowSnapshotV2(String nodesJson, String edgesJson) {

    public LegacyFlowSnapshotV2 {
        nodesJson = normalize(nodesJson);
        edgesJson = normalize(edgesJson);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }

    public static LegacyFlowSnapshotV2 empty() {
        return new LegacyFlowSnapshotV2("[]", "[]");
    }
}
