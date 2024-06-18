package org.e2immu.parserimpl;

import org.e2immu.annotation.NotNull;
import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.element.ImportStatement;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.parserapi.ImportMap;
import org.e2immu.parserapi.PackagePrefix;
import org.e2immu.parserapi.TypeContext;
import org.e2immu.resourceapi.TypeMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TypeContextImpl implements TypeContext {
    private final TypeContextImpl parentContext;

    public final TypeMap.Builder typeMap;
    public final CompilationUnit compilationUnit;
    private final ImportMap importMap;
    private final Map<String, NamedType> map = new HashMap<>();

    public TypeContextImpl(TypeMap.Builder typeMap, CompilationUnit compilationUnit, ImportMap importMap) {
        this.typeMap = typeMap;
        this.importMap = importMap;
        this.compilationUnit = compilationUnit;
        this.parentContext = null;
    }

    public TypeContextImpl(TypeContext parentContext) {
        this.typeMap = parentContext.typeMap();
        this.importMap = parentContext.importMap();
        this.compilationUnit = parentContext.compilationUnit();
        this.parentContext = (TypeContextImpl) parentContext;
    }

    @Override
    public void addToImportMap(ImportStatement importStatement) {
        String fqn = importStatement.importString();
        if (fqn.endsWith(".*")) {
            throw new UnsupportedOperationException("NYI");
        } else {
            TypeInfo typeInfo = typeMap().get(fqn);
            importMap.putTypeMap(fqn, typeInfo, false, true);
            addToContext(typeInfo);
        }
    }

    @Override
    public TypeMap.Builder typeMap() {
        return typeMap;
    }

    @Override
    public ImportMap importMap() {
        return importMap;
    }

    @Override
    public CompilationUnit compilationUnit() {
        return compilationUnit;
    }


    @Override
    public TypeInfo getFullyQualified(Class<?> clazz) {
        return getFullyQualified(clazz.getCanonicalName(), true);
    }

    /**
     * Look up a type by FQN. Ensure that the type has been inspected.
     *
     * @param fullyQualifiedName the fully qualified name, such as java.lang.String
     * @return the type
     */
    @Override
    public TypeInfo getFullyQualified(String fullyQualifiedName, boolean complain) {
        TypeInfo typeInfo = internalGetFullyQualified(fullyQualifiedName, complain);
        if (typeInfo != null) {
            typeMap.ensureInspection(typeInfo);
        }
        return typeInfo;
    }

    private TypeInfo internalGetFullyQualified(String fullyQualifiedName, boolean complain) {
        TypeInfo typeInfo = typeMap.get(fullyQualifiedName);
        if (typeInfo == null) {
            // see InspectionGaps_9: we don't have the type, but we do have an import of its enclosing type
            TypeInfo imported = importMap.isImported(fullyQualifiedName);
            if (imported != null) {
                return imported;
            }
            return typeMap.getOrCreate(fullyQualifiedName, complain);
        }
        return typeInfo;
    }


    @Override
    public NamedType get(String name, boolean complain) {
        NamedType simple = getSimpleName(name);
        if (simple != null) {
            if (simple instanceof TypeInfo typeInfo) {
                typeMap.ensureInspection(typeInfo);
            }
            return simple;
        }

        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            // name can be fully qualified, or semi qualified
            // try fully qualified first
            NamedType fullyQualified = getFullyQualified(name, false);
            if (fullyQualified != null) return fullyQualified;
            // it must be semi qualified now... go recursive
            String prefix = name.substring(0, dot);
            NamedType prefixType = get(prefix, complain);
            if (prefixType instanceof TypeInfo typeInfo) {
                String fqn = typeInfo.fullyQualifiedName() + "." + name.substring(dot + 1);
                return getFullyQualified(fqn, complain);
            }
            throw new UnsupportedOperationException("?");
        }
        // try out java.lang; has been preloaded
        TypeInfo inJavaLang = typeMap.get("java.lang." + name);
        if (inJavaLang != null) return inJavaLang;

        // go fully qualified using the package
        String fqn = compilationUnit.packageName() + "." + name;
        return getFullyQualified(fqn, complain);
    }


    private NamedType getSimpleName(String name) {
        NamedType namedType = map.get(name);
        if (namedType != null) {
            return namedType;
        }

        // explicit imports
        TypeInfo fromImport = importMap.getSimpleName(name);
        if (fromImport != null) {
            return fromImport;
        }

        // Same package, and * imports (in that order!)
        if (parentContext != null) {
            NamedType fromParent = parentContext.getSimpleName(name);
            if (fromParent != null) {
                return fromParent;
            }
        }

        /*
        On-demand: subtype from import statement (see e.g. Import_2)
        This is done on-demand to fight cyclic dependencies if we do eager inspection.
         */
        TypeInfo parent = importMap.getStaticMemberToTypeInfo(name);
        if (parent != null) {
            TypeInfo subType = parent.subTypes()
                    .stream().filter(st -> name.equals(st.simpleName())).findFirst().orElse(null);
            if (subType != null) {
                importMap.putTypeMap(subType.fullyQualifiedName(), subType, false, false);
                return subType;
            }
        }
        /*
        On-demand: try to resolve the * imports registered in this type context
         */
        for (TypeInfo wildcard : importMap.importAsterisk()) {
            // the call to getTypeInspection triggers the JavaParser
            TypeInfo subType = wildcard.subTypes()
                    .stream().filter(st -> name.equals(st.simpleName())).findFirst().orElse(null);
            if (subType != null) {
                importMap.putTypeMap(subType.fullyQualifiedName(), subType, false, false);
                return subType;
            }
        }
        return null;
    }

    @Override
    public boolean isKnown(String fullyQualified) {
        return typeMap.get(fullyQualified) != null;
    }

    @Override
    public void addToContext(@NotNull NamedType namedType) {
        addToContext(namedType, true);
    }

    @Override
    public void addToContext(@NotNull NamedType namedType, boolean allowOverwrite) {
        String simpleName = namedType.simpleName();
        if (allowOverwrite || !map.containsKey(simpleName)) {
            map.put(simpleName, namedType);
        }
    }

    @Override
    public void addToContext(String altName, @NotNull NamedType namedType, boolean allowOverwrite) {
        if (allowOverwrite || !map.containsKey(altName)) {
            map.put(altName, namedType);
        }
    }


    private List<TypeInfo> extractTypeInfo(Runtime runtime,
                                           ParameterizedType typeOfObject,
                                           Map<NamedType, ParameterizedType> typeMap) {
        TypeInfo typeInfo;
        if (typeOfObject.typeInfo() == null) {
            if (typeOfObject.typeParameter() == null) {
                throw new UnsupportedOperationException();
            }
            ParameterizedType pt = typeMap.get(typeOfObject.typeParameter());
            if (pt == null) {
                // rather than give an exception here, we replace t by the type that it extends, so that we can find those methods
                // in the case that there is no explicit extension/super, we replace it by the implicit Object
                List<ParameterizedType> typeBounds = typeOfObject.typeParameter().typeBounds();
                if (!typeBounds.isEmpty()) {
                    return typeBounds.stream().flatMap(bound -> extractTypeInfo(runtime, bound, typeMap).stream())
                            .collect(Collectors.toList());
                } else {
                    typeInfo = runtime.objectTypeInfo();
                }
            } else {
                typeInfo = pt.typeInfo();
            }
        } else {
            typeInfo = typeOfObject.typeInfo();
        }
        assert typeInfo != null;
        this.typeMap.ensureInspection(typeInfo);
        return List.of(typeInfo);
    }

    public void addImportStaticWildcard(TypeInfo typeInfo) {
        importMap.addStaticAsterisk(typeInfo);
    }

    public void addImportStatic(TypeInfo typeInfo, String member) {
        importMap.putStaticMemberToTypeInfo(member, typeInfo);
    }

    public Map<String, FieldReference> staticFieldImports(Runtime runtime) {
        Map<String, FieldReference> map = new HashMap<>();
        for (Map.Entry<String, TypeInfo> entry : importMap.staticMemberToTypeInfoEntrySet()) {
            TypeInfo typeInfo = entry.getValue();
            String memberName = entry.getKey();
            typeInfo.fields().stream()
                    .filter(FieldInfo::isStatic)
                    .filter(f -> f.name().equals(memberName))
                    .findFirst()
                    .ifPresent(fieldInfo -> map.put(memberName, runtime.newFieldReference(fieldInfo)));
        }
        for (TypeInfo typeInfo : importMap.staticAsterisk()) {
            typeInfo.fields().stream()
                    .filter(FieldInfo::isStatic)
                    .forEach(fieldInfo -> map.put(fieldInfo.name(), runtime.newFieldReference(fieldInfo)));
        }
        return map;
    }

    public void addImport(TypeInfo typeInfo, boolean highPriority, boolean directImport) {
        importMap.putTypeMap(typeInfo.fullyQualifiedName(), typeInfo, highPriority, directImport);
        if (!directImport) {
            addToContext(typeInfo, highPriority);
        }
    }

    public void addImportWildcard(TypeInfo typeInfo) {
        importMap.addToSubtypeAsterisk(typeInfo);
        // not adding the type to the context!!! the subtypes will be added by the inspector
    }

    public boolean isPackagePrefix(PackagePrefix packagePrefix) {
        return typeMap.isPackagePrefix(packagePrefix);
    }

    public void recursivelyAddVisibleSubTypes(TypeInfo typeInfo) {
        typeInfo.subTypes()
                .stream().filter(st -> !typeInfo.access().isPrivate())
                .forEach(this::addToContext);
        if (!typeInfo.parentClass().isJavaLangObject()) {
            recursivelyAddVisibleSubTypes(typeInfo.parentClass().typeInfo());
        }
        for (ParameterizedType interfaceImplemented : typeInfo.interfacesImplemented()) {
            recursivelyAddVisibleSubTypes(interfaceImplemented.typeInfo());
        }
    }
}
