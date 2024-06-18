package org.e2immu.parser.java;

import org.e2immu.cstapi.expression.BooleanConstant;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.statement.*;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseFor extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public static void main(String[] args) {
                for(int i=0; i<args.length; i++) {
                  System.out.println(args[i]);
                }
              }
            }
            """;


    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo main = typeInfo.findUniqueMethod("main", 1);
        if (main.methodBody().statements().get(0) instanceof ForStatement s) {
            assertEquals(1, s.initializers().size());
            assertInstanceOf(LocalVariableCreation.class, s.initializers().get(0));
            assertEquals("int i=0", s.initializers().get(0).toString());

            assertEquals("i<args.length", s.expression().toString());

            assertEquals("i++", s.updaters().get(0).toString());
        } else fail();
    }

    @Language("java")
    private static final String INPUT2 = """
            package a.b;
            class C {
              public static void main(String[] args) {
                for(int i=0, j=10; i<args.length && j>0; i++, --j) {
                  System.out.println(args[i]);
                }
              }
            }
            """;

    @Test
    public void test2() {
        TypeInfo typeInfo = parse(INPUT2);
        MethodInfo main = typeInfo.findUniqueMethod("main", 1);
        if (main.methodBody().statements().get(0) instanceof ForStatement s) {
            assertEquals(1, s.initializers().size());
            assertInstanceOf(LocalVariableCreation.class, s.initializers().get(0));
            assertEquals("int i=0,j=10", s.initializers().get(0).toString());

            assertEquals("j>0&&i<args.length", s.expression().toString());

            assertEquals("i++", s.updaters().get(0).toString());
            assertEquals("--j", s.updaters().get(1).toString());
        } else fail();
    }

    @Language("java")
    private static final String INPUT3 = """
            package a.b;
            class C {
              public static void main(String[] args) {
                int i, j;
                for(i=0, j=10; i<args.length && j>0; ) {
                  System.out.println(args[i]);
                  i++;
                  j -= 2;
                }
              }
            }
            """;

    @Test
    public void test3() {
        TypeInfo typeInfo = parse(INPUT3);
        MethodInfo main = typeInfo.findUniqueMethod("main", 1);
        assertEquals(2, main.methodBody().statements().size());
        if (main.methodBody().statements().get(1) instanceof ForStatement s) {
            assertEquals(2, s.initializers().size());
            assertEquals("j>0&&i<args.length", s.expression().toString());
            assertTrue(s.updaters().isEmpty());
        } else fail();
    }


    @Language("java")
    private static final String INPUT4 = """
            package a.b;
            class C {
              public static void main(String[] args) {
                int i = 0, j = 10;
                for( ; ; ) {
                  if(i >= args.length || j <= 0) break;
                  if(i == 1) continue;
                  System.out.println(args[i]);
                  i++;
                  j -= 2;
                }
              }
            }
            """;

    @Test
    public void test4() {
        TypeInfo typeInfo = parse(INPUT4);
        MethodInfo main = typeInfo.findUniqueMethod("main", 1);
        assertEquals(2, main.methodBody().statements().size());
        if (main.methodBody().statements().get(1) instanceof ForStatement s) {
            assertTrue(s.initializers().isEmpty());
            assertTrue(s.expression() instanceof BooleanConstant bc && bc.constant());
            assertTrue(s.updaters().isEmpty());

            if (s.block().statements().get(0) instanceof IfElseStatement ifElse) {
                assertInstanceOf(BreakStatement.class, ifElse.block().statements().get(0));
            } else fail();
            if (s.block().statements().get(1) instanceof IfElseStatement ifElse) {
                assertInstanceOf(ContinueStatement.class, ifElse.block().statements().get(0));
            } else fail();
        } else fail();
    }
}
