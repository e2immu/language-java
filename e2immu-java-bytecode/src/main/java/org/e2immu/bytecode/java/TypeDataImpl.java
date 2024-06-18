package org.e2immu.bytecode.java;

import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.inputapi.InspectionState;
import org.e2immu.inputapi.TypeMap;

public class TypeDataImpl implements TypeData {
    private final TypeInfo typeInfo;
    private volatile InspectionState inspectionState;

    public TypeDataImpl(TypeInfo typeInfo, InspectionState inspectionState) {
        this.typeInfo = typeInfo;
        this.inspectionState = inspectionState;
    }

    @Override
    public void setInspectionState(InspectionState inspectionState) {
        this.inspectionState = inspectionState;
    }

    @Override
    public InspectionState getInspectionState() {
        return inspectionState;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public TypeMap.InspectionAndState toInspectionAndState() {
       return new TypeMap.InspectionAndState(typeInfo, getInspectionState());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TypeDataImpl td && td.typeInfo.equals(typeInfo);
    }

    @Override
    public int hashCode() {
        return typeInfo.fullyQualifiedName().hashCode();
    }
}
