package org.e2immu.resourceapi;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.parserapi.PackagePrefix;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/*
Connects to the Resources maps.
 */
public interface TypeMap {

    TypeInfo get(Class<?> clazz);

    TypeInfo get(String fullyQualifiedName);

    boolean isPackagePrefix(PackagePrefix packagePrefix);

    interface Builder extends TypeMap {
        // generic, could be from source, could be from byte code; used in direct type access in source code
        TypeInfo getOrCreate(String fqn, boolean complain);

        void ensureInspection(TypeInfo typeInfo);
    }
}
