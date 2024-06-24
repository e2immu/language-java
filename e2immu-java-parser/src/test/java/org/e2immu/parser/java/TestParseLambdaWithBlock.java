package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.Lambda;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ReturnStatement;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestParseLambdaWithBlock extends CommonTestParse {


    @Language("java")
    private static final String INPUT_1 = """
            package a.b;
            import java.util.function.BiConsumer;
            class C {
              BiConsumer<Integer, Integer, Integer> mapper(int k) {
                 int lv = k*2;
                 return (i,j) -> {
                     System.out.println("lv+t", i+j+lv);
                 };
              }
            }
            """;

    @Test
    public void test1() {
        TypeInfo typeInfo = parse(INPUT_1);
        test(typeInfo);
    }


    private static void test(TypeInfo typeInfo) {
        MethodInfo mapper = typeInfo.findUniqueMethod("mapper", 1);
        assertEquals("BiConsumer<Integer,Integer,Integer>", mapper.returnType().toString());
        if (mapper.methodBody().statements().get(1) instanceof ReturnStatement rs
            && rs.expression() instanceof Lambda lambda) {

        } else fail();
    }

    @Language("java")
    private static final String INPUT_2 = """
            package a.b;
            import java.util.function.BiConsumer;
            class C {
              BiConsumer<Integer, Integer, Integer> mapper(int k) {
                 int lv = k*2;
                 return (int i,int j) -> {
                     System.out.println("lv+t", i+j+lv);
                 };
              }
            }
            """;

    @Test
    public void test2() {
        TypeInfo typeInfo = parse(INPUT_2);
        test(typeInfo);
    }


    @Language("java")
    private static final String INPUT_3 = """
            package a.b;
            import java.util.function.BiConsumer;
            class C {
              BiConsumer<Integer, Integer, Integer> mapper(int k) {
                 int lv = k*2;
                 return (var i,var j) -> {
                     System.out.println("lv+t", i+j+lv);
                 };
              }
            }
            """;

    @Test
    public void test3() {
        TypeInfo typeInfo = parse(INPUT_3);
        test(typeInfo);
    }
}
