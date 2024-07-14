package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.MethodCall;
import org.e2immu.language.cst.api.expression.VariableExpression;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.api.statement.TryStatement;
import org.e2immu.language.cst.api.variable.DependentVariable;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseTryResource extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              class A implements AutoCloseable {
                  void close() { }
              }
              public static void main(String[] args) {
                try(A a = new A()) {
                   System.out.println(a);
                } catch(Exception e) {
                   System.out.println("exception"+args[0]);
                } finally {
                   System.out.println("bye");
                }
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo main = typeInfo.findUniqueMethod("main", 1);
        if (main.methodBody().statements().get(0) instanceof TryStatement tryStatement) {
            assertEquals(1, tryStatement.resources().size());
            assertEquals("0.0.0", tryStatement.resources().get(0).source().index());
            if (tryStatement.block().statements().get(0) instanceof ExpressionAsStatement eas) {
                if (eas.expression() instanceof MethodCall mc) {
                    assertEquals("a", mc.parameterExpressions().get(0).toString());
                }
                assertEquals("0.1.0", eas.source().index());
            } else fail();
            assertEquals(1, tryStatement.catchClauses().size());
            assertEquals(1, tryStatement.catchClauses().get(0).block().statements().size());
            assertEquals(1, tryStatement.finallyBlock().statements().size());

            assertEquals("""
                    try(A a=new A();){System.out.println(a);}catch(Exception e){System.out.println("exception"+args[0]);}finally{System.out.println("bye");}\
                    """, tryStatement.toString());
        } else fail();
    }


}
