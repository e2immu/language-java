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
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

record ParseGenerics(Runtime runtime,
                     TypeParameterContext typeContext,
                     TypeInfo typeInfo,
                     LocalTypeMap findType,
                     LocalTypeMap.LoadMode loadMode) {
    public static final char COLON = ':';
    public static final char GT_END_TYPE_PARAMS = '>';
    public static final char CARET_THROWS = '^';
    public static final char CLOSE_BRACKET = ')';

    private static class IterativeParsing<R> {
        int startPos;
        int endPos;
        R result;
        boolean more;
        String name;
        boolean typeNotFoundError;
    }

    int parseTypeGenerics(String signature) {
        IterativeParsing<TypeParameter> iterativeParsing = new IterativeParsing<>();
        while (true) {
            iterativeParsing.startPos = 1;
            AtomicInteger index = new AtomicInteger();
            do {
                iterativeParsing = iterativelyParseGenerics(signature,
                        iterativeParsing,
                        name -> {
                            TypeParameter typeParameter = runtime.newTypeParameter(index.getAndIncrement(), name, typeInfo);
                            typeContext.add(typeParameter);
                            typeInfo.builder().addTypeParameter(typeParameter);
                            return typeParameter;
                        },
                        runtime,
                        findType,
                        loadMode);
                if (iterativeParsing == null) {
                    return -1; // error state
                }
            } while (iterativeParsing.more);
            if (!iterativeParsing.typeNotFoundError) break;
            iterativeParsing = new IterativeParsing<>();
        }
        return iterativeParsing.endPos;
    }

    private IterativeParsing<TypeParameter> iterativelyParseGenerics(String signature,
                                                                     IterativeParsing<TypeParameter> iterativeParsing,
                                                                     Function<String, TypeParameter> createTypeParameterAndAddToContext,
                                                                     Runtime runtime,
                                                                     LocalTypeMap localTypeMap,
                                                                     LocalTypeMap.LoadMode loadMode) {
        int end = signature.indexOf(COLON, iterativeParsing.startPos);
        char atEnd = COLON;

        boolean typeNotFoundError = iterativeParsing.typeNotFoundError;
        // example for extends keyword: sig='<T::Ljava/lang/annotation/Annotation;>(Ljava/lang/Class<TT;>;)TT;' for
        // method getAnnotation in java.lang.reflect.AnnotatedElement

        String name = signature.substring(iterativeParsing.startPos, end);
        TypeParameter typeParameter = createTypeParameterAndAddToContext.apply(name);
        List<ParameterizedType> typeBounds = new ArrayList<>();

        IterativeParsing<TypeParameter> next = new IterativeParsing<>();
        next.name = name;

        while (atEnd == COLON) {
            char charAfterColon = signature.charAt(end + 1);
            if (charAfterColon == COLON) { // this can happen max. once, when there is no class extension, but there are interface extensions
                end++;
            }
            ParameterizedTypeFactory.Result result = ParameterizedTypeFactory.from(runtime, typeContext, localTypeMap,
                    loadMode, signature.substring(end + 1));
            if (result == null) return null; // unable to load type
            if (result.parameterizedType.typeInfo() != null
                && !result.parameterizedType.typeInfo().isJavaLangObject()) {
                typeBounds.add(result.parameterizedType);
            }

            end = result.nextPos + end + 1;
            atEnd = signature.charAt(end);

            next.typeNotFoundError = typeNotFoundError || result.typeNotFoundError;
        }

        typeParameter.builder().setTypeBounds(List.copyOf(typeBounds)).commit();
        next.result = typeParameter;


        if (GT_END_TYPE_PARAMS == atEnd) {
            next.more = false;
            next.endPos = end;
        } else {
            next.more = true;
            next.startPos = end;
        }
        return next;
    }


    // result should be
    // entrySet()                                       has a complicated return type, but that is skipped
    // addFirst(E)                                      type parameter of interface/class as first argument
    // ArrayList(java.util.Collection<? extends E>)     this is a constructor
    // copyOf(U[], int, java.lang.Class<? extends T[]>) spaces between parameter types

    int parseMethodGenerics(String signature,
                            MethodInfo methodInfo,
                            MethodInfo.Builder methodInspectionBuilder,
                            Runtime runtime,
                            TypeParameterContext methodContext) {
        IterativeParsing<TypeParameter> iterativeParsing = new IterativeParsing<>();
        while (true) {
            iterativeParsing.startPos = 1;
            AtomicInteger index = new AtomicInteger();
            do {
                iterativeParsing = iterativelyParseGenerics(signature,
                        iterativeParsing, name -> {
                            TypeParameter typeParameter = runtime.newTypeParameter(index.getAndIncrement(), name, methodInfo);
                            methodInspectionBuilder.addTypeParameter(typeParameter);
                            methodContext.add(typeParameter);
                            return typeParameter;
                        },
                        runtime,
                        findType,
                        loadMode);
                if (iterativeParsing == null) {
                    return -1; // error state
                }
            } while (iterativeParsing.more);
            if (!iterativeParsing.typeNotFoundError) break;
            iterativeParsing = new IterativeParsing<>();
        }
        return iterativeParsing.endPos;
    }

    List<ParameterizedType> parseParameterTypesOfMethod(TypeParameterContext typeContext, String signature) {
        if (signature.startsWith("()")) {
            ParameterizedTypeFactory.Result from = ParameterizedTypeFactory.from(runtime, typeContext,
                    findType, loadMode, signature.substring(2));
            if (from == null) return null;
            return List.of(from.parameterizedType);
        }
        List<ParameterizedType> methodTypes = new ArrayList<>();

        IterativeParsing<ParameterizedType> iterativeParsing = new IterativeParsing<>();
        iterativeParsing.startPos = 1;
        do {
            iterativeParsing = iterativelyParseMethodTypes(typeContext, signature, iterativeParsing);
            if (iterativeParsing == null) return null;
            methodTypes.add(iterativeParsing.result);
        } while (iterativeParsing.more);
        return methodTypes;
    }

    private IterativeParsing<ParameterizedType> iterativelyParseMethodTypes(TypeParameterContext typeContext,
                                                                            String signature,
                                                                            IterativeParsing<ParameterizedType> iterativeParsing) {
        ParameterizedTypeFactory.Result result = ParameterizedTypeFactory.from(runtime, typeContext,
                findType, loadMode, signature.substring(iterativeParsing.startPos));
        if (result == null) return null;
        int end = iterativeParsing.startPos + result.nextPos;
        IterativeParsing<ParameterizedType> next = new IterativeParsing<>();
        next.result = result.parameterizedType;
        if (end >= signature.length()) {
            next.more = false;
            next.endPos = end;
        } else {
            char atEnd = signature.charAt(end);
            if (atEnd == CARET_THROWS) {
                // FIXME: this marks the "throws" block, which we're NOT parsing at the moment!!
                next.more = false;
                next.endPos = end;
            } else {
                next.more = true;
                if (atEnd == CLOSE_BRACKET) {
                    next.startPos = end + 1;
                } else {
                    next.startPos = end;
                }
            }
        }
        return next;
    }
}
