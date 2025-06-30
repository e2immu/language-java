package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.DetailedSources;
import org.e2immu.language.cst.api.element.ModuleInfo;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;

public class ParseModuleInfo extends CommonParse {
    public ParseModuleInfo(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public ModuleInfo parse(ModularCompilationUnit mcu, Context context) {
        ModuleInfo.Builder builder = runtime.newModuleInfoBuilder();
        DetailedSources.Builder detailedSourcesBuilder = context.newDetailedSourcesBuilder();
        int i = 0;
        if (mcu.get(i) instanceof KeyWord kw && kw.getType() == Token.TokenType.MODULE) {
            ++i;
        } else throw new Summary.ParseException(context, "Expect keyword 'module'");
        if (mcu.get(i) instanceof Name name) {
            String n = name.getSource();
            builder.setName(n);
            if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(n, source(name));
            i += 2;
        } else throw new Summary.ParseException(context, "Expect name after keyword 'module'");
        for (; i < mcu.size(); ++i) {
            Node nodeI = mcu.get(i);
            switch (nodeI) {
                case RequiresDirective rd -> processRequired(context, rd, builder);
                case ExportsDirective ed -> processExportsDirective(context, ed, builder);
                case OpensDirective od -> processOpensDirective(context, od, builder);
                case UsesDirective ud -> processUsesDirective(context, ud, builder);
                case ProvidesDirective pd -> processProvidesDirective(context, pd, builder);
                default -> {
                }
            }
        }
        Source source = source(mcu);
        return builder
                .setSource(detailedSourcesBuilder == null ? source : source.withDetailedSources(detailedSourcesBuilder.build()))
                .addComments(comments(mcu)).build();
    }

    private void processProvidesDirective(Context context, ProvidesDirective pd, ModuleInfo.Builder builder) {
        DetailedSources.Builder dsb = context.newDetailedSourcesBuilder();
        Node apiNode = pd.get(1);
        String api = apiNode.getSource();
        if (dsb != null) dsb.put(api, source(apiNode));
        String implementation;
        if (pd.get(2) instanceof KeyWord kwTo && Token.TokenType.WITH == kwTo.getType()) {
            Node iNode = pd.get(3);
            implementation = iNode.getSource();
            if (dsb != null) dsb.put(implementation, source(iNode));
        } else {
            implementation = null;
        }
        Source source = source(pd);
        builder.addProvides(dsb == null ? source : source.withDetailedSources(dsb.build()),
                comments(pd), api, implementation);
    }

    private void processUsesDirective(Context context, UsesDirective ud, ModuleInfo.Builder builder) {
        DetailedSources.Builder dsb = context.newDetailedSourcesBuilder();
        Node apiNode = ud.get(1);
        String api = apiNode.getSource();
        if (dsb != null) dsb.put(api, source(apiNode));
        Source source = source(ud);
        builder.addUses(dsb == null ? source : source.withDetailedSources(dsb.build()), comments(ud), api);
    }

    private void processOpensDirective(Context context, OpensDirective ed, ModuleInfo.Builder builder) {
        DetailedSources.Builder dsb = context.newDetailedSourcesBuilder();
        Node packageNameNode = ed.get(1);
        String packageName = packageNameNode.getSource();
        if (dsb != null) dsb.put(packageName, source(packageNameNode));
        String toPackageName;
        if (ed.get(2) instanceof KeyWord kwTo && Token.TokenType.TO == kwTo.getType()) {
            Node toNode = ed.get(3);
            toPackageName = toNode.getSource();
            if (dsb != null) dsb.put(toPackageName, source(toNode));
        } else {
            toPackageName = null;
        }
        Source source = source(ed);
        builder.addOpens(dsb == null ? source : source.withDetailedSources(dsb.build()),
                comments(ed), packageName, toPackageName);
    }


    private void processExportsDirective(Context context, ExportsDirective ed, ModuleInfo.Builder builder) {
        DetailedSources.Builder dsb = context.newDetailedSourcesBuilder();
        Node packageNameNode = ed.get(1);
        String packageName = packageNameNode.getSource();
        if (dsb != null) dsb.put(packageName, source(packageNameNode));
        String toPackageName;
        if (ed.get(2) instanceof KeyWord kwTo && Token.TokenType.TO == kwTo.getType()) {
            Node toNode = ed.get(3);
            toPackageName = toNode.getSource();
            if (dsb != null) dsb.put(toPackageName, source(toNode));
        } else {
            toPackageName = null;
        }
        Source source = source(ed);
        builder.addExports(dsb == null ? source : source.withDetailedSources(dsb.build()),
                comments(ed), packageName, toPackageName);
    }

    private void processRequired(Context context, RequiresDirective rd, ModuleInfo.Builder builder) {
        int j = 1;
        boolean isStatic = false;
        boolean isTransitive = false;
        while (rd.get(j) instanceof KeyWord modifier) {
            if (Token.TokenType.STATIC == modifier.getType()) {
                isStatic = true;
            } else if (Token.TokenType.TRANSITIVE == modifier.getType()) {
                isTransitive = true;
            } else {
                throw new Summary.ParseException(context, "Unexpected modifier " + modifier.getSource()
                                                          + " in 'requires'");
            }
            ++j;
        }
        Name reqName = (Name) rd.get(j);
        DetailedSources.Builder dsb = context.newDetailedSourcesBuilder();
        String n = reqName.getSource();
        Source source = source(rd);
        if (dsb != null) dsb.put(n, source(reqName));
        builder.addRequires(dsb == null ? source : source.withDetailedSources(dsb.build()),
                comments(rd), n, isStatic, isTransitive);
    }
}
