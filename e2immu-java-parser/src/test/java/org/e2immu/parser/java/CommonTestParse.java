package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.impl.runtime.RuntimeImpl;
import org.e2immu.language.inspection.api.InspectionState;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Resolver;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.language.inspection.impl.parser.*;
import org.e2immu.language.inspection.api.parser.PackagePrefix;
import org.e2immu.language.inspection.api.resource.TypeMap;
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
            case "java.lang.Integer" -> runtime.integerTypeInfo();
            case "java.lang.SuppressWarnings" -> suppressWarnings;
            case "java.lang.System" -> system;
            case "java.lang.Math" -> math;
            case "java.lang.Exception" -> exception;
            case "java.io.PrintStream" -> printStream;
            case "java.util.function.Function" -> function;
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
            throw new UnsupportedOperationException();
        }
    };

    protected final TypeInfo clazz;
    protected final TypeInfo math;
    protected final TypeInfo system;
    protected final TypeInfo exception;
    protected final TypeInfo printStream;
    protected final TypeInfo function;
    protected final TypeInfo suppressWarnings;

    class TypeMapBuilder implements TypeMap.Builder {

        @Override
        public TypeInfo getOrCreate(String fqn, boolean complain) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensureInspection(TypeInfo typeInfo) {

        }

        @Override
        public TypeInfo get(Class<?> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeInfo get(String name, boolean complain) {
            return null;
        }

        @Override
        public void add(TypeInfo typeInfo, InspectionState inspectionState) {

        }

        @Override
        public void addToByteCodeQueue(String fqn) {

        }

        @Override
        public TypeInfo addToTrie(TypeInfo subType) {
            return null;
        }

        @Override
        public TypeInfo get(String fullyQualifiedName) {
            return runtime.getFullyQualified(fullyQualifiedName, true);
        }

        @Override
        public boolean isPackagePrefix(PackagePrefix packagePrefix) {
            return false;
        }

        @Override
        public String pathToFqn(String interfaceName) {
            return "";
        }

        @Override
        public InspectionAndState typeInspectionSituation(String fqn) {
            return null;
        }
    }

    protected CommonTestParse() {
        CompilationUnit javaLang = runtime.newCompilationUnitBuilder().setPackageName("java.lang").build();
        CompilationUnit javaIo = runtime.newCompilationUnitBuilder().setPackageName("java.io").build();
        CompilationUnit javaUtilFunction = runtime.newCompilationUnitBuilder().setPackageName("java.util.function").build();

        suppressWarnings = runtime.newTypeInfo(javaLang, "SuppressWarnings");
        clazz = runtime.newTypeInfo(javaLang, "Class");
        math = runtime.newTypeInfo(javaLang, "Math");
        printStream = runtime.newTypeInfo(javaIo, "PrintStream");
        system = runtime.newTypeInfo(javaLang, "System");
        exception = runtime.newTypeInfo(javaLang, "Exception");
        function = runtime.newTypeInfo(javaUtilFunction, "Function");

        clazz.builder().addTypeParameter(runtime.newTypeParameter(0, "C", clazz));

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

        MethodInfo pow = runtime.newMethod(math, "pow", runtime.methodTypeStaticMethod());
        pow.builder().addParameter("base", runtime.doubleParameterizedType());
        pow.builder().addParameter("exponent", runtime.doubleParameterizedType());
        pow.builder().setReturnType(runtime.doubleParameterizedType());
        pow.builder().commit();
        math.builder().addMethod(pow);
        math.builder().commit();

        MethodInfo println = runtime.newMethod(printStream, "println", runtime.methodTypeMethod());
        println.builder().addParameter("string", runtime.stringParameterizedType());
        println.builder().setReturnType(runtime.voidParameterizedType());
        println.builder().commit();
        printStream.builder().addMethod(println);
        printStream.builder().commit();

        FieldInfo out = runtime.newFieldInfo("out", true, printStream.asSimpleParameterizedType(), system);
        system.builder().addField(out);
        system.builder().commit();
    }

    protected Context parseReturnContext(String input) {
        Summary failFastSummary = new SummaryImpl(false);
        JavaParser parser = new JavaParser(input);
        parser.setParserTolerant(false);
        TypeMap.Builder typeMapBuilder = new TypeMapBuilder();
        Resolver resolver = new ResolverImpl(new ParseHelperImpl(runtime));
        TypeContextImpl typeContext = new TypeContextImpl(typeMapBuilder);
        VariableContextImpl variableContext = new VariableContextImpl();
        AnonymousTypeCountersImpl anonymousTypeCounters = new AnonymousTypeCountersImpl();
        Context rootContext = new ContextImpl(runtime, failFastSummary, resolver, typeContext, variableContext,
                anonymousTypeCounters, null);
        ParseCompilationUnit parseCompilationUnit = new ParseCompilationUnit(typeMapBuilder, rootContext);
        try {
            parseCompilationUnit.parse(new URI("input"), parser.CompilationUnit());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        resolver.resolve();
        return rootContext;
    }

    protected TypeInfo parse(String input) {
        Summary failFastSummary = new SummaryImpl(true);
        JavaParser parser = new JavaParser(input);
        parser.setParserTolerant(false);
        TypeMap.Builder typeMapBuilder = new TypeMapBuilder();
        Resolver resolver = new ResolverImpl(new ParseHelperImpl(runtime));
        TypeContextImpl typeContext = new TypeContextImpl(typeMapBuilder);
        VariableContextImpl variableContext = new VariableContextImpl();
        AnonymousTypeCountersImpl anonymousTypeCounters = new AnonymousTypeCountersImpl();
        Context rootContext = new ContextImpl(runtime, failFastSummary, resolver, typeContext, variableContext,
                anonymousTypeCounters, null);
        ParseCompilationUnit parseCompilationUnit = new ParseCompilationUnit(typeMapBuilder, rootContext);
        try {
            List<TypeInfo> types = parseCompilationUnit.parse(new URI("input"), parser.CompilationUnit());
            resolver.resolve();
            return types.get(0);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
