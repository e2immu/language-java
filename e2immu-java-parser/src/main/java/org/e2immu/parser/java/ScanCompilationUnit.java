package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.FingerPrint;
import org.e2immu.language.cst.api.element.ImportStatement;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.element.SourceSet;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.support.Either;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/*
First round, we only prepare a type map.
 */
public class ScanCompilationUnit extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanCompilationUnit.class);

    private final Summary summary;

    public ScanCompilationUnit(Summary summary, Runtime runtime) {
        super(runtime, new Parsers(runtime));
        this.summary = summary;
    }

    public record ScanResult(Map<String, TypeInfo> sourceTypes,
                             org.e2immu.language.cst.api.element.CompilationUnit compilationUnit) {
    }

    public ScanResult scan(URI uri,
                           SourceSet sourceSet,
                           FingerPrint fingerPrint,
                           CompilationUnit cu,
                           boolean addDetailedSources) {
        try {
            return internalScan(uri, sourceSet, fingerPrint, cu, addDetailedSources);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            re.printStackTrace(System.err);
            LOGGER.error("Caught exception scanning compilation unit {}", uri);
            summary.addParseException(new Summary.ParseException(uri, cu, "Caught exception scanning compilation unit", re));
            return new ScanResult(Map.of(), null);
        }
    }

    private ScanResult internalScan(URI uri,
                                    SourceSet sourceSet,
                                    FingerPrint fingerPrint,
                                    CompilationUnit cu,
                                    boolean addDetailedSources) {
        PackageDeclaration packageDeclaration = cu.getPackageDeclaration();
        String packageName;
        Source s1 = source(cu);
        Source source;
        if (packageDeclaration == null) {
            source = s1;
            packageName = "";
        } else {
            Name name = packageDeclaration.firstChildOfType(Name.class);
            packageName = Objects.requireNonNullElse(name.toString(), "");
            source = addDetailedSources
                    ? s1.withDetailedSources(runtime.newDetailedSourcesBuilder().put(packageName, source(name)).build())
                    : s1;
        }
        org.e2immu.language.cst.api.element.CompilationUnit.Builder compilationUnitBuilder
                = runtime.newCompilationUnitBuilder()
                .setSource(source)
                .setURI(uri)
                .setPackageName(packageName)
                .setSourceSet(sourceSet)
                .setFingerPrint(fingerPrint);
        for (ImportDeclaration id : cu.childrenOfType(ImportDeclaration.class)) {
            ImportStatement importStatement = parseImportDeclaration(id);
            compilationUnitBuilder.addImportStatement(importStatement);
        }
        org.e2immu.language.cst.api.element.CompilationUnit compilationUnit = compilationUnitBuilder.build();
        Map<String, TypeInfo> sourceTypes = recursivelyFindTypes(Either.left(compilationUnit), null, cu,
                addDetailedSources);
        return new ScanResult(sourceTypes, compilationUnit);
    }


    private ImportStatement parseImportDeclaration(ImportDeclaration id) {
        boolean isStatic = id.get(1) instanceof KeyWord kw && Token.TokenType.STATIC.equals(kw.getType());
        int i = isStatic ? 2 : 1;
        String importString = id.get(i).getSource();
        ImportStatement.Builder builder = runtime.newImportStatementBuilder();
        builder.setSource(source(id))
                .addComments(comments(id))
                .setIsStatic(isStatic);

        if (id.get(i + 1) instanceof Delimiter d && Token.TokenType.DOT.equals(d.getType())
            && id.get(i + 2) instanceof Operator o && Token.TokenType.STAR.equals(o.getType())) {
            return builder.setImport(importString + ".*").build();
        }
        return builder.setImport(importString).build();
    }
}
