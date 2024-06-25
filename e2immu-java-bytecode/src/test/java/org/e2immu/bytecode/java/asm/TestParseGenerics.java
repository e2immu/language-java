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


import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.impl.output.QualificationImpl;
import org.e2immu.language.cst.impl.type.DiamondEnum;
import org.e2immu.language.cst.impl.type.ParameterizedTypePrinter;
import org.e2immu.language.cst.impl.type.TypeParameterImpl;
import org.e2immu.language.inspection.api.parser.TypeContext;
import org.e2immu.language.inspection.api.parser.TypeParameterMap;
import org.e2immu.support.Either;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.*;

import static org.e2immu.language.inspection.api.InspectionState.STARTING_BYTECODE;
import static org.junit.jupiter.api.Assertions.*;

public class TestParseGenerics extends CommonJmodBaseTests {

    @Test
    public void testNormalTypeParameter() {
        TypeInfo typeInfo = typeMap.get(Spliterator.class);
        assertEquals("java.util.Spliterator<T>", typeInfo.asParameterizedType(runtime)
                .printForMethodFQN(false, DiamondEnum.SHOW_ALL));
        assertEquals("java.util.Spliterator<>", typeInfo.asParameterizedType(runtime).
                printForMethodFQN(false, DiamondEnum.YES));
        assertEquals("java.util.Spliterator", typeInfo.asParameterizedType(runtime)
                .printForMethodFQN(false, DiamondEnum.NO));
    }

    @Test
    public void testWildcard() {
        TypeInfo typeInfo = typeMap.get(Collection.class);
        assertEquals("java.util.Collection<E>", typeInfo.asParameterizedType(runtime)
                .printForMethodFQN(false, DiamondEnum.SHOW_ALL));
        MethodInfo containsAll = typeInfo.methods().stream()
                .filter(m -> m.name().equals("containsAll")).findFirst().orElseThrow();
        assertEquals("java.util.Collection.containsAll(java.util.Collection<?>)", containsAll.fullyQualifiedName());
    }

    @Test
    public void testExtends1() {
        TypeInfo typeInfo = typeMap.get(Collection.class);
        MethodInfo addAll = typeInfo.methods().stream().filter(m -> m.name().equals("addAll")).findFirst().orElseThrow();
        assertEquals("java.util.Collection.addAll(java.util.Collection<? extends E>)", addAll.fullyQualifiedName());
    }

    @Test
    public void testExtends2() throws URISyntaxException {
        TypeInfo typeInfo = typeMap.get(EnumMap.class);

        String signature = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/io/Serializable;Ljava/lang/Cloneable;";
        ParseGenerics parseGenerics = new ParseGenerics(runtime, new TypeParameterContext(), typeInfo,
                byteCodeInspector.localTypeMap(), LocalTypeMap.LoadMode.NOW);
        int expected = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        assertEquals(expected, pos);

        TypeParameter K = typeInfo.typeParameters().get(0);
        assertEquals(1, K.typeBounds().size());
        ParameterizedType typeBoundK = K.typeBounds().get(0);
        assertNull(typeBoundK.wildcard());

        Set<TypeParameter> visited = new HashSet<>();
        visited.add(K);
        assertEquals("Enum<K>", ParameterizedTypePrinter.print(
                QualificationImpl.FULLY_QUALIFIED_NAMES, typeBoundK, false, DiamondEnum.SHOW_ALL,
                false, visited).toString());
        assertSame(K, typeBoundK.parameters().get(0).typeParameter());

        ParameterizedType pt = typeInfo.asParameterizedType(runtime);
        assertEquals("java.util.EnumMap<K extends Enum<K>,V>",
                pt.printForMethodFQN(false, DiamondEnum.SHOW_ALL));
    }

    @Test
    public void testSuper() {
        TypeInfo sortedSet = typeMap.get(SortedSet.class);
        MethodInfo comparator = sortedSet.methods().stream().filter(m -> m.name().equals("comparator"))
                .findFirst().orElseThrow();
        assertEquals("java.util.Comparator<? super E>",
                comparator.returnType().printForMethodFQN(false, DiamondEnum.SHOW_ALL));
    }

    /*
      <T:Ljava/lang/Object;T_CONS:Ljava/lang/Object;T_SPLITR::Ljava/util/Spliterator$OfPrimitive<TT;TT_CONS;TT_SPLITR;>;>Ljava/lang/Object;Ljava/util/Spliterator<TT;>;

     The double colon indicates that there is no extension for a class, but there is one for an interface (OfPrimitive is a sub-interface of Spliterator)
     */
    @Test
    public void testExtends3() {
        TypeInfo typeInfo = typeMap.get(Spliterator.OfPrimitive.class);
        ParameterizedType pt = typeInfo.asParameterizedType(runtime);

        TypeParameter splitter = typeInfo.typeParameters().get(2);
        ParameterizedType typeBoundSplitter = splitter.typeBounds().get(0);
        assertNull(typeBoundSplitter.wildcard());

        assertSame(splitter, typeBoundSplitter.parameters().get(2).typeParameter());

        assertEquals("java.util.Spliterator.OfPrimitive<T,T_CONS,T_SPLITR extends java.util.Spliterator.OfPrimitive<T,T_CONS,T_SPLITR>>",
                pt.printForMethodFQN(false, DiamondEnum.SHOW_ALL));
    }

    @Test
    public void testGenericsAbstractClassLoaderValue() throws URISyntaxException {
        TypeParameterContext context = new TypeParameterContext();
        TypeInfo typeInfo = runtime.newTypeInfo(runtime.newCompilationUnitBuilder().setPackageName("jdk.internal.loader").build(), "AbstractClassLoaderValue");
        context.add(new TypeParameterImpl(0, "V", Either.left(typeInfo)).builder().commit());
        context.add(new TypeParameterImpl(1, "CLV", Either.left(typeInfo)).builder().commit());

        ByteCodeInspectorImpl byteCodeInspector = new ByteCodeInspectorImpl(runtime, classPath, null, typeMap);
        typeMap.add(typeInfo, STARTING_BYTECODE);

        ParseGenerics parseGenerics = new ParseGenerics(runtime, context, typeInfo,
                byteCodeInspector.localTypeMap(), LocalTypeMap.LoadMode.NOW);
        String signature = "<K:Ljava/lang/Object;>Ljdk/internal/loader/AbstractClassLoaderValue<Ljdk/internal/loader/AbstractClassLoaderValue<TCLV;TV;>.Sub<TK;>;TV;>;";

        int expected = "<K:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        assertEquals(expected, pos);
    }
}
