package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.Lambda;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ReturnStatement;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseLambdaExpression extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            import java.util.function.Function;
            class C {
              String s;
              Function<C, String> mapper(int k) {
                 int lv = k*2;
                 return t -> t+s+k+lv;
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        test(typeInfo);
    }

    private static void test(TypeInfo typeInfo) {
        MethodInfo mapper = typeInfo.findUniqueMethod("mapper", 1);
        assertEquals("Type java.util.function.Function<a.b.C,String>", mapper.returnType().toString());
        if (mapper.methodBody().statements().get(1) instanceof ReturnStatement rs
            && rs.expression() instanceof Lambda lambda) {
            assertEquals("t->t+this.s+k+lv", lambda.toString());
            assertEquals("a.b.C.$1.apply(a.b.C)", lambda.methodInfo().fullyQualifiedName());
            assertEquals("Type String", lambda.methodInfo().returnType().toString());
            assertEquals("Type String", lambda.concreteReturnType().toString());
            assertEquals(2, lambda.concreteFunctionalType().parameters().size());
            assertEquals("Type java.util.function.Function<a.b.C,String>", lambda.concreteFunctionalType().toString());
            assertSame(mapper, lambda.methodInfo().typeInfo().enclosingMethod());
        } else fail();
    }

    // there should be no difference with INPUT
    @Language("java")
    private static final String INPUT_2 = """
            package a.b;
            import java.util.function.Function;
            class C {
              String s;
              Function<C, String> mapper(int k) {
                 int lv = k*2;
                 return t -> {
                     return t+s+k+lv;
                 };
              }
            }
            """;

    @Test
    public void test2() {
        TypeInfo typeInfo = parse(INPUT_2);
        test(typeInfo);
    }

}
