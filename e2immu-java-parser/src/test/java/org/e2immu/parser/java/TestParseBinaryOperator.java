package org.e2immu.parser.java;

import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.statement.Block;
import org.e2immu.cstapi.statement.ReturnStatement;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
              boolean c;
              boolean and(boolean a, boolean b) {
                return a && b && c;
              }
              boolean or(boolean a, boolean b) {
                return b || c || !a;
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
            assertEquals("this.c&&a&&b", rs.expression().toString());
        }
        MethodInfo or = typeInfo.findUniqueMethod("or", 2);
        if (or.methodBody().statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("this.c||!a||b", rs.expression().toString());
        }
    }
}
