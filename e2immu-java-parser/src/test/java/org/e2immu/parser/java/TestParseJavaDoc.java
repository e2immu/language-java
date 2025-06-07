package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.JavaDoc;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseJavaDoc extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            /**
             * Line 1
             * Link to {@link D} and to {@see D#a()}
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

        JavaDoc.Tag tag0 = javaDoc.tags().getFirst();
        assertEquals(JavaDoc.TagIdentifier.LINK, tag0.identifier());
        assertFalse(tag0.blockTag());
        assertEquals("4-11:4-20", tag0.source().compact2());

        JavaDoc.Tag tag1 = javaDoc.tags().getLast();
        assertEquals(JavaDoc.TagIdentifier.SEE, tag1.identifier());
        assertFalse(tag1.blockTag());
        assertEquals("4-28:4-40", tag1.source().compact2());

        assertEquals("""
                *
                 * Line 1
                 * Link to {@link D} and to {@see D#a()}\
                """, javaDoc.comment());

        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 2);
        JavaDoc javaDocMethod = methodInfo.javaDoc();
        assertNotNull(javaDocMethod);
        assertEquals("""
                *
                    * This is a method
                    * param in1\s
                    * param in2 some comment
                    * return a value\
                """, javaDocMethod.comment());
    }
}
