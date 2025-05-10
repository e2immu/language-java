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


import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.impl.output.QualificationImpl;
import org.e2immu.language.cst.impl.type.DiamondEnum;
import org.e2immu.language.cst.impl.type.ParameterizedTypePrinter;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseGenerics extends CommonJmodBaseTests {

    @Test
    public void testNormalTypeParameter() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(Spliterator.class);
        assertEquals("java.util.Spliterator<T>", typeInfo.asParameterizedType()
                .printForMethodFQN(false, DiamondEnum.SHOW_ALL));
        assertEquals("java.util.Spliterator<>", typeInfo.asParameterizedType()
                .printForMethodFQN(false, DiamondEnum.YES));
        assertEquals("java.util.Spliterator", typeInfo.asParameterizedType()
                .printForMethodFQN(false, DiamondEnum.NO));
    }

    @Test
    public void testWildcard() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(Collection.class);
        assertEquals("java.util.Collection<E>", typeInfo.asParameterizedType()
                .printForMethodFQN(false, DiamondEnum.SHOW_ALL));
        MethodInfo containsAll = typeInfo.methods().stream()
                .filter(m -> m.name().equals("containsAll")).findFirst().orElseThrow();
        assertEquals("java.util.Collection.containsAll(java.util.Collection<?>)", containsAll.fullyQualifiedName());
    }

    @Test
    public void testExtends1() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(Collection.class);
        MethodInfo addAll = typeInfo.methods().stream().filter(m -> m.name().equals("addAll")).findFirst().orElseThrow();
        assertEquals("java.util.Collection.addAll(java.util.Collection<? extends E>)", addAll.fullyQualifiedName());
    }

    @Test
    public void testExtends2() {
        CompilationUnit cu = runtime.newCompilationUnitBuilder().setPackageName("java.util").build();
        TypeInfo typeInfo = runtime.newTypeInfo(cu, "EnumMap");

        String signature = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/io/Serializable;Ljava/lang/Cloneable;";
        ParseGenerics parseGenerics = new ParseGenerics(runtime, new TypeParameterContext(), typeInfo,
                byteCodeInspector, LocalTypeMap.LoadMode.NOW);
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

        ParameterizedType pt = typeInfo.asParameterizedType();
        assertEquals("java.util.EnumMap<K extends Enum<K>,V>",
                pt.printForMethodFQN(false, DiamondEnum.SHOW_ALL));
    }

    @Test
    public void testSuper() {
        TypeInfo sortedSet = compiledTypesManager.getOrLoad(SortedSet.class);
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
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(Spliterator.OfPrimitive.class);
        ParameterizedType pt = typeInfo.asParameterizedType();
        String fqn = "java.util.Spliterator.OfPrimitive<T,T_CONS,T_SPLITR extends java.util.Spliterator.OfPrimitive<T,T_CONS,T_SPLITR>>";
        assertEquals("Type " + fqn, pt.toString());

        TypeParameter splitter = typeInfo.typeParameters().get(2);
        ParameterizedType typeBoundSplitter = splitter.typeBounds().get(0);
        assertNull(typeBoundSplitter.wildcard());

        assertSame(splitter, typeBoundSplitter.parameters().get(2).typeParameter());

        assertEquals(fqn, pt.printForMethodFQN(false, DiamondEnum.SHOW_ALL));
    }

    @Test
    public void testExtends4() {
        TypeInfo SliceSpliterator = compiledTypesManager.getOrLoad("java.util.stream.StreamSpliterators.SliceSpliterator");
        assertEquals(2, SliceSpliterator.typeParameters().size());

        TypeInfo OfPrimitive = SliceSpliterator.findSubType("OfPrimitive");
        /*
        String signature = """
                <T:Ljava/lang/Object;\    pos = 21
                T_SPLITR::Ljava/util/Spliterator$OfPrimitive<TT;TT_CONS;TT_SPLITR;>;\
                T_CONS:Ljava/lang/Object;>\
                Ljava/util/stream/StreamSpliterators$SliceSpliterator<TT;TT_SPLITR;>;\
                Ljava/util/Spliterator$OfPrimitive<TT;TT_CONS;TT_SPLITR;>;
                """;
        */
        // not 6!
        assertEquals(3, OfPrimitive.typeParameters().size());
    }

    @Test
    public void testGenericsAbstractClassLoaderValue() throws URISyntaxException {
        TypeParameterContext context = new TypeParameterContext();
        TypeInfo typeInfo = runtime.newTypeInfo(runtime.newCompilationUnitBuilder().
                setPackageName("jdk.internal.loader")
                .build(), "AbstractClassLoaderValue");
        context.add(runtime.newTypeParameter(0, "V", typeInfo).builder().commit());
        context.add(runtime.newTypeParameter(1, "CLV", typeInfo).builder().commit());

        compiledTypesManager.add(typeInfo);

        ParseGenerics parseGenerics = new ParseGenerics(runtime, context, typeInfo, byteCodeInspector,
                LocalTypeMap.LoadMode.NOW);
        String signature = "<K:Ljava/lang/Object;>Ljdk/internal/loader/AbstractClassLoaderValue<Ljdk/internal/loader/AbstractClassLoaderValue<TCLV;TV;>.Sub<TK;>;TV;>;";

        int expected = "<K:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        assertEquals(expected, pos);
    }

    @Test
    public void testEnumSet() {
        TypeInfo enumSet = compiledTypesManager.getOrLoad(EnumSet.class);
        MethodInfo constructor = enumSet.findConstructor(2);
        ParameterizedType p0Type = constructor.parameters().getFirst().parameterizedType();
        assertEquals("Type Class<E extends Enum<E>>", p0Type.toString());
        ParameterizedType tp0 = p0Type.parameters().getFirst();
        assertEquals("Type param E extends Enum<E>", tp0.toString());
        assertEquals("E=TP#0 in EnumSet", tp0.typeParameter().toString());
        assertSame(enumSet.typeParameters().getFirst(), tp0.typeParameter());

        TypeInfo enumSetProxy = enumSet.findSubType("SerializationProxy");
        assertEquals("E=TP#0 in SerializationProxy", enumSetProxy.typeParameters().getFirst().toString());
    }


    // example from "call" method in picocli's CommandLine class
    @Test
    public void testExtendsIterative() {
        CompilationUnit cu = runtime.newCompilationUnitBuilder().setPackageName("java.util").build();
        TypeInfo typeInfo = runtime.newTypeInfo(cu, "CommandLine");
        MethodInfo mi = runtime.newMethod(typeInfo, "call", runtime.methodTypeStaticMethod());
        MethodInfo.Builder mib = mi.builder();
        String signature = "<C::Ljava/util/concurrent/Callable<TT;>;T:Ljava/lang/Object;>(TC;[Ljava/lang/String;)TT;";
        TypeParameterContext typeContext = new TypeParameterContext();
        ParseGenerics parseGenerics = new ParseGenerics(runtime, typeContext, typeInfo,
                byteCodeInspector, LocalTypeMap.LoadMode.NOW);
        TypeParameterContext methodContext = new TypeParameterContext();
        int pos = parseGenerics.parseMethodGenerics(signature, mi, mib, runtime, methodContext) + 1;
        assertEquals(2, mib.typeParameters().size());
        // assertEquals(2, methodContext.size());
        // assertEquals("C=TP#0 in CommandLine", methodContext.getMap().get("C").toString());
        // assertEquals("T=TP#1 in CommandLine", methodContext.getMap().get("T").toString());

    }

    @Test
    public void testParse() {
        TypeParameterContext typeContext = new TypeParameterContext();
        ParseParameterTypes ppt = new ParseParameterTypes(runtime, byteCodeInspector, LocalTypeMap.LoadMode.NOW);
        ParseParameterTypes.Result r = ppt.parseParameterTypesOfMethod(typeContext,
                "(Ljava/lang/CharSequence;[Ljava/lang/CharSequence;)Ljava/lang/String;");
        assertNotNull(r);
        assertEquals(2, r.parameterTypes().size());
        assertEquals("Type String", r.returnType().toString());
    }
}
