package me.albert.truesight;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
}
