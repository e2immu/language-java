package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.Assignment;
import org.e2immu.language.cst.api.expression.IntConstant;
import org.e2immu.language.cst.api.expression.LongConstant;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.api.statement.LocalVariableCreation;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseLiteral extends CommonTestParse {
    @Language("java")
    String INPUT1 = """
            package a.b;
            public class X {
                public double parse() {
                    return 0xF + 001 + 0.01e-1f + 2L + 1.0e2d;
                }
            }
            """;

    @Test
    public void test8() {
        parse(INPUT1);
    }

    @Language("java")
    String INPUT2 = """
            package a.b;
            public class X {
                public double parse() {
                   int a = 0xFF_FF_FF_FF;
                   int b = 0xFFFFFFFE;
                   int i = 0xFF000000;
                   int j = 0xcafebabe;
                   return i+j;
                }
            }
            """;

    @Test
    public void test2() {
        TypeInfo X = parse(INPUT2);
        MethodInfo parse = X.findUniqueMethod("parse", 0);
        LocalVariableCreation a = (LocalVariableCreation) parse.methodBody().statements().get(0);
        IntConstant ac = (IntConstant) a.localVariable().assignmentExpression();
        assertEquals(-1, ac.constant());
        LocalVariableCreation b = (LocalVariableCreation) parse.methodBody().statements().get(1);
        IntConstant bc = (IntConstant) b.localVariable().assignmentExpression();
        assertEquals(-2, bc.constant());
        LocalVariableCreation i = (LocalVariableCreation) parse.methodBody().statements().get(2);
        IntConstant ic = (IntConstant) i.localVariable().assignmentExpression();
        assertEquals(-16777216, ic.constant());
        LocalVariableCreation j = (LocalVariableCreation) parse.methodBody().statements().get(3);
        IntConstant jc = (IntConstant) j.localVariable().assignmentExpression();
        assertEquals(-889275714, jc.constant());
        assertEquals(-9.0605293E8, ic.constant() + jc.constant());
    }

    @Test
    public void test() {
        assertEquals(0xFF000000, -16777216);
        assertEquals(0xcafebabe, -889275714);
    }

    @Language("java")
    String INPUT3 = """
            package a.b;
            public class X {
                public long parse(long l) {
                    long ll = l;
                    ll &= 0x00000000FFFFFFFFl;
                    return ll;
                }
            }
            """;

    @Test
    public void test3() {
        TypeInfo typeInfo = parse(INPUT3);
        MethodInfo parse = typeInfo.findUniqueMethod("parse", 1);
        if (parse.methodBody().statements().get(1) instanceof ExpressionAsStatement eas
            && eas.expression() instanceof Assignment a) {
            assertInstanceOf(LongConstant.class, a.value());
            assertEquals("ll&=4294967295L", a.toString());
        } else fail();
    }
}
