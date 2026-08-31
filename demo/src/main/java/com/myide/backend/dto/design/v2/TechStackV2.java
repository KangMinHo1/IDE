package com.myide.backend.dto.design.v2;

public record TechStackV2(String backend, String frontend, String db) {

    public TechStackV2 {
        backend = backend == null ? "" : backend;
        frontend = frontend == null ? "" : frontend;
        db = db == null ? "" : db;
    }

    public static TechStackV2 empty() {
        return new TechStackV2("", "", "");
    }
}
