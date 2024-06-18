package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.expression.MethodCall;
import org.e2immu.language.cst.api.expression.StringConstant;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.cstimpl.element.SingleLineComment;
import org.e2immu.cstimpl.element.SourceImpl;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParse101 extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            // some comment
            class C {
              public static void main(String[] args) {
                System.out.println("hello");
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        assertEquals("C", typeInfo.simpleName());
        assertEquals("a.b.C", typeInfo.fullyQualifiedName());
        assertEquals(1, typeInfo.methods().size());
        assertNotNull(typeInfo.comments());
        assertEquals(1, typeInfo.comments().size());
        Comment comment = typeInfo.comments().get(0);
        if (comment instanceof SingleLineComment slc) {
            assertEquals("// some comment\n", slc.print(null).toString());
        } else fail();
        if (typeInfo.source() instanceof SourceImpl source) {
            assertSame(typeInfo, source.info());
            assertEquals(3, source.beginLine());
            assertEquals(7, source.endLine());
        }
        assertTrue(typeInfo.typeNature().isClass());

        MethodInfo methodInfo = typeInfo.methods().get(0);
        assertEquals("main", methodInfo.name());
        assertEquals("a.b.C.main(String[])", methodInfo.fullyQualifiedName());
        if (methodInfo.source() instanceof SourceImpl source) {
            assertSame(methodInfo, source.info());
            assertEquals(4, source.beginLine());
            assertEquals(6, source.endLine());
        }
        assertEquals(1, methodInfo.parameters().size());
        ParameterInfo pi = methodInfo.parameters().get(0);
        assertEquals("args", pi.name());
        assertEquals(0, pi.index());
        assertEquals("String[]", pi.parameterizedType().fullyQualifiedName());
        assertEquals(1, pi.parameterizedType().arrays());

        if (methodInfo.methodBody().statements().get(0) instanceof ExpressionAsStatement eas
            && eas.expression() instanceof MethodCall mc) {
            if (mc.parameterExpressions().get(0) instanceof StringConstant sc) {
                assertEquals("hello", sc.constant());
                assertEquals("System.out.println(\"hello\")", mc.toString());
            } else fail();
        } else fail();
    }
}
