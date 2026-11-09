package me.albert.truesight;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 透视用的两个渲染层（高亮矿物方块与准星射线）：
 *
 * 兼容性说明：
 * - Minecraft 26.1 / 26.2 中 RenderPipeline 位于 com.mojang.blaze3d.pipeline.RenderPipeline
 * - Minecraft 26.3+ 中 RenderPipeline 迁移至 com.mojang.renderpearl.api.pipeline.RenderPipeline，
 *   并且引入了 OIT（顺序无关半透明渲染）的多附件 RenderPass。
 * 如果自定义注册 RenderPipeline 而未正确挂载 OitPipelineSet，或者 colorTargetStates 数量与 RenderPass 的
 * color attachment 数量不匹配，将在 26.3 下抛出：
 *   IllegalStateException: Render pass color attachment count must match pipeline color target state count.
 *
 * 原版 RenderTypes 提供了官方配置好所有 OIT 与深度属性的渲染层：
 * - 方块高亮：RenderTypes.debugFilledBox() / debugQuads()（在 26.1-26.3 均完整存在且配置好 OIT/透明混合）
 * - 射线：RenderTypes.linesTranslucentNoDepthWrite()（26.3+ 新增，关闭深度写入且支持 OIT）/ linesTranslucent()（26.1-26.2 回退）
 *
 * 通过动态检测原版提供的 RenderType，既杜绝了硬编码 RenderPipeline 类路径导致的 ClassNotFoundException，
 * 也彻底避免了 26.3 OIT 管线中 color attachment 数量不匹配的崩溃，100% 兼顾 26.1、26.2 与 26.3。
 */
public final class TrueSightRenderTypes {

    public static final RenderType ESP_QUADS;
    public static final RenderType ESP_LINES;

    static {
        try {
            ESP_QUADS = resolveEspQuads();
            ESP_LINES = resolveEspLines();
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    private TrueSightRenderTypes() {
    }

    private static RenderType resolveEspQuads() throws Exception {
        // 优先查找 RenderTypes.debugFilledBox() 或 debugQuads()
        for (String methodName : new String[]{"debugFilledBox", "debugQuads"}) {
            Method m = findStaticMethod(RenderTypes.class, methodName);
            if (m != null) {
                return (RenderType) m.invoke(null);
            }
        }
        for (String fieldName : new String[]{"DEBUG_FILLED_BOX", "DEBUG_QUADS"}) {
            Field f = findStaticField(RenderTypes.class, fieldName);
            if (f != null) {
                return (RenderType) f.get(null);
            }
        }
        throw new NoSuchMethodException("Cannot resolve debugFilledBox/debugQuads RenderType");
    }

    private static RenderType resolveEspLines() throws Exception {
        // 26.3+ 优先 linesTranslucentNoDepthWrite()，旧版回退 linesTranslucent() / lines()
        for (String methodName : new String[]{
                "linesTranslucentNoDepthWrite",
                "linesTranslucent",
                "lines"
        }) {
            Method m = findStaticMethod(RenderTypes.class, methodName);
            if (m != null) {
                return (RenderType) m.invoke(null);
            }
        }
        for (String fieldName : new String[]{
                "LINES_TRANSLUCENT_NO_DEPTH_WRITE",
                "LINES_TRANSLUCENT",
                "LINES"
        }) {
            Field f = findStaticField(RenderTypes.class, fieldName);
            if (f != null) {
                return (RenderType) f.get(null);
            }
        }
        throw new NoSuchMethodException("Cannot resolve linesTranslucent RenderType");
    }

    private static Method findStaticMethod(Class<?> clazz, String name) {
        try {
            Method m = clazz.getDeclaredMethod(name);
            if (Modifier.isStatic(m.getModifiers()) && RenderType.class.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                return m;
            }
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private static Field findStaticField(Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            if (Modifier.isStatic(f.getModifiers()) && RenderType.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        } catch (NoSuchFieldException ignored) {
        }
        return null;
    }
}
