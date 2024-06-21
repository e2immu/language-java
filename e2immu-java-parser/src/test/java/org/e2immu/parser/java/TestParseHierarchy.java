package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.ArrayLength;
import org.e2immu.language.cst.api.expression.BinaryOperator;
import org.e2immu.language.cst.api.expression.IntConstant;
import org.e2immu.language.cst.api.expression.VariableExpression;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.LocalVariableCreation;
import org.e2immu.language.cst.api.statement.ReturnStatement;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.variable.LocalVariable;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseHierarchy extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              interface I { }
              interface J extends I { }
              class A implements I { }
              class B extends A implements I, J { }
              interface K<T> { }
              class D extends K<Integer> { }
              class E<S> extends K<S> { }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        TypeInfo I = typeInfo.findSubType("I");
        assertTrue(I.isInterface());
        TypeInfo J = typeInfo.findSubType("J");
        assertTrue(J.isInterface());
        assertSame(I, J.interfacesImplemented().get(0).bestTypeInfo());
        TypeInfo A = typeInfo.findSubType("A");
        assertFalse(A.isInterface());
        assertSame(I, A.interfacesImplemented().get(0).bestTypeInfo());
        TypeInfo B = typeInfo.findSubType("B");
        assertSame(A, B.parentClass().typeInfo());
        assertEquals(2, B.interfacesImplemented().size());
        assertSame(J, B.interfacesImplemented().get(1).typeInfo());

        TypeInfo K = typeInfo.findSubType("K");
        TypeInfo D = typeInfo.findSubType("D");
        assertEquals("K<Integer>", D.parentClass().toString());
        assertSame(K, D.parentClass().typeInfo());
        TypeInfo E = typeInfo.findSubType("E");
        assertEquals(1, E.typeParameters().size());
        assertSame(E.typeParameters().get(0), E.parentClass().parameters().get(0).typeParameter());
    }
}
