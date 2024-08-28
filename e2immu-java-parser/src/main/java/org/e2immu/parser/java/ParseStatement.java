package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.*;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.variable.LocalVariable;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.util.internal.util.StringUtil;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.parsers.java.ast.AssertStatement;
import org.parsers.java.ast.BreakStatement;
import org.parsers.java.ast.ContinueStatement;
import org.parsers.java.ast.DoStatement;
import org.parsers.java.ast.EmptyStatement;
import org.parsers.java.ast.ReturnStatement;
import org.parsers.java.ast.Statement;
import org.parsers.java.ast.SynchronizedStatement;
import org.parsers.java.ast.ThrowStatement;
import org.parsers.java.ast.TryStatement;
import org.parsers.java.ast.WhileStatement;
import org.parsers.java.ast.YieldStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.List;


public class ParseStatement extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseStatement.class);

    private static final String FIRST_BLOCK = ".0";
    private static final String FIRST_STATEMENT = ".0";
    private static final String SECOND_BLOCK = ".1";

    public ParseStatement(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public org.e2immu.language.cst.api.statement.Statement parse(Context context, String index, Statement statement) {
        try {
            return internalParse(context, index, statement);
        } catch (Throwable t) {
            LOGGER.error("Caught exception parsing statement at line {}", statement.getBeginLine());
            throw t;
        }
    }

    private org.e2immu.language.cst.api.statement.Statement internalParse(Context context, String index, Statement statementIn) {
        String label;
        Statement statement;
        if (statementIn instanceof LabeledStatement ls) {
            label = ls.get(0).get(0).getSource();
            statement = (Statement) ls.get(1);
        } else {
            label = null;
            statement = statementIn;
        }
        List<Comment> comments = comments(statement);
        Source source = source(context.enclosingMethod(), index, statement);

        if (statement instanceof ExpressionStatement es) {
            StatementExpression se = (StatementExpression) es.children().get(0);
            Expression e = parsers.parseExpression().parse(context, index, context.emptyForwardType(), se.get(0));
            return runtime.newExpressionAsStatementBuilder().setExpression(e).setSource(source).setLabel(label)
                    .addComments(comments).build();
        }
        if (statement instanceof ReturnStatement rs) {
            ForwardType forwardType = context.newForwardType(context.enclosingMethod().returnType());
            Expression e;
            if (rs.size() == 2) {
                assert rs.get(1) instanceof Delimiter;
                e = runtime.newEmptyExpression();
            } else {
                e = parsers.parseExpression().parse(context, index, forwardType, rs.get(1));
            }
            assert e != null;
            return runtime.newReturnBuilder()
                    .setExpression(e).setSource(source).addComments(comments).setLabel(label)
                    .build();
        }

        // type declarator delimiter declarator
        if (statement instanceof NoVarDeclaration nvd) {
            int i = 0;
            LocalVariableCreation.Builder builder = runtime.newLocalVariableCreationBuilder();
            while (true) {
                if (nvd.get(i) instanceof KeyWord) {
                    if (Token.TokenType.FINAL.equals(nvd.get(i).getType())) {
                        builder.addModifier(runtime.localVariableModifierFinal());
                    } else throw new Summary.ParseException(context.info(), "Expect final");
                } else if (nvd.get(i) instanceof Annotation a) {
                    AnnotationExpression ae = parsers.parseAnnotationExpression().parse(context, a);
                    builder.addAnnotation(ae);
                } else break;
                i++;
            }
            ParameterizedType type = parsers.parseType().parse(context, nvd.get(i));
            i++;
            boolean first = true;
            while (i < nvd.size() && nvd.get(i) instanceof VariableDeclarator vd) {
                Identifier identifier = (Identifier) vd.get(0);
                Expression expression;
                if (vd.size() > 2) {
                    ForwardType forwardType = context.newForwardType(type);
                    expression = parsers.parseExpression().parse(context, index, forwardType, vd.get(2));
                } else {
                    expression = runtime.newEmptyExpression();
                }
                String variableName = identifier.getSource();
                LocalVariable lv = runtime.newLocalVariable(variableName, type, expression);
                context.variableContext().add(lv);
                if (first) {
                    builder.setLocalVariable(lv);
                    first = false;
                } else {
                    builder.addOtherLocalVariable(lv);
                }
                i += 2;
            }
            return builder.setSource(source).addComments(comments).setLabel(label).build();
        }
        if (statement instanceof EnhancedForStatement enhancedFor) {
            // kw, del, noVarDecl (objectType, vardcl), operator, name (Name), del, code block
            LocalVariableCreation loopVariableCreation;
            TypeInfo iterable = runtime.getFullyQualified(Iterable.class, false);
            ForwardType forwardType = iterable == null ? context.emptyForwardType() :
                    context.newForwardType(iterable.asSimpleParameterizedType());
            Expression expression = parsers.parseExpression().parse(context, index, forwardType, enhancedFor.get(4));
            Context newContext = context.newVariableContext("forEach");
            if (enhancedFor.get(2) instanceof NoVarDeclaration nvd) {
                loopVariableCreation = (LocalVariableCreation) parse(newContext, index, nvd);
            } else throw new UnsupportedOperationException();
            Node n6 = enhancedFor.get(6);
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, n6);
            return runtime.newForEachBuilder().addComments(comments).setSource(source).setLabel(label)
                    .setInitializer(loopVariableCreation).setBlock(block).setExpression(expression).build();
        }
        if (statement instanceof IfStatement ifStatement) {
            ForwardType forwardType = context.newForwardType(runtime.booleanParameterizedType());
            Expression expression = parsers.parseExpression().parse(context, index, forwardType, ifStatement.get(2));
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
                    .addComments(comments).setSource(source).setLabel(label)
                    .setExpression(expression).setIfBlock(block).setElseBlock(elseBlock)
                    .build();
        }
        if (statement instanceof TryStatement tryStatement) {
            org.e2immu.language.cst.api.statement.TryStatement.Builder builder = runtime.newTryBuilder();
            int i = 1;
            Context newContext = context.newVariableContext("tryBlock");
            int mainBlockIndex;
            if (tryStatement.get(i) instanceof ResourcesInTryBlock r) {
                int j = 1;
                int rCount = 0;
                int n = (r.size() - 1) / 2;
                while (j < r.size() && !(r.get(j) instanceof Delimiter)) {
                    // resources
                    String newIndex = index + ".0." + StringUtil.pad(rCount, n);
                    LocalVariableCreation resource = (LocalVariableCreation) parse(newContext, newIndex, (Statement) r.get(j));
                    builder.addResource(resource);
                    j += 2;
                    rCount++;
                }
                i++;
                mainBlockIndex = 1;
            } else {
                mainBlockIndex = 0;
            }
            int n = countCatchBlocks(tryStatement) + mainBlockIndex + 2;
            String firstIndex = index + "." + StringUtil.pad(mainBlockIndex, n);
            Block block = parseBlockOrStatement(newContext, firstIndex, tryStatement.get(i));
            i++;
            builder.setBlock(block).addComments(comments).setSource(source).setLabel(label);
            int blockCount = 1 + mainBlockIndex;
            while (i < tryStatement.size() && tryStatement.get(i) instanceof CatchBlock catchBlock) {
                Context catchContext = context.newVariableContext("catchBlock" + blockCount);
                org.e2immu.language.cst.api.statement.TryStatement.CatchClause.Builder ccBuilder
                        = runtime.newCatchClauseBuilder();
                int j = 2;
                if (catchBlock.get(j) instanceof KeyWord kw) {
                    if (Token.TokenType.FINAL.equals(kw.getType())) {
                        ccBuilder.setFinal(true);
                    } else throw new UnsupportedOperationException();
                    j++;
                } else {
                    ccBuilder.setFinal(false);
                }
                ParameterizedType pt;
                if (catchBlock.get(j) instanceof Type type) {
                    pt = parsers.parseType().parse(context, type);
                    ccBuilder.addType(pt);
                    j++;
                } else {
                    throw new UnsupportedOperationException();
                }
                if (catchBlock.get(j) instanceof Identifier identifier) {
                    String variableName = identifier.getSource();
                    ccBuilder.setVariableName(variableName);
                    LocalVariable catchVariable = runtime.newLocalVariable(variableName, pt);
                    catchContext.variableContext().add(catchVariable);
                    j++;
                } else throw new UnsupportedOperationException();
                j++; // ) delimiter
                if (catchBlock.get(j) instanceof CodeBlock cb) {
                    String newIndex = index + "." + StringUtil.pad(blockCount, n);
                    Block cbb = parsers.parseBlock().parse(catchContext, newIndex, null, cb);
                    blockCount++;
                    builder.addCatchClause(ccBuilder.setBlock(cbb).build());
                } else throw new UnsupportedOperationException();
                i++;
            }
            Block finallyBlock;
            if (i < tryStatement.size() && tryStatement.get(i) instanceof FinallyBlock fb) {
                Context finallyContext = context.newVariableContext("finallyBlock");
                String newIndex = index + "." + StringUtil.pad(blockCount, n);
                finallyBlock = parseBlockOrStatement(finallyContext, newIndex, fb.get(1));
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
                        Expression initializer = parsers.parseExpression().parse(newContext, index,
                                newContext.emptyForwardType(), se.get(0));
                        builder.addInitializer(initializer);
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
                Expression condition = parsers.parseExpression().parse(newContext, index, context.emptyForwardType(), statement.get(i));
                builder.setExpression(condition);
                i += 2;
            }
            // updaters

            if ((statement.get(i) instanceof Delimiter d && Token.TokenType.RPAREN.equals(d.getType()))) {
                i++; // no updaters
            } else {
                while (statement.get(i) instanceof StatementExpression se) {
                    Expression updater = parsers.parseExpression().parse(newContext, index, newContext.emptyForwardType(),
                            se.get(0));
                    builder.addUpdater(updater);
                    i += 2;
                    if (statement.get(i - 1) instanceof Delimiter d && Token.TokenType.RPAREN.equals(d.getType())) {
                        break;
                    }
                }
            }
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, statement.get(i));
            return builder.setBlock(block).setSource(source).addComments(comments).setLabel(label).build();
        }
        if (statement instanceof SynchronizedStatement) {
            Context newContext = context.newVariableContext("synchronized");
            Expression expression = parsers.parseExpression().parse(context, index, context.emptyForwardType(), statement.get(2));
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, statement.get(4));
            return runtime.newSynchronizedBuilder().setExpression(expression).setBlock(block)
                    .setSource(source).addComments(comments).setLabel(label).build();
        }
        if (statement instanceof BreakStatement) {
            String goToLabel;
            if (statement.size() > 1 && statement.get(1) instanceof Identifier identifier) {
                goToLabel = identifier.getSource();
            } else {
                goToLabel = null;
            }
            return runtime.newBreakBuilder().setSource(source).addComments(comments).setLabel(label)
                    .setGoToLabel(goToLabel)
                    .build();
        }
        if (statement instanceof ContinueStatement) {
            String goToLabel;
            if (statement.size() > 1 && statement.get(1) instanceof Identifier identifier) {
                goToLabel = identifier.getSource();
            } else {
                goToLabel = null;
            }
            return runtime.newContinueBuilder().setSource(source).addComments(comments).setLabel(label)
                    .setGoToLabel(goToLabel)
                    .build();
        }
        if (statement instanceof AssertStatement) {
            Expression expression = parsers.parseExpression().parse(context, index,
                    context.newForwardType(runtime.booleanParameterizedType()), statement.get(1));
            Expression message = Token.TokenType.COLON.equals(statement.get(2).getType())
                    ? parsers.parseExpression().parse(context, index, context.newForwardType(runtime.stringParameterizedType()),
                    statement.get(3))
                    : runtime.newEmptyExpression();
            return runtime.newAssertBuilder().setSource(source).addComments(comments).setLabel(label)
                    .setExpression(expression)
                    .setMessage(message)
                    .build();
        }
        if (statement instanceof ThrowStatement) {
            TypeInfo throwable = runtime.getFullyQualified(Throwable.class, false);
            ForwardType fwd = throwable != null ? context.newForwardType(throwable.asSimpleParameterizedType())
                    : context.emptyForwardType();
            Expression expression = parsers.parseExpression().parse(context, index, fwd, statement.get(1));
            return runtime.newThrowBuilder()
                    .addComments(comments).setSource(source).setLabel(label)
                    .setExpression(expression).build();
        }
        if (statement instanceof SwitchStatement) {
            int no = countStatementsInOldStyleSwitch(statement);
            if (no > 0) {
                return parseOldStyleSwitch(context, index, statement, comments, source, label, no);
            }
            int nn = countStatementsInNewStyleSwitch(statement);
            if (nn >= 0) {
                return parseNewStyleSwitch(context, index, statement, comments, source, label, nn);
            }
        }
        if (statement instanceof YieldStatement ys) {
            ForwardType forwardType = context.newForwardType(context.enclosingMethod().returnType());
            Expression e;
            if (ys.size() == 2) {
                assert ys.get(1) instanceof Delimiter;
                e = runtime.newEmptyExpression();
            } else {
                e = parsers.parseExpression().parse(context, index, forwardType, ys.get(1));
            }
            assert e != null;
            return runtime.newYieldBuilder()
                    .setExpression(e).setSource(source).addComments(comments).setLabel(label)
                    .build();
        }
        if (statement instanceof CodeBlock cb) {
            return parsers.parseBlock().parse(context, index, label, cb);
        }
        if (statement instanceof VarDeclaration varDeclaration) {
            LocalVariableCreation.Builder builder = runtime.newLocalVariableCreationBuilder();
            Identifier identifier = (Identifier) varDeclaration.get(1);
            ForwardType forwardType = context.emptyForwardType();
            Expression expression = parsers.parseExpression().parse(context, index, forwardType, varDeclaration.get(3));
            String variableName = identifier.getSource();
            ParameterizedType type = expression.parameterizedType();
            LocalVariable lv = runtime.newLocalVariable(variableName, type, expression);
            context.variableContext().add(lv);
            builder.setLocalVariable(lv);
            return builder.setSource(source).addComments(comments).setLabel(label).build();
        }
        if (statement instanceof WhileStatement whileStatement) {
            Context newContext = context.newVariableContext("while");
            ForwardType forwardType = context.newForwardType(runtime.booleanParameterizedType());
            Expression expression = parsers.parseExpression().parse(context, index, forwardType, whileStatement.get(2));
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, whileStatement.get(4));
            return runtime.newWhileBuilder()
                    .addComments(comments).setSource(source).setLabel(label)
                    .setExpression(expression).setBlock(block).build();
        }
        if (statement instanceof DoStatement doStatement) {
            Context newContext = context.newVariableContext("do-while");
            ForwardType forwardType = context.newForwardType(runtime.booleanParameterizedType());
            Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, doStatement.get(1));
            Expression expression = parsers.parseExpression().parse(context, index, forwardType, doStatement.get(4));
            return runtime.newDoBuilder()
                    .addComments(comments).setSource(source).setLabel(label)
                    .setExpression(expression).setBlock(block).build();
        }
        if (statement instanceof EmptyStatement) {
            return runtime.newEmptyStatementBuilder()
                    .addComments(comments).setSource(source).setLabel(label)
                    .build();
        }
        throw new UnsupportedOperationException("Node " + statement.getClass());
    }

    private LocalVariableCreation parseResource(Node node) {
        return null;
    }

    private static int countCatchBlocks(TryStatement tryStatement) {
        return (int) tryStatement.children().stream().filter(n -> n instanceof CatchBlock).count();
    }

    private SwitchStatementNewStyle parseNewStyleSwitch(Context context, String index, Statement statement,
                                                        List<Comment> comments, Source source, String label, int n) {
        Expression selector = parsers.parseExpression().parse(context, index, context.emptyForwardType(),
                statement.get(2));
        ForwardType selectorTypeFwd = context.newForwardType(selector.parameterizedType());
        List<SwitchEntry> entries = new ArrayList<>();
        Context newContext = context.newVariableContext("switch-new-style");
        TypeInfo selectorTypeInfo = selector.parameterizedType().bestTypeInfo();
        if (selectorTypeInfo.typeNature().isEnum()) {
            selectorTypeInfo.fields().stream().filter(Info::isSynthetic)
                    .forEach(f -> newContext.variableContext().add(runtime.newFieldReference(f)));
        }
        int count = 0;
        for (Node child : statement) {
            if (child instanceof NewCaseStatement ncs) {
                SwitchEntry.Builder entryBuilder = runtime.newSwitchEntryBuilder();
                if (ncs.get(0) instanceof NewSwitchLabel nsl) {
                    List<Expression> conditions = new ArrayList<>();
                    if (Token.TokenType._DEFAULT.equals(nsl.get(0).getType())) {
                        conditions.add(runtime.newEmptyExpression());
                    } else if (!Token.TokenType.CASE.equals(nsl.get(0).getType())) {
                        throw new Summary.ParseException(newContext.info(), "Expect 'case' or 'default'");
                    }
                    int j = 1;
                    while (j < nsl.size() - 1) {
                        Expression c = parsers.parseExpression().parse(newContext, index, selectorTypeFwd, nsl.get(j));
                        conditions.add(c);
                        Node next = nsl.get(j + 1);
                        if (!Token.TokenType.COMMA.equals(next.getType())) break;
                        j += 2;
                    }
                    entryBuilder.addConditions(conditions);
                } else throw new Summary.ParseException(newContext.info(), "Expect NewCaseStatement");
                Expression whenExpression = runtime.newEmptyExpression(); // FIXME
                if (ncs.get(1) instanceof CodeBlock cb) {
                    String newIndex = index + "." + StringUtil.pad(count, n);
                    entryBuilder.setStatement(parsers.parseBlock().parse(newContext, newIndex, null, cb));
                } else if (ncs.get(1) instanceof Statement st) {
                    String newIndex = index + "." + StringUtil.pad(count, n) + "0";
                    entryBuilder.setStatement(parse(newContext, newIndex, st));
                } else throw new Summary.ParseException(newContext.info(), "Expect statement");
                count++;
                entries.add(entryBuilder.setWhenExpression(whenExpression).build());
            }
        }
        return runtime.newSwitchStatementNewStyleBuilder().addComments(comments).setSource(source).setLabel(label)
                .setSelector(selector).addSwitchEntries(entries).build();
    }

    private SwitchStatementOldStyle parseOldStyleSwitch(Context context, String index, Statement statement,
                                                        List<Comment> comments, Source source, String label, int n) {
        Expression selector = parsers.parseExpression().parse(context, index, context.emptyForwardType(),
                statement.get(2));
        Block.Builder builder = runtime.newBlockBuilder();
        List<SwitchStatementOldStyle.SwitchLabel> switchLabels = new ArrayList<>();
        Context newContext = context.newVariableContext("switch-old-style");
        int pos = 0;

        for (int i = 5; i < statement.size(); i++) {
            if (statement.get(i) instanceof ClassicCaseStatement ccs) {
                if (ccs.get(0) instanceof ClassicSwitchLabel csl) {
                    if (Token.TokenType._DEFAULT.equals(csl.get(0).getType())) {
                        SwitchStatementOldStyle.SwitchLabel sld
                                = runtime.newSwitchLabelOldStyle(runtime.newEmptyExpression(), pos, null,
                                runtime.newEmptyExpression());
                        switchLabels.add(sld);
                    } else {
                        assert Token.TokenType.CASE.equals(csl.get(0).getType());
                        for (int k = 1; k < csl.size(); k += 2) {
                            String newIndex = index + ".0." + StringUtil.pad(pos, n);
                            Expression literal = parsers.parseExpression().parse(newContext, newIndex,
                                    newContext.emptyForwardType(), csl.get(k));
                            SwitchStatementOldStyle.SwitchLabel sl = runtime.newSwitchLabelOldStyle(literal, pos,
                                    null, runtime.newEmptyExpression());
                            switchLabels.add(sl);
                        }
                    }
                }
                for (int j = 1; j < ccs.size(); j++) {
                    if (ccs.get(j) instanceof Statement s) {
                        String newIndex = index + ".0." + StringUtil.pad(pos, n);
                        org.e2immu.language.cst.api.statement.Statement st
                                = parse(context, newIndex, s);
                        builder.addStatement(st);
                        pos++;
                    }
                }
            }
        }
        return runtime.newSwitchStatementOldStyleBuilder()
                .addComments(comments).setSource(source).setLabel(label)
                .setSelector(selector)
                .setBlock(builder.build())
                .addSwitchLabels(switchLabels)
                .build();
    }

    private static int countStatementsInOldStyleSwitch(Statement statement) {
        int n = 0;
        for (int i = 5; i < statement.size(); i++) {
            if (statement.get(i) instanceof ClassicCaseStatement ccs) {
                for (int j = 1; j < ccs.size(); j++) {
                    if (ccs.get(j) instanceof Statement s) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static int countStatementsInNewStyleSwitch(Statement statement) {
        return statement.children().stream().mapToInt(n -> n instanceof NewCaseStatement ? 1 : 0).sum();
    }

    private Block parseBlockOrStatement(Context context, String index, Node node) {
        if (node instanceof CodeBlock codeBlock) {
            return parsers.parseBlock().parse(context, index, null, codeBlock);
        }
        if (node instanceof Statement s) {
            org.e2immu.language.cst.api.statement.Statement st = parse(context, index + FIRST_STATEMENT, s);
            return runtime.newBlockBuilder().addStatement(st).build();
        }
        throw new UnsupportedOperationException();
    }
}
