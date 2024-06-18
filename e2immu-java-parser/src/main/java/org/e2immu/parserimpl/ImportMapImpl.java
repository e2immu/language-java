package org.e2immu.parserimpl;

import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.parserapi.ImportMap;

import java.util.*;

public class ImportMapImpl implements ImportMap {

    private final Set<TypeInfo> staticAsterisk = new LinkedHashSet<>();
    private final Map<String, TypeInfo> staticMemberToTypeInfo = new HashMap<>();
    private final Map<String, TypeInfo> typeMap = new HashMap<>();
    private final Map<String, TypeInfo> simpleNameTypeMap = new HashMap<>();
    private final Set<TypeInfo> subtypeAsterisk = new LinkedHashSet<>();

    @Override
    public void addStaticAsterisk(TypeInfo typeInfo) {
        staticAsterisk.add(typeInfo);
        subtypeAsterisk.add(typeInfo); // see InspectionGaps_13: we also add the subtypes
    }

    @Override
    public void putStaticMemberToTypeInfo(String member, TypeInfo typeInfo) {
        staticMemberToTypeInfo.put(member, typeInfo);
    }

    @Override
    public Iterable<? extends Map.Entry<String, TypeInfo>> staticMemberToTypeInfoEntrySet() {
        return staticMemberToTypeInfo.entrySet();
    }

    @Override
    public Iterable<? extends TypeInfo> staticAsterisk() {
        return staticAsterisk;
    }

    @Override
    public TypeInfo getStaticMemberToTypeInfo(String methodName) {
        return staticMemberToTypeInfo.get(methodName);
    }


    @Override
    public void putTypeMap(String fullyQualifiedName, TypeInfo typeInfo, boolean highPriority, boolean isDirectImport) {
        if (highPriority || !typeMap.containsKey(fullyQualifiedName)) {
            typeMap.put(fullyQualifiedName, typeInfo);
        }
        if (isDirectImport) {
            simpleNameTypeMap.put(typeInfo.simpleName(), typeInfo);
        }
    }

    @Override
    public TypeInfo isImported(String fullyQualifiedName) {
        TypeInfo typeInfo = typeMap.get(fullyQualifiedName);
        if (typeInfo == null) {
            int dot = fullyQualifiedName.lastIndexOf('.');
            if (dot > 0) {
                TypeInfo outer = isImported(fullyQualifiedName.substring(0, dot));
                if (outer == null) return null;
                String subTypeName = fullyQualifiedName.substring(dot + 1);
                return outer.subTypes().stream().filter(st -> st.simpleName().equals(subTypeName))
                        .findFirst().orElse(null);
            }
        }
        return typeInfo;
    }

    @Override
    public void addToSubtypeAsterisk(TypeInfo typeInfo) {
        subtypeAsterisk.add(typeInfo);
    }

    @Override
    public boolean isSubtypeAsterisk(TypeInfo typeInfo) {
        return subtypeAsterisk.contains(typeInfo);
    }

    @Override
    public Iterable<? extends TypeInfo> importAsterisk() {
        return new HashSet<>(subtypeAsterisk); // to avoid concurrent modification issues
    }

    @Override
    public TypeInfo getSimpleName(String name) {
        return simpleNameTypeMap.get(name);
    }
}
