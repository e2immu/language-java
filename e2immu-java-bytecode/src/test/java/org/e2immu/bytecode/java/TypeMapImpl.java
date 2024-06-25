package org.e2immu.bytecode.java;

import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.api.InspectionState;
import org.e2immu.language.inspection.api.resource.Resources;
import org.e2immu.language.inspection.api.resource.SourceFile;
import org.e2immu.language.inspection.api.resource.TypeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeMapImpl implements TypeMap {
    private final List<String> queue = new ArrayList<>();
    private final Map<String, InspectionAndState> map = new HashMap<>();
    private ByteCodeInspector byteCodeInspector;
    private final Resources classPath;

    public TypeMapImpl(Resources classPath) {
        this.classPath = classPath;
    }

    public void setByteCodeInspector(ByteCodeInspector byteCodeInspector) {
        this.byteCodeInspector = byteCodeInspector;
    }

    @Override
    public void add(TypeInfo typeInfo, InspectionState inspectionState) {
        map.put(typeInfo.fullyQualifiedName(), new InspectionAndState(typeInfo, inspectionState));
    }

    @Override
    public void addToByteCodeQueue(String fqn) {
        queue.add(fqn);
    }

    @Override
    public TypeInfo get(String fqn, boolean complain) {
        InspectionAndState ias = map.get(fqn);
        if (ias != null) {
            if (ias.state().isDone()) return ias.typeInfo();
        }
        SourceFile sourceFile = classPath.fqnToPath(fqn, ".class");
        List<TypeData> typeDataList = byteCodeInspector.inspectFromPath(sourceFile);
        TypeInfo typeInfo = null;
        for (TypeData typeData : typeDataList) {
            TypeInfo ti = typeData.getTypeInfo();
            if (ti.fullyQualifiedName().equals(fqn)) {
                typeInfo = ti;
            }
            map.put(fqn, typeData.toInspectionAndState());
        }
        assert typeInfo != null;
        return typeInfo;
    }

    @Override
    public boolean isPackagePrefix(List<String> packagePrefix) {
        return false;
    }

    @Override
    public TypeInfo addToTrie(TypeInfo subType) {
        map.put(subType.fullyQualifiedName(), new InspectionAndState(subType, null));
        return subType;
    }

    @Override
    public TypeInfo get(String fullyQualifiedName) {
        InspectionAndState ias = map.get(fullyQualifiedName);
        return ias == null ? null : ias.typeInfo();
    }

    @Override
    public InspectionAndState typeInspectionSituation(String fqn) {
        return map.get(fqn);
    }

    public List<String> getQueue() {
        return queue;
    }


    @Override
    public String pathToFqn(String path) {
        String stripDotClass = Resources.stripDotClass(path);
        if (stripDotClass.endsWith("$")) {
            // scala
            return stripDotClass.substring(0, stripDotClass.length() - 1).replaceAll("[/$]", ".") + ".object";
        }
        if (stripDotClass.endsWith("$class")) {
            // scala; keep it as is, ending in .class
            return stripDotClass.replaceAll("[/$]", ".");
        }
        int anon;
        if ((anon = stripDotClass.indexOf("$$anonfun")) > 0) {
            // scala
            String random = Integer.toString(Math.abs(stripDotClass.hashCode()));
            return stripDotClass.substring(0, anon).replaceAll("[/$]", ".") + "." + random;
        }
        return stripDotClass.replaceAll("[/$]", ".");
    }

}
