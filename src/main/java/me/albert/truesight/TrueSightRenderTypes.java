package me.albert.truesight;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * 透视用的两个渲染层，都去掉深度测试（隔墙可见）：
 * - ESP_QUADS: 填充方块透视高亮
 * - ESP_LINES: 射线透视高亮
 *
 * 兼容性实现：
 * - 26.1 / 26.2: RenderPipeline 位于 com.mojang.blaze3d.pipeline.RenderPipeline，构建无深度测试的 Pipeline。
 * - 26.3+: RenderPipeline 位于 com.mojang.renderpearl.api.pipeline.RenderPipeline。
 *   在 26.3 中，原版增加了 OIT（顺序无关半透明渲染）。如果自定义 RenderPipeline 未挂载 OitPipelineSet，
 *   在多附件的 RenderPass 中会导致颜色附件数与管线不匹配的 IllegalStateException。
 *   因此，26.3 下使用 OitPipelineSet.builder(...).withoutDepthTest().build() 动态创建并注册 OIT 管线集合，
 *   使方块在完全关闭深度测试（隔墙穿透可见）的同时，完美兼容 26.3 的 OIT 渲染。
 */
public final class TrueSightRenderTypes {

    public static final RenderType ESP_QUADS;
    public static final RenderType ESP_LINES;

