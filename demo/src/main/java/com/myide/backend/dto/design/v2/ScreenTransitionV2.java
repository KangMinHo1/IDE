package com.myide.backend.dto.design.v2;

import java.util.List;

public record ScreenTransitionV2(
        String id,
        String from,
        String to,
        String trigger,
        String kind,
        String condition,
        List<String> apiIds
) {
    public ScreenTransitionV2 {
        from = from == null ? "" : from;
        to = to == null ? "" : to;
        trigger = trigger == null ? "" : trigger;
        kind = kind == null || kind.isBlank() ? "navigate" : kind;
        condition = condition == null ? "" : condition;
        apiIds = apiIds == null ? List.of() : List.copyOf(apiIds);
    }
}
