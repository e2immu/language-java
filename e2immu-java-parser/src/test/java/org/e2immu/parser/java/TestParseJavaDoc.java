package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.JavaDoc;
import org.e2immu.language.cst.api.expression.BinaryOperator;
import org.e2immu.language.cst.api.expression.CharConstant;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.expression.VariableExpression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.FieldModifier;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.ReturnStatement;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.cst.api.variable.This;
import org.e2immu.language.cst.impl.element.MultiLineComment;
import org.e2immu.language.cst.impl.element.SingleLineComment;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseJavaDoc extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            /**
             * Line 1
             * Link to {@link D} and to {@link D#a()}
             */
            interface C {
               /**
                * This is a method
                * @param in1
                * @param in2 some comment
                * @return a value
                */
                int method(String in1, String in2);
            }
            class D extends C {
                public int a() {
                    return 3;
                }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        assertEquals("C", typeInfo.simpleName());
        JavaDoc javaDoc = typeInfo.javaDoc();
        assertNotNull(javaDoc);
        assertEquals(2, javaDoc.tags().size());
    }
}
