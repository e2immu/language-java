package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.ArrayInitializer;
import org.e2immu.language.cst.api.expression.BinaryOperator;
import org.e2immu.language.cst.api.expression.ConstructorCall;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.AssertStatement;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.LocalVariableCreation;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseArrayInitializer extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public void test(String s) {
                int[] a = {1, 2, 3};
                Object[] o = {1, s, "abc"};
                boolean[] b = new boolean[]{true, false, false, true};
                String[][] bs = new String[][] {{"a"}, {}};
                String[][] bs2 = new String[2][1];
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("test", 1);

        Block block = methodInfo.methodBody();
        assertEquals(5, block.statements().size());
        if (block.statements().get(0) instanceof LocalVariableCreation lvc &&
            lvc.localVariable().assignmentExpression() instanceof ArrayInitializer ai) {
            assertTrue(ai.parameterizedType().copyWithOneFewerArrays().isInt());
            assertEquals("int[] a={1,2,3};", lvc.toString());
        } else fail();
        if (block.statements().get(1) instanceof LocalVariableCreation lvc &&
            lvc.localVariable().assignmentExpression() instanceof ArrayInitializer ai) {
            assertTrue(ai.parameterizedType().copyWithOneFewerArrays().isJavaLangObject());
            assertEquals("Object[] o={1,s,\"abc\"};", lvc.toString());
        } else fail();
        if (block.statements().get(2) instanceof LocalVariableCreation lvc &&
            lvc.localVariable().assignmentExpression() instanceof ConstructorCall cc) {
            assertEquals(runtime.booleanParameterizedType(), cc.parameterizedType().copyWithOneFewerArrays());
            assertEquals("new boolean[]{true,false,false,true}", cc.toString());
            assertEquals(1, cc.constructor().parameters().size());
        } else fail();
        if (block.statements().get(3) instanceof LocalVariableCreation lvc &&
            lvc.localVariable().assignmentExpression() instanceof ConstructorCall cc) {
            assertNotNull(cc.arrayInitializer());
            assertEquals(2, cc.parameterizedType().arrays());
            assertEquals(runtime.stringParameterizedType(), cc.parameterizedType().copyWithoutArrays());
            assertEquals("new String[][]{{\"a\"},{}}", cc.toString());
        } else fail();
        if (block.statements().get(4) instanceof LocalVariableCreation lvc &&
            lvc.localVariable().assignmentExpression() instanceof ConstructorCall cc) {
            assertNull(cc.arrayInitializer());
            assertEquals(2, cc.parameterizedType().arrays());
            assertEquals(runtime.stringParameterizedType(), cc.parameterizedType().copyWithoutArrays());
            assertEquals("new String[2][1]", cc.toString());
        } else fail();
    }
}
