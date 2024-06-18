package org.e2immu.parser.java;

import org.e2immu.cstapi.expression.InlineConditional;
import org.e2immu.cstapi.expression.IntConstant;
import org.e2immu.cstapi.expression.StringConstant;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.statement.ExplicitConstructorInvocation;
import org.e2immu.cstapi.statement.ReturnStatement;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestExplicitConstructorInvocation extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              final int i;
              final String s;
              C() {
                this(1);
              }
              C(int i) {
                 this("a", i);
                 System.out.println(i);
              }
              C(String s, int i) {
                this.s = s;
                this.i = i;
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo c0 = typeInfo.findConstructor(0);
        MethodInfo c1 = typeInfo.findConstructor(1);
        MethodInfo c2 = typeInfo.findConstructor(2);

        assertEquals(1, c0.methodBody().statements().size());
        if (c0.methodBody().statements().get(0) instanceof ExplicitConstructorInvocation eci) {
            assertFalse(eci.parameterExpressions().isEmpty());
            assertInstanceOf(IntConstant.class, eci.parameterExpressions().get(0));
            assertFalse(eci.isSuper());
            assertSame(c1, eci.methodInfo());
        } else fail();

        assertEquals(2, c1.methodBody().statements().size());
        if (c1.methodBody().statements().get(0) instanceof ExplicitConstructorInvocation eci) {
            assertEquals(2, eci.parameterExpressions().size());
            assertInstanceOf(StringConstant.class, eci.parameterExpressions().get(0));
            assertFalse(eci.isSuper());
            assertSame(c2, eci.methodInfo());
        } else fail();

        assertEquals(2, c2.methodBody().statements().size());
        assertEquals("1", c2.methodBody().statements().get(1).source().index());
    }
}
