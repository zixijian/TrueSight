package me.albert.truesight;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class CompatibilityTest {

    @Test
    void testNoHardcodedRenderPipelineReference() {
        for (Field field : TrueSightRenderTypes.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().contains("RenderPipeline"),
                    "TrueSightRenderTypes should not have fields referencing RenderPipeline directly: " + field);
        }
        for (Method method : TrueSightRenderTypes.class.getDeclaredMethods()) {
            assertFalse(method.getReturnType().getName().contains("RenderPipeline"),
                    "TrueSightRenderTypes should not return RenderPipeline directly: " + method);
            for (Class<?> param : method.getParameterTypes()) {
                assertFalse(param.getName().contains("RenderPipeline"),
                        "TrueSightRenderTypes method parameters should not reference RenderPipeline directly: " + method);
            }
        }
    }

    @Test
    void testRenderTypesResolution() {
        assertNotNull(TrueSightRenderTypes.ESP_QUADS, "ESP_QUADS should be resolved");
        assertNotNull(TrueSightRenderTypes.ESP_LINES, "ESP_LINES should be resolved");
    }

    @Test
    void testOitPipelineSetAttachedIn263() throws Exception {
        // When running in Minecraft 26.3 environment where OitPipelineSet exists,
        // ESP_QUADS and ESP_LINES must have oitPipelineSet successfully built and attached.
        Class<?> oitPipelineSetClass = null;
        try {
            oitPipelineSetClass = Class.forName("net.minecraft.client.renderer.oit.OitPipelineSet");
        } catch (ClassNotFoundException ignored) {
        }

        if (oitPipelineSetClass != null) {
            Field stateField = net.minecraft.client.renderer.rendertype.RenderType.class.getDeclaredField("state");
            stateField.setAccessible(true);

            Field oitField = net.minecraft.client.renderer.rendertype.RenderSetup.class.getDeclaredField("oitPipelineSet");
            oitField.setAccessible(true);

            Object quadsSetup = stateField.get(TrueSightRenderTypes.ESP_QUADS);
            Object linesSetup = stateField.get(TrueSightRenderTypes.ESP_LINES);

            Object quadsOit = oitField.get(quadsSetup);
            Object linesOit = oitField.get(linesSetup);

            assertNotNull(quadsOit, "ESP_QUADS must have oitPipelineSet attached in 26.3+");
            assertNotNull(linesOit, "ESP_LINES must have oitPipelineSet attached in 26.3+");
        }
    }
}
