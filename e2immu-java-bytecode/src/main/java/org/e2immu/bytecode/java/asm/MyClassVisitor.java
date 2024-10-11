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

import org.e2immu.bytecode.java.ExpressionFactory;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeNature;
import org.e2immu.language.inspection.api.resource.SourceFile;
import org.e2immu.language.inspection.api.util.GetSetUtil;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.objectweb.asm.Opcodes.ASM9;

public class MyClassVisitor extends ClassVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyClassVisitor.class);
    private final TypeParameterContext typeParameterContext;
    private final LocalTypeMap localTypeMap;
    private final SourceFile pathAndURI;
    private final Runtime runtime;
    private final TypeInfo currentType;
    private TypeInfo.Builder currentTypeBuilder;
    private String currentTypePath;
    private boolean currentTypeIsInterface;

    public MyClassVisitor(Runtime runtime,
                          TypeInfo typeInfo,
                          LocalTypeMap localTypeMap,
                          TypeParameterContext typeParameterContext,
                          SourceFile pathAndURI) {
        super(ASM9);
        this.runtime = runtime;
        this.localTypeMap = localTypeMap;
        this.pathAndURI = pathAndURI;
        this.typeParameterContext = typeParameterContext;
        this.currentType = typeInfo;
    }

    private TypeNature typeNatureFromOpCode(int opCode) {
        if ((opCode & Opcodes.ACC_ANNOTATION) != 0) return runtime.typeNatureAnnotation();
        if ((opCode & Opcodes.ACC_ENUM) != 0) return runtime.typeNatureEnum();
        if ((opCode & Opcodes.ACC_INTERFACE) != 0) return runtime.typeNatureInterface();
        if ((opCode & Opcodes.ACC_RECORD) != 0) return runtime.typeNatureRecord();
        return runtime.typeNatureClass();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        LOGGER.debug("Visit {} {} {} {} {} {}", version, access, name, signature, superName, interfaces);
        String fqName = localTypeMap.pathToFqn(name);
        assert fqName != null;
        assert localTypeMap.acceptFQN(fqName);

        assert currentType != null : "Must be in local map! " + fqName;
        assert currentType == localTypeMap.getLocal(fqName);
        currentTypeBuilder = currentType.builder();
        currentTypePath = name;

        // may be overwritten, but this is the default UNLESS it's JLO itself
        if (!currentType.isJavaLangObject()) {
            currentTypeBuilder.setParentClass(runtime.objectParameterizedType());
        }
        currentTypeBuilder.setSource(runtime.newCompiledClassSource(currentType.compilationUnit()));

        TypeNature currentTypeNature = typeNatureFromOpCode(access);
        currentTypeBuilder.setTypeNature(currentTypeNature);
        currentTypeIsInterface = currentTypeNature.isInterface();

        checkTypeFlags(access, currentTypeBuilder);
        if (currentTypeNature.isClass()) {
            if ((access & Opcodes.ACC_ABSTRACT) != 0)
                currentTypeBuilder.addTypeModifier(runtime.typeModifierAbstract());
            if ((access & Opcodes.ACC_FINAL) != 0) currentTypeBuilder.addTypeModifier(runtime.typeModifierFinal());
        }
        currentTypeBuilder.computeAccess();

        String parentFqName = superName == null ? null : localTypeMap.pathToFqn(superName);
        if (parentFqName != null && !localTypeMap.acceptFQN(parentFqName)) {
            return;
        }
        if (signature == null) {
            if (superName != null) {
                TypeInfo typeInfo = mustFindTypeInfo(parentFqName, superName);
                if (typeInfo == null) {
                    LOGGER.debug("Stop inspection of {}, parent type {} unknown",
                            currentType.fullyQualifiedName(), parentFqName);
                    errorStateForType(parentFqName);
                    return;
                }
                currentTypeBuilder.setParentClass(typeInfo.asParameterizedType(runtime));
            } else {
                LOGGER.debug("No parent name for {}", fqName);
            }
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    String fqn = localTypeMap.pathToFqn(interfaceName);
                    if (localTypeMap.acceptFQN(fqn)) {
                        TypeInfo typeInfo = mustFindTypeInfo(fqn, interfaceName);
                        if (typeInfo == null) {
                            LOGGER.debug("Stop inspection of {}, interface type {} unknown",
                                    currentType.fullyQualifiedName(), fqn);
                            errorStateForType(fqn);
                            return;
                        }
                        currentTypeBuilder.addInterfaceImplemented(typeInfo.asParameterizedType(runtime));
                    } // else: ignore!
                }
            }
        } else {
            try {
                int pos = 0;
                if (signature.charAt(0) == '<') {
                    ParseGenerics parseGenerics = new ParseGenerics(runtime, typeParameterContext, currentType, localTypeMap,
                            LocalTypeMap.LoadMode.NOW);
                    pos = parseGenerics.parseTypeGenerics(signature) + 1;
                }
                {
                    String substring = signature.substring(pos);
                    ParameterizedTypeFactory.Result res = ParameterizedTypeFactory.from(runtime, typeParameterContext,
                            localTypeMap, LocalTypeMap.LoadMode.NOW, substring);
                    if (res == null) {
                        LOGGER.error("Stop inspection of {}, parent type unknown", currentType);
                        errorStateForType(substring);
                        return;
                    }
                    currentTypeBuilder.setParentClass(res.parameterizedType);
                    pos += res.nextPos;
                }
                if (interfaces != null) {
                    for (int i = 0; i < interfaces.length; i++) {
                        String interfaceSignature = signature.substring(pos);
                        ParameterizedTypeFactory.Result interFaceRes = ParameterizedTypeFactory.from(runtime,
                                typeParameterContext, localTypeMap, LocalTypeMap.LoadMode.NOW, interfaceSignature);
                        if (interFaceRes == null) {
                            LOGGER.error("Stop inspection of {}, interface type unknown", currentType);
                            errorStateForType(interfaceSignature);
                            return;
                        }
                        if (!interFaceRes.parameterizedType.typeInfo().isJavaLangObject()) {
                            currentTypeBuilder.addInterfaceImplemented(interFaceRes.parameterizedType);
                        }
                        pos += interFaceRes.nextPos;
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.error("Caught exception parsing signature {}", signature);
                throw e;
            }
        }
    }

    /**
     * Both parameters are two versions of the same type reference
     *
     * @param fqn  dot-separated
     * @param path / and $ separated
     * @return the type
     */
    private TypeInfo mustFindTypeInfo(String fqn, String path) {
        if (path.equals(currentTypePath)) {
            return currentType;
        }
        TypeInfo fromMap = localTypeMap.getOrCreate(fqn, LocalTypeMap.LoadMode.NOW);
        if (fromMap == null) {
            LOGGER.error("Type inspection of {} is null", fqn);
            LOGGER.error("Current type is {}, source: {}", currentType.fullyQualifiedName(), currentType.source());
            throw new UnsupportedOperationException("Cannot load type '" + fqn + "'");
        }
        return fromMap;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (currentType == null) return null;
        boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
        LOGGER.debug("Field {} {} desc='{}' sig='{}' {} synthetic? {}", access,
                name, descriptor, signature, value, synthetic);
        if (synthetic) return null;

        ParameterizedTypeFactory.Result from = ParameterizedTypeFactory.from(runtime, typeParameterContext, localTypeMap,
                LocalTypeMap.LoadMode.QUEUE,
                signature != null ? signature : descriptor);
        if (from == null) return null; // jdk
        ParameterizedType type = from.parameterizedType;
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;

        FieldInfo fieldInfo = runtime.newFieldInfo(name, isStatic, type, currentType);
        FieldInfo.Builder fieldInspectionBuilder = fieldInfo.builder();
        if (isStatic) fieldInspectionBuilder.addFieldModifier(runtime.fieldModifierStatic());
        if ((access & Opcodes.ACC_PUBLIC) != 0) fieldInspectionBuilder.addFieldModifier(runtime.fieldModifierPublic());
        if ((access & Opcodes.ACC_PRIVATE) != 0)
            fieldInspectionBuilder.addFieldModifier(runtime.fieldModifierPrivate());
        if ((access & Opcodes.ACC_PROTECTED) != 0)
            fieldInspectionBuilder.addFieldModifier(runtime.fieldModifierProtected());
        if ((access & Opcodes.ACC_FINAL) != 0) fieldInspectionBuilder.addFieldModifier(runtime.fieldModifierFinal());
        if ((access & Opcodes.ACC_VOLATILE) != 0)
            fieldInspectionBuilder.addFieldModifier(runtime.fieldModifierVolatile());
        if ((access & Opcodes.ACC_ENUM) != 0) fieldInspectionBuilder.setSynthetic(true); // what we use synthetic for

        if (value != null) {
            Expression expression = ExpressionFactory.from(runtime, localTypeMap, value);
            if (expression.isEmpty()) {
                fieldInspectionBuilder.setInitializer(expression);
            }
        }
        return new MyFieldVisitor(runtime, typeParameterContext, fieldInfo, localTypeMap);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (currentType == null) return null;

        if (name.startsWith("lambda$") || name.equals("<clinit>")) {
            return null;
        }

        boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
        LOGGER.debug("Method {} {} desc='{}' sig='{}' {} synthetic? {}", access, name,
                descriptor, signature, Arrays.toString(exceptions), synthetic);
        if (synthetic) return null;

        MethodInfo methodInfo;
        if ("<init>".equals(name)) {
            methodInfo = runtime.newConstructor(currentType);
        } else {
            MethodInfo.MethodType methodType = extractMethodType(access);
            methodInfo = runtime.newMethod(currentType, name, methodType);
        }
        MethodInfo.Builder methodInspectionBuilder = methodInfo.builder();
        if ((access & Opcodes.ACC_PUBLIC) != 0 && !currentTypeIsInterface) {
            methodInspectionBuilder.addMethodModifier(runtime.methodModifierPublic());
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0)
            methodInspectionBuilder.addMethodModifier(runtime.methodModifierPrivate());
        if ((access & Opcodes.ACC_PROTECTED) != 0)
            methodInspectionBuilder.addMethodModifier(runtime.methodModifierProtected());
        if ((access & Opcodes.ACC_FINAL) != 0) methodInspectionBuilder.addMethodModifier(runtime.methodModifierFinal());

        boolean lastParameterIsVarargs = (access & Opcodes.ACC_VARARGS) != 0;

        TypeParameterContext methodContext = typeParameterContext.newContext();
        ParseGenerics parseGenerics = new ParseGenerics(runtime, methodContext, currentType, localTypeMap,
                LocalTypeMap.LoadMode.QUEUE);

        String signatureOrDescription = signature != null ? signature : descriptor;
        if (signatureOrDescription.startsWith("<")) {
            int end = parseGenerics.parseMethodGenerics(signatureOrDescription, methodInfo, methodInspectionBuilder,
                    runtime, methodContext);
            if (end < 0) {
                // error state
                errorStateForType(signatureOrDescription);
                return null; // dropping the method, and the type!
            }
            signatureOrDescription = signatureOrDescription.substring(end + 1); // 1 to get rid of >
        }
        List<ParameterizedType> types = parseGenerics.parseParameterTypesOfMethod(methodContext, signatureOrDescription);
        if (types == null) {
            return null; // jdk
        }
        methodInspectionBuilder.setReturnType(types.get(types.size() - 1));
        if (exceptions != null) {
            for (String exception : exceptions) {
                // java/io/IOException
                String fqnName = localTypeMap.pathToFqn(exception);
                TypeInfo typeInfo = mustFindTypeInfo(fqnName, exception);
                methodInspectionBuilder.addExceptionType(typeInfo.asSimpleParameterizedType());
            }
        }
        return new MyMethodVisitor(runtime, typeParameterContext, localTypeMap, currentType, methodInfo,
                types, lastParameterIsVarargs);
    }

    private MethodInfo.MethodType extractMethodType(int access) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        if (isStatic) {
            return runtime.methodTypeStaticMethod();
        }
        boolean isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        if (isAbstract) {
            return runtime.methodTypeAbstractMethod();
        }
        if (currentTypeIsInterface) {
            return runtime.methodTypeDefaultMethod();
        }
        return runtime.methodTypeMethod();
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (currentType == null) return;

        LOGGER.debug("Visit inner class {} {} {} {}", name, outerName, innerName, access);
        if (name.equals(currentTypePath)) {
            checkTypeFlags(access, currentTypeBuilder);
        } else if (innerName != null && outerName != null) {
            String fqnOuter = localTypeMap.pathToFqn(outerName);
            boolean stepDown = currentTypePath.equals(outerName);
            boolean stepSide = currentType.compilationUnitOrEnclosingType().isRight() &&
                               currentType.compilationUnitOrEnclosingType().getRight()
                                       .fullyQualifiedName().equals(fqnOuter);
            // step down
            if (stepSide || stepDown) {
                String fqn = fqnOuter + "." + innerName;

                LOGGER.debug("Processing sub-type {} of/in {}, step side? {} step down? {}", fqn,
                        currentType.fullyQualifiedName(), stepSide, stepDown);

                TypeInfo localOrRemote = localTypeMap.getLocal(fqn);
                if (localOrRemote == null) {
                    TypeInfo enclosing = stepDown ? currentType
                            : currentType.compilationUnitOrEnclosingType().getRight();
                    TypeInfo subType = runtime.newTypeInfo(enclosing, innerName);
                    checkTypeFlags(access, subType.builder());
                    SourceFile newPath = new SourceFile(name + ".class", pathAndURI.uri());
                    TypeParameterContext newTypeParameterContext = typeParameterContext.newContext();
                    localTypeMap.inspectFromPath(subType, newPath, newTypeParameterContext, LocalTypeMap.LoadMode.NOW);
                    if (stepDown) {
                        currentTypeBuilder.addSubType(subType);
                    }
                } else {
                    if (stepDown) {
                        currentTypeBuilder.addSubType(localOrRemote);
                    }
                }
            } //else? potentially add: String fqn = pathToFqn(name); localTypeMap.getOrCreate(fqn, true);
        }
    }

    private void checkTypeFlags(int access, TypeInfo.Builder builder) {
        if ((access & Opcodes.ACC_STATIC) != 0) builder.addTypeModifier(runtime.typeModifierStatic());
        if ((access & Opcodes.ACC_PRIVATE) != 0) builder.addTypeModifier(runtime.typeModifierPrivate());
        if ((access & Opcodes.ACC_PROTECTED) != 0)
            builder.addTypeModifier(runtime.typeModifierProtected());
        if ((access & Opcodes.ACC_PUBLIC) != 0) builder.addTypeModifier(runtime.typeModifierPublic());
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (currentType == null) return null;

        LOGGER.debug("Have class annotation {} {}", descriptor, visible);
        return new MyAnnotationVisitor<>(runtime, typeParameterContext, localTypeMap, descriptor, currentTypeBuilder);
    }

    // not overriding visitOuterClass

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        if (currentType == null) return null;

        LOGGER.debug("Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
        return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitEnd() {
        if (currentType != null) {
            try {
                LOGGER.debug("Visit end of class {}", currentType.fullyQualifiedName());
                if (currentTypeBuilder == null)
                    throw new UnsupportedOperationException("? was expecting a type inspection builder");

                if (currentType.isAbstract()) {
                    new GetSetUtil(runtime).createSyntheticFieldsCorrespondingToGetSetAnnotation(currentType);
                }
                currentTypeBuilder.setSingleAbstractMethod(functionalInterface());
                currentTypeBuilder.commit();
                currentTypeBuilder = null;
            } catch (RuntimeException rte) {
                LOGGER.error("Caught exception bytecode inspecting type {}", currentType.fullyQualifiedName());
                throw rte;
            }
        }
    }

    private MethodInfo functionalInterface() {
        if (currentType.typeNature().isInterface()) {
            return runtime.computeMethodOverrides().computeFunctionalInterface(currentType);
        }
        return null;
    }

    private void errorStateForType(String pathCausingFailure) {
        LOGGER.error("Current source: {}", pathAndURI);
        LOGGER.error("Current type: {}", currentType);
        if (currentType == null || currentTypeBuilder == null || currentTypeBuilder.hasBeenCommitted()) {
            throw new UnsupportedOperationException();
        }
        String message = "Unable to inspect " + currentType.fullyQualifiedName() + ": Cannot load " + pathCausingFailure;
        throw new RuntimeException(message);
    }
}
