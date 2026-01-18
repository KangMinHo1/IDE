package com.myide.backend.domain.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class FileNode {
    private String name;        // Main.java
    private boolean isDirectory; // true/false
    private String path;        // src/Main.java
    private List<FileNode> children; // 하위 폴더/파일들
}