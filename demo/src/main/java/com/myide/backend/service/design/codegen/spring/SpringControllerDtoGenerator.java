package com.myide.backend.service.design.codegen.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.dto.design.codegen.GeneratedFile;
import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.service.design.codegen.CodegenOptions;
import com.myide.backend.service.design.codegen.CodegenTarget;
import com.myide.backend.service.design.codegen.DesignCodeGenerator;
import com.myide.backend.service.design.codegen.JsonStubShaper;
import com.myide.backend.service.design.codegen.NameMapper;
import com.myide.backend.service.design.codegen.Traceability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 명세를 컨트롤러 뼈대와 DTO 로 옮긴다.
 *
 * 몸통은 만들지 않는다. 무엇을 어떻게 저장할지는 사람이 정할 일이고, AI 가
 * 지어낸 구현이 들어 있으면 오히려 지우는 데 시간이 든다. 대신 <b>요구사항과
 * 화면 정보를 주석으로 넣어</b> 이 API 가 왜 있는지 코드에 남긴다.
 */
@Component
@RequiredArgsConstructor
public class SpringControllerDtoGenerator implements DesignCodeGenerator {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}/]+)}");

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(CodegenTarget target) {
        return target == CodegenTarget.SPRING_CONTROLLER_DTO;
    }

    @Override
    public List<GeneratedFile> generate(DesignModelV2 model, CodegenOptions options) {
        Map<String, List<ApiSpecV2>> byResource = new LinkedHashMap<>();

        for (ApiSpecV2 api : model.apis()) {
            if (api.endpoint().isBlank()) {
                continue;
            }
            byResource.computeIfAbsent(NameMapper.resourceOf(api.endpoint()),
                    key -> new ArrayList<>()).add(api);
        }

        List<GeneratedFile> files = new ArrayList<>();
        Map<String, String> dtoSources = new LinkedHashMap<>();
        Set<String> usedRecordNames = new LinkedHashSet<>();

        for (Map.Entry<String, List<ApiSpecV2>> entry : byResource.entrySet()) {
            String controllerName = NameMapper.toPascalCase(
                    NameMapper.singularize(entry.getKey())) + "Controller";

            files.add(new GeneratedFile(
                    options.javaSourceDir() + "/controller/" + controllerName + ".java",
                    renderController(model, entry.getValue(), controllerName, options,
                            dtoSources, usedRecordNames),
                    CodegenTarget.SPRING_CONTROLLER_DTO,
                    entry.getKey() + " API " + entry.getValue().size() + "개"));
        }

        dtoSources.forEach((name, source) -> files.add(new GeneratedFile(
                options.javaSourceDir() + "/dto/" + name + ".java",
                source,
                CodegenTarget.SPRING_CONTROLLER_DTO,
                "API 요청/응답 " + name)));

        return files;
    }

    private String renderController(DesignModelV2 model, List<ApiSpecV2> apis, String controllerName,
                                    CodegenOptions options, Map<String, String> dtoSources,
                                    Set<String> usedRecordNames) {
        StringBuilder methods = new StringBuilder();
        Set<String> usedMethodNames = new LinkedHashSet<>();
        Set<String> dtoImports = new LinkedHashSet<>();
        boolean needsMap = false;
        boolean needsList = false;

        for (ApiSpecV2 api : apis) {
            String methodName = unique(NameMapper.toFunctionName(api.method(), api.endpoint()),
                    usedMethodNames);

            String stem = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);

            JsonStubShaper.Shape request = hasBody(api.method())
                    ? shapeOf(api.request(), unique(stem + "Request", usedRecordNames), options,
                    api.description(), dtoSources)
                    : JsonStubShaper.mapShape();

            JsonStubShaper.Shape response = shapeOf(api.response(),
                    unique(stem + "Response", usedRecordNames), options,
                    api.description(), dtoSources);

            if (request.hasRecord()) {
                dtoImports.add(options.basePackage() + ".dto." + request.recordName());
            }
            if (response.hasRecord()) {
                dtoImports.add(options.basePackage() + ".dto." + response.recordName());
            }

            needsMap = needsMap || request.javaType().startsWith("Map")
                    || response.javaType().startsWith("Map");
            needsList = needsList || request.javaType().startsWith("List")
                    || response.javaType().startsWith("List");

            methods.append(renderMethod(model, api, methodName, request, response));
        }

        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(options.basePackage()).append(".controller;\n\n");

        for (String value : dtoImports) {
            builder.append("import ").append(value).append(";\n");
        }

        builder.append("import org.springframework.http.ResponseEntity;\n");
        builder.append("import org.springframework.web.bind.annotation.*;\n");

        if (needsList) {
            builder.append("import java.util.List;\n");
        }
        if (needsMap) {
            builder.append("import java.util.Map;\n");
        }

        builder.append("\n/**\n");
        builder.append(" * 설계 관리에서 생성된 컨트롤러 뼈대입니다.\n");
        builder.append(" *\n");
        builder.append(" * 각 메서드의 주석에 그 API 가 어느 요구사항에서 나왔고 어느 화면이\n");
        builder.append(" * 부르는지 적혀 있습니다. 몸통을 채워 주세요.\n");
        builder.append(" */\n");
        builder.append("@RestController\n");
        builder.append("public class ").append(controllerName).append(" {\n");
        builder.append(methods);
        builder.append("}\n");

        return builder.toString();
    }

    private String renderMethod(DesignModelV2 model, ApiSpecV2 api, String methodName,
                                JsonStubShaper.Shape request, JsonStubShaper.Shape response) {
        StringBuilder builder = new StringBuilder("\n");

        builder.append("    /**\n");
        builder.append("     * ").append(api.description().isBlank()
                ? api.method() + " " + api.endpoint()
                : api.description().replace("*/", "*")).append("\n");

        List<String> requirements = Traceability.requirementLabels(model, api.requirementIds());
        if (!requirements.isEmpty()) {
            builder.append("     *\n");
            builder.append("     * 요구사항: ").append(String.join(", ", requirements)).append("\n");
        }

        List<String> screens = Traceability.screenLabels(model, api);
        if (!screens.isEmpty()) {
            builder.append("     * 부르는 화면: ").append(String.join(", ", screens)).append("\n");
        }

        if (api.auth()) {
            builder.append("     * 로그인이 필요한 API 입니다.\n");
        }

        builder.append("     */\n");
        builder.append("    @").append(annotationOf(api.method()))
                .append("(\"").append(api.endpoint()).append("\")\n");

        List<String> params = new ArrayList<>();

        Matcher matcher = PATH_PARAM.matcher(api.endpoint());
        while (matcher.find()) {
            String raw = matcher.group(1);
            params.add("@PathVariable(\"" + raw + "\") String " + NameMapper.toParamName(raw));
        }

        if (hasBody(api.method())) {
            params.add("@RequestBody " + request.javaType() + " request");
        }

        builder.append("    public ResponseEntity<").append(response.javaType()).append("> ")
                .append(methodName).append("(").append(String.join(", ", params)).append(") {\n");
        builder.append("        // TODO: 구현해 주세요.\n");
        builder.append("        throw new UnsupportedOperationException(\"아직 구현되지 않았습니다.\");\n");
        builder.append("    }\n");

        return builder.toString();
    }

    private JsonStubShaper.Shape shapeOf(String rawJson, String recordName, CodegenOptions options,
                                         String description, Map<String, String> dtoSources) {
        JsonStubShaper.Shape shape = JsonStubShaper.shape(
                objectMapper, rawJson, recordName, options.basePackage(), description);

        if (shape.hasRecord()) {
            dtoSources.put(shape.recordName(), shape.recordSource());
        }

        return shape;
    }

    private boolean hasBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private String annotationOf(String method) {
        return switch (method) {
            case "POST" -> "PostMapping";
            case "PUT" -> "PutMapping";
            case "PATCH" -> "PatchMapping";
            case "DELETE" -> "DeleteMapping";
            default -> "GetMapping";
        };
    }

    private String unique(String candidate, Set<String> used) {
        String name = candidate;
        int suffix = 2;

        while (!used.add(name)) {
            name = candidate + suffix++;
        }

        return name;
    }
}
