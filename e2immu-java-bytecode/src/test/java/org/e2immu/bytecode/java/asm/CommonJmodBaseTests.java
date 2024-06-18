package org.e2immu.bytecode.java.asm;

import org.e2immu.bytecode.java.ResourcesImpl;
import org.e2immu.bytecode.java.TypeContextImpl;
import org.e2immu.bytecode.java.TypeMapImpl;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.impl.runtime.RuntimeImpl;
import org.e2immu.language.inspection.api.parser.TypeContext;
import org.e2immu.language.inspection.api.resource.Resources;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.URL;

public abstract class CommonJmodBaseTests {
    protected static Runtime runtime;

    protected static TypeContext typeContext;
    protected static Resources classPath;
    protected static ByteCodeInspectorImpl byteCodeInspector;

    @BeforeAll
    public static void beforeClass() throws IOException {
        ResourcesImpl cp = new ResourcesImpl();
        classPath = cp;
        cp.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        //Resources annotationResources = new Resources();
        // AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        TypeMapImpl typeMap = new TypeMapImpl(classPath);
        typeContext = new TypeContextImpl(typeMap);
        runtime = new RuntimeImpl();
        byteCodeInspector = new ByteCodeInspectorImpl(runtime, classPath, null, typeContext);
        typeMap.setByteCodeInspector(byteCodeInspector);
        //typeContext.typeMap().setByteCodeInspector(byteCodeInspector);
        //typeContext.loadPrimitives();
        //Input.preload(typeContext, classPath, "java.util");
    }

}
