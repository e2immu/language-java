package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.TypeInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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


    @Language("java")
    private static final String INPUT3 = """
            class X {
                public static long[] method(Long[] list) {
                  long[] result = new long[list.length];
                  int i = 0;
                  for (long v : list) {
                    result[i++] = v;
                  }
                  return result;
                }
            }
            """;

    @Test
    public void test3() {
        TypeInfo typeInfo = parse(INPUT3);
        assertEquals(" class X{static long[] method(Long[] list){long[] result=new long[list.length];int i=0;for(long v:list){result[i++]=v;}return result;}}", typeInfo.print(runtime.qualificationQualifyFromPrimaryType()).toString());
    }

}
