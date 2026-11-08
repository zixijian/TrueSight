package me.albert.truesight;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * 透视用的两个渲染层，都去掉深度测试（隔墙可见）：
 * ESP_QUADS 是原版 DEBUG_QUADS 的底子画填充方块；ESP_LINES 是原版 LINES 的底子画射线。
 *
 * 兼容性说明：
 * - Minecraft 26.1 / 26.2 中 RenderPipeline 位于 com.mojang.blaze3d.pipeline.RenderPipeline
 * - Minecraft 26.3+ 中 RenderPipeline 迁移至 com.mojang.renderpearl.api.pipeline.RenderPipeline
 * 为保证一个 jar 同时在 26.1、26.2、26.3 上正常运行，此处使用反射/动态调用构建并注册 RenderPipeline，
 * 避免对特定版本的 RenderPipeline 类产生硬编码引用导致 ClassNotFoundException / NoClassDefFoundError。
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
        Object pipeline = buildAndRegisterPipeline(
                snippet,
                Identifier.fromNamespaceAndPath("truesight", "pipeline/esp_quads")
        );
        RenderSetup setup = buildRenderSetup(pipeline, true);
        return createRenderType("truesight:esp_quads", setup);
    }

    private static RenderType createEspLines() throws Exception {
        Object snippet = getSnippet("LINES_SNIPPET");
        Object pipeline = buildAndRegisterPipeline(
                snippet,
                Identifier.fromNamespaceAndPath("truesight", "pipeline/esp_lines")
        );
        RenderSetup setup = buildRenderSetup(pipeline, false);
        return createRenderType("truesight:esp_lines", setup);
    }

    private static Object getSnippet(String fieldName) throws Exception {
        Field field = RenderPipelines.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object buildAndRegisterPipeline(Object snippet, Identifier location) throws Exception {
        Class<?> snippetClass = snippet.getClass();
        Class<?> pipelineClass = snippetClass.getDeclaringClass();
        if (pipelineClass == null) {
            // 如果 snippet 不是内部类，尝试寻找 RenderPipeline
            for (Class<?> candidate : new Class<?>[]{
                    findClass("com.mojang.renderpearl.api.pipeline.RenderPipeline"),
                    findClass("com.mojang.blaze3d.pipeline.RenderPipeline")
            }) {
                if (candidate != null && snippetClass.getName().startsWith(candidate.getName())) {
                    pipelineClass = candidate;
                    break;
                }
            }
        }
        if (pipelineClass == null) {
            throw new IllegalStateException("Cannot resolve RenderPipeline class for snippet: " + snippetClass);
        }

        // RenderPipeline.builder(Snippet... snippets)
        Object snippetArray = Array.newInstance(snippetClass, 1);
        Array.set(snippetArray, 0, snippet);
        Method builderMethod = pipelineClass.getMethod("builder", snippetArray.getClass());
        builderMethod.setAccessible(true);
        Object builder = builderMethod.invoke(null, snippetArray);

        // builder.withLocation(Identifier location)
        Method withLocationMethod = builder.getClass().getMethod("withLocation", Identifier.class);
        withLocationMethod.setAccessible(true);
        withLocationMethod.invoke(builder, location);

        // builder.withDepthStencilState(Optional.empty())
        Method withDepthStencilMethod = builder.getClass().getMethod("withDepthStencilState", Optional.class);
        withDepthStencilMethod.setAccessible(true);
        withDepthStencilMethod.invoke(builder, Optional.empty());

        // builder.withCull(false)
        Method withCullMethod = builder.getClass().getMethod("withCull", boolean.class);
        withCullMethod.setAccessible(true);
        withCullMethod.invoke(builder, false);

        // builder.build()
        Method buildMethod = builder.getClass().getMethod("build");
        buildMethod.setAccessible(true);
        Object pipeline = buildMethod.invoke(builder);

        // RenderPipelines.register(pipeline)
        Method registerMethod = null;
        for (Method m : RenderPipelines.class.getDeclaredMethods()) {
            if (m.getName().equals("register") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(pipelineClass)
                    && Modifier.isStatic(m.getModifiers())) {
                registerMethod = m;
                break;
            }
        }
        if (registerMethod == null) {
            throw new NoSuchMethodException("RenderPipelines.register(" + pipelineClass.getName() + ")");
        }
        registerMethod.setAccessible(true);
        return registerMethod.invoke(null, pipeline);
    }

    private static RenderSetup buildRenderSetup(Object pipeline, boolean sortOnUpload) throws Exception {
        // RenderSetup.builder(RenderPipeline)
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

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