    static {
        try {
            ESP_QUADS = createEspQuads();
            ESP_LINES = createEspLines();
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    private TrueSightRenderTypes() {
    }

    private static RenderType createEspQuads() throws Exception {
        Object snippet = getSnippet("DEBUG_FILLED_SNIPPET");
        Identifier location = Identifier.fromNamespaceAndPath("truesight", "pipeline/esp_quads");
        Object pipeline = buildPipeline(snippet, location);
        registerPipeline(pipeline);

        Object oitSet = tryBuildOitSet("OIT_DEBUG_FILLED_SNIPPET", "esp_quads");
        if (oitSet != null) {
            registerOitSet(oitSet);
        }

        RenderSetup setup = buildRenderSetup(pipeline, oitSet, true);
        return createRenderType("truesight:esp_quads", setup);
    }

    private static RenderType createEspLines() throws Exception {
        Object snippet = getSnippet("LINES_SNIPPET");
        Identifier location = Identifier.fromNamespaceAndPath("truesight", "pipeline/esp_lines");
        Object pipeline = buildPipeline(snippet, location);
        registerPipeline(pipeline);

        Object oitSet = tryBuildOitSet("OIT_LINES_SNIPPET", "esp_lines");
        if (oitSet != null) {
            registerOitSet(oitSet);
        }

        RenderSetup setup = buildRenderSetup(pipeline, oitSet, false);
        return createRenderType("truesight:esp_lines", setup);
    }

    private static Object getSnippet(String fieldName) throws Exception {
        Field field = RenderPipelines.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object buildPipeline(Object snippet, Identifier location) throws Exception {
        Class<?> snippetClass = snippet.getClass();
        Class<?> pipelineClass = resolvePipelineClass(snippetClass);

        Object snippetArray = Array.newInstance(snippetClass, 1);
        Array.set(snippetArray, 0, snippet);
        Method builderMethod = pipelineClass.getMethod("builder", snippetArray.getClass());
        builderMethod.setAccessible(true);
        Object builder = builderMethod.invoke(null, snippetArray);

        Method withLocationMethod = builder.getClass().getMethod("withLocation", Identifier.class);
        withLocationMethod.setAccessible(true);
        withLocationMethod.invoke(builder, location);

        Method withDepthStencilMethod = builder.getClass().getMethod("withDepthStencilState", Optional.class);
        withDepthStencilMethod.setAccessible(true);
        withDepthStencilMethod.invoke(builder, Optional.empty());

        Method withCullMethod = builder.getClass().getMethod("withCull", boolean.class);
        withCullMethod.setAccessible(true);
        withCullMethod.invoke(builder, false);

        Method buildMethod = builder.getClass().getMethod("build");
        buildMethod.setAccessible(true);
        return buildMethod.invoke(builder);
    }

    private static Object tryBuildOitSet(String oitSnippetFieldName, String locationSuffix) {
        try {
            Field oitSnippetField = null;
            try {
                oitSnippetField = RenderPipelines.class.getDeclaredField(oitSnippetFieldName);
            } catch (NoSuchFieldException e) {
                return null;
            }
            oitSnippetField.setAccessible(true);
            Object oitSnippet = oitSnippetField.get(null);
            if (oitSnippet == null) return null;

            Class<?> oitBuilderClass = findClass("net.minecraft.client.renderer.oit.OitPipelineSet$Builder");
            if (oitBuilderClass == null) return null;

            Constructor<?> ctor = oitBuilderClass.getDeclaredConstructor(oitSnippet.getClass(), String.class);
            ctor.setAccessible(true);
            Object oitBuilder = ctor.newInstance(oitSnippet, locationSuffix);

            Method withoutDepthTest = oitBuilderClass.getMethod("withoutDepthTest");
            withoutDepthTest.setAccessible(true);
            withoutDepthTest.invoke(oitBuilder);

            Method buildMethod = oitBuilderClass.getMethod("build");
            buildMethod.setAccessible(true);
            return buildMethod.invoke(oitBuilder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void registerPipeline(Object pipeline) throws Exception {
        Class<?> pipelineClass = pipeline.getClass();
        for (Method m : RenderPipelines.class.getDeclaredMethods()) {
            if (m.getName().equals("register") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(pipelineClass)
                    && Modifier.isStatic(m.getModifiers())) {
                m.setAccessible(true);
                m.invoke(null, pipeline);
                return;
            }
        }
        throw new NoSuchMethodException("RenderPipelines.register(" + pipelineClass.getName() + ")");
    }

    private static void registerOitSet(Object oitSet) {
        try {
            for (Method m : RenderPipelines.class.getDeclaredMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(oitSet.getClass())
                        && Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    m.invoke(null, oitSet);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static RenderSetup buildRenderSetup(Object pipeline, Object oitSet, boolean sortOnUpload) throws Exception {
        Method builderMethod = null;
        for (Method m : RenderSetup.class.getDeclaredMethods()) {
            if (m.getName().equals("builder") && m.getParameterCount() == 1
                    && Modifier.isStatic(m.getModifiers())) {
                builderMethod = m;
                break;
            }
        }
        if (builderMethod == null) {
            throw new NoSuchMethodException("RenderSetup.builder(RenderPipeline)");
        }
        builderMethod.setAccessible(true);
        Object setupBuilder = builderMethod.invoke(null, pipeline);

        if (oitSet != null) {
            try {
                Method setOitMethod = setupBuilder.getClass().getMethod("setOitPipelines", oitSet.getClass());
                setOitMethod.setAccessible(true);
                setOitMethod.invoke(setupBuilder, oitSet);
            } catch (NoSuchMethodException ignored) {
            }
        }

        if (sortOnUpload) {
            Method sortMethod = setupBuilder.getClass().getMethod("sortOnUpload");
            sortMethod.setAccessible(true);
            sortMethod.invoke(setupBuilder);
        }

        Method createMethod = setupBuilder.getClass().getMethod("createRenderSetup");
        createMethod.setAccessible(true);
        return (RenderSetup) createMethod.invoke(setupBuilder);
    }

    private static RenderType createRenderType(String name, RenderSetup setup) throws Exception {
        Method createMethod = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
        createMethod.setAccessible(true);
        return (RenderType) createMethod.invoke(null, name, setup);
    }

    private static Class<?> resolvePipelineClass(Class<?> snippetClass) {
        Class<?> pipelineClass = snippetClass.getDeclaringClass();
        if (pipelineClass != null) return pipelineClass;
        for (Class<?> candidate : new Class<?>[]{
                findClass("com.mojang.renderpearl.api.pipeline.RenderPipeline"),
                findClass("com.mojang.blaze3d.pipeline.RenderPipeline")
        }) {
            if (candidate != null && snippetClass.getName().startsWith(candidate.getName())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot resolve RenderPipeline class for snippet: " + snippetClass);
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
