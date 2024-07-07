package org.e2immu.parser.java;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

public class TestParseArray extends CommonTestParse {
    @Language("java")
    private static final String INPUT1 = """
            public class Array_1 {

                public Array_1(String s) {

                }

                private Array_1[] copiesOfMyself;

                private void make() {
                    copiesOfMyself = new Array_1[3];
                }

                Array_1 get(int i) {
                    return copiesOfMyself[i];
                }
            }
            """;

    @Test
    public void test1() {
        parse(INPUT1);
    }

    @Language("java")
    private static final String INPUT2 = """
            package org.e2immu.analyser.resolver.testexample;

            public class Array_2 {

                public void method() {
                    int[] a = new int[3];
                }
            }
            """;

    @Test
    public void test2() {
        parse(INPUT2);
    }
}
