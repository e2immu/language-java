package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.parsers.java.Node;
import org.parsers.java.ast.MultiLineComment;
import org.parsers.java.ast.SingleLineComment;

import java.util.List;
import java.util.Objects;

public abstract class CommonParse {
    protected final Runtime runtime;

    protected CommonParse(Runtime runtime) {
        this.runtime = runtime;
    }

    public List<Comment> comments(Node node) {
        return node.getAllTokens(true).stream().map(t -> {
            if (t instanceof SingleLineComment slc) {
                return runtime.newSingleLineComment(slc.getSource());
            }
            if (t instanceof MultiLineComment multiLineComment) {
                return runtime.newMultilineComment(multiLineComment.getSource());
            }
            return null;
        }).filter(Objects::nonNull).toList();
    }

    /*
    this implementation gives an "imperfect" parent... See e.g. parseBlock: we cannot pass on the parent during
    parsing, because we still have the builder at that point in time.
     */
    public Source source(Info info, String index, Node node) {
        return runtime.newParserSource(info, index, node.getBeginLine(), node.getBeginColumn(), node.getEndLine(),
                node.getEndColumn());
    }
}
