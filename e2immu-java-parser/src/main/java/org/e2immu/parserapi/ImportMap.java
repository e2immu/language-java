package org.e2immu.parserapi;

import org.e2immu.cstapi.info.TypeInfo;

import java.util.Map;

public interface ImportMap {
    void addStaticAsterisk(TypeInfo typeInfo);

    void putStaticMemberToTypeInfo(String member, TypeInfo typeInfo);

    Iterable<? extends Map.Entry<String, TypeInfo>> staticMemberToTypeInfoEntrySet();

    Iterable<? extends TypeInfo> staticAsterisk();

    TypeInfo getStaticMemberToTypeInfo(String methodName);

    void putTypeMap(String fullyQualifiedName, TypeInfo typeInfo, boolean highPriority, boolean isDirectImport);

    TypeInfo isImported(String fullyQualifiedName);

    void addToSubtypeAsterisk(TypeInfo typeInfo);

    boolean isSubtypeAsterisk(TypeInfo typeInfo);

    Iterable<? extends TypeInfo> importAsterisk();

    TypeInfo getSimpleName(String name);
}
