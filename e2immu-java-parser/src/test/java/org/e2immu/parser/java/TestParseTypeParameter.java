package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.MethodCall;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.ExpressionAsStatement;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseTypeParameter extends CommonTestParse {

    @Language("java")
    private static final String INPUT = """
            package a.b;
            public interface C<T> {
              T t();
            }
            """;

    @Test
    public void test() {
        TypeInfo typeInfo = parse(INPUT);
        MethodInfo t = typeInfo.findUniqueMethod("t", 0);
        assertEquals("T", t.returnType().fullyQualifiedName());
        assertTrue(t.returnType().isTypeParameter());
    }

    @Language("java")
    private static final String INPUT2 = """
            package a.b;
            public interface C<T, @SuppressWarnings("?") U extends Class<?>> {
              T t();
              U u();
            }
            """;

    @Test
    public void test2() {
        TypeInfo typeInfo = parse(INPUT2);
        MethodInfo u = typeInfo.findUniqueMethod("u", 0);
        ParameterizedType pt = u.returnType();
        TypeParameter tp = pt.typeParameter();
        assertEquals("U", tp.simpleName());
        assertNull(pt.typeInfo());
        assertNull(pt.wildcard());
        assertTrue(pt.parameters().isEmpty());
        assertTrue(pt.isTypeParameter());

        assertEquals(1, tp.typeBounds().size());
        ParameterizedType typeBound = tp.typeBounds().get(0);
        assertEquals(1, typeBound.parameters().size());
        assertSame(runtime.parameterizedTypeWildcard(), typeBound.parameters().get(0));
        assertNull(typeBound.wildcard());
        assertNull(typeBound.typeParameter());
        assertEquals("java.lang.Class", typeBound.typeInfo().fullyQualifiedName());
        assertEquals("U extends Class<?>", pt.fullyQualifiedName());

        assertEquals(1, pt.typeParameter().annotations().size());
        assertEquals("@SuppressWarnings(\"?\")", pt.typeParameter().annotations().get(0).toString());
    }

    @Language("java")
    private static final String INPUT3 = """
            package org.e2immu.analyser.resolver.testexample;
            
            public class TypeParameter_3 {
                enum Visibility {
                    NONE;
                }
            
                interface SerializationConfig {
                    VisibilityChecker<?> getDefaultVisibilityChecker();
                }
            
                // from com.fasterxml.jackson.databind.introspect
                interface VisibilityChecker<T extends VisibilityChecker<T>> {
                    T withGetterVisibility(Visibility v);
            
                    T withSetterVisibility(Visibility v);
                }
            
                static class ObjectMapper {
                    public void setVisibilityChecker(VisibilityChecker<?> vc) {
            
                    }
            
                    public SerializationConfig getSerializationConfig() {
                        return null;
                    }
            
                }
            
                private final ObjectMapper mapper = new ObjectMapper();
            
                // CRASH
                public void method1() {
                     mapper.setVisibilityChecker(mapper.getSerializationConfig().getDefaultVisibilityChecker().
                             withGetterVisibility(Visibility.NONE).
                             withSetterVisibility(Visibility.NONE));
                 }
            
                // NO METHOD FOUND
                public void method2() {
                    VisibilityChecker<?> o = mapper.getSerializationConfig().getDefaultVisibilityChecker().
                            withGetterVisibility(Visibility.NONE);
                    mapper.setVisibilityChecker(o.withSetterVisibility(Visibility.NONE));
                }
            
                public void method3() {
                    VisibilityChecker<?> o = mapper.getSerializationConfig().getDefaultVisibilityChecker().
                            withGetterVisibility(Visibility.NONE);
                    VisibilityChecker<?> vc = o.withSetterVisibility(Visibility.NONE);
                    mapper.setVisibilityChecker(vc);
                }
            }
            """;

    @Test
    public void test3() {
        TypeInfo typeInfo = parse(INPUT3);
        TypeInfo objectMapper = typeInfo.findSubType("ObjectMapper");
        MethodInfo setVc = objectMapper.findUniqueMethod("setVisibilityChecker", 1);
        MethodInfo u = typeInfo.findUniqueMethod("method1", 0);
        if (u.methodBody().statements().get(0) instanceof ExpressionAsStatement eas && eas.expression() instanceof MethodCall mc) {
            assertSame(setVc, mc.methodInfo());
        } else fail();
    }

    @Language("java")
    public static final String INPUT4 = """
            package a.b;
            import java.util.Hashtable;
            class X {
            public static <K, V> Hashtable<K, V> copy(Hashtable<K, V> map) {
              Hashtable<K, V> copy = new Hashtable<K, V>();
              return copy;
            }
            }
            """;

    @Test
    public void test4() {
        TypeInfo typeInfo = parse(INPUT4);
        MethodInfo copy = typeInfo.findUniqueMethod("copy", 1);
        assertEquals(2, copy.typeParameters().size());
        assertEquals("a.b.X.copy(java.util.Hashtable<K,V>)", copy.fullyQualifiedName());
        assertEquals("""
                public static <K,V> Hashtable<K,V> copy(Hashtable<K,V> map){Hashtable<K,V> copy=new Hashtable<K,V>();return copy;}\
                """, copy.print(runtime.qualificationQualifyFromPrimaryType()).toString());
        Block newBody = runtime.emptyBlock();
        MethodInfo copyNewBody = copy.withMethodBody(newBody);
        assertEquals("""
                public static <K,V> Hashtable<K,V> copy(Hashtable<K,V> map){}\
                """, copyNewBody.print(runtime.qualificationQualifyFromPrimaryType()).toString());
    }


    @Language("java")
    public static final String INPUT5 = """
            package a.b;
            import java.util.Hashtable;
            class X {
              class Class$<@SuppressWarnings("?") T> {
            
              }
            }
            """;

    @Test
    public void test5() {
        TypeInfo typeInfo = parse(INPUT5);
        TypeInfo clazz = typeInfo.findSubType("Class$");
        TypeParameter tp = clazz.typeParameters().getFirst();
        assertEquals(1, tp.annotations().size());
    }

}
