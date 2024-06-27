package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseRecord extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            record C(String s, int i) {

              private record P() {}

              public record R(C... cs) {
                 public R {
                   assert cs[0] != null;
                   System.out.println(cs.length);
                 }
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        assertTrue(typeInfo.typeNature().isRecord());

        MethodInfo cc = typeInfo.findConstructor(2);
        assertTrue(cc.isSyntheticConstructor());
        assertTrue(cc.isSynthetic());
        assertEquals(2, cc.methodBody().statements().size());
        assertEquals("this.s=s;", cc.methodBody().statements().get(0).toString());

        TypeInfo p = typeInfo.findSubType("P");
        assertTrue(p.typeNature().isRecord());
        assertTrue(p.hasBeenInspected());
        assertTrue(p.fields().isEmpty());
        assertTrue(p.isPrivate());

        TypeInfo r = typeInfo.findSubType("R");
        assertTrue(r.typeNature().isRecord());
        assertEquals(1, r.fields().size());
        FieldInfo cs = r.getFieldByName("cs", true);
        assertEquals("a.b.C.R.cs", cs.fullyQualifiedName());
        assertEquals("Type a.b.C[]", cs.type().toString());

        MethodInfo ccR = r.findConstructor(0);
        assertTrue(ccR.methodType().isCompactConstructor());
        assertEquals(2, ccR.methodBody().statements().size());
    }
}
