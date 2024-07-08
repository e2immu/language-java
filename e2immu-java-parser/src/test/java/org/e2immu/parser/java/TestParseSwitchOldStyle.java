package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.api.statement.IfElseStatement;
import org.e2immu.language.cst.api.statement.SwitchStatementNewStyle;
import org.e2immu.language.cst.api.statement.SwitchStatementOldStyle;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseSwitchOldStyle extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public static void main(String[] args) {
                switch(args.length) {
                  case 0:
                    System.out.println("zero!");
                  case 1, 2:
                  case 3:
                    System.out.println("less than 3");
                    return;
                  default:
                    System.out.println("all the rest");
                }
              }
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo main = typeInfo.findUniqueMethod("main", 1);
        if (main.methodBody().statements().get(0) instanceof SwitchStatementOldStyle sso) {
            assertEquals("switch(args.length){case 0:System.out.println(\"zero!\");case 1:case 2:case 3:System.out.println(\"less than 3\");return;default:System.out.println(\"all the rest\");}",
                    sso.print(runtime.qualificationDoNotQualifyImplicit()).toString());
        } else fail();
    }


    @Language("java")
    private static final String INPUT2 = """
            package org.e2immu.analyser.resolver.testexample;

            public class OldSwitchStatement_0 {

                public static String method(int dataType) {
                    String s;
                    a:
                    switch (dataType) {

                        case 3: {
                            s = "x";
                            break;
                        }

                        case 4:
                            s = "z";
                            b:
                            break a;

                        default:
                            s = "y";

                    }
                    return s;
                }

            }
            """;

    @Test
    public void test2() {
        TypeInfo typeInfo = parse(INPUT2);
        MethodInfo main = typeInfo.findUniqueMethod("method", 1);
        if (main.methodBody().statements().get(1) instanceof SwitchStatementNewStyle ssn) {
            assertEquals("""
                            switch(dataType){case 3->{s="x";}case 4->s="z";default->s="y";}\
                            """,
                    ssn.print(runtime.qualificationDoNotQualifyImplicit()).toString());
        } else fail();
    }
}
