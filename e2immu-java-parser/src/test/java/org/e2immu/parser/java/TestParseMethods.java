package org.e2immu.parser.java;


import org.e2immu.language.cst.api.expression.ConstructorCall;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ThrowStatement;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseMethods extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            abstract class C {
              public abstract void methodA(String s);
              static class E {}
              static void methodB(@SuppressWarnings("!!") int j) throws E {
                 throw new E();
              }
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

        TypeInfo E = typeInfo.findSubType("E");
        MethodInfo mb = typeInfo.findUniqueMethod("methodB", 1);
        assertSame(E, mb.exceptionTypes().get(0).typeInfo());
        assertEquals(1, mb.methodBody().statements().size());
        if (mb.methodBody().statements().get(0) instanceof ThrowStatement throwStatement
                && throwStatement.expression() instanceof ConstructorCall cc) {
            assertSame(E, cc.constructor().typeInfo());
        } else fail();
        ParameterInfo pj = mb.parameters().get(0);
        assertEquals(1, pj.annotations().size());
        assertEquals("@SuppressWarnings(\"!!\")", pj.annotations().get(0).toString());
    }
}
