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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
First round, we only prepare a type map.
 */
public class ScanCompilationUnit extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanCompilationUnit.class);

    private final Context rootContext;

    public ScanCompilationUnit(Context rootContext) {
        super(rootContext.runtime(), new Parsers(rootContext.runtime()));
        this.rootContext = rootContext;
    }

    public record ScanResult(Map<String, TypeInfo> sourceTypes,
                             org.e2immu.language.cst.api.element.CompilationUnit compilationUnit) {
    }

    public ScanResult scan(URI uri, CompilationUnit cu) {
        try {
            return internalScan(uri, null, cu);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            re.printStackTrace(System.err);
            LOGGER.error("Caught exception scanning compilation unit {}", uri);
            rootContext.summary().addParserError(re);
            return new ScanResult(Map.of(), null);
        }
    }

    // this version is for when the TypeInfo object has already been created, and must be reused
    // Identity of the compilation unit is not that important, we'll overwrite the source in the TypeInfo builder.
    public ScanResult scan(TypeInfo typeInfo, CompilationUnit cu) {
        URI uri = typeInfo.compilationUnit().uri();
        try {
            return internalScan(uri, typeInfo, cu);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            re.printStackTrace(System.err);
            LOGGER.error("Caught exception scanning compilation unit {} from type {}", uri, typeInfo);
            rootContext.summary().addParserError(re);
            return new ScanResult(Map.of(), null);
        }
    }

    private ScanResult internalScan(URI uri, TypeInfo typeInfoOrNull, CompilationUnit cu) {
        PackageDeclaration packageDeclaration = cu.getPackageDeclaration();
        String packageName = packageDeclaration == null ? ""
                : Objects.requireNonNullElse(packageDeclaration.getName(), "");

        org.e2immu.language.cst.api.element.CompilationUnit compilationUnit = runtime.newCompilationUnitBuilder()
                .setURI(uri)
                .setPackageName(packageName)
                .build();

        return new ScanResult(recursivelyFindTypes(Either.left(compilationUnit), typeInfoOrNull, cu), compilationUnit);
    }

}
