package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.Lambda;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ReturnStatement;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestParseLambda extends CommonTestParse {

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
        MethodInfo mapper = typeInfo.findUniqueMethod("mapper", 1);
        assertEquals("Function<C,String>", mapper.returnType().toString());
        if (mapper.methodBody().statements().get(1) instanceof ReturnStatement rs
            && rs.expression() instanceof Lambda lambda) {
            assertEquals("t->t+this.s+k+lv", lambda.toString());
            assertEquals("a.b.C.$1.apply(C)", lambda.methodInfo().fullyQualifiedName());
            assertEquals("String", lambda.methodInfo().returnType().toString());
            assertEquals("String", lambda.concreteReturnType().toString());
            assertEquals(2, lambda.concreteFunctionalType().parameters().size());
            assertEquals("Function<C,String>", lambda.concreteFunctionalType().toString());
        } else fail();
    }
}
