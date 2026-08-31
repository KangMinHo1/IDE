package com.myide.backend.dto.design.codegen;

import java.util.List;

public record CodegenApplyResponse(
        List<CodegenApplyResult> results,
        int written,
        int skipped,
        int failed
) {
    public CodegenApplyResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
