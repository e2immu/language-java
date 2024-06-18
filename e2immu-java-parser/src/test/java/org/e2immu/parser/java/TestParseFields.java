package org.e2immu.parser.java;

import org.e2immu.cstapi.expression.BinaryOperator;
import org.e2immu.cstapi.expression.CharConstant;
import org.e2immu.cstapi.expression.Expression;
import org.e2immu.cstapi.expression.VariableExpression;
import org.e2immu.cstapi.info.FieldInfo;
import org.e2immu.cstapi.info.FieldModifier;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.statement.Block;
import org.e2immu.cstapi.statement.ReturnStatement;
import org.e2immu.cstapi.variable.FieldReference;
import org.e2immu.cstapi.variable.This;
import org.e2immu.cstimpl.element.MultiLineComment;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseFields extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              /* not final */
              int a;
              private String b;
              // a transient character
              transient char c = ' ';
              public final double d = 3 + a;
              public int a() {
                  return a;
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        assertEquals("C", typeInfo.simpleName());

        FieldInfo a = typeInfo.fields().get(0);
        assertSame(a, typeInfo.getFieldByName("a", true));
        assertTrue(a.access().isPackage());
        assertEquals(4, a.source().beginLine());
        assertEquals(1, a.comments().size());
        if (a.comments().get(0) instanceof MultiLineComment mlc) {
            assertEquals("/* not final */", mlc.print(null).toString());
        } else fail();

        FieldInfo b = typeInfo.fields().get(1);
        assertTrue(b.access().isPrivate());
        assertEquals("java.lang.String", b.type().typeInfo().fullyQualifiedName());

        FieldInfo c = typeInfo.fields().get(2);
        assertTrue(c.access().isPackage());
        assertTrue(c.modifiers().stream().anyMatch(FieldModifier::isTransient));
        assertEquals("char", c.type().typeInfo().fullyQualifiedName());
        assertEquals("char", c.type().typeInfo().simpleName());
        Expression ce = c.initializer();
        if (ce instanceof CharConstant cc) {
            assertEquals(' ', cc.constant());
            assertEquals("' '", ce.print(null).toString());
        } else fail();
        assertFalse(c.isFinal());

        FieldInfo d = typeInfo.getFieldByName("d", true);
        assertTrue(d.isFinal());
        assertTrue(d.access().isPackage()); // and not public, because the enclosing type is package
        assertEquals("double", d.type().typeInfo().fullyQualifiedName());
        Expression de = d.initializer();
        if (de instanceof BinaryOperator bo) {
            if (bo.rhs() instanceof VariableExpression ve && ve.variable() instanceof FieldReference fr) {
                assertSame(a, fr.fieldInfo());
                if (fr.scope() instanceof VariableExpression ve2 && ve2.variable() instanceof This thisVar) {
                    assertSame(typeInfo, thisVar.typeInfo());
                } else fail();
                assertTrue(fr.scopeIsRecursivelyThis());
                assertTrue(fr.scopeIsThis());
            } else fail();
        } else fail();

        MethodInfo methodInfo = typeInfo.methods().get(0);
        assertEquals("a", methodInfo.name());
        Block block = methodInfo.methodBody();
        assertEquals(1, block.statements().size());
        if (block.statements().get(0) instanceof ReturnStatement rs) {
            if (rs.expression() instanceof VariableExpression ve) {
                if (ve.variable() instanceof FieldReference fr) {
                    assertSame(a, fr.fieldInfo());
                } else fail();
            } else fail("Have " + rs.expression().getClass());
            assertEquals("return this.a;", rs.toString());
        } else fail();
    }
}
