/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.bytecode.java.asm;


import org.e2immu.bytecode.java.ResourcesImpl;
import org.e2immu.bytecode.java.TypeContextImpl;
import org.e2immu.bytecode.java.TypeMapImpl;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.cstimpl.runtime.RuntimeImpl;
import org.e2immu.inputapi.TypeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestParameterizedTypeFactory extends CommonJmodBaseTests {


    @Test
    public void testInt() {
        assertEquals(runtime.intParameterizedType(), create("I").parameterizedType);
    }

    private ParameterizedTypeFactory.Result create(String signature) {
        return ParameterizedTypeFactory.from(runtime, typeContext, byteCodeInspector.localTypeMap(),
                LocalTypeMap.LoadMode.NOW, signature);
    }

    @Test
    public void testString() {
        ParameterizedType pt = create("[Ljava/lang/String;").parameterizedType;
        assertEquals(runtime.stringTypeInfo(), pt.typeInfo());
        assertEquals(1, pt.arrays());
        assertNotNull(pt.typeInfo());
    }

    @Test
    public void testStringAndInt() {
        String desc = "[Ljava/lang/String;I";
        ParameterizedTypeFactory.Result res = create(desc);
        assertEquals(runtime.stringTypeInfo(), res.parameterizedType.typeInfo());
        assertEquals(1, res.parameterizedType.arrays());
        assertEquals('I', desc.charAt(res.nextPos));
    }

    @Test
    public void testCharArray() {
        ParameterizedType pt = create("[C").parameterizedType;
        assertEquals(runtime.charTypeInfo(), pt.typeInfo());
        assertEquals(1, pt.arrays());
    }

    @Test
    public void testGenerics() {
        ParameterizedType pt = create("Ljava/util/List<Ljava/lang/String;>;").parameterizedType;
        assertEquals("java.util.List",
                Objects.requireNonNull(pt.typeInfo()).fullyQualifiedName());
        assertEquals(1, pt.parameters().size());
        ParameterizedType tp1 = pt.parameters().get(0);
        assertNotNull(tp1.typeInfo());
        assertEquals("java.lang.String", tp1.typeInfo().fullyQualifiedName());
    }


    @Test
    public void testWildcard() {
        ParameterizedType pt = create("Ljava/lang/Class<*>;").parameterizedType;
        assertNotNull(pt.typeInfo());
        assertEquals("java.lang.Class", pt.typeInfo().fullyQualifiedName());
        assertEquals(1, pt.parameters().size());
        ParameterizedType tp0 = pt.parameters().get(0);
        assertNull(tp0.wildcard());
    }

    @Test
    public void testWildcardSuper() {
        ParameterizedType pt = create("Ljava/lang/Class<-Ljava/lang/Number;>;").parameterizedType;
        assertEquals("java.lang.Class", pt.typeInfo().fullyQualifiedName());
        assertEquals(1, pt.parameters().size());
        ParameterizedType tp0 = pt.parameters().get(0);
        assertSame(runtime.wildcardSuper(), tp0.wildcard());
    }

    @Test
    public void testWildcardExtends() {
        ParameterizedType pt = create("Ljava/lang/Class<+Ljava/lang/Number;>;").parameterizedType;
        assertEquals("java.lang.Class", pt.typeInfo().fullyQualifiedName());
        assertEquals(1, pt.parameters().size());
        ParameterizedType tp0 = pt.parameters().get(0);
        assertSame(runtime.wildcardExtends(), tp0.wildcard());
    }

    @Test
    public void testGenerics2() {
        String signature = "Ljava/util/List<Ljava/lang/String;Ljava/lang/Integer;>;";
        ParameterizedType pt = create(signature).parameterizedType;
        assertEquals("java.util.List", pt.typeInfo().fullyQualifiedName());
        assertEquals(2, pt.parameters().size());
        ParameterizedType tp1 = pt.parameters().get(0);
        assertEquals("java.lang.String", tp1.typeInfo().fullyQualifiedName());
        ParameterizedType tp2 = pt.parameters().get(1);
        assertNotNull(tp2.typeInfo());
        assertEquals("java.lang.Integer",
                tp2.typeInfo().fullyQualifiedName());
    }

    @Test
    public void testGenericsMap() {
        String signature = "Ljava/util/List<Ljava/util/Map<Ljava/lang/String;Ljava/lang/Double;>;>;";
        ParameterizedType pt = create(signature).parameterizedType;
        assertNotNull(pt.typeInfo());
        assertEquals("java.util.List", pt.typeInfo().fullyQualifiedName());
        assertEquals(1, pt.parameters().size());
        ParameterizedType tp1 = pt.parameters().get(0);
        assertNotNull(tp1.typeInfo());
        assertEquals("java.util.Map", tp1.typeInfo().fullyQualifiedName());
        assertEquals(2, tp1.parameters().size());
        ParameterizedType p1 = tp1.parameters().get(1);
        assertNotNull(p1);
        assertEquals("java.lang.Double", p1.typeInfo().fullyQualifiedName());
    }
}
