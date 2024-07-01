package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
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
}
