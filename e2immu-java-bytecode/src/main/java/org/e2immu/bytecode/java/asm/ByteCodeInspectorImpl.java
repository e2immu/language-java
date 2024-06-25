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

import org.e2immu.bytecode.java.ByteCodeInspector;
import org.e2immu.bytecode.java.TypeData;
import org.e2immu.bytecode.java.TypeDataImpl;
import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.inspection.api.*;
import org.e2immu.language.inspection.api.parser.TypeContext;
import org.e2immu.language.inspection.api.resource.*;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.language.inspection.api.InspectionState.STARTING_BYTECODE;
import static org.e2immu.language.inspection.api.InspectionState.TRIGGER_BYTECODE_INSPECTION;


/*
The ByteCodeInspectorImpl is used as a singleton.
Concurrent calls to 'inspectFromPath' each create a new LocalTypeMapImpl.
This method must only be called from TypeMapImpl.Builder, which handles all threading and synchronization.
*/
public class ByteCodeInspectorImpl implements ByteCodeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ByteCodeInspectorImpl.class);

    private final Runtime runtime;
    private final Resources classPath;
    private final TypeMap typeMap;
    private final AnnotationStore annotationStore;

    public ByteCodeInspectorImpl(Runtime runtime, Resources classPath, AnnotationStore annotationStore, TypeMap typeMap) {
        this.classPath = Objects.requireNonNull(classPath);
        this.typeMap = Objects.requireNonNull(typeMap);
        this.annotationStore = annotationStore;
        this.runtime = runtime;
    }

    /**
     * Given a path pointing to a .class file, load the bytes, and inspect the byte code
     *
     * @param source important: path must be split by /, not by .  It may or may not end in .class.
     * @return one or more types; the first one is the main type of the .class file
     */
    @Override
    public List<TypeData> inspectFromPath(SourceFile source) {
        LocalTypeMapImpl localTypeMap = new LocalTypeMapImpl();
        localTypeMap.inspectFromPath(source, typeMap, LocalTypeMap.LoadMode.NOW);
        return localTypeMap.loaded();
    }

    // for testing only!!
    public LocalTypeMap localTypeMap() {
        return new LocalTypeMapImpl();
    }


    private class LocalTypeMapImpl implements LocalTypeMap {

        private final Map<String, TypeData> localTypeMap = new LinkedHashMap<>();

        @Override
        public TypeMap.InspectionAndState typeInspectionSituation(String fqn) {
            TypeData local = localTypeMap.get(fqn);
            if (local != null) {
                return local.toInspectionAndState();
            }
            return typeMap.typeInspectionSituation(fqn);
        }

        @Override
        public TypeInfo getOrCreate(String fqn, LoadMode loadMode) {
            if (!Input.acceptFQN(fqn)) {
                return null;
            }
            TypeData typeData = localTypeMap.get(fqn);
            if (typeData != null) {
                if (LoadMode.NOW != loadMode || typeData.getInspectionState().ge(STARTING_BYTECODE)) {
                    if (loadMode == LoadMode.QUEUE) {
                        typeMap.addToByteCodeQueue(fqn);
                    }
                    return typeData.getTypeInfo();
                }
                // START!
            }
            TypeMap.InspectionAndState remote = typeMap.typeInspectionSituation(fqn);
            if (remote != null) {
                if (LoadMode.NOW != loadMode || remote.state().ge(STARTING_BYTECODE)) {
                    if (loadMode == LoadMode.QUEUE) {
                        typeMap.addToByteCodeQueue(fqn);
                    }
                    return remote.typeInfo();
                }
                if (typeData == null) {
                    localTypeMapPut(fqn, new TypeDataImpl(remote.typeInfo(), TRIGGER_BYTECODE_INSPECTION));
                }
            }
            SourceFile source = classPath.fqnToPath(fqn, ".class");
            if (source == null) {
                return null;
            }
            return inspectFromPath(source, typeMap, loadMode);
        }

        @Override
        public TypeInfo getOrCreate(TypeInfo subType) {
            TypeData typeData = localTypeMap.get(subType.fullyQualifiedName());
            if (typeData != null) {
                return typeData.getTypeInfo();
            }
            TypeMap.InspectionAndState remote = typeMap.typeInspectionSituation(subType.fullyQualifiedName());
            if (remote != null) {
                return remote.typeInfo();
            }
            TypeInfo typeInfoInMap = typeMap.addToTrie(subType);
            TypeData newTypeData = new TypeDataImpl(typeInfoInMap, TRIGGER_BYTECODE_INSPECTION);
            localTypeMapPut(typeInfoInMap.fullyQualifiedName(), newTypeData);
            return typeInfoInMap;
        }

        private void localTypeMapPut(String fullyQualifiedName, TypeData newTypeData) {
            assert fullyQualifiedName.equals(newTypeData.getTypeInfo().fullyQualifiedName());
            localTypeMap.put(fullyQualifiedName, newTypeData);
        }

        @Override
        public List<TypeData> loaded() {
            return localTypeMap.values().stream().toList();
        }

        @Override
        public TypeInfo inspectFromPath(SourceFile path, TypeMap parentTypeContext, LoadMode loadMode) {
            assert path != null && path.path().endsWith(".class");

            String fqn = typeMap.pathToFqn(path.stripDotClass());
            TypeData typeDataInMap = localTypeMap.get(fqn);
            if (typeDataInMap != null && typeDataInMap.getInspectionState().ge(STARTING_BYTECODE)) {
                return typeDataInMap.getTypeInfo();
            }
            TypeMap.InspectionAndState inspectionAndState = typeMap.typeInspectionSituation(fqn);
            if (inspectionAndState != null && inspectionAndState.state().isDone()) {
                return inspectionAndState.typeInfo();
            }

            TypeData typeData;
            if (typeDataInMap == null) {
                // create, but ensure that all enclosing are present FIRST: potential recursion
                TypeInfo typeInfo = createTypeInfo(path, fqn, loadMode);
                TypeData typeDataAgain = localTypeMap.get(fqn);
                if (typeDataAgain == null) {
                    typeData = new TypeDataImpl(typeInfo, TRIGGER_BYTECODE_INSPECTION);
                    localTypeMapPut(fqn, typeData);
                } else {
                    typeData = typeDataAgain;
                }
            } else {
                TypeInfo typeInfo = typeDataInMap.getTypeInfo();
                if (typeInfo.compilationUnitOrEnclosingType().isRight()) {
                    // ensure that the enclosing type is inspected first! (TestByteCodeInspectorCommonsPool)
                    getOrCreate(typeInfo.primaryType().fullyQualifiedName(), LoadMode.NOW);
                }
                typeData = typeDataInMap;
            }
            if (typeData.getInspectionState().ge(STARTING_BYTECODE)) {
                return typeData.getTypeInfo();
            }
            if (loadMode == LoadMode.NOW) {
                return continueLoadByteCodeAndStartASM(path, parentTypeContext, fqn, typeData);
            }
            LOGGER.debug("Stored type data for {}, state {}", fqn, typeData.getInspectionState());
            return typeData.getTypeInfo();
        }

        private TypeInfo createTypeInfo(SourceFile source, String fqn, LoadMode loadMode) {
            String path = source.stripDotClass();
            int dollar = path.lastIndexOf('$');
            TypeInfo typeInfo;
            if (dollar >= 0) {
                String simpleName = path.substring(dollar + 1); // FIXME interaction with inspectFromPath
                String newPath = SourceFile.ensureDotClass(path.substring(0, dollar));
                SourceFile newSource = new SourceFile(newPath, source.uri());
                typeInfo = inspectFromPath(newSource, typeMap, loadMode);
            } else {
                int lastDot = fqn.lastIndexOf(".");
                String packageName = fqn.substring(0, lastDot);
                String simpleName = fqn.substring(lastDot + 1);
                CompilationUnit cu = runtime.newCompilationUnitBuilder()
                        .setURI(source.uri())
                        .setPackageName(packageName).build();
                typeInfo = runtime.newTypeInfo(cu, simpleName);
            }
            return typeMap.addToTrie(typeInfo);
        }

        private TypeInfo continueLoadByteCodeAndStartASM(SourceFile path,
                                                         TypeMap typeMap,
                                                         String fqn,
                                                         TypeData typeData) {
            assert typeData.getInspectionState() == TRIGGER_BYTECODE_INSPECTION;
            typeData.setInspectionState(STARTING_BYTECODE);
            try {
                byte[] classBytes = classPath.loadBytes(path.path());
                if (classBytes == null) {
                    return null;
                }

                ClassReader classReader = new ClassReader(classBytes);
                LOGGER.debug("Constructed class reader with {} bytes", classBytes.length);

                MyClassVisitor myClassVisitor = new MyClassVisitor(runtime, this, annotationStore,
                        typeMap, new TypeParameterContext(), path);
                classReader.accept(myClassVisitor, 0);
                typeData.setInspectionState(InspectionState.FINISHED_BYTECODE);
                LOGGER.debug("Finished bytecode inspection of {}", fqn);
                return typeData.getTypeInfo();
            } catch (RuntimeException re) {
                LOGGER.error("Path = {}", path);
                LOGGER.error("FQN  = {}", fqn);
                throw re;
            }
        }
    }
}
