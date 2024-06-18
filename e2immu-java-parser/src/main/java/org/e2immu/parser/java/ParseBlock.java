package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.parserapi.Context;
import org.parsers.java.Node;
import org.parsers.java.ast.CodeBlock;
import org.parsers.java.ast.Statement;

import java.util.List;

public class ParseBlock extends CommonParse {
    private final ParseStatement parseStatement;

    public ParseBlock(Runtime runtime, ParseStatement parseStatement) {
        super(runtime);
        this.parseStatement = parseStatement;
    }

    public Block parse(Context context, String index, CodeBlock codeBlock) {
        return parse(context, index, codeBlock, 0);
    }

    public Block parse(Context context, String index, CodeBlock codeBlock, int startCount) {
        Source source = source(context.info(), index, codeBlock);
        List<Comment> comments = comments(codeBlock);
        Block.Builder builder = runtime.newBlockBuilder();
        int count = startCount;
        for (Node child : codeBlock.children()) {
            if (child instanceof Statement s) {
                String sIndex = (index.isEmpty() ? "" : index + ".") + count;
                org.e2immu.language.cst.api.statement.Statement statement = parseStatement.parse(context, sIndex, s);
                builder.addStatement(statement);
                count++;
            }
        }
        return builder.setSource(source).addComments(comments).build();
    }
}
