package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.DetailedSources;
import org.e2immu.language.cst.api.element.ModuleInfo;
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
            builder.setName(name.getSource());
            i += 2;
        } else throw new Summary.ParseException(context, "Expect name after keyword 'module'");
        while (true) {
            Node nodeI = mcu.get(i);
            if (nodeI instanceof RequiresDirective rd) {
                int j = 1;
                boolean isStatic = false;
                boolean isTransitive = false;
                while (rd.get(j) instanceof KeyWord modifier) {
                    if (Token.TokenType.STATIC == modifier.getType()) {
                        isStatic = true;
                    } else if (Token.TokenType.TRANSITIVE == modifier.getType()) {
                        isTransitive = true;
                    } else {
                        throw new Summary.ParseException(context, "Unexpected modifier " + modifier.getSource() + " in 'requires'");
                    }
                    ++j;
                }
                Name reqName = (Name) rd.get(j);
                builder.addRequires(reqName.getSource(), isStatic, isTransitive);
            } else if (nodeI instanceof ExportsDirective ed) {
            } else if (nodeI instanceof OpensDirective od) {
            } else if (nodeI instanceof UsesDirective ud) {
            } else if (nodeI instanceof ProvidesDirective pd) {
            } else break;
            ++i;
        }
        return builder.setSource(source(mcu)).addComments(comments(mcu)).build();
    }
}
