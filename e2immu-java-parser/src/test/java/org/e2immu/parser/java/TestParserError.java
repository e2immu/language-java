package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.expression.MethodCall;
import org.e2immu.language.cst.api.expression.StringConstant;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.impl.element.SingleLineComment;
import org.e2immu.language.cst.impl.element.SourceImpl;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParserError extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public static void method1(String[] args) {
                System.out.println(arguments.length);
              }
              public void method2(int i) {
                 // nothing wrong here
              }
            }
            """;

    @Test
    public void test() {
        assertThrows(Summary.FailFastException.class, () -> parse(INPUT));

        Context c = parseReturnContext(INPUT);
        Summary s = c.summary();
        assertTrue(s.haveErrors());
        assertEquals(1, s.methodsWithErrors());
        assertEquals(1, s.methodsSuccess());
        assertEquals("Unknown variable in context: 'arguments' in VariableContext{VariableContext{null, " +
                     "local [], fields []}, local [], fields []}", s.parserErrors().get(0).getMessage());
    }
}
