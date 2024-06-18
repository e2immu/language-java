package org.e2immu.parser.java;

import org.e2immu.cstapi.element.ImportStatement;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.parserapi.Context;
import org.e2immu.parserimpl.ContextImpl;
import org.e2immu.resourceapi.TypeMap;
import org.e2immu.support.Either;
import org.parsers.java.Node;
import org.parsers.java.ast.CompilationUnit;
import org.parsers.java.ast.ImportDeclaration;
import org.parsers.java.ast.PackageDeclaration;
import org.parsers.java.ast.TypeDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ParseCompilationUnit extends CommonParse {
    private final Context rootContext;
    private final TypeMap.Builder typeMap;
    private final ParseTypeDeclaration parseTypeDeclaration;

    public ParseCompilationUnit(TypeMap.Builder typeMap, Context rootContext) {
        super(rootContext.runtime());
        this.rootContext = rootContext;
        this.typeMap = typeMap;
        parseTypeDeclaration = new ParseTypeDeclaration(runtime);
    }

    public List<TypeInfo> parse(CompilationUnit cu) {
        PackageDeclaration packageDeclaration = cu.getPackageDeclaration();
        String packageName = packageDeclaration == null ? ""
                : Objects.requireNonNullElse(packageDeclaration.getName(), "");
        org.e2immu.cstapi.element.CompilationUnit.Builder builder = runtime.newCompilationUnitBuilder()
                .setPackageName(packageName);
        int i = 0;
        while (i < cu.size() && !(cu.get(i) instanceof TypeDeclaration)) {
            if (cu.get(i) instanceof ImportDeclaration id) {
                ImportStatement importStatement = parseImportDeclaration(id);
                builder.addImportStatement(importStatement);
            } else if(!(cu.get(i) instanceof PackageDeclaration)){
                throw new UnsupportedOperationException();
            }
            i++;
        }
        org.e2immu.cstapi.element.CompilationUnit compilationUnit = builder.build();

        Context newContext = rootContext.newCompilationUnit(rootContext.resolver(), typeMap, compilationUnit);
        compilationUnit.importStatements().forEach(is -> newContext.typeContext().addToImportMap(is));

        List<TypeInfo> types = new ArrayList<>();
        while (i < cu.size() && cu.get(i) instanceof TypeDeclaration cd) {
            TypeInfo typeInfo = parseTypeDeclaration.parse(newContext, Either.left(compilationUnit), cd);
            types.add(typeInfo);
            i++;
        }
        return types;
    }

    private ImportStatement parseImportDeclaration(ImportDeclaration id) {
        String is = id.get(1).getSource();
        return runtime.newImportStatement(is);
    }
}
