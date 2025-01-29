package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.LocalVariableCreation;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestParseDetailedSources extends CommonTestParse {

    // be careful changing this string, many tests are dependent on exact positions
    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public static void main(String[] args) {
                var len = args.length;
                final int len2 = args[0].length();
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT, true);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("main", 1);
        LocalVariableCreation lvc0 = (LocalVariableCreation) methodInfo.methodBody().statements().get(0);
        assertEquals("4-5:4-25", lvc0.source().compact2());
        LocalVariableCreation.Modifier v = lvc0.modifiers().stream().findFirst().orElseThrow();
        assertEquals("4-5:4-7", lvc0.source().detailedSources().detail(v).compact2());
        assertEquals("4-9:4-11", lvc0.source().detailedSources().detail(lvc0.localVariable()).compact2());

        LocalVariableCreation lvc1 = (LocalVariableCreation) methodInfo.methodBody().statements().get(1);
        assertEquals("5-5:5-37", lvc1.source().compact2());
        LocalVariableCreation.Modifier f = lvc1.modifiers().stream().findFirst().orElseThrow();
        assertEquals("5-5:5-9", lvc1.source().detailedSources().detail(f).compact2());
        ParameterizedType pt1 = lvc1.localVariable().parameterizedType();
        assertEquals("5-11:5-13", lvc1.source().detailedSources().detail(pt1).compact2());
        assertEquals("5-15:5-18", lvc1.source().detailedSources().detail(lvc1.localVariable()).compact2());
    }
}
