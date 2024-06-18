package org.e2immu.parser.java;

import org.e2immu.cstapi.expression.ArrayLength;
import org.e2immu.cstapi.expression.BinaryOperator;
import org.e2immu.cstapi.expression.IntConstant;
import org.e2immu.cstapi.expression.VariableExpression;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.ParameterInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.statement.Block;
import org.e2immu.cstapi.statement.LocalVariableCreation;
import org.e2immu.cstapi.statement.ReturnStatement;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.cstapi.variable.LocalVariable;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseLocalVariable extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public int length(String[] args) {
                int a = args.length;
                return a+1;
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        assertEquals("C", typeInfo.simpleName());
        MethodInfo methodInfo = typeInfo.methods().get(0);
        assertEquals("length", methodInfo.name());
        assertEquals("a.b.C.length(String[])", methodInfo.fullyQualifiedName());

        Block block = methodInfo.methodBody();
        assertEquals(2, block.statements().size());
        if (block.statements().get(0) instanceof LocalVariableCreation lvc) {
            LocalVariable lv = lvc.localVariable();
            assertTrue(lvc.hasSingleDeclaration());
            assertTrue(lvc.otherLocalVariables().isEmpty());
            assertEquals("a", lv.fullyQualifiedName());
            if (lv.assignmentExpression() instanceof ArrayLength al) {
                if (al.scope() instanceof VariableExpression ve) {
                    assertEquals("args", ve.variable().simpleName());
                    assertEquals("String[]", ve.variable().parameterizedType().toString());
                } else fail();
            } else fail();
            assertEquals("args.length", lv.assignmentExpression().toString());
        } else fail();
        if (block.statements().get(1) instanceof ReturnStatement rs) {
            if (rs.expression() instanceof BinaryOperator plus) {
                assertSame(runtime.plusOperatorInt(), plus.operator());
                if (plus.lhs() instanceof VariableExpression ve) {
                    assertEquals("a", ve.variable().simpleName());
                } else fail();
                if (plus.rhs() instanceof IntConstant i) {
                    assertEquals(1, i.constant());
                } else fail();
            } else fail("Have " + rs.expression().getClass());
            assertEquals("return a+1;", rs.toString());
        } else fail();
        assertEquals(1, methodInfo.parameters().size());
        ParameterInfo pi = methodInfo.parameters().get(0);
        assertEquals("args", pi.name());
        ParameterizedType pt = pi.parameterizedType();
        assertEquals(1, pt.arrays());
    }
}
