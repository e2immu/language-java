package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.language.inspection.api.parser.TypeContext;
import org.e2immu.support.Either;
import org.parsers.java.ast.Annotation;
import org.parsers.java.ast.CompilationUnit;
import org.parsers.java.ast.PackageDeclaration;
import org.parsers.java.ast.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/*
Second round! ScanCompilationUnit has already run.
We parse the import statements, add them to the compilation unit object, and then continue with the
actual parsing of types.
 */
public class ParseCompilationUnit extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseCompilationUnit.class);

    private final Context rootContext;

    public ParseCompilationUnit(Context rootContext) {
        super(rootContext.runtime(), new Parsers(rootContext.runtime()));
        this.rootContext = rootContext;
    }

    public List<TypeInfo> parse(org.e2immu.language.cst.api.element.CompilationUnit compilationUnit, CompilationUnit cu) {
        assert compilationUnit.packageName() != null;
        assert compilationUnit.uri() != null;
        try {
            return internalParse(compilationUnit, cu);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            re.printStackTrace(System.err);
            LOGGER.error("Caught exception parsing compilation unit {}", compilationUnit.uri());
            rootContext.summary().addParseException(new Summary.ParseException(compilationUnit, compilationUnit,
                    re.getMessage(), re));
            return List.of();
        }
    }

    private List<TypeInfo> internalParse(org.e2immu.language.cst.api.element.CompilationUnit compilationUnit,
                                         CompilationUnit cu) {
        assert compilationUnit.packageName() != null;

        Context newContext = rootContext.newCompilationUnit(compilationUnit);
        TypeContext typeContext = newContext.typeContext();
        compilationUnit.importStatements().forEach(is -> {
            if (is.isStatic()) {
                typeContext.addToStaticImportMap(is);
            } else {
                typeContext.addNonStaticImportToContext(is);
            }
        });

        // then, with lower priority, add type names from the same package
        rootContext.typeContext().typesInSamePackage(compilationUnit.packageName())
                .forEach(ti -> typeContext.addToContext(ti, false));

        List<TypeInfo> types = new ArrayList<>();
        if (compilationUnit.uri().toString().endsWith("package-info.java")) {
            TypeInfo typeInfo = buildPackageInfoType(compilationUnit, cu, newContext);
            types.add(typeInfo);
            LOGGER.debug("Added {}, test? {}", typeInfo, typeInfo.compilationUnit().sourceSet().test());
        } else {
            for (TypeDeclaration td : cu.childrenOfType(TypeDeclaration.class)) {
                TypeInfo typeInfo = parsers.parseTypeDeclaration().parse(newContext, Either.left(compilationUnit), td);
                if (typeInfo != null) {
                    types.add(typeInfo);
                } // else: error...
            }
        }
        return types;
    }

    private TypeInfo buildPackageInfoType(org.e2immu.language.cst.api.element.CompilationUnit compilationUnit,
                                          CompilationUnit cu,
                                          Context context) {
        PackageDeclaration packageDeclaration = cu.getPackageDeclaration();
        List<Annotation> annotations = packageDeclaration.childrenOfType(Annotation.class);
        String suffix = compilationUnit.sourceSet().test() ? "-test" : "";
        TypeInfo typeInfo = runtime.newTypeInfo(compilationUnit, "package-info" + suffix);
        TypeInfo.Builder builder = typeInfo.builder();
        parseAnnotations(context, builder, annotations);
        builder.setTypeNature(runtime.typeNaturePackageInfo())
                .setParentClass(runtime.objectParameterizedType())
                .setAccess(runtime.accessPublic())
                .setSource(source(cu))
                .commit();
        return typeInfo;
    }

}
