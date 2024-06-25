package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.impl.runtime.RuntimeImpl;
import org.e2immu.language.inspection.api.InspectionState;
import org.e2immu.language.inspection.api.parser.*;
import org.e2immu.language.inspection.api.resource.*;
import org.e2immu.language.inspection.impl.parser.*;
import org.e2immu.language.inspection.impl.parser.ResolverImpl;
import org.parsers.java.JavaParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;


public class CommonTestParse {

    private TypeInfo predefined(String fullyQualifiedName, boolean complain) {
        return switch (fullyQualifiedName) {
            case "java.lang.Class" -> clazz;
            case "java.lang.Object" -> runtime.objectTypeInfo();
            case "java.lang.String" -> runtime.stringTypeInfo();
            case "java.lang.Override" -> override;
            case "java.lang.Integer" -> runtime.integerTypeInfo();
            case "java.lang.SuppressWarnings" -> suppressWarnings;
            case "java.lang.System" -> system;
            case "java.lang.Math" -> math;
            case "java.lang.Exception" -> exception;
            case "java.io.PrintStream" -> printStream;
            case "java.util.function.Function" -> function;
            case "java.util.function.BiConsumer" -> biConsumer;
            default -> {
                if (complain) throw new UnsupportedOperationException("Type " + fullyQualifiedName);
                yield null;
            }
        };
    }

    protected final Runtime runtime = new RuntimeImpl() {
        @Override
        public TypeInfo getFullyQualified(String name, boolean complain) {
            return predefined(name, complain);
        }

        @Override
        public TypeInfo syntheticFunctionalType(int inputParameters, boolean hasReturnValue) {
            if (inputParameters == 1 && hasReturnValue) return function;
            if (inputParameters == 2 && !hasReturnValue) return biConsumer;
            throw new UnsupportedOperationException();
        }
    };

    protected final TypeInfo clazz;
    protected final TypeInfo math;
    protected final TypeInfo system;
    protected final TypeInfo exception;
    protected final TypeInfo printStream;
    protected final TypeInfo function;
    protected final TypeInfo biConsumer;
    protected final TypeInfo suppressWarnings;
    protected final TypeInfo override;


    class CompiledTypesManagerImpl implements CompiledTypesManager {

