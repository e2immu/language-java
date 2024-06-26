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
public class ByteCodeInspectorImpl implements ByteCodeInspector, LocalTypeMap {
    private static final Logger LOGGER = LoggerFactory.getLogger(ByteCodeInspectorImpl.class);

    private enum Status {
        BEING_LOADED, DONE, IN_QUEUE, ON_DEMAND
    }

    private record TypeInfoAndStatus(TypeInfo typeInfo, Status status) {
    }

    private final Map<String, TypeInfoAndStatus> localTypeMap = new LinkedHashMap<>();
    private final Runtime runtime;
    private final CompiledTypesManager compiledTypesManager;

    public ByteCodeInspectorImpl(Runtime runtime,
                                 CompiledTypesManager compiledTypesManager) {
        this.runtime = runtime;
        this.compiledTypesManager = compiledTypesManager;
        for (TypeInfo ti : runtime.predefinedObjects()) {
            localTypeMap.put(ti.fullyQualifiedName(), new TypeInfoAndStatus(ti, Status.IN_QUEUE));
        }
    }

    @Override
    public boolean acceptFQN(String fqName) {
        return compiledTypesManager.acceptFQN(fqName);
    }

    @Override
    public TypeInfo getLocal(String fqName) {
        TypeInfoAndStatus ts = localTypeMap.get(fqName);
        return ts == null ? null : ts.typeInfo;
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
        TypeInfoAndStatus local = localTypeMap.get(fqn);
        TypeInfo typeInfo;
        if (local != null) {
            if (local.status == Status.DONE || local.status == Status.BEING_LOADED) {
                return local.typeInfo;
            }
            typeInfo = local.typeInfo;
        } else {
            typeInfo = null;
        }
        SourceFile source = typeInfo == null
                ? compiledTypesManager.classPath().fqnToPath(fqn, ".class")
                : compiledTypesManager.classPath().sourceFileOfType(typeInfo, ".class");
        assert source != null;
        return inspectFromPath(typeInfo, source, loadMode);
    }

    @Override
    public TypeInfo load(TypeInfo subType) {
        String fqn = subType.fullyQualifiedName();
        TypeInfoAndStatus local = localTypeMap.get(fqn);
        TypeInfo typeInfo;
        if (local != null) {
            if (local.status == Status.DONE || local.status == Status.BEING_LOADED) {
                return local.typeInfo;
            }
            typeInfo = local.typeInfo;
        } else {
            typeInfo = subType;
        }
        SourceFile source = compiledTypesManager.classPath().sourceFileOfType(typeInfo, ".class");
        assert source != null;
        return inspectFromPath(typeInfo, source, LoadMode.NOW);
    }

    @Override
    public TypeInfo load(SourceFile sourceFile) {
        return inspectFromPath(null, sourceFile, LoadMode.NOW);
    }

    @Override
    public TypeInfo inspectFromPath(TypeInfo typeInfoOrNull, SourceFile path, LoadMode loadMode) {
        assert path != null && path.path().endsWith(".class");
        String fqn;
        if (typeInfoOrNull != null) fqn = typeInfoOrNull.fullyQualifiedName();
        else fqn = pathToFqn(path.stripDotClass());
        TypeInfoAndStatus ts = localTypeMap.get(fqn);
        if (ts != null && (ts.status == Status.DONE || ts.status == Status.BEING_LOADED)) {
            return ts.typeInfo; // already working on it
        }
        TypeInfo typeInfo;
        if (ts == null) {
            typeInfo = createTypeInfo(path, fqn, loadMode);
            TypeInfoAndStatus inMapAgain = localTypeMap.get(fqn);
            if (inMapAgain != null && (inMapAgain.status == Status.DONE || inMapAgain.status == Status.BEING_LOADED)) {
                return inMapAgain.typeInfo;
            }
        } else {
            typeInfo = ts.typeInfo;
            if (typeInfo.compilationUnitOrEnclosingType().isRight()) {
                getOrCreate(typeInfo.primaryType().fullyQualifiedName(), loadMode);
            }
        }
        if (loadMode == LoadMode.NOW) {
            return continueLoadByteCodeAndStartASM(path, fqn, typeInfo);
        }
        localTypeMap.put(fqn, new TypeInfoAndStatus(typeInfo, loadMode == LoadMode.QUEUE
                ? Status.IN_QUEUE : Status.ON_DEMAND));
        if (!typeInfo.haveOnDemandInspection()) {
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
        return typeInfo;
    }

    private TypeInfo continueLoadByteCodeAndStartASM(SourceFile path,
                                                     String fqn,
                                                     TypeInfo typeInfo) {
        localTypeMap.put(fqn, new TypeInfoAndStatus(typeInfo, Status.BEING_LOADED));
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
            localTypeMap.put(fqn, new TypeInfoAndStatus(typeInfo, Status.DONE));
            return typeInfo;
        } catch (RuntimeException re) {
            LOGGER.error("Path = {}", path);
            LOGGER.error("FQN  = {}", fqn);
            throw re;
        }
    }
}

