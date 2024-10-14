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

package org.e2immu.bytecode.java;

import org.e2immu.bytecode.java.asm.LocalTypeMap;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.objectweb.asm.Type;

public class ExpressionFactory {

    public static Expression from(Runtime runtime, LocalTypeMap localTypeMap, Object value) {
        if (value == null) return runtime.nullConstant();
        if (value instanceof String s) return runtime.newStringConstant(s);
        if (value instanceof Integer i) return runtime.newInt(i);
        if (value instanceof Short s) return runtime.newShort(s);
        if (value instanceof Long l) return runtime.newLong(l);
        if (value instanceof Byte b) return runtime.newByte(b);
        if (value instanceof Double d) return runtime.newDouble(d);
        if (value instanceof Float f) return runtime.newFloat(f);
        if (value instanceof Character c) return runtime.newChar(c);
        if (value instanceof Boolean b) return runtime.newBoolean(b);
        if (value instanceof Type t) {
            ParameterizedType parameterizedType =
                    switch (t.getClassName()) {
                        case "boolean" -> runtime.booleanParameterizedType();
                        case "byte" -> runtime.byteParameterizedType();
                        case "char" -> runtime.charParameterizedType();
                        case "double" -> runtime.doubleParameterizedType();
                        case "float" -> runtime.floatParameterizedType();
                        case "int" -> runtime.intParameterizedType();
                        case "long" -> runtime.longParameterizedType();
                        case "short" -> runtime.shortParameterizedType();
                        case "void" -> runtime.voidParameterizedType();
                        default -> {
                            TypeInfo ti = localTypeMap.getOrCreate(t.getClassName(), LocalTypeMap.LoadMode.TRIGGER);
                            if (ti == null) {
                                throw new UnsupportedOperationException("Cannot load type " + t.getClassName());
                            }
                            yield ti.asParameterizedType();
                        }
                    };
            return runtime.newTypeExpression(parameterizedType, runtime.diamondShowAll());
        }
        throw new UnsupportedOperationException("Value " + value + " is of " + value.getClass());
    }
}
