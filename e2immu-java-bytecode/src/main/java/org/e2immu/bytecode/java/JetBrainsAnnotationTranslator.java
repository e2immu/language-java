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


import org.e2immu.annotation.NotNull;
import org.e2immu.bytecode.java.asm.MyMethodVisitor;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.inspection.api.resource.AnnotationStore;

import java.util.Arrays;
import java.util.List;

public class JetBrainsAnnotationTranslator {
    private static final String ORG_JETBRAINS_ANNOTATIONS_NOTNULL = "org.jetbrains.annotations.NotNull";
    private static final String E2IMMU = "org.e2immu.annotation";

    private final Runtime runtime;

    public JetBrainsAnnotationTranslator(Runtime runtime) {
        this.runtime = runtime;
    }

    public <T extends Info.Builder<T>> void mapAnnotations(List<AnnotationStore.Annotation> annotations, Info.Builder<T> inspectionBuilder) {
        for (AnnotationStore.Annotation annotation : annotations) {
            mapAnnotation(annotation, inspectionBuilder);
        }
    }

    private <T extends Info.Builder<? extends T>> void mapAnnotation(AnnotationStore.Annotation annotation, Info.Builder<T> inspectionBuilder) {
        if (ORG_JETBRAINS_ANNOTATIONS_NOTNULL.equals(annotation.name())) {
            if (inspectionBuilder instanceof MyMethodVisitor.ParamBuilder) {
                inspectionBuilder.addAnnotation(runtime.e2immuAnnotation(NotNull.class.getCanonicalName()));
            }
        } else if (annotation.name().startsWith(E2IMMU)) {
            inspectionBuilder.addAnnotation(toAnnotationExpression(annotation));
        }
    }

    private AnnotationExpression toAnnotationExpression(AnnotationStore.Annotation annotation) {
        AnnotationExpression withoutParams = runtime.e2immuAnnotation(annotation.name());
        AnnotationExpression.Builder builder = runtime.newAnnotationExpressionBuilder()
                .setTypeInfo(withoutParams.typeInfo());
        builder.addKeyValuePair(runtime.e2aContract(), runtime.constantTrue());
        for (AnnotationStore.KeyValuePair value : annotation.values()) {
            builder.addKeyValuePair(value.name(), convert(value.value()));
        }
        return builder.build();
    }

    private Expression convert(String string) {
        if ("true".equals(string)) return runtime.constantTrue();
        if ("false".equals(string)) return runtime.constantFalse();
        if (string.length() > 2 && string.charAt(0) == '{' && string.charAt(string.length() - 1) == '}') {
            String[] splitComma = string.substring(1, string.length() - 1).split(",");
            return runtime.newArrayInitializer(Arrays.stream(splitComma).map(this::convert).toList(), runtime.objectParameterizedType());
        }
        try {
            return runtime.newInt(Integer.parseInt(string));
        } catch (NumberFormatException nfe) {
            // that's ok
        }
        return runtime.newStringConstant(string);
    }
}
