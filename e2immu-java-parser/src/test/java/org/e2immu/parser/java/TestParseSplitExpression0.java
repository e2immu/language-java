package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.*;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.api.statement.ReturnStatement;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseSplitExpression0 extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            public abstract class SplitExpression_0 {

                private final int base;
                private int j;

                public SplitExpression_0(int base) {
                    this.base = base;
                }

                protected abstract int method(int i1, int i2, int i3, int i4);

                private int compute(int i) {
                    return (int) Math.pow(base, i);
                }

                public int same1(int k) {
                    return method(compute(3), compute(k), j = k + 2, compute(j));
                }
                        
                public int same2(int k) {
                    int c3 = compute(3);
                    int ck = compute(k);
                    int cj = j = k + 2;
                    int c2 = compute(j);
                    return method(c3, ck, cj, c2);
                }

                public int same3(int k) {
                    int ck = compute(k);
                    int a = j = k + 2;
                    int c2 = compute(j);
                    int c3 = compute(3);
                    return method(c3, ck, a, c2);
                }

                public int same5(int k) {
                    int ck = compute(k);
                    int c3 = compute(3);
                    int a = j = k + 2;
                    int cj = compute(j);
                    // note: the + in +a gets evaluated away
                    return method(c3, ck, +a, cj);
                }

                public int same6(int k) {
                    // note: 2+1 gets evaluated to 3
                    int c3 = compute(2+1);
                    return method(c3, compute(k), (j = k + 2), compute(j));
                }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        assertEquals(2, typeInfo.fields().size());
        FieldInfo base = typeInfo.fields().get(0);
        assertTrue(base.isFinal());
        FieldInfo j = typeInfo.fields().get(1);
        assertFalse(j.isFinal());

        assertEquals(7, typeInfo.methods().size());
        assertEquals(1, typeInfo.constructors().size());

        MethodInfo constructor = typeInfo.findConstructor(1);
        assertSame(constructor, typeInfo.constructors().get(0));
        assertTrue(constructor.isPublic());
        assertEquals(1, constructor.methodBody().statements().size());
        if (constructor.methodBody().statements().get(0) instanceof ExpressionAsStatement eas
            && eas.expression() instanceof Assignment assignment) {
            assertEquals("this.base=base", assignment.toString());
            if (assignment.variableTarget() instanceof FieldReference fr) {
                assertTrue(fr.scopeIsThis());
                assertEquals("base", fr.fieldInfo().name());
                assertSame(base, fr.fieldInfo());
            } else fail();
            if (assignment.value() instanceof VariableExpression ve && ve.variable() instanceof ParameterInfo pi) {
                assertSame(pi, constructor.parameters().get(0));
            } else fail();
        } else fail();


        MethodInfo method = typeInfo.findUniqueMethod("method", 4);
        assertTrue(method.isAbstract());

        MethodInfo compute = typeInfo.findUniqueMethod("compute", 1);
        assertEquals(1, compute.methodBody().statements().size());
        if (compute.methodBody().statements().get(0) instanceof ReturnStatement rs
            && rs.expression() instanceof Cast cast
            && cast.expression() instanceof MethodCall mc) {
            assertSame(runtime.intTypeInfo(), cast.parameterizedType().typeInfo());
            assertEquals(2, mc.parameterExpressions().size());
            if(mc.object() instanceof TypeExpression te) {
                assertEquals("java.lang.Math", te.parameterizedType().typeInfo().fullyQualifiedName());
            } else fail();
        } else fail();


        MethodInfo same6 = typeInfo.findUniqueMethod("same6", 1);
        assertEquals(2, same6.methodBody().statements().size());
        if (same6.methodBody().statements().get(1) instanceof ReturnStatement rs
            && rs.expression() instanceof MethodCall mc) {
            assertEquals(4, mc.parameterExpressions().size());
            if (mc.parameterExpressions().get(2) instanceof EnclosedExpression ee) {
                assertEquals("this.j=k+2", ee.inner().toString());
                if (ee.inner() instanceof Assignment assignment) {
                    if (assignment.target() instanceof VariableExpression ve) {
                        assertEquals("j", ve.variable().simpleName());
                        if (ve.variable() instanceof FieldReference fr) {
                            assertSame(runtime.intTypeInfo(), fr.parameterizedType().typeInfo());
                        } else fail();
                    } else fail();
                    assertSame(runtime.intTypeInfo(), assignment.parameterizedType().typeInfo());
                } else fail();
            }
        } else fail();
    }
}