        @Override
        public ByteCodeInspector byteCodeInspector() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Resources classPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(TypeInfo typeInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SourceFile fqnToPath(String fqn, String suffix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeInfo get(String fullyQualifiedName) {
            return predefined(fullyQualifiedName, false);
        }

        @Override
        public TypeInfo getOrCreate(String fullyQualifiedName, boolean complain) {
            return predefined(fullyQualifiedName, false);
        }

        @Override
        public void ensureInspection(TypeInfo typeInfo) {
            // do nothing
        }

        @Override
        public TypeInfo load(SourceFile path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLazyInspection(TypeInfo typeInfo) {
            throw new UnsupportedOperationException();
        }
    }

    protected CommonTestParse() {
        CompilationUnit javaLang = runtime.newCompilationUnitBuilder().setPackageName("java.lang").build();
        CompilationUnit javaIo = runtime.newCompilationUnitBuilder().setPackageName("java.io").build();
        CompilationUnit javaUtilFunction = runtime.newCompilationUnitBuilder().setPackageName("java.util.function").build();

        suppressWarnings = runtime.newTypeInfo(javaLang, "SuppressWarnings");
        clazz = runtime.newTypeInfo(javaLang, "Class");
        math = runtime.newTypeInfo(javaLang, "Math");
        override = runtime.newTypeInfo(javaLang, "Override");
        printStream = runtime.newTypeInfo(javaIo, "PrintStream");
        system = runtime.newTypeInfo(javaLang, "System");
        exception = runtime.newTypeInfo(javaLang, "Exception");
        function = runtime.newTypeInfo(javaUtilFunction, "Function");
        biConsumer = runtime.newTypeInfo(javaUtilFunction, "BiConsumer");

        clazz.builder().addTypeParameter(runtime.newTypeParameter(0, "C", clazz));

        defineFunction();
        defineBiConsumer();

        MethodInfo pow = runtime.newMethod(math, "pow", runtime.methodTypeStaticMethod());
        pow.builder().addParameter("base", runtime.doubleParameterizedType());
        pow.builder().addParameter("exponent", runtime.doubleParameterizedType());
        pow.builder().setReturnType(runtime.doubleParameterizedType());
        pow.builder().commit();
        math.builder().addMethod(pow).setParentClass(runtime.objectParameterizedType());
        math.builder().commit();

        MethodInfo printlnString = runtime.newMethod(printStream, "println", runtime.methodTypeMethod());
        printlnString.builder().addParameter("string", runtime.stringParameterizedType());
        printlnString.builder().setReturnType(runtime.voidParameterizedType());
        printlnString.builder().commit();
        printStream.builder().addMethod(printlnString);

        MethodInfo printlnInt = runtime.newMethod(printStream, "println", runtime.methodTypeMethod());
        printlnInt.builder().addParameter("i", runtime.intParameterizedType());
        printlnInt.builder().setReturnType(runtime.voidParameterizedType());
        printlnInt.builder().commit();
        printStream.builder().addMethod(printlnInt);

        MethodInfo printlnObject = runtime.newMethod(printStream, "println", runtime.methodTypeMethod());
        printlnObject.builder().addParameter("object", runtime.objectParameterizedType());
        printlnObject.builder().setReturnType(runtime.voidParameterizedType());
        printlnObject.builder().commit();
        printStream.builder().addMethod(printlnObject);

        printStream.builder().setParentClass(runtime.objectParameterizedType());
        printStream.builder().commit();

        FieldInfo out = runtime.newFieldInfo("out", true, printStream.asSimpleParameterizedType(), system);
        system.builder().addField(out);
        system.builder().commit();

        override.builder().setTypeNature(runtime.typeNatureAnnotation());
    }

    private void defineFunction() {
        TypeParameter T = runtime.newTypeParameter(0, "T", function);
        TypeParameter R = runtime.newTypeParameter(1, "R", function);
        function.builder().addTypeParameter(T).addTypeParameter(R).setTypeNature(runtime.typeNatureInterface());
        MethodInfo apply = runtime.newMethod(function, "apply", runtime.methodTypeAbstractMethod());
        apply.builder().setReturnType(runtime.newParameterizedType(R, 0, null))
                .addMethodModifier(runtime.methodModifierPublic())
                .addParameter("t", runtime.newParameterizedType(T, 0, null));
        apply.builder().computeAccess();
        apply.builder().commit();
        function.builder().addMethod(apply).addTypeModifier(runtime.typeModifierPublic())
                .setSingleAbstractMethod(apply)
                .computeAccess();
    }

    private void defineBiConsumer() {
        TypeParameter T = runtime.newTypeParameter(0, "T", biConsumer);
        TypeParameter U = runtime.newTypeParameter(1, "U", biConsumer);
        biConsumer.builder().addTypeParameter(T).addTypeParameter(U).setTypeNature(runtime.typeNatureInterface());
        MethodInfo accept = runtime.newMethod(biConsumer, "accept", runtime.methodTypeAbstractMethod());
        accept.builder().setReturnType(runtime.voidParameterizedType())
                .addMethodModifier(runtime.methodModifierPublic())
                .addParameter("t", runtime.newParameterizedType(T, 0, null));
        accept.builder().addParameter("u", runtime.newParameterizedType(U, 1, null));
        accept.builder().computeAccess();
        accept.builder().commit();
        biConsumer.builder().addMethod(accept).addTypeModifier(runtime.typeModifierPublic())
                .setSingleAbstractMethod(accept)
                .computeAccess();
    }

    protected Context parseReturnContext(String input) {
        Summary failFastSummary = new SummaryImpl(false);
        JavaParser parser = new JavaParser(input);
        parser.setParserTolerant(false);
        SourceTypesImpl sourceTypes = new SourceTypesImpl();
        CompiledTypesManager compiledTypesManager = new CompiledTypesManagerImpl();
        TypeContextImpl typeContext = new TypeContextImpl(compiledTypesManager, sourceTypes);
        Resolver resolver = new ResolverImpl(new ParseHelperImpl(runtime));
        Context rootContext = ContextImpl.create(runtime, failFastSummary, resolver, typeContext);
        ParseCompilationUnit parseCompilationUnit = new ParseCompilationUnit(rootContext);
        try {
            parseCompilationUnit.parse(new URI("input"), parser.CompilationUnit());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        rootContext.resolver().resolve();
        return rootContext;
    }

    protected TypeInfo parse(String input) {
        Summary failFastSummary = new SummaryImpl(true);
        JavaParser parser = new JavaParser(input);
        parser.setParserTolerant(false);
        Resolver resolver = new ResolverImpl(new ParseHelperImpl(runtime));
        SourceTypesImpl sourceTypes = new SourceTypesImpl();
        CompiledTypesManager compiledTypesManager = new CompiledTypesManagerImpl();
        TypeContextImpl typeContext = new TypeContextImpl(compiledTypesManager, sourceTypes);
        Context rootContext = ContextImpl.create(runtime, failFastSummary, resolver, typeContext);
        ParseCompilationUnit parseCompilationUnit = new ParseCompilationUnit(rootContext);
        try {
            List<TypeInfo> types = parseCompilationUnit.parse(new URI("input"), parser.CompilationUnit());
            resolver.resolve();
            return types.get(0);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
