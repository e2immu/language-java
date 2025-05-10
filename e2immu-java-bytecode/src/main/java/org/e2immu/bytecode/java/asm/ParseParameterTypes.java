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

import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;

import java.util.ArrayList;
import java.util.List;

record ParseParameterTypes(Runtime runtime,
                           LocalTypeMap findType,
                           LocalTypeMap.LoadMode loadMode) {
    public static final char CARET_THROWS = '^';
    public static final char CLOSE_BRACKET = ')';

    private static class IterativeParsing<R> {
        int startPos;
        int endPos;
        R result;
        boolean more;
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
                throw new UnsupportedOperationException("Found caret");
               // next.more = false;
              //  next.endPos = end;
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
