package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.ImportStatement;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.language.inspection.api.resource.TypeMap;
import org.e2immu.support.Either;
import org.parsers.java.ast.CompilationUnit;
import org.parsers.java.ast.ImportDeclaration;
import org.parsers.java.ast.PackageDeclaration;
import org.parsers.java.ast.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ParseCompilationUnit extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseCompilationUnit.class);

    private final Context rootContext;
    private final TypeMap.Builder typeMap;
    private final ParseTypeDeclaration parseTypeDeclaration;

    public ParseCompilationUnit(TypeMap.Builder typeMap, Context rootContext) {
        super(rootContext.runtime());
        this.rootContext = rootContext;
        this.typeMap = typeMap;
        parseTypeDeclaration = new ParseTypeDeclaration(runtime);
    }

    public List<TypeInfo> parse(URI uri, CompilationUnit cu) {
        try {
            return internalParse(uri, cu);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            LOGGER.error("Caught exception parsing compilation unit {}", uri);
            rootContext.summary().addParserError(re);
            return List.of();
        }
    }

    private List<TypeInfo> internalParse(URI uri, CompilationUnit cu) {
        PackageDeclaration packageDeclaration = cu.getPackageDeclaration();
        String packageName = packageDeclaration == null ? ""
                : Objects.requireNonNullElse(packageDeclaration.getName(), "");
        org.e2immu.language.cst.api.element.CompilationUnit.Builder builder = runtime.newCompilationUnitBuilder()
                .setPackageName(packageName);
        int i = 0;
        while (i < cu.size() && !(cu.get(i) instanceof TypeDeclaration)) {
            if (cu.get(i) instanceof ImportDeclaration id) {
                ImportStatement importStatement = parseImportDeclaration(id);
                builder.addImportStatement(importStatement);
            } else if (!(cu.get(i) instanceof PackageDeclaration)) {
                throw new Summary.ParseException(uri, "Expect PackageDeclaration, got " + cu.get(i).getClass() + " in {}");
            }
            i++;
        }
        org.e2immu.language.cst.api.element.CompilationUnit compilationUnit = builder.build();

        Context newContext = rootContext.newCompilationUnit(rootContext.resolver(), typeMap, compilationUnit);
        compilationUnit.importStatements().forEach(is -> newContext.typeContext().addToImportMap(is));

        List<TypeInfo> types = new ArrayList<>();
        while (i < cu.size() && cu.get(i) instanceof TypeDeclaration cd) {
            TypeInfo typeInfo = parseTypeDeclaration.parse(newContext, Either.left(compilationUnit), cd);
            if (typeInfo != null) {
                types.add(typeInfo);
            } // else: error...
            i++;
        }
        return types;
    }

    private ImportStatement parseImportDeclaration(ImportDeclaration id) {
        String is = id.get(1).getSource();
        return runtime.newImportStatement(is);
    }
}
