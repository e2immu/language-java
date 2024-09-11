package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.BinaryOperator;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.expression.Negation;
import org.e2immu.language.cst.api.expression.Sum;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.LocalVariableCreation;
import org.e2immu.language.cst.api.statement.ReturnStatement;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseBinaryOperator extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            // some comment
            class C {
              int times(int i, int j) {
                /* return comment
                   the product of i and j */
                return i*j;
              }
              int div(int i, int j) {
                return i/j;
              }
              boolean c;
              boolean and(boolean a, boolean b) {
                return a && b && c;
              }
              boolean or(boolean a, boolean b) {
                return b || c || !a;
              }
              int subtract(int i, int j) {
                return i - j;
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);

        MethodInfo methodInfo = typeInfo.methods().get(0);
        assertEquals("times", methodInfo.name());
        assertEquals("a.b.C.times(int,int)", methodInfo.fullyQualifiedName());
        Block block = methodInfo.methodBody();
        ReturnStatement returnStatement = (ReturnStatement) block.statements().get(0);
        assertEquals("i*j", returnStatement.expression().toString());

        MethodInfo and = typeInfo.findUniqueMethod("and", 2);
        if (and.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("a&&b&&this.c", rs.expression().toString());
        } else fail();
        MethodInfo or = typeInfo.findUniqueMethod("or", 2);
        if (or.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("b||this.c||!a", rs.expression().toString());
            assertEquals("this.c||!a||b", runtime.sortAndSimplify(rs.expression()).toString());
        } else fail();
        MethodInfo subtract = typeInfo.findUniqueMethod("subtract", 2);
        if (subtract.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("return i-j;", rs.toString());
            assertInstanceOf(BinaryOperator.class, rs.expression());
            Expression ss = runtime.sortAndSimplify(rs.expression());
            if (ss instanceof Sum s) {
                assertEquals("i", s.lhs().toString());
                if (s.rhs() instanceof Negation n) {
                    assertEquals("j", n.expression().toString());
                } else fail();
            } else fail();
            assertEquals("i-j", ss.toString());
        } else fail();

        MethodInfo div = typeInfo.findUniqueMethod("div", 2);
        if (div.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("return i/j;", rs.toString());
        } else fail();
    }

    @Language("java")
    private static final String INPUT2 = """
            package a.b;
            // left-to-right associative!
            class C {
              int multDiv(int i, int j) {
                return 2 * i / (j + 5);
              }
              int multDiv2(int i, int j) {
                return 2 * i / j * 5;
              }
            }
            """;

    @Test
    public void test2() {
        TypeInfo typeInfo = parse(INPUT2);
        MethodInfo multDiv = typeInfo.findUniqueMethod("multDiv", 2);
        if (multDiv.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("return 2*i/(j+5);", rs.toString());
            if (rs.expression() instanceof BinaryOperator bo) {
                assertEquals("2*i", bo.lhs().toString());
            } else fail();
        } else fail();

        MethodInfo multDiv2 = typeInfo.findUniqueMethod("multDiv2", 2);
        if (multDiv2.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("return 2*i/j*5;", rs.toString());
            if (rs.expression() instanceof BinaryOperator bo) {
                assertEquals("2*i/j", bo.lhs().toString());
            } else fail();
        } else fail();
    }


    @Language("java")
    private static final String INPUT3 = """
            package a.b;
            // string concat
            class C {
              String concat1(int i) {
                return i + "a";
              }
              String concat2(int i, int j) {
                return i + "a" + j;
              }
            }
            """;

    @Test
    public void test3() {
        TypeInfo typeInfo = parse(INPUT3);

        MethodInfo concat1 = typeInfo.findUniqueMethod("concat1", 1);
        if (concat1.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("return i+\"a\";", rs.toString());
            if (rs.expression() instanceof BinaryOperator bo) {
               assertSame(runtime.plusOperatorString(), bo.operator());
            } else fail();
            assertTrue(rs.expression().parameterizedType().isJavaLangString());
        } else fail();

        MethodInfo concat2 = typeInfo.findUniqueMethod("concat2", 2);
        if (concat2.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("return i+\"a\"+j;", rs.toString());
            if (rs.expression() instanceof BinaryOperator bo) {
                assertSame(runtime.plusOperatorString(), bo.operator());
            } else fail();
            assertTrue(rs.expression().parameterizedType().isJavaLangString());
        } else fail();

    }


    @Language("java")
    private static final String INPUT4 = """
            package a.b;
            class C {
              public int method(int j, int k) {
                int i = j + k - 1;
                return i;
              }
            }
            """;

    @Test
    public void test4() {
        TypeInfo C = parse(INPUT4);
        MethodInfo mi = C.findUniqueMethod("method", 2);
        LocalVariableCreation iLvc = (LocalVariableCreation) mi.methodBody().statements().get(0);
        assertEquals("int i=j+k-1;", iLvc.toString());
    }
}
