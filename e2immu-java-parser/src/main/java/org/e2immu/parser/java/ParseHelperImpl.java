package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Element;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.Statement;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.ParseHelper;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;

public class ParseHelperImpl implements ParseHelper {
    private final Parsers parsers;
    private final Runtime runtime;

    public ParseHelperImpl(Runtime runtime) {
        this(runtime, new Parsers(runtime));
    }

    public ParseHelperImpl(Runtime runtime, Parsers parsers) {
        this.parsers = parsers;
        this.runtime = runtime;
    }

    @Override
    public Expression parseExpression(Context context, String index, ForwardType forward, Object expression) {
        if (expression instanceof InvocationArguments ia) {
            TypeInfo enumType = forward.type().typeInfo();
            // we'll have to parse a constructor, new C(invocation arguments)
            return parsers.parseConstructorCall().parseEnumConstructor(context, index, enumType, ia);
        }
        return parsers.parseExpression().parse(context, index, forward, (Node) expression);
    }

    @Override
    public void resolveMethodInto(MethodInfo.Builder builder,
                                  Context context,
                                  ForwardType forwardType,
                                  Object unparsedEci,
                                  Object expression) {
        org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation eci;
        if (unparsedEci == null) {
            eci = null;
        } else {
            eci = parseEci(context, unparsedEci);
        }
        Element e;
        if (expression instanceof CompactConstructorDeclaration ccd) {
            int j = 0;
            while (!Token.TokenType.LBRACE.equals(ccd.get(j).getType())) j++;
            j++;
            if (ccd.get(j) instanceof org.parsers.java.ast.Statement s) {
                e = parseStatements(context, s, 0);
            } else if (ccd.get(j) instanceof Delimiter) {
                e = runtime.emptyBlock();
            } else throw new Summary.ParseException(context.info(), "Expected either empty block, or statements");
        } else if (expression instanceof CodeBlock codeBlock) {
            e = parsers.parseBlock().parse(context, "", codeBlock, eci == null ? 0 : 1);
        } else {
            e = parseStatements(context, forwardType, expression, eci != null);
        }
        if (e instanceof Block b) {
            Block bWithEci;
            if (eci == null) {
                bWithEci = b;
            } else {
                bWithEci = runtime.newBlockBuilder().addStatement(eci).addStatements(b.statements()).build();
            }
            builder.setMethodBody(bWithEci);
        } else if (e instanceof Statement s) {
            Block.Builder bb = runtime.newBlockBuilder();
            if (eci != null) bb.addStatement(eci);
            builder.setMethodBody(bb.addStatement(s).build());
        } else if (e == null && eci != null) {
            builder.setMethodBody(runtime.newBlockBuilder().addStatement(eci).build());
        } else {
            // in Java, we must have a block
            throw new UnsupportedOperationException();
        }
        builder.commit();
    }

    /*
    we have and the potential "ECI" to deal with, and the fact that sometimes, multiple
    ExpressionStatements can replace a CodeBlock (no idea why, but we need to deal with it)
    See TestExplicitConstructorInvocation.
     */
    private Element parseStatements(Context context, ForwardType forwardType, Object expression, boolean haveEci) {
        int start = haveEci ? 1 : 0;
        if (expression instanceof ExpressionStatement est) {
            return parseStatements(context, est, start);
        }
        if (expression != null) {
            return parseExpression(context, "" + start, forwardType, expression);
        }
        return null;
    }

    private Statement parseStatements(Context context, org.parsers.java.ast.Statement first, int start) {
        Statement firstStatement = parsers.parseStatement().parse(context, "" + start, first);

        List<org.parsers.java.ast.Statement> siblings = new ArrayList<>();
        while (first.nextSibling() instanceof org.parsers.java.ast.Statement next) {
            siblings.add(next);
            first = next;
        }
        if (siblings.isEmpty()) {
            return firstStatement;
        }
        Block.Builder b = runtime.newBlockBuilder();
        b.addStatement(firstStatement);
        for (org.parsers.java.ast.Statement es : siblings) {
            ++start;
            Statement s2 = parsers.parseStatement().parse(context, "" + start, es);
            b.addStatement(s2);
        }
        return b.build();
    }

    private org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation parseEci(Context context,
                                                                                         Object eciObject) {
        ExplicitConstructorInvocation unparsedEci = (ExplicitConstructorInvocation) eciObject;
        org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation eci;
        ConstructorDeclaration cd = (ConstructorDeclaration) unparsedEci.getParent();
        List<Comment> comments = parsers.parseStatement().comments(cd);
        Source source = parsers.parseStatement().source(context.enclosingMethod(), "0", cd);
        boolean isSuper = Token.TokenType.SUPER.equals(unparsedEci.get(0).getType());
        List<Expression> parameterExpressions = parseArguments(context, unparsedEci.get(1));
        MethodInfo eciMethod = context.enclosingType().findConstructor(parameterExpressions.size());
        eci = runtime.newExplicitConstructorInvocationBuilder()
                .addComments(comments)
                .setSource(source)
                .setIsSuper(isSuper)
                .setMethodInfo(eciMethod)
                .setParameterExpressions(parameterExpressions)
                .build();
        return eci;
    }

    // arguments of ECI
    private List<Expression> parseArguments(Context context, Node node) {
        assert node instanceof InvocationArguments;
        List<Expression> expressions = new ArrayList<>();
        for (int k = 1; k < node.size(); k += 2) {
            Expression e = parsers.parseExpression().parse(context, "0", context.emptyForwardType(), node.get(k));
            expressions.add(e);
        }
        return List.copyOf(expressions);
    }
}
