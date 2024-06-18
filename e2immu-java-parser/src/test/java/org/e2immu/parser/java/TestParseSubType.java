package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.Block;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseSubType extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              interface I {
                 int i();
              }
              private static class II implements I {
                 int i() {
                   return 3;
                 }
              }
              interface J {
                 boolean j();
              }
              private static class JJ extends II implements I, J {
                 int j() {
                   return true;
                 }
              }
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
        assertEquals(4, typeInfo.subTypes().size());

        TypeInfo subType1 = typeInfo.subTypes().get(0);
        assertTrue(subType1.typeNature().isInterface());
        assertEquals(1, subType1.methods().size());
        MethodInfo i = subType1.methods().get(0);
        assertEquals("i", i.name());
        assertTrue(i.methodBody().isEmpty());

        TypeInfo subType2 = typeInfo.subTypes().get(1);
        assertEquals("a.b.C.II", subType2.fullyQualifiedName());
        assertTrue(subType2.typeNature().isClass());
        assertTrue(subType2.isStatic());
        assertFalse(subType2.isPublic());
        assertTrue(subType2.isPrivate());
        assertEquals(1, subType2.methods().size());
        MethodInfo i2 = subType2.methods().get(0);
        assertEquals("i", i.name());
        Block block = i2.methodBody();
        assertEquals(1, block.statements().size());
    }
}
