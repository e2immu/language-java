package org.e2immu.parserimpl;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Element;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.Statement;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.Resolver;
import org.e2immu.parser.java.ParseBlock;
import org.e2immu.parser.java.ParseExpression;
import org.e2immu.parser.java.ParseStatement;

import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ResolverImpl implements Resolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    private final ParseStatement parseStatement;
    private final ParseExpression parseExpression;
    private final ParseBlock parseBlock;
    private final Runtime runtime;

    public ResolverImpl(Runtime runtime) {
        this.parseExpression = new ParseExpression(runtime);
        this.parseStatement = new ParseStatement(runtime);
        this.parseBlock = new ParseBlock(runtime, parseStatement);
        this.runtime = runtime;
    }

    record Todo(Info.Builder<?> infoBuilder, ForwardType forwardType, ExplicitConstructorInvocation eci,
                Node expression, Context context) {
    }

    private final List<Todo> todos = new LinkedList<>();
    private final List<TypeInfo.Builder> types = new LinkedList<>();


    public void add(Info.Builder<?> infoBuilder, ForwardType forwardType, Object eci,
                    Object expression, Context context) {
        todos.add(new Todo(infoBuilder, forwardType, (ExplicitConstructorInvocation) eci, (Node) expression, context));
    }


    @Override
    public void add(TypeInfo.Builder typeInfoBuilder) {
        types.add(typeInfoBuilder);
    }

    private static final String START_INDEX = "";


    public void resolve() {
        LOGGER.info("Start resolving {} type(s), {} field(s)/method(s)", types.size(), todos.size());

        for (Todo todo : todos) {
            if (todo.infoBuilder instanceof FieldInfo.Builder builder) {
                boolean success = true;
                try {
                    resolveField(todo, builder);
                } catch (RuntimeException re) {
                    success = false;
                    todo.context.summary().addParserError(re);
                }
                todo.context.summary().addType(todo.context.enclosingType().primaryType(), success);
            } else if (todo.infoBuilder instanceof MethodInfo.Builder builder) {
                boolean success = true;
                try {
                    resolveMethod(todo, builder);
                } catch (RuntimeException re) {
                    success = false;
                    todo.context.summary().addParserError(re);
                }
                todo.context.summary().addType(todo.context.enclosingType().primaryType(), success);
                todo.context.summary().addMethod(success);
            } else throw new UnsupportedOperationException("In java, we cannot have expressions in other places");
        }
        for (TypeInfo.Builder builder : types) {
            builder.commit();
        }
    }

    private void resolveField(Todo todo, FieldInfo.Builder builder) {
        Expression e = parseExpression.parse(todo.context, START_INDEX,
                todo.forwardType, todo.expression);
        builder.setInitializer(e);
        builder.commit();
    }

    private void resolveMethod(Todo todo, MethodInfo.Builder builder) {
        org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation eci;
        if (todo.eci == null) {
            eci = null;
        } else {
            eci = parseEci(todo);
        }
        Element e;
        if (todo.expression instanceof CodeBlock codeBlock) {
            e = parseBlock.parse(todo.context, START_INDEX, codeBlock, eci == null ? 0 : 1);
        } else {
            e = parseStatements(todo, eci != null);
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
    private Element parseStatements(Todo todo, boolean haveEci) {
        int start = haveEci ? 1 : 0;
        if (todo.expression instanceof ExpressionStatement est) {
            Statement firstStatement = parseStatement.parse(todo.context, "" + start, est);

            List<ExpressionStatement> siblings = new ArrayList<>();
            while (est.nextSibling() instanceof ExpressionStatement next) {
                siblings.add(next);
                est = next;
            }
            if (siblings.isEmpty()) {
                return firstStatement;
            }
            Block.Builder b = runtime.newBlockBuilder();
            b.addStatement(firstStatement);
            for (ExpressionStatement es : siblings) {
                ++start;
                Statement s2 = parseStatement.parse(todo.context, "" + start, es);
                b.addStatement(s2);
            }
            return b.build();

        }
        if (todo.expression != null) {
            return parseExpression.parse(todo.context, "" + start, todo.forwardType, todo.expression);
        }
        return null;
    }

    private org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation parseEci(Todo todo) {
        org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation eci;
        ConstructorDeclaration cd = (ConstructorDeclaration) todo.eci.getParent();
        List<Comment> comments = parseStatement.comments(cd);
        Source source = parseStatement.source(todo.context.enclosingMethod(), "0", cd);
        boolean isSuper = Token.TokenType.SUPER.equals(todo.eci.get(0).getType());
        List<Expression> parameterExpressions = parseArguments(todo.context, todo.eci.get(1));
        MethodInfo eciMethod = todo.context.enclosingType().findConstructor(parameterExpressions.size());
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
            Expression e = parseExpression.parse(context, "0", context.emptyForwardType(), node.get(k));
            expressions.add(e);
        }
        return List.copyOf(expressions);
    }

}
