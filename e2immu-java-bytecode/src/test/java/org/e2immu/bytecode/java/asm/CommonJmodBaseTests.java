package org.e2immu.bytecode.java.asm;

import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.impl.runtime.RuntimeImpl;
import org.e2immu.language.inspection.api.resource.CompiledTypesManager;
import org.e2immu.language.inspection.api.resource.Resources;
import org.e2immu.language.inspection.resource.CompiledTypesManagerImpl;
import org.e2immu.language.inspection.resource.ResourcesImpl;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.URL;

public abstract class CommonJmodBaseTests {
    protected static Runtime runtime;
    protected static Resources classPath;
    protected static ByteCodeInspectorImpl byteCodeInspector;
    protected static CompiledTypesManager compiledTypesManager;

    @BeforeAll
    public static void beforeClass() throws IOException {
        Resources cp = new ResourcesImpl(true);
        classPath = cp;
        cp.addJmod("home", new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        CompiledTypesManagerImpl mgr = new CompiledTypesManagerImpl(classPath);
        compiledTypesManager = mgr;
        runtime = new RuntimeImpl();
        byteCodeInspector = new ByteCodeInspectorImpl(runtime, compiledTypesManager);
        mgr.setByteCodeInspector(byteCodeInspector);
    }

}
