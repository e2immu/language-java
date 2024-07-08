package org.e2immu.parser.java;

import org.e2immu.annotation.Identity;
import org.e2immu.language.cst.api.element.ImportStatement;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.support.Either;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

public class ParseCompilationUnit extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseCompilationUnit.class);

    private final Context rootContext;

    public ParseCompilationUnit(Context rootContext) {
        super(rootContext.runtime(), new Parsers(rootContext.runtime()));
        this.rootContext = rootContext;
    }

    public List<TypeInfo> parse(URI uri, CompilationUnit cu) {
        try {
            return internalParse(uri, null, cu);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            re.printStackTrace(System.err);
            LOGGER.error("Caught exception parsing compilation unit {}", uri);
            rootContext.summary().addParserError(re);
            return List.of();
        }
    }

    // this version is for when the TypeInfo object has already been created, and must be reused
    // Identity of the compilation unit is not that important, we'll overwrite the source in the TypeInfo builder.
    public List<TypeInfo> parse(TypeInfo typeInfo, CompilationUnit cu) {
        URI uri = typeInfo.compilationUnit().uri();
        try {
            return internalParse(uri, typeInfo, cu);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            re.printStackTrace(System.err);
            LOGGER.error("Caught exception parsing compilation unit {} from type {}", uri, typeInfo);
            rootContext.summary().addParserError(re);
            return List.of();
        }
    }

    private List<TypeInfo> internalParse(URI uri, TypeInfo typeInfoOrNull, CompilationUnit cu) {
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

        Context newContext = rootContext.newCompilationUnit(compilationUnit);
        compilationUnit.importStatements().forEach(is -> newContext.typeContext().addToImportMap(is));

        Map<String, TypeInfo> typesByFQN = recursivelyFindTypes(Either.left(compilationUnit), cu);

        List<TypeInfo> types = new ArrayList<>();
        while (i < cu.size() && cu.get(i) instanceof TypeDeclaration cd) {
            TypeInfo typeInfo = parsers.parseTypeDeclaration().parse(newContext, typeInfoOrNull,
                    Either.left(compilationUnit), typesByFQN, cd);
            if (typeInfo != null) {
                types.add(typeInfo);
            } // else: error...
            i++;
        }
        return types;
    }

    private ImportStatement parseImportDeclaration(ImportDeclaration id) {
        boolean isStatic = id.get(1) instanceof KeyWord kw && Token.TokenType.STATIC.equals(kw.getType());
        int i = isStatic ? 2 : 1;
        String importString = id.get(i).getSource();
        if (id.get(i + 1) instanceof Delimiter d && Token.TokenType.DOT.equals(d.getType())
            && id.get(i + 2) instanceof Operator o && Token.TokenType.STAR.equals(o.getType())) {
            return runtime.newImportStatement(importString + ".*", isStatic);
        }
        return runtime.newImportStatement(importString, isStatic);
    }
}
