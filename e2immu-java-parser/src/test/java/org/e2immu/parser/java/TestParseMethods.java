package org.e2immu.parser.java;


import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.ParameterInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseMethods extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            abstract class C {
              public abstract void methodA(String s);
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        assertEquals("C", typeInfo.simpleName());
        assertTrue(typeInfo.isAbstract());
        assertTrue(typeInfo.access().isPackage());

        MethodInfo methodInfo = typeInfo.methods().get(0);
        assertEquals("methodA", methodInfo.name());
        assertTrue(methodInfo.isAbstract());
        assertTrue(methodInfo.methodBody().isEmpty());
        assertTrue(methodInfo.isVoid());
        assertEquals(1, methodInfo.parameters().size());
        ParameterInfo pi = methodInfo.parameters().get(0);
        assertFalse(pi.isVarArgs());
        assertEquals("s", pi.simpleName());
        assertEquals(0, pi.index());
        assertEquals("java.lang.String", pi.parameterizedType().typeInfo().fullyQualifiedName());
    }
}
