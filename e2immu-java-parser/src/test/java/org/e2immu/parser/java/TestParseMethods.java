package org.e2immu.parser.java;


import org.e2immu.language.cst.api.expression.ConstructorCall;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ThrowStatement;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseMethods extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            abstract class C {
              public abstract void methodA(String s);
              static class E {}
              static void methodB(@SuppressWarnings("!!") int j) throws E {
                 throw new E();
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        assertEquals("C", typeInfo.simpleName());
        assertTrue(typeInfo.isAbstract());
        assertTrue(typeInfo.access().isPackage());

        MethodInfo methodInfo = typeInfo.methods().get(0);
        assertEquals("methodA", methodInfo.name());
        assertTrue(methodInfo.isAbstract());
        assertTrue(methodInfo.methodBody().isEmpty());
        assertTrue(methodInfo.isVoid());
        assertEquals(1, methodInfo.parameters().size());
        ParameterInfo pi = methodInfo.parameters().get(0);
        assertFalse(pi.isVarArgs());
        assertEquals("s", pi.simpleName());
        assertEquals(0, pi.index());
        assertEquals("java.lang.String", pi.parameterizedType().typeInfo().fullyQualifiedName());

        TypeInfo E = typeInfo.findSubType("E");
        MethodInfo mb = typeInfo.findUniqueMethod("methodB", 1);
        assertSame(E, mb.exceptionTypes().get(0).typeInfo());
        assertEquals(1, mb.methodBody().statements().size());
        if (mb.methodBody().statements().get(0) instanceof ThrowStatement throwStatement
            && throwStatement.expression() instanceof ConstructorCall cc) {
            assertSame(E, cc.constructor().typeInfo());
        } else fail();
        ParameterInfo pj = mb.parameters().get(0);
        assertEquals(1, pj.annotations().size());
        assertEquals("@SuppressWarnings(\"!!\")", pj.annotations().get(0).toString());
    }

    @Language("java")
    private static final String INPUT2 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_73 {

                interface I {
                }

                interface P {
                }

                interface R {
                    void call(I i, P p);
                }

                static <T> T verify(T t) {
                    return t;
                }

                static <T> T any(Class<T> clazz) {
                    return null;
                }

                // need to achieve that 2nd argument's erasure becomes ? extends Object
                void method(R r, P p) {
                    verify(r).call(any(I.class), any(p.getClass()));
                }

                static Class<? extends Object> clazz(Object p) {
                    return p.getClass();
                }

                // need to achieve that 2nd argument's erasure becomes ? extends Object
                void method2(R r, P p) {
                    // DOES NOT COMPILE verify(r).call(any(I.class), any(clazz(p)));
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

            public class MethodCall_72 {

                static void assertEquals(Object o1, Object o2) {
                }

                static void assertEquals(Object[] o1, Object[] o2) {
                }

                void method(String[] s1, String[] s2) {
                    assertEquals(s1, s2);
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

            public class MethodCall_71 {

                static <R> R[] add(R[] rs, R r) {
                    return rs;
                }

                static <T> T any1() {
                    return null;
                }

                static <S> S any2() {
                    return null;
                }

                void method1() {
                    add(any1(), any2());
                }

                void method2() {
                    add(any1(), any1());
                }
            }
            """;

    @Test
    public void test4() {
        parse(INPUT4);
    }

    @Language("java")
    private static final String INPUT5 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_69 {

                public static short eq(int value) {
                    return 0;
                }

                public static short eq(char value) {
                    return 0;
                }

                public static short eq(short value) {
                    return 0;
                }

                public static <T> T eq(T value) {
                    return value;
                }

                public static <T> T method() {
                    return eq(null);
                }
            }
            """;

    @Test
    public void test5() {
        parse(INPUT5);
    }

    @Language("java")
    private static final String INPUT6 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_68 {

                interface A {
                }

                static class B implements A {
                    void set() {
                        System.out.println(5);
                    }
                }

                interface Run<T extends A> {
                    T run(T t);
                }

                B get() {
                    return method(new B(), a -> {
                        a.set();
                        return a;
                    });
                }

                <T extends A> T method(T t, Run<T> run) {
                    run.run(t);
                    return t;
                }

                // change the order of the parameters; now parameter 1 takes from param 2

                B get2() {
                    return method2(a -> {
                        a.set();
                        return a;
                    }, new B());
                }

                <T extends A> T method2(Run<T> run, T t) {
                    run.run(t);
                    return t;
                }

                // without new B(), extends A: info about T must come from return type

                B get3() {
                    return method3(a -> {
                        a.set();
                        return a;
                    });
                }

                <T extends A> T method3(Run<T> run) {
                    run.run(null);
                    return null;
                }

                // without new B(), extends B: no transfer necessary, because base is B

                B get4() {
                    return method4(a -> {
                        a.set();
                        return a;
                    });
                }

                <T extends B> T method4(Run<T> run) {
                    run.run(null);
                    return null;
                }
            }
            """;

    @Test
    public void test6() {
        parse(INPUT6);
    }

    @Language("java")
    private static final String INPUT7 = """
            package org.e2immu.analyser.resolver.testexample;

            /*
            simply take the first one, if 'null' is involved
             */
            public class MethodCall_66 {
                static class MyException extends RuntimeException {

                }
                interface Logger {
                    void logError(String msg, MyException myException);

                    void logError(String msg, Throwable throwable);
                }

                void method1(Logger logger) {
                    logger.logError("hello", null);
                }
               \s
                void method2(Logger logger) {
                    logger.logError("hello", (Throwable) null);
                }
            }
            """;

    @Test
    public void test7() {
        parse(INPUT7);
    }

    @Language("java")
    private static final String INPUT8 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_64 {

                static class B {
                    void set(Double d) {
                    }
                }

                B b;

                Double getDoubleValueFromString(String val) {
                    return Double.valueOf(val);
                }

                void method(String s) {
                    b.set(getDoubleValueFromString(s) * 100);
                }
            }
            """;

    @Test
    public void test8() {
        parse(INPUT8);
    }

    @Language("java")
    private static final String INPUT9 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_63 {

                interface I {
                }

                void set(I[] is) {
                }

                // from org.apache.commons.lang3
                @SafeVarargs
                static <T> T[] addAll(final T[] array1, final T... array2) {
                    return null;
                }

                void method(I i, I data[]) {
                    set(addAll(new I[]{i}, data));
                }
            }
            """;

    @Test
    public void test9() {
        parse(INPUT9);
    }

    @Language("java")
    private static final String INPUT10 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_60 {

                static class ArrayList<I> extends java.util.ArrayList<I> {
                }

                ArrayList<String[]> list;

                String accept(String[][] nameValuePairs) {
                    return nameValuePairs.length + "?";
                }

                String method() {
                    String[][] a = new String[list.size()][2];
                    return accept(list.toArray(a));
                }
            }
            """;

    @Test
    public void test10() {
        parse(INPUT10);
    }

    @Language("java")
    private static final String INPUT11 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_56 {

                interface I<T> {

                }
                static class AI<A>  implements I<A> {

                }
                static class BI<B> implements I<B> {

                }

                String method(I<? extends Number> i) {
                    return "hello "+i;
                }
                public String method(boolean b) {
                    return method(b ? new AI<Long>(): new BI<Integer>());
                }
            }
            """;

    @Test
    public void test11() {
        parse(INPUT11);
    }

    @Language("java")
    private static final String INPUT12 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_55 {

                interface D {
                    long id();
                }

                String method(D d) {
                    // noinspection ALL
                    StringBuilder buf = new StringBuilder();
                    buf.append(d.id() != Long.MIN_VALUE ? d.id() : "");
                    return buf.toString();
                }
            }
            """;

    @Test
    public void test12() {
        parse(INPUT12);
    }

    @Language("java")
    private static final String INPUT13 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_54 {

                interface I {

                }

                record M(I[] is) {

                }

                static class Vector {
                    Vector(long[] longs) {
                    }

                    Vector(I[] objects) {
                    }
                }

                interface KI {
                    void set(I[] is);
                }

                static class K implements KI {
                    I i;

                    @Override
                    public void set(I[] is) {
                        this.i = is[0];
                    }
                }

                public Object method(long[] longs) {
                    return new Vector(longs.clone());
                }

                public long[] method2(long[] longs) {
                    return longs.clone();
                }

                public Object method3(long[] longs) {
                    return new Vector(longs == null ? null : longs.clone());
                }

                Object method4(M m) {
                    return new Vector(m.is() == null ? null : m.is().clone());
                }

                Object method5(M m) {
                    return new Vector(m.is == null ? null : m.is.clone());
                }

                void method6(M m) {
                    K k = new K();
                    k.set(m.is() == null ? null : m.is().clone());
                }
            }
            """;

    @Test
    public void test13() {
        parse(INPUT13);
    }

    @Language("java")
    private static final String INPUT14 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_53 {

                private interface Test {
                    boolean test(char c);
                }

                public static String method1(String s) {
                    return ((Test) c -> c != '%' && isPercent(c)).toString();
                }

                public static String method2(String s) {
                    return (new Test() {
                        @Override
                        public boolean test(char c) {
                            return c != '%' && isPercent(c);
                        }
                    }).toString();
                }

                public static String method3(String s) {
                    Test test = new Test() {
                        @Override
                        public boolean test(char c) {
                            return c != '%' && isPercent(c);
                        }
                    };
                    return test.toString();
                }

                private static boolean isPercent(char c) {
                    return c == '%';
                }

            }

            """;

    @Test
    public void test14() {
        parse(INPUT14);
    }

    @Language("java")
    private static final String INPUT15 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_46 {
                interface I {
                    int method1();

                    int method2();
                }

                interface L extends I {
                    int method4();

                    int method5();

                    int getId();
                }

                int method(L l, String s, int k) {
                    return (s + l).length() + k;
                }

                static <T extends I> T filterByID(T t, long l) {
                    return f(t, new long[]{l}, null);
                }

                static <T extends I> T f(T s, long[] l, T t) {
                    return t;
                }

                static <T extends I> T filterByID(T t, long[] longs) {
                    return f(t, longs, null);
                }

                int test1(L l) {
                    return method(l, "abc", 3);
                }

                int test2(L l) {
                    return method(filterByID(l, 9), "abc", 6);
                }

                int test3(L l) {
                    return method(filterByID(l, new long[]{l.getId()}), "abc", 9);
                }
            }
            """;

    @Test
    public void test15() {
        parse(INPUT15);
    }

    String INPUT16 = """
            package org.e2immu.analyser.resolver.testexample;

            // String[][] --> Object[]
            public class MethodCall_44 {

                interface Y {
                    String method( Object[] objects);
                }

                String test(Y y, String[][] strings) {
                    return y.method( strings);
                }
            }
            """;

    String INPUT17 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_23 {

                interface Analyser {
                    record SharedState(int iteration, String context) {}
                }

                static class MethodAnalyser implements Analyser {

                    private record SharedState(boolean allowBreaking) {}

                    public boolean method(SharedState sharedState) {
                        return true;
                    }

                    public boolean other() {
                        return false;
                    }
                }
            }
            """;
    String INPUT18 = """
            package org.e2immu.analyser.resolver.testexample;

            // identical to _21, but for the Analyser. qualifier
            public class MethodCall_22 {

                interface Analyser {
                    record SharedState(int iteration, String context) {}
                }

                static class MethodAnalyser implements Analyser {

                    public boolean method(Analyser.SharedState sharedState) {
                        return true;
                    }

                    public boolean other() {
                        return false;
                    }
                }
            }
            """;

    String INPUT19 = """
            package org.e2immu.analyser.resolver.testexample;

            public class MethodCall_21 {

                interface Analyser {
                    record SharedState(int iteration, String context) {}
                }

                static class MethodAnalyser implements Analyser {

                    public boolean method(SharedState sharedState) {
                        return true;
                    }

                    public boolean other() {
                        return false;
                    }
                }
            }
            """;
}



