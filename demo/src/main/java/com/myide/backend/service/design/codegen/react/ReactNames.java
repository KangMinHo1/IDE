package com.myide.backend.service.design.codegen.react;

import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.service.design.codegen.NameMapper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * React 쪽 이름을 한곳에서 정한다.
 *
 * 라우트 파일과 화면 파일과 API 호출 파일이 서로를 import 하는데, 각자
 * 이름을 따로 지으면 이름이 하나만 어긋나도 화면이 통째로 안 뜬다.
 * 세 생성기가 모두 여기를 거치게 해서 그런 일이 생길 수 없게 만든다.
 */
public final class ReactNames {

    private ReactNames() {
    }

    /** 화면 id → 컴포넌트 이름. 겹치면 뒤에 숫자를 붙인다. */
    public static Map<String, String> componentNames(DesignModelV2 model) {
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();

        for (ScreenV2 screen : model.screens()) {
            names.put(screen.id(),
                    unique(NameMapper.toComponentName(screen.key(), screen.name()), used));
        }

        return names;
    }

    /** API id → 호출 함수 이름. */
    public static Map<String, String> functionNames(DesignModelV2 model) {
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();

        for (ApiSpecV2 api : model.apis()) {
            if (api.endpoint().isBlank()) {
                continue;
            }
            names.put(api.id(),
                    unique(NameMapper.toFunctionName(api.method(), api.endpoint()), used));
        }

        return names;
    }

    /** 화면 파일 이름은 컴포넌트 이름을 그대로 따른다. */
    public static String pagePath(String componentName) {
        return "src/pages/" + componentName + ".jsx";
    }

    private static String unique(String candidate, Set<String> used) {
        String name = candidate;
        int suffix = 2;

        while (!used.add(name)) {
            name = candidate + suffix++;
        }

        return name;
    }
}
