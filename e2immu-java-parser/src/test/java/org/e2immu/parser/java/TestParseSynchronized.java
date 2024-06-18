package org.e2immu.parser.java;

import org.e2immu.cstapi.expression.VariableExpression;
import org.e2immu.cstapi.info.FieldInfo;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.statement.SynchronizedStatement;
import org.e2immu.cstapi.variable.FieldReference;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseSynchronized extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public synchronized void method1() {
                 method2();
              }
              private final String s = "?";
              public void method2() {
                synchronized (s) {
                  System.out.println(s);
                }
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo m1 = typeInfo.findUniqueMethod("method1", 0);
        assertTrue(m1.isSynchronized());
        MethodInfo m2 = typeInfo.findUniqueMethod("method2", 0);
        assertFalse(m2.isSynchronized());
        FieldInfo fieldInfo = typeInfo.getFieldByName("s", true);
        if (m2.methodBody().statements().get(0) instanceof SynchronizedStatement s) {
            if (s.expression() instanceof VariableExpression ve && ve.variable() instanceof FieldReference fr) {
                assertSame(fieldInfo, fr.fieldInfo());
            } else fail();
            assertEquals(1, s.block().statements().size());
        }
    }
}
