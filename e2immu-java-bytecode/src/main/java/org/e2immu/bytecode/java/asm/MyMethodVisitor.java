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


import org.e2immu.bytecode.java.JetBrainsAnnotationTranslator;
import org.e2immu.cstapi.element.Comment;
import org.e2immu.cstapi.element.Source;
import org.e2immu.cstapi.expression.AnnotationExpression;
import org.e2immu.cstapi.info.*;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.inputapi.AnnotationStore;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.ASM9;

public class MyMethodVisitor extends MethodVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyMethodVisitor.class);

    // we must have a different implementation here, because the name of the parameter may be known after
    // its annotations...
    public static class ParamBuilder implements ParameterInfo.Builder {
        private final int index;
        private String name;
        private boolean isVarArgs;
        private final List<AnnotationExpression> annotations = new ArrayList<>();

        ParamBuilder(int i) {
            index = i;
        }

        @Override
        public ParamBuilder setAccess(Access access) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParamBuilder setSource(Source source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParamBuilder addComment(Comment comment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParamBuilder addComments(List<Comment> comments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParamBuilder addAnnotation(AnnotationExpression annotation) {
            annotations.add(annotation);
            return this;
        }

        @Override
        public ParamBuilder addAnnotations(List<AnnotationExpression> annotations) {
            this.annotations.addAll(annotations);
            return this;
        }

        @Override
        public ParamBuilder setSynthetic(boolean synthetic) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasBeenCommitted() {
            return false;
        }

        @Override
        public ParamBuilder computeAccess() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void commit() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParameterInfo.Builder setVarArgs(boolean varArgs) {
            this.isVarArgs = varArgs;
            return this;
        }
    }

    private final Runtime runtime;
    private final LocalTypeMap localTypeMap;
    private final TypeInfo typeInfo;
    private final MethodInfo methodInfo;
    private final List<ParameterizedType> types;
    private final ParamBuilder[] parameterInspectionBuilders;
    private final int numberOfParameters;
    private final JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator;
    private final AnnotationStore.MethodItem methodItem;
    private final boolean lastParameterIsVarargs;


    public MyMethodVisitor(Runtime runtime,
                           LocalTypeMap localTypeMap,
                           TypeInfo typeInfo,
                           MethodInfo methodInfo,
                           List<ParameterizedType> types,
                           boolean lastParameterIsVarargs,
                           AnnotationStore.MethodItem methodItem,
                           JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator) {
        super(ASM9);
        this.runtime = runtime;
        this.localTypeMap = localTypeMap;
        this.methodInfo = methodInfo;
        this.typeInfo = typeInfo;
        this.types = types;
        this.jetBrainsAnnotationTranslator = jetBrainsAnnotationTranslator;
        this.methodItem = methodItem;
        numberOfParameters = types.size() - 1;
        parameterInspectionBuilders = new ParamBuilder[numberOfParameters];
        for (int i = 0; i < numberOfParameters; i++) {
            parameterInspectionBuilders[i] = new ParamBuilder(i);
        }
        this.lastParameterIsVarargs = lastParameterIsVarargs;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        LOGGER.debug("Have method annotation {} {}", descriptor, visible);
        return new MyAnnotationVisitor<>(runtime, localTypeMap, descriptor, methodInfo.builder());
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
        LOGGER.debug("Have parameter annotation {} on parameter {}", descriptor, parameter);
        return new MyAnnotationVisitor<>(runtime, localTypeMap, descriptor, parameterInspectionBuilders[parameter]);
    }

    /*
    index order seems to be: this params localVars
     */
    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        int base = methodInfo.isStatic() ? 0 : 1; // get rid of "this" if non-static
        int i = index - base;
        if (i >= 0 && i < numberOfParameters) {
            ParamBuilder pib = parameterInspectionBuilders[i];
            pib.name = name;
            if (lastParameterIsVarargs && i == numberOfParameters - 1) {
                parameterInspectionBuilders[i].isVarArgs = true;
            }
        }
    }

    @Override
    public void visitEnd() {
        ParameterNameFactory factory = new ParameterNameFactory();
        for (int i = 0; i < numberOfParameters; i++) {
            ParamBuilder pib = parameterInspectionBuilders[i];
            ParameterizedType type = types.get(i);
            String name = pib.name == null ? factory.next(type) : pib.name;
            ParameterInfo pi = methodInfo.builder().addParameter(name, type);
            assert pi.index() == pib.index;
            if (lastParameterIsVarargs && i == numberOfParameters - 1) {
                pi.builder().setVarArgs(true);
            }
            pi.builder().addAnnotations(pib.annotations);
            pi.builder().setVarArgs(pib.isVarArgs);
            LOGGER.debug("Commit parameterInspection {}", i);
            pi.builder().commit();
        }
        try {
            // this call triggers type inspections of all the parameter's types via PT.printForMethodFQN
            methodInfo.builder().commitParameters();
            methodInfo.builder().computeAccess();
        } catch (RuntimeException e) {
            LOGGER.error("Caught exception parsing {}, method {}", typeInfo.fullyQualifiedName(), methodInfo.name());
            throw e;
        }
        if (methodItem != null) {
            for (AnnotationStore.ParameterItem parameterItem : methodItem.parameterItems()) {
                if (parameterItem.index() < parameterInspectionBuilders.length) {
                    if (!parameterItem.annotations().isEmpty()) {
                        jetBrainsAnnotationTranslator.mapAnnotations(parameterItem.annotations(),
                                parameterInspectionBuilders[parameterItem.index()]);
                    }
                } else {
                    LOGGER.debug("Ignoring parameter with index {} on method {}",
                            parameterItem.index(), methodInfo.fullyQualifiedName());
                }
            }
        }
        methodInfo.builder().commit();
        if (methodInfo.isConstructor()) {
            typeInfo.builder().addConstructor(methodInfo);
        } else {
            typeInfo.builder().addMethod(methodInfo);
        }
    }
}
