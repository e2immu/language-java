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
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.inspection.api.resource.*;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/*
The ByteCodeInspectorImpl is used as a singleton.
Concurrent calls to 'inspectFromPath' each create a new LocalTypeMapImpl.
This method must only be called from TypeMapImpl.Builder, which handles all threading and synchronization.
*/
public class ByteCodeInspectorImpl implements ByteCodeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ByteCodeInspectorImpl.class);

    private final Runtime runtime;
    private final CompiledTypesManager compiledTypesManager;

    public ByteCodeInspectorImpl(Runtime runtime, CompiledTypesManager compiledTypesManager) {
        this.runtime = runtime;
        this.compiledTypesManager = compiledTypesManager;
    }

    /**
     * Given a path pointing to a .class file, load the bytes, and inspect the byte code
     *
     * @param source important: path must be split by /, not by .  It may or may not end in .class.
     * @return one or more types; the first one is the main type of the .class file
     */
    @Override
    public List<TypeInfo> load(SourceFile source) {
        LocalTypeMapImpl localTypeMap = new LocalTypeMapImpl();
        localTypeMap.inspectFromPath(null, source, LocalTypeMap.LoadMode.NOW);
        return localTypeMap.loaded();
    }

    // for testing only!!
    public LocalTypeMap localTypeMap() {
        return new LocalTypeMapImpl();
    }


    private class LocalTypeMapImpl implements LocalTypeMap {

        private final Map<String, TypeInfo> localTypeMap = new LinkedHashMap<>();

        @Override
        public boolean acceptFQN(String fqName) {
            return compiledTypesManager.acceptFQN(fqName);
        }

        @Override
        public TypeInfo getLocal(String fqName) {
            return localTypeMap.get(fqName);
        }

        @Override
        public TypeInfo getLocalOrRemote(String fqn) {
            TypeInfo local = localTypeMap.get(fqn);
            if (local != null) return local;
            return compiledTypesManager.get(fqn);
        }

        @Override
        public String pathToFqn(String name) {
            return compiledTypesManager.classPath().pathToFqn(name);
        }

        @Override
        public TypeInfo getOrCreate(String fqn, LoadMode loadMode) {
            if (!compiledTypesManager.acceptFQN(fqn)) {
                return null;
            }
            TypeInfo local = localTypeMap.get(fqn);
            if (local != null) return local;
            TypeInfo remote = compiledTypesManager.get(fqn);
            if (remote == null) {
                SourceFile source = compiledTypesManager.classPath().fqnToPath(fqn, ".class");
                if (source == null) {
                    return null;
                }
                return inspectFromPath(null, source, loadMode);
            }
            if (remote.hasBeenInspected()) return remote;
            switch (loadMode) {
                case QUEUE -> {
                    compiledTypesManager.addToQueue(remote);
                    return remote;
                }
                case NOW -> {
                    SourceFile source = compiledTypesManager.classPath().sourceFileOfType(remote, ".class");
                    assert source != null;
                    return inspectFromPath(remote, source, loadMode);
                }
                case TRIGGER -> {
                    remote.setOnDemandInspection(ti -> {
                        SourceFile source = compiledTypesManager.classPath().sourceFileOfType(remote, ".class");
                        assert source != null;
                        inspectFromPath(ti, source, LoadMode.NOW);
                    });
                    return remote;
                }
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeInfo getOrCreate(TypeInfo subType) {
            TypeInfo local = localTypeMap.get(subType.fullyQualifiedName());
            if (local != null) return local; // already working on it
            TypeInfo remote = compiledTypesManager.get(subType.fullyQualifiedName());
            if (remote != null) {
                if (remote.hasBeenInspected()) return remote;
                remote.setOnDemandInspection(ti -> {
                    SourceFile source = compiledTypesManager.classPath().sourceFileOfType(subType, ".class");
                    assert source != null;
                    inspectFromPath(ti, source, LoadMode.NOW);
                });
                return remote;
            }
            TypeInfo typeInfoInMap = compiledTypesManager.addToTrie(subType);
            typeInfoInMap.setOnDemandInspection(ti -> {
                SourceFile source = compiledTypesManager.classPath().sourceFileOfType(subType, ".class");
                assert source != null;
                inspectFromPath(ti, source, LoadMode.NOW);
            });
            return typeInfoInMap;
        }

        @Override
        public List<TypeInfo> loaded() {
            return localTypeMap.values().stream().toList();
        }

        @Override
        public TypeInfo inspectFromPath(TypeInfo typeInfoOrNull, SourceFile path, LoadMode loadMode) {
            assert path != null && path.path().endsWith(".class");
            String fqn;
            if (typeInfoOrNull != null) fqn = typeInfoOrNull.fullyQualifiedName();
            else fqn = pathToFqn(path.stripDotClass());
            TypeInfo typeDataInMap = localTypeMap.get(fqn);
            if (typeDataInMap != null) {
                return typeDataInMap; // already working on it
            }
            TypeInfo typeInfo;
            if (typeInfoOrNull == null) {
                typeInfo = createTypeInfo(path, fqn, loadMode);
                TypeInfo inMapAgain = localTypeMap.get(fqn);
                if (inMapAgain != null) {
                    return inMapAgain;
                }
            } else {
                typeInfo = typeInfoOrNull;
                if (typeInfo.compilationUnitOrEnclosingType().isRight()) {
                    getOrCreate(typeInfo.primaryType().fullyQualifiedName(), LoadMode.NOW);
                }
            }
            if (loadMode == LoadMode.NOW) {
                return continueLoadByteCodeAndStartASM(path, fqn, typeInfo);
            }
            if (loadMode == LoadMode.TRIGGER && !typeInfo.haveOnDemandInspection()) {
                typeInfo.setOnDemandInspection(ti -> {
                    SourceFile source = compiledTypesManager.classPath().sourceFileOfType(ti, ".class");
                    assert source != null;
                    inspectFromPath(ti, source, LoadMode.NOW);
                });
            }
            return typeInfo;
        }

        private TypeInfo createTypeInfo(SourceFile source, String fqn, LoadMode loadMode) {
            String path = source.stripDotClass();
            int dollar = path.lastIndexOf('$');
            TypeInfo typeInfo;
            if (dollar >= 0) {
                String simpleName = path.substring(dollar + 1);
                String newPathWithoutSubType = SourceFile.ensureDotClass(path.substring(0, dollar));
                SourceFile newSource = new SourceFile(newPathWithoutSubType, source.uri());
                TypeInfo parent = inspectFromPath(null, newSource, loadMode);
                typeInfo = runtime.newTypeInfo(parent, simpleName);
            } else {
                int lastDot = fqn.lastIndexOf(".");
                String packageName = fqn.substring(0, lastDot);
                String simpleName = fqn.substring(lastDot + 1);
                CompilationUnit cu = runtime.newCompilationUnitBuilder()
                        .setURI(source.uri())
                        .setPackageName(packageName).build();
                typeInfo = runtime.newTypeInfo(cu, simpleName);
            }
            return compiledTypesManager.addToTrie(typeInfo);
        }

        private TypeInfo continueLoadByteCodeAndStartASM(SourceFile path,
                                                         String fqn,
                                                         TypeInfo typeInfo) {
            localTypeMap.put(fqn, typeInfo);
            try {
                byte[] classBytes = compiledTypesManager.classPath().loadBytes(path.path());
                if (classBytes == null) {
                    return null;
                }

                ClassReader classReader = new ClassReader(classBytes);
                LOGGER.debug("Constructed class reader for {} with {} bytes", fqn, classBytes.length);

                MyClassVisitor myClassVisitor = new MyClassVisitor(runtime, typeInfo, this,
                        new TypeParameterContext(), path);
                classReader.accept(myClassVisitor, 0);
                LOGGER.debug("Finished bytecode inspection of {}", fqn);
                compiledTypesManager.add(typeInfo);
                return typeInfo;
            } catch (RuntimeException re) {
                LOGGER.error("Path = {}", path);
                LOGGER.error("FQN  = {}", fqn);
                throw re;
            }
        }
    }
}
