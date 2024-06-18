package org.e2immu.bytecode.java;

import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.api.InspectionState;
import org.e2immu.language.inspection.api.resource.TypeMap;

public interface TypeData {
    InspectionState getInspectionState();

    void setInspectionState(InspectionState inspectionState);

    TypeInfo getTypeInfo();

    TypeMap.InspectionAndState toInspectionAndState();
/*
    Iterable<Map.Entry<String, MethodInfo.Builder>> methodInspectionBuilders();

    Iterable<Map.Entry<FieldInfo, FieldInfo.Builder>> fieldInspectionBuilders();

    FieldInspection.Builder fieldInspectionsPut(FieldInfo fieldInfo, FieldInspection.Builder builder);

    MethodInspection.Builder methodInspectionsPut(String distinguishingName, MethodInspection.Builder builder);

    FieldInspection fieldInspectionsGet(FieldInfo fieldInfo);

    MethodInspection methodInspectionsGet(String distinguishingName);

    default TypeMap.InspectionAndState toInspectionAndState() {
        return new TypeMap.InspectionAndState(getTypeInspectionBuilder(), getInspectionState());
    }*/
}
