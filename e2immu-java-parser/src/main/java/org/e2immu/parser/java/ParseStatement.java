package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.LocalVariableCreation;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.variable.LocalVariable;
import org.e2immu.parserimpl.Context;
import org.e2immu.parserimpl.ForwardType;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.List;


public class ParseStatement extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseStatement.class);
    private final ParseBlock parseBlock;
    private final ParseExpression parseExpression;
    private final ParseType parseType;

    private static final String FIRST_BLOCK = ".0";
    private static final String FIRST_STATEMENT = ".0";
    private static final String SECOND_BLOCK = ".1";

    public ParseStatement(Runtime runtime) {
        super(runtime);
        parseExpression = new ParseExpression(runtime);
        parseType = new ParseType(runtime);
        parseBlock = new ParseBlock(runtime, this);
    }

    public org.e2immu.language.cst.api.statement.Statement parse(Context context, String index, Statement statement) {
        try {
            return internalParse(context, index, statement);
        } catch (Throwable t) {
            LOGGER.error("Caught exception parsing statement at line {}", statement.getBeginLine());
            throw t;
        }
    }

    private org.e2immu.language.cst.api.statement.Statement internalParse(Context context, String index, Statement statement) {
        List<Comment> comments = comments(statement);
        Source source = source(context.enclosingMethod(), index, statement);

        if (statement instanceof ExpressionStatement es) {
            StatementExpression se = (StatementExpression) es.children().get(0);
            Expression e = parseExpression.parse(context, index, context.emptyForwardType(), se.get(0));
            return runtime.newExpressionAsStatementBuilder().setExpression(e).setSource(source)
                    .addComments(comments).build();
        }
        if (statement instanceof ReturnStatement rs) {
            ForwardType forwardType = context.newForwardType(context.enclosingMethod().returnType());
            Expression e = parseExpression.parse(context, index, forwardType, rs.get(1));
            assert e != null;
            return runtime.newReturnBuilder()
                    .setExpression(e).setSource(source).addComments(comments)
                    .build();
        }

        // type declarator delimiter declarator
        if (statement instanceof NoVarDeclaration nvd) {
            ParameterizedType type = parseType.parse(context, nvd.get(0));
            LocalVariableCreation.Builder builder = runtime.newLocalVariableCreationBuilder();
            int i = 1;
            while (i < nvd.size() && nvd.get(i) instanceof VariableDeclarator vd) {
                Identifier identifier = (Identifier) vd.get(0);
                Expression expression;
                if (vd.size() > 2) {
                    ForwardType forwardType = context.newForwardType(type);
                    expression = parseExpression.parse(context, index, forwardType, vd.get(2));
                } else {
                    expression = runtime.newEmptyExpression();
                }
                String variableName = identifier.getSource();
                LocalVariable lv = runtime.newLocalVariable(variableName, type, expression);
                context.variableContext().add(lv);
                if (i == 1) builder.setLocalVariable(lv);
                else builder.addOtherLocalVariable(lv);
                i += 2;
            }
            return builder.setSource(source).addComments(comments).build();
        }
        if (statement instanceof EnhancedForStatement enhancedFor) {
            // kw, del, noVarDecl (objectType, vardcl), operator, name (Name), del, code block
            LocalVariableCreation loopVariableCreation;
            TypeInfo iterable = runtime.getFullyQualified(Iterable.class, false);
            ForwardType forwardType = iterable == null ? context.emptyForwardType() :
                    context.newForwardType(iterable.asSimpleParameterizedType());
            Expression expression = parseExpression.parse(context, index, forwardType, enhancedFor.get(4));
            Context newContext = context.newVariableContext("forEach");
            if (enhancedFor.get(2) instanceof NoVarDeclaration nvd) {
                loopVariableCreation = (LocalVariableCreation) parse(newContext, index, nvd);
            } else throw new UnsupportedOperationException();
            Node n6 = enhancedFor.get(6);
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, n6);
            return runtime.newForEachBuilder().setInitializer(loopVariableCreation)
                    .setBlock(block).setExpression(expression).build();
        }
        if (statement instanceof WhileStatement whileStatement) {
            Context newContext = context.newVariableContext("while");
            ForwardType forwardType = context.newForwardType(runtime.booleanParameterizedType());
            Expression expression = parseExpression.parse(context, index, forwardType, whileStatement.get(2));
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, whileStatement.get(4));
            return runtime.newWhileBuilder().setExpression(expression).setBlock(block)
                    .setSource(source).addComments(comments).build();
        }
        if (statement instanceof IfStatement ifStatement) {
            ForwardType forwardType = context.newForwardType(runtime.booleanParameterizedType());
            Expression expression = parseExpression.parse(context, index, forwardType, ifStatement.get(2));
            Node n4 = ifStatement.get(4);
            Context newContext = context.newVariableContext("ifBlock");
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, n4);
            Block elseBlock;
            if (ifStatement.size() > 5 && ifStatement.get(5) instanceof KeyWord) {
                assert Token.TokenType.ELSE.equals(ifStatement.get(5).getType());
                Node n6 = ifStatement.get(6);
                Context newContext2 = context.newVariableContext("elseBlock");
                elseBlock = parseBlockOrStatement(newContext2, index + SECOND_BLOCK, n6);
            } else {
                elseBlock = runtime.emptyBlock();
            }
            return runtime.newIfElseBuilder()
                    .setExpression(expression).setIfBlock(block).setElseBlock(elseBlock)
                    .addComments(comments).setSource(source)
                    .build();
        }
        if (statement instanceof TryStatement tryStatement) {
            int i = 1;
            if (tryStatement.get(i) instanceof Delimiter) {
                // resources
                i += 2;
            }
            Context newContext = context.newVariableContext("tryBlock");
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, tryStatement.get(i));
            i++;
            org.e2immu.language.cst.api.statement.TryStatement.Builder builder = runtime.newTryBuilder()
                    .setBlock(block)
                    .addComments(comments).setSource(source);
            int blockCount = 1;
            while (tryStatement.get(i) instanceof CatchBlock catchBlock) {
                Context catchContext = context.newVariableContext("catchBlock" + blockCount);
                org.e2immu.language.cst.api.statement.TryStatement.CatchClause.Builder ccBuilder = runtime.newCatchClauseBuilder();
                int j = 2;
                if (catchBlock.get(j) instanceof Type type) {
                    ParameterizedType pt = parseType.parse(context, type);
                    ccBuilder.addType(pt);
                    j++;
                } else throw new UnsupportedOperationException();
                if (catchBlock.get(j) instanceof Identifier identifier) {
                    ccBuilder.setVariableName(identifier.getSource());
                    j++;
                }
                j++; // ) delimiter
                if (catchBlock.get(j) instanceof CodeBlock cb) {
                    Block cbb = parseBlock.parse(catchContext, index + "." + blockCount, cb);
                    blockCount++;
                    builder.addCatchClause(ccBuilder.setBlock(cbb).build());
                } else throw new UnsupportedOperationException();
                i++;
            }
            Block finallyBlock;
            if (tryStatement.get(i) instanceof FinallyBlock fb) {
                Context finallyContext = context.newVariableContext("finallyBlock");
                finallyBlock = parseBlockOrStatement(finallyContext, index + "." + blockCount, fb.get(1));
            } else {
                finallyBlock = runtime.emptyBlock();
            }
            return builder.setFinallyBlock(finallyBlock).build();
        }
        if (statement instanceof BasicForStatement) {
            org.e2immu.language.cst.api.statement.ForStatement.Builder builder = runtime.newForBuilder();
            Context newContext = context.newVariableContext("for-loop");

            // initializers

            int i = 2;
            if (statement.get(i) instanceof Delimiter d && Token.TokenType.SEMICOLON.equals(d.getType())) {
                // no initializer
                i++;
            } else if (statement.get(i) instanceof Statement s) {
                builder.addInitializer(parse(newContext, index, s));
                i += 2;
            } else if (statement.get(i) instanceof StatementExpression) {
                // there could be more than one
                do {
                    if (statement.get(i) instanceof StatementExpression se) {
                        builder.addInitializer(parseExpression.parse(newContext, index, newContext.emptyForwardType(), se.get(0)));
                    } else throw new UnsupportedOperationException();
                    i += 2;
                } while (Token.TokenType.COMMA.equals(statement.get(i - 1).getType()));
            } else throw new UnsupportedOperationException();

            // condition

            if (statement.get(i) instanceof Delimiter d && Token.TokenType.SEMICOLON.equals(d.getType())) {
                // no condition
                builder.setExpression(runtime.constantTrue());
                i++;
            } else {
                Expression condition = parseExpression.parse(newContext, index, context.emptyForwardType(), statement.get(i));
                builder.setExpression(condition);
                i += 2;
            }
            // updaters

            if ((statement.get(i) instanceof Delimiter d && Token.TokenType.RPAREN.equals(d.getType()))) {
                i++; // no updaters
            } else {
                while (statement.get(i) instanceof StatementExpression se) {
                    Expression updater = parseExpression.parse(newContext, index, newContext.emptyForwardType(),
                            se.get(0));
                    builder.addUpdater(updater);
                    i += 2;
                    if (statement.get(i - 1) instanceof Delimiter d && Token.TokenType.RPAREN.equals(d.getType())) {
                        break;
                    }
                }
            }
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, statement.get(i));
            return builder.setBlock(block).setSource(source).addComments(comments).build();
        }
        if (statement instanceof SynchronizedStatement) {
            Context newContext = context.newVariableContext("synchronized");
            Expression expression = parseExpression.parse(context, index, context.emptyForwardType(), statement.get(2));
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, statement.get(4));
            return runtime.newSynchronizedBuilder().setExpression(expression).setBlock(block)
                    .setSource(source).addComments(comments).build();
        }
        if (statement instanceof BreakStatement) {
            return runtime.newBreakBuilder().setSource(source).addComments(comments).build();
        }
        if (statement instanceof ContinueStatement) {
            return runtime.newContinueBuilder().setSource(source).addComments(comments).build();
        }
        if (statement instanceof AssertStatement) {
            Expression expression = parseExpression.parse(context, index,
                    context.newForwardType(runtime.booleanParameterizedType()), statement.get(1));
            Expression message = Token.TokenType.COLON.equals(statement.get(2).getType())
                    ? parseExpression.parse(context, index, context.newForwardType(runtime.stringParameterizedType()),
                    statement.get(3))
                    : runtime.newEmptyExpression();
            return runtime.newAssertBuilder().setSource(source).addComments(comments)
                    .setExpression(expression)
                    .setMessage(message)
                    .build();
        }
        if (statement instanceof ThrowStatement) {
            TypeInfo throwable = runtime.getFullyQualified(Throwable.class, false);
            ForwardType fwd = throwable != null ? context.newForwardType(throwable.asSimpleParameterizedType())
                    : context.emptyForwardType();
            Expression expression = parseExpression.parse(context, index, fwd, statement.get(1));
            return runtime.newThrowBuilder()
                    .addComments(comments).setSource(source)
                    .setExpression(expression).build();
        }
        throw new UnsupportedOperationException("Node " + statement.getClass());
    }

    private Block parseBlockOrStatement(Context context, String index, Node node) {
        if (node instanceof CodeBlock codeBlock) {
            return parseBlock.parse(context, index, codeBlock);
        }
        if (node instanceof Statement s) {
            org.e2immu.language.cst.api.statement.Statement st = parse(context, index + FIRST_STATEMENT, s);
            return runtime.newBlockBuilder().addStatement(st).build();
        }
        throw new UnsupportedOperationException();
    }
}
