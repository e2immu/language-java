package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.ImportStatement;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.support.Either;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

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
            rootContext.summary().addParserError(re);
            return List.of();
        }
    }

    private List<TypeInfo> internalParse(org.e2immu.language.cst.api.element.CompilationUnit compilationUnit, CompilationUnit cu) {
        assert compilationUnit.packageName() != null;

        int i = 0;
        List<ImportStatement> importStatements = new LinkedList<>();
        while (i < cu.size() && !(cu.get(i) instanceof TypeDeclaration)) {
            if (cu.get(i) instanceof ImportDeclaration id) {
                ImportStatement importStatement = parseImportDeclaration(id);
                importStatements.add(importStatement);
            }
            i++;
        }
        compilationUnit.setImportStatements(List.copyOf(importStatements));

        Context newContext = rootContext.newCompilationUnit(compilationUnit);
        compilationUnit.importStatements().forEach(is -> newContext.typeContext().addToImportMap(is));

        List<TypeInfo> types = new ArrayList<>();
        while (i < cu.size() && cu.get(i) instanceof TypeDeclaration cd) {
            TypeInfo typeInfo = parsers.parseTypeDeclaration().parse(newContext, Either.left(compilationUnit), cd);
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
