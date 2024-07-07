package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.SwitchStatementOldStyle;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestParseSwitchNewStyle extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            class C {
              public static void main(String[] args) {
                switch(args.length) {
                  case 0 ->
                    System.out.println("zero!");
                  case 1, 2 -> {
                    System.out.println("less than 3");
                    return;
                  }
                  default ->
                  // noinspection ALL
                  {
                    System.out.println("all the rest");
                  }
                }
                System.out.println("end");
              }
            }
            """;

    @Disabled
    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo main = typeInfo.findUniqueMethod("main", 1);
        if (main.methodBody().statements().get(0) instanceof SwitchStatementOldStyle sso) {
            assertEquals("switch(args.length){case 0:System.out.println(\"zero!\");case 1:case 2:case 3:System.out.println(\"less than 3\");return;default:System.out.println(\"all the rest\");}",
                    sso.print(runtime.qualificationDoNotQualifyImplicit()).toString());
        } else fail();
    }
}
