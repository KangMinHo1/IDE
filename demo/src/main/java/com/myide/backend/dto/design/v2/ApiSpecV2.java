package com.myide.backend.dto.design.v2;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

public record ApiSpecV2(
        String id,
        String method,
        String endpoint,
        String description,
        String request,
        String response,
        @JsonProperty("auth") boolean auth,
        String crud,
        List<String> requirementIds,
        List<String> screenIds,
        List<String> tableIds
) {
    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    public ApiSpecV2 {
        method = normalizeMethod(method);
        endpoint = endpoint == null ? "" : endpoint;
        description = description == null ? "" : description;
        request = request == null ? "" : request;
        response = response == null ? "" : response;
        crud = crud == null ? "" : crud;
        requirementIds = requirementIds == null ? List.of() : List.copyOf(requirementIds);
        screenIds = screenIds == null ? List.of() : List.copyOf(screenIds);
        tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
    }

    private static String normalizeMethod(String value) {
        if (value == null) {
            return "GET";
        }
        String upper = value.trim().toUpperCase();
        return ALLOWED_METHODS.contains(upper) ? upper : "GET";
    }
}
