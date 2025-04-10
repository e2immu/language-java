package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.impl.runtime.RuntimeImpl;
import org.e2immu.language.inspection.api.parser.*;
import org.e2immu.language.inspection.api.resource.*;
import org.e2immu.language.inspection.impl.parser.*;
import org.e2immu.language.inspection.impl.parser.ResolverImpl;
import org.parsers.java.JavaParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Supplier;


public class CommonTestParse {

    private TypeInfo predefined(String fullyQualifiedName, boolean complain) {
        return switch (fullyQualifiedName) {
            case "java.lang.Class" -> runtime.classTypeInfo();
            case "java.lang.Object" -> runtime.objectTypeInfo();
            case "java.lang.Long" -> runtime.boxedLongTypeInfo();
            case "java.lang.String" -> runtime.stringTypeInfo();
            case "java.lang.Override" -> override;
            case "java.lang.Integer" -> runtime.integerTypeInfo();
            case "java.lang.SuppressWarnings" -> suppressWarnings;
            case "java.lang.System" -> system;
            case "java.lang.Math" -> math;
            case "java.lang.Exception" -> exception;
            case "java.lang.RuntimeException" -> runtimeException;
            case "java.lang.Enum" -> enumTypeInfo;
            case "java.lang.AutoCloseable" -> autoCloseable;
            case "java.io.PrintStream" -> printStream;
            case "java.util.function.Function" -> function;
            case "java.util.function.BiConsumer" -> biConsumer;
            case "java.util.Hashtable" -> hashtable;
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

    protected final TypeInfo math;
    protected final TypeInfo system;
    protected final TypeInfo exception;
    protected final TypeInfo runtimeException;
    protected final TypeInfo printStream;
    protected final TypeInfo function;
    protected final TypeInfo biConsumer;
    protected final TypeInfo suppressWarnings;
    protected final TypeInfo override;
    protected final TypeInfo enumTypeInfo;
    protected final TypeInfo autoCloseable;
    protected final TypeInfo hashtable;

    class CompiledTypesManagerImpl implements CompiledTypesManager {

        @Override
        public TypeInfo get(String fullyQualifiedName) {
            return predefined(fullyQualifiedName, false);
        }

    }

    protected CommonTestParse() {
        CompilationUnit javaLang = runtime.newCompilationUnitBuilder().setPackageName("java.lang").build();
        CompilationUnit javaIo = runtime.newCompilationUnitBuilder().setPackageName("java.io").build();
        CompilationUnit javaUtil = runtime.newCompilationUnitBuilder().setPackageName("java.util").build();
        CompilationUnit javaUtilFunction = runtime.newCompilationUnitBuilder().setPackageName("java.util.function").build();

        suppressWarnings = runtime.newTypeInfo(javaLang, "SuppressWarnings");
        enumTypeInfo = runtime.newTypeInfo(javaLang, "Enum");
        math = runtime.newTypeInfo(javaLang, "Math");
        override = runtime.newTypeInfo(javaLang, "Override");
        printStream = runtime.newTypeInfo(javaIo, "PrintStream");
        system = runtime.newTypeInfo(javaLang, "System");
        exception = runtime.newTypeInfo(javaLang, "Exception");
        runtimeException = runtime.newTypeInfo(javaLang, "RuntimeException");
        function = runtime.newTypeInfo(javaUtilFunction, "Function");
        biConsumer = runtime.newTypeInfo(javaUtilFunction, "BiConsumer");
        autoCloseable = runtime.newTypeInfo(javaLang, "AutoCloseable");
        hashtable = runtime.newTypeInfo(javaUtil, "Hashtable");

        TypeParameter tpClass = runtime.newTypeParameter(0, "C",
                runtime.classTypeInfo()).builder().commit();
        runtime.classTypeInfo().builder().addOrSetTypeParameter(tpClass);
        MethodInfo getClass = runtime.newMethod(runtime.objectTypeInfo(), "getClass", runtime.methodTypeMethod());
        getClass.builder()
                .setAccess(runtime.accessPublic())
                .setReturnType(runtime.newParameterizedType(runtime.classTypeInfo(), List.of(runtime.parameterizedTypeWildcard())))
                .commitParameters();
        MethodInfo clone = runtime.newMethod(runtime.objectTypeInfo(), "clone", runtime.methodTypeAbstractMethod());
        clone.builder()
                .setAccess(runtime.accessProtected())
                .setReturnType(runtime.newParameterizedType(runtime.objectTypeInfo(), 0))
                .commitParameters();
        MethodInfo toString = runtime.newMethod(runtime.objectTypeInfo(), "toString", runtime.methodTypeMethod());
        toString.builder().setAccess(runtime.accessPublic()).setReturnType(runtime.stringParameterizedType()).commitParameters();
        runtime.objectTypeInfo().builder().addMethod(getClass).addMethod(clone).addMethod(toString);

        defineFunction();
        defineBiConsumer();

        MethodInfo equals = runtime.newMethod(runtime.objectTypeInfo(), "equals", runtime.methodTypeMethod());
        equals.builder().addParameter("other", runtime.objectParameterizedType());
        equals.builder()
                .setAccess(runtime.accessPublic())
                .addMethodModifier(runtime.methodModifierPublic())
                .setReturnType(runtime.booleanParameterizedType())
                .commitParameters().commit();
        runtime.objectTypeInfo().builder().addMethod(equals);

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

        system.builder().setParentClass(runtime.objectParameterizedType());

        FieldInfo out = runtime.newFieldInfo("out", true, printStream.asSimpleParameterizedType(), system);
        system.builder().addField(out);
        system.builder().commit();

        override.builder().setTypeNature(runtime.typeNatureAnnotation());

        enumTypeInfo.builder()
                .setParentClass(runtime.objectParameterizedType())
                .addOrSetTypeParameter(runtime.newTypeParameter(0, "E", enumTypeInfo).builder().commit());
        enumTypeInfo.builder().commit();

        exception.builder().setTypeNature(runtime.typeNatureClass());

        MethodInfo rteConstructor = runtime.newConstructor(runtimeException);
        rteConstructor.builder()
                .setReturnType(runtime.parameterizedTypeReturnTypeOfConstructor())
                .commitParameters().commit();
        runtimeException.builder().setParentClass(runtime.newParameterizedType(exception, 0))
                .addConstructor(rteConstructor);

        MethodInfo valueOf = runtime.newMethod(runtime.boxedLongTypeInfo(), "valueOf", runtime.methodTypeStaticMethod());
        valueOf.builder()
                .setReturnType(runtime.newParameterizedType(runtime.boxedLongTypeInfo(), 0))
                .addParameter("l", runtime.longParameterizedType());
        valueOf.builder().commitParameters().commit();
        runtime.boxedLongTypeInfo().builder().addMethod(valueOf);

        MethodInfo length = runtime.newMethod(runtime.stringTypeInfo(), "length", runtime.methodTypeMethod());
        length.builder().setReturnType(runtime.intParameterizedType());
        length.builder().commit();
        runtime.stringTypeInfo().builder().addMethod(length);

        MethodInfo close = runtime.newMethod(autoCloseable, "close", runtime.methodTypeAbstractMethod());
        close.builder().setReturnType(runtime.voidParameterizedType()).commit();
        autoCloseable.builder().addMethod(close).setParentClass(runtime.objectParameterizedType());

        TypeParameter htTpK = runtime.newTypeParameter(0, "K", hashtable);
        TypeParameter htTpV = runtime.newTypeParameter(1, "V", hashtable);
        ParameterizedType htTpKPt = runtime.newParameterizedType(htTpK, 0, null);
        ParameterizedType htTpVPt = runtime.newParameterizedType(htTpV, 0, null);
        hashtable.builder()
                .setParentClass(runtime.objectParameterizedType())
                .addOrSetTypeParameter(htTpK)
                .addOrSetTypeParameter(htTpV);
        MethodInfo htConstructor = runtime.newConstructor(hashtable);
        htConstructor.builder()
                .setReturnType(hashtable.asParameterizedType())
                .setAccess(runtime.accessPublic()).commit();
        hashtable.builder().addConstructor(htConstructor);
        MethodInfo htPut = runtime.newMethod(hashtable, "put", runtime.methodTypeAbstractMethod());
        htPut.builder().setReturnType(htTpVPt)
                .addParameter("k", htTpKPt);
        htPut.builder().setAccess(runtime.accessPublic()).commitParameters().commit();
        hashtable.builder().addMethod(htPut);
        MethodInfo htGet = runtime.newMethod(hashtable, "get", runtime.methodTypeAbstractMethod());
        htGet.builder()
                .setReturnType(htTpVPt)
                .addParameter("k", htTpKPt);
        htGet.builder().setAccess(runtime.accessPublic()).commitParameters().commit();
        hashtable.builder().addMethod(htGet);
        hashtable.builder().commit();
    }

    private void defineFunction() {
        TypeParameter T = runtime.newTypeParameter(0, "T", function).builder().commit();
        TypeParameter R = runtime.newTypeParameter(1, "R", function).builder().commit();
        function.builder().addOrSetTypeParameter(T).addOrSetTypeParameter(R).setTypeNature(runtime.typeNatureInterface());
        MethodInfo apply = runtime.newMethod(function, "apply", runtime.methodTypeAbstractMethod());
        apply.builder().setReturnType(runtime.newParameterizedType(R, 0, null))
                .addMethodModifier(runtime.methodModifierPublic())
                .addParameter("t", runtime.newParameterizedType(T, 0, null));
        apply.builder().computeAccess();
        apply.builder().commit();
        function.builder()
                .setParentClass(runtime.objectParameterizedType())
                .addMethod(apply).addTypeModifier(runtime.typeModifierPublic())
                .setSingleAbstractMethod(apply)
                .computeAccess();
    }

    private void defineBiConsumer() {
        TypeParameter T = runtime.newTypeParameter(0, "T", biConsumer).builder().commit();
        TypeParameter U = runtime.newTypeParameter(1, "U", biConsumer).builder().commit();
        biConsumer.builder().addOrSetTypeParameter(T).addOrSetTypeParameter(U).setTypeNature(runtime.typeNatureInterface());
        MethodInfo accept = runtime.newMethod(biConsumer, "accept", runtime.methodTypeAbstractMethod());
        accept.builder().setReturnType(runtime.voidParameterizedType())
                .addMethodModifier(runtime.methodModifierPublic())
                .addParameter("t", runtime.newParameterizedType(T, 0, null));
        accept.builder().addParameter("u", runtime.newParameterizedType(U, 0, null));
        accept.builder().computeAccess();
        accept.builder().commit();
        biConsumer.builder().addMethod(accept).addTypeModifier(runtime.typeModifierPublic())
                .setSingleAbstractMethod(accept)
                .computeAccess();
    }


    protected record ParseResult(Context context, List<TypeInfo> types) {
    }

    protected Context parseReturnContext(String input) {
        return parseReturnBoth(input, false, false).context;
    }

    protected TypeInfo parse(String input) {
        return parse(input, false);
    }

    protected TypeInfo parse(String input, boolean detailedSources) {
        return parseReturnBoth(input, true, detailedSources).types.get(0);
    }

    protected ParseResult parseReturnBoth(String input, boolean failFast, boolean detailedSources) {
        Summary failFastSummary = new SummaryImpl(failFast);
        Supplier<JavaParser> parser = () -> {
            JavaParser p = new JavaParser(input);
            p.setParserTolerant(false);
            return p;
        };
        CompiledTypesManager compiledTypesManager = new CompiledTypesManagerImpl();
        SourceTypeMapImpl stm = new SourceTypeMapImpl();
        TypeContextImpl typeContext = new TypeContextImpl(runtime, compiledTypesManager, stm, false);
        Resolver resolver = new ResolverImpl(runtime.computeMethodOverrides(), new ParseHelperImpl(runtime));
        Context rootContext = ContextImpl.create(runtime, failFastSummary, resolver, typeContext, detailedSources);

        ScanCompilationUnit scanCompilationUnit = new ScanCompilationUnit(failFastSummary, runtime);
        CompilationUnit cu;
        try {
            ScanCompilationUnit.ScanResult sr = scanCompilationUnit.scan(new URI("input"),
                    parser.get().CompilationUnit(), detailedSources);
            stm.putAll(sr.sourceTypes());
            cu = sr.compilationUnit();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        ParseCompilationUnit parseCompilationUnit = new ParseCompilationUnit(rootContext);
        List<TypeInfo> types = parseCompilationUnit.parse(cu, parser.get().CompilationUnit());
        rootContext.resolver().resolve();
        return new ParseResult(rootContext, types);
    }


}
