package org.e2immu.bytecode.java;

import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.element.ImportStatement;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.inspection.api.parser.ImportMap;
import org.e2immu.language.inspection.api.parser.TypeContext;
import org.e2immu.language.inspection.api.resource.TypeMap;

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
    public NamedType get(String name, boolean complain) {
        return null;
    }

    @Override
    public boolean isKnown(String fullyQualified) {
        return false;
    }

    @Override
    public void addToContext(NamedType namedType) {

    }

    @Override
    public void addToContext(NamedType namedType, boolean allowOverwrite) {

    }

    @Override
    public void addToContext(String altName, NamedType namedType, boolean allowOverwrite) {

    }

    @Override
    public TypeContext newTypeContext() {
        return new TypeContextImpl(this);
    }

    @Override
    public void addToImportMap(ImportStatement importStatement) {
    }

    @Override
    public TypeMap.Builder typeMap() {
        return null;
    }

    @Override
    public ImportMap importMap() {
        return null;
    }

    @Override
    public CompilationUnit compilationUnit() {
        return null;
    }

    @Override
    public TypeInfo getFullyQualified(Class<?> clazz) {
        return null;
    }
}
