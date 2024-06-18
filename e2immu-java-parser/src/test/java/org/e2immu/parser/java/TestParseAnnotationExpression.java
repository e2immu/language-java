package org.e2immu.parser.java;

import org.e2immu.cstapi.expression.AnnotationExpression;
import org.e2immu.cstapi.expression.IntConstant;
import org.e2immu.cstapi.expression.StringConstant;
import org.e2immu.cstapi.info.FieldInfo;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseAnnotationExpression extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            @SuppressWarnings(4)
            class C {

              @SuppressWarnings
              private static int K = 3;

              @SuppressWarnings("on Method")
              void method() {

              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        AnnotationExpression aeType = typeInfo.annotations().get(0);
        assertSame(runtime.getFullyQualified("java.lang.SuppressWarnings", true), aeType.typeInfo());
        AnnotationExpression.KV kv0Type = aeType.keyValuePairs().get(0);
        assertEquals("4", kv0Type.value().toString());
        assertInstanceOf(IntConstant.class, kv0Type.value());

        FieldInfo fieldInfo = typeInfo.getFieldByName("K", true);
        assertEquals(1, fieldInfo.annotations().size());
        assertTrue(fieldInfo.annotations().get(0).keyValuePairs().isEmpty());

        MethodInfo method = typeInfo.findUniqueMethod("method", 0);
        AnnotationExpression aeMethod = method.annotations().get(0);
        AnnotationExpression.KV kv0 = aeMethod.keyValuePairs().get(0);
        assertEquals("\"on Method\"", kv0.value().toString());
        assertInstanceOf(StringConstant.class, kv0.value());
    }
}
