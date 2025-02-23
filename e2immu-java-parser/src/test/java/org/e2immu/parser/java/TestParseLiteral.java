package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.*;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.api.statement.LocalVariableCreation;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseLiteral extends CommonTestParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestParseLiteral.class);

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


    String INPUT4 = "package a.b; public class X { public void parse() { String s = \"a \\\" and \\\" b\"; } }";

    @Test
    public void test4() {
        LOGGER.debug(INPUT4);
        TypeInfo typeInfo = parse(INPUT4);
        MethodInfo parse = typeInfo.findUniqueMethod("parse", 0);
        if (parse.methodBody().statements().get(0) instanceof LocalVariableCreation lvc) {
            if (lvc.localVariable().assignmentExpression() instanceof StringConstant sc) {
                assertEquals("a \" and \" b", sc.constant());
            } else fail();
        } else fail();
    }

    @Language("java")
    String INPUT5 = """
            package a.b;
            public class X {
                public void parse() {
                    String s = \"""
                        abc
                           12"3"
                        \""";
                }
            }
            """;

    @DisplayName("text block, basics")
    @Test
    public void test5() {
        LOGGER.debug(INPUT5);
        TypeInfo typeInfo = parse(INPUT5);
        MethodInfo parse = typeInfo.findUniqueMethod("parse", 0);
        if (parse.methodBody().statements().get(0) instanceof LocalVariableCreation lvc) {
            if (lvc.localVariable().assignmentExpression() instanceof TextBlock tb) {
                assertEquals("abc\n   12\"3\"\n", tb.constant());
                assertEquals("TextBlockFormattingImpl[lineBreaks=[], optOutWhiteSpaceStripping=false, trailingClosingQuotes=false]",
                        tb.textBlockFormatting().toString());
            } else fail();
        } else fail();
    }


    @Language("java")
    String INPUT5B = """
            package a.b;
            public class X {
                public void parse() {
                    String s = \"""  
                        abc
                
                        def
                        
                           123
                        \""";
                }
            }
            """;

    @DisplayName("text block, extra spacing and blank lines")
    @Test
    public void test5B() {
        TypeInfo typeInfo = parse(INPUT5B);
        MethodInfo parse = typeInfo.findUniqueMethod("parse", 0);
        if (parse.methodBody().statements().get(0) instanceof LocalVariableCreation lvc) {
            if (lvc.localVariable().assignmentExpression() instanceof TextBlock tb) {
                assertEquals("abc\n\ndef\n\n   123\n", tb.constant());
                assertEquals("TextBlockFormattingImpl[lineBreaks=[], optOutWhiteSpaceStripping=false, trailingClosingQuotes=false]",
                        tb.textBlockFormatting().toString());
            } else fail();
        } else fail();
    }


    @Language("java")
    String INPUT6 = """
            package a.b;
            public class X {
                public void parse() {
                    String s = \"""
                        abc
                        def
                           123\""";
                }
            }
            """;

    @DisplayName("text block, trailing quotes")
    @Test
    public void test6() {
        LOGGER.debug(INPUT6);
        TypeInfo typeInfo = parse(INPUT6);
        MethodInfo parse = typeInfo.findUniqueMethod("parse", 0);
        if (parse.methodBody().statements().get(0) instanceof LocalVariableCreation lvc) {
            if (lvc.localVariable().assignmentExpression() instanceof TextBlock tb) {
                assertEquals("abc\ndef\n   123", tb.constant());
                assertEquals("TextBlockFormattingImpl[lineBreaks=[], optOutWhiteSpaceStripping=false, trailingClosingQuotes=true]",
                        tb.textBlockFormatting().toString());
            } else fail();
        } else fail();
    }


    @Language("java")
    String INPUT6B = """
            package a.b;
            public class X {
                public void parse() {
                    String s = \"""
                        abc
                        def
                           123\\
                        \""";
                }
            }
            """;

    @DisplayName("text block, trailing quotes with backslash")
    @Test
    public void test6B() {
        assertTrue(INPUT6B.contains("\\"));
        TypeInfo typeInfo = parse(INPUT6B);
        MethodInfo parse = typeInfo.findUniqueMethod("parse", 0);
        if (parse.methodBody().statements().get(0) instanceof LocalVariableCreation lvc) {
            if (lvc.localVariable().assignmentExpression() instanceof TextBlock tb) {
                assertEquals("abc\ndef\n   123", tb.constant());
                assertEquals("TextBlockFormattingImpl[lineBreaks=[], optOutWhiteSpaceStripping=false, trailingClosingQuotes=true]",
                        tb.textBlockFormatting().toString());
            } else fail();
        } else fail();
    }
}
