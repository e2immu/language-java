package org.e2immu.bytecode.java;

import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.type.NamedType;
import org.e2immu.cstapi.type.TypeParameter;
import org.e2immu.inputapi.TypeContext;
import org.e2immu.inputapi.TypeMap;

import java.util.HashMap;
import java.util.Map;

public class TypeContextImpl implements TypeContext {
    private final TypeMap typeMap;
    private final TypeContext parent;
    private final Map<String, NamedType> localMap = new HashMap<>();

    public TypeContextImpl(TypeMap typeMap) {
        this.typeMap = typeMap;
        this.parent = null;
    }

    private TypeContextImpl(TypeContextImpl parent) {
        this.typeMap = parent.typeMap();
        this.parent = parent;
    }

    @Override
    public TypeInfo getFullyQualified(String name, boolean complain) {
        NamedType local = localMap.get(name);
        if (local != null) return (TypeInfo) local;
        if (parent != null) return parent.getFullyQualified(name, complain);
        return typeMap.get(name, complain);
    }

    @Override
    public void addToContext(TypeParameter typeParameter) {
        localMap.put(typeParameter.simpleName(), typeParameter);
    }

    @Override
    public TypeContext newTypeContext() {
        return new TypeContextImpl(this);
    }

    @Override
    public TypeMap typeMap() {
        return typeMap;
    }
}
