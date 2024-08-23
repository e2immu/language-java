package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.util.internal.util.StringUtil;
import org.parsers.java.Node;
import org.parsers.java.ast.CodeBlock;
import org.parsers.java.ast.Statement;

import java.util.List;

public class ParseBlock extends CommonParse {

    public ParseBlock(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public Block parse(Context context, String index, String label, CodeBlock codeBlock) {
        return parse(context, index, label, codeBlock, 0);
    }

    public Block parse(Context context, String index, String label, CodeBlock codeBlock, int startCount) {
        Source source = source(context.info(), index, codeBlock);
        List<Comment> comments = comments(codeBlock);
        Block.Builder builder = runtime.newBlockBuilder();
        int count = startCount;
        int n = codeBlock.size() - 2; // delimiters at front and back: '{', '}'
        for (Node child : codeBlock) {
            if (child instanceof Statement s) {
                String sIndex = (index.isEmpty() ? "" : index + ".") + StringUtil.pad(count, n);
                org.e2immu.language.cst.api.statement.Statement statement = parsers.parseStatement()
                        .parse(context, sIndex, s);
                builder.addStatement(statement);
                count++;
            }
        }
        return builder.setSource(source).addComments(comments).setLabel(label).build();
    }
}
