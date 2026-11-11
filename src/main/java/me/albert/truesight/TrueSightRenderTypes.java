package me.albert.truesight;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import java.lang.reflect.Method;

/**
 * 透视用的原版内置渲染层，零私有管线注册、零侵入、零 mixin，与 Vitrail / OIT 100% 兼容：
 * - ESP_QUADS: 原版 debugFilledBox / debugQuads 或 secondaryBlockOutline
 * - ESP_LINES: 原版 linesTranslucentNoDepthWrite 或 linesTranslucent
 */
public final class TrueSightRenderTypes {

    public static final RenderType ESP_QUADS;
    public static final RenderType ESP_LINES;

    static {
        ESP_QUADS = resolveQuads();
        ESP_LINES = resolveLines();
    }

    private TrueSightRenderTypes() {
    }

    private static RenderType resolveQuads() {
        // 首选 debugFilledBox，如果版本中不存在则 fallback 到 debugQuads 或 lines
        RenderType rt = callRenderTypeMethod("debugFilledBox");
        if (rt != null) return rt;
        rt = callRenderTypeMethod("debugQuads");
        if (rt != null) return rt;
        return RenderTypes.lines();
    }

    private static RenderType resolveLines() {
        // 26.2/26.3 提供 linesTranslucentNoDepthWrite，天然关闭深度写入，完美适配 OIT / Vitrail
        RenderType rt = callRenderTypeMethod("linesTranslucentNoDepthWrite");
        if (rt != null) return rt;
        rt = callRenderTypeMethod("linesTranslucent");
        if (rt != null) return rt;
        return RenderTypes.lines();
    }

    private static RenderType callRenderTypeMethod(String methodName) {
        try {
            Method m = RenderTypes.class.getMethod(methodName);
            m.setAccessible(true);
            return (RenderType) m.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
