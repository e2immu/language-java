package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestStringConstant extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              String s = "[\\\\d]{5}\\\\.xml$";
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        FieldInfo s = typeInfo.getFieldByName("s", true);
        assertEquals("\"[\\\\d]{5}\\\\.xml$\"", s.initializer().toString());
    }
}
