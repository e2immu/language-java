package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.expression.MethodCall;
import org.e2immu.language.cst.api.expression.StringConstant;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.impl.element.SingleLineComment;
import org.e2immu.language.cst.impl.element.SourceImpl;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParse101 extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            // some comment
            class C {
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
        assertEquals(1, typeInfo.methods().size());
        assertNotNull(typeInfo.comments());
        assertEquals(1, typeInfo.comments().size());
        Comment comment = typeInfo.comments().get(0);
        if (comment instanceof SingleLineComment slc) {
            assertEquals("// some comment\n", slc.print(null).toString());
        } else fail();
        if (typeInfo.source() instanceof SourceImpl source) {
            assertSame(typeInfo, source.info());
            assertEquals(3, source.beginLine());
            assertEquals(7, source.endLine());
        }
        assertTrue(typeInfo.typeNature().isClass());

        MethodInfo methodInfo = typeInfo.methods().get(0);
        assertEquals("main", methodInfo.name());
        assertEquals("a.b.C.main(String[])", methodInfo.fullyQualifiedName());
        if (methodInfo.source() instanceof SourceImpl source) {
            assertSame(methodInfo, source.info());
            assertEquals(4, source.beginLine());
            assertEquals(6, source.endLine());
        }
        assertEquals(1, methodInfo.parameters().size());
        ParameterInfo pi = methodInfo.parameters().get(0);
        assertEquals("args", pi.name());
        assertEquals(0, pi.index());
        assertEquals("String[]", pi.parameterizedType().fullyQualifiedName());
        assertEquals(1, pi.parameterizedType().arrays());

        if (methodInfo.methodBody().statements().get(0) instanceof ExpressionAsStatement eas
            && eas.expression() instanceof MethodCall mc) {
            if (mc.parameterExpressions().get(0) instanceof StringConstant sc) {
                assertEquals("hello", sc.constant());
                assertEquals("System.out.println(\"hello\")", mc.toString());
            } else fail();
        } else fail();
    }

    @Language("java")
    private static final String INPUT2 = """
            package org.e2immu.analyser.resolver.testexample;
            public class Basics_11 {
                public String method(char c) {
                    return "abc" + +c;
                }
                public String method2(char c) {
                    return "abc" + -c;
                }
            }
            """;

    @Test
    public void test2() {
        parse(INPUT2);
    }

    @Language("java")
    private static final String INPUT3 = """
            package org.e2immu.analyser.resolver.testexample;
            public abstract class Basics_10 {
                interface X {
                }
                abstract X getX();
                void method() {
                    X X;
                    X = getX();
                }
            }
            """;

    @Test
    public void test3() {
        parse(INPUT3);
    }

    @Language("java")
    private static final String INPUT4 = """
            package org.e2immu.analyser.resolver.testexample;

            // orphan to type
            // comment on type
            public class Basics_8 {

                // orphan to field 1
                // comment on field 1
                public static final int CONSTANT_1 = 3;

                // orphan to method
                // comment on method
                public static int method(int in) {
                    // orphan on if
                    // comment on 'if'
                    if (in > 9) {
                        return 1;
                    }
                    System.out.println("in = " + in);
                    return in;
                }

                // orphan to field 2
                // comment on field 2
                public static final int CONSTANT_2 = 3;
            }
            """;

    @Test
    public void test4() {
        parse(INPUT4);
    }

    @Language("java")
    private static final String INPUT5 = """
            package org.e2immu.analyser.resolver.testexample;
            public interface Basics_6 {
                String name();
            }
            """;

    @Test
    public void test5() {
        parse(INPUT5);
    }

    @Language("java")
    private static final String INPUT6 = """
            package org.e2immu.analyser.resolver.testexample;

            public class Basics_5 {

                public int method1() {
                    int i, j;
                    i = 3;
                    j = i + 8;
                    return i + j;
                }

                public int method2() {
                    final int i = 4, j;
                    j = i + 8;
                    return i + j;
                }

                public int method3() {
                    // int i = 4 + j, j = 2; does not compile!
                    int i = 4, j = i + 2;
                    assert j == 6;
                    return j;
                }

                public int method4() {
                    var i = 4;
                    return i+2;
                }
            }
            """;

    @Test
    public void test6() {
        parse(INPUT6);
    }

    @Language("java")
    String INPUT7 = """
            package org.e2immu.analyser.resolver.testexample;
            public class Basics_4 {
                public boolean test(boolean b1, boolean b2) {
                    return b1 & b2 & true;
                }
            }
            """;

    @Test
    public void test7() {
        parse(INPUT7);
    }

    @Language("java")
    String INPUT8 = """
            package a.b;
            public class X {
                public double parse() {
                    return 0xF + 001 + 0.01e-1f + 2L + 1.0e2d;
                }
            }
            """;

    @Test
    public void test8() {
        parse(INPUT8);
    }
}
