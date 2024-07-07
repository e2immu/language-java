package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.SwitchExpression;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.api.statement.ReturnStatement;
import org.e2immu.language.cst.api.statement.SwitchStatementNewStyle;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestParseSwitchExpression extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public static int method(String[] args) {
                return switch(args.length) {
                  case 0 -> -1;
                  case 1, 2 -> {
                    System.out.println("less than 3");
                    yield -2;
                  }
                  default ->
                  // noinspection ALL
                  {
                    System.out.println("all the rest");
                    yield 1;
                  }
                };
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo main = typeInfo.findUniqueMethod("method", 1);
        if (main.methodBody().statements().get(0) instanceof ReturnStatement rs
            && rs.expression() instanceof SwitchExpression se) {
            assertEquals("""
                            switch(args.length){case 0->-1;case 1,2->{System.out.println("less than 3");yield -2;}default ->// noinspection ALL

                            {System.out.println("all the rest");yield 1;}}\
                            """,
                    se.print(runtime.qualificationDoNotQualifyImplicit()).toString());
        } else fail();
    }
}
