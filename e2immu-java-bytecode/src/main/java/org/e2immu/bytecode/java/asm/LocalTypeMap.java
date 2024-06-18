package org.e2immu.bytecode.java.asm;


import org.e2immu.annotation.Modified;
import org.e2immu.bytecode.java.TypeData;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.inputapi.SourceFile;
import org.e2immu.inputapi.TypeContext;
import org.e2immu.inputapi.TypeMap;

import java.util.List;

/*
In the local type map, types are either
 */
public interface LocalTypeMap  {

    /*
    now = directly
    trigger = leave in TRIGGER_BYTE_CODE state; if never visited, it'll not be loaded
    queue = ensure that it gets loaded before building the type map
     */
    enum LoadMode {NOW, TRIGGER, QUEUE}

    // null if not present
    TypeMap.InspectionAndState typeInspectionSituation(String fqn);

    /*
    up to a TRIGGER_BYTE_CODE_INSPECTION stage, or, when start is true,
    actual loading
     */
    @Modified
    TypeInfo getOrCreate(String fqn, LoadMode loadMode);

    /*
    same as the string version, but here we already know the enclosure relation
     */
    @Modified
    TypeInfo getOrCreate(TypeInfo subType);

    List<TypeData> loaded();
    /*
     Call from My*Visitor back to ByteCodeInspector, as part of a `inspectFromPath(Source)` call.
     */

    // do actual byte code inspection
    @Modified
    TypeInfo inspectFromPath(SourceFile name, TypeContext typeContext, LoadMode loadMode);

}
