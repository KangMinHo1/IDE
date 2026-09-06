package com.myide.backend.dto.design.v2;

/** 다이어그램 좌표. 이름 편집과 드래그가 충돌하지 않도록 항상 별도 필드로 둔다. */
public record PointV2(double x, double y) {

    public static PointV2 origin() {
        return new PointV2(0, 0);
    }
}
