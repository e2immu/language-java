package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.expression.VariableExpression;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.*;
import org.e2immu.language.cst.api.statement.ForStatement;
import org.e2immu.language.cst.api.statement.Statement;
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

    public Statement parse(Context context, String index, org.parsers.java.ast.Statement statement) {
        try {
            return internalParse(context, index, statement);
        } catch (Throwable t) {
            LOGGER.error("Caught exception parsing statement at line {}", statement.getBeginLine());
            throw t;
        }
    }

    private Statement internalParse(Context context, String index, org.parsers.java.ast.Statement statementIn) {
        String label;
        org.parsers.java.ast.Statement statement;
        if (statementIn instanceof LabeledStatement ls) {
            label = ls.get(0).getFirst().getSource();
            statement = (org.parsers.java.ast.Statement) ls.get(1);
        } else {
            label = null;
            statement = statementIn;
        }
        List<Comment> comments = comments(statement);
        Source source = source(context.enclosingMethod(), index, statement);
        List<AnnotationExpression> annotations = new ArrayList<>();
        List<LocalVariableCreation.Modifier> lvcModifiers = new ArrayList<>();

        // annotations
        int i = 0;
        while (true) {
            Node si = statement.get(i);
            if (si instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof Annotation a) {
                        annotations.add(parsers.parseAnnotationExpression().parse(context, a));
                    }
                }
            } else if (si instanceof KeyWord) {
                if (Token.TokenType.FINAL.equals(si.getType())) {
                    lvcModifiers.add(runtime.localVariableModifierFinal());
                } else if (Token.TokenType.VAR.equals(si.getType())) {
                    lvcModifiers.add(runtime.localVariableModifierVar());
                } else break;
            } else if (si instanceof Annotation a) {
                annotations.add(parsers.parseAnnotationExpression().parse(context, a));
            } else break;
            i++;
        }

        Statement.Builder<?> b = switch (statement) {
            case AssertStatement as -> handleAssertStatement(context, index, as, i);
            case BasicForStatement basicForStatement -> handleBasicForStatement(context, index, basicForStatement, i);
            case BreakStatement bs -> handleBreakStatement(bs, i);
            // important: for reasons of consistency, an individual block has statements with index .0.n, not just .n
            case CodeBlock cb -> handleCodeBlock(context, index, cb);
            case ContinueStatement cs -> handleContinueStatement(cs, i);
            case DoStatement ds -> handleDoStatement(context, index, ds, i);
            case EmptyStatement es -> runtime.newEmptyStatementBuilder();
            case ExpressionStatement es -> handleExpressionAsStatement(context, index, es, i);
            case EnhancedForStatement enhancedFor -> handleEnhancedFor(context, index, enhancedFor, i);
            case IfStatement ifStatement -> handleIfStatement(context, index, ifStatement, i);
            case NoVarDeclaration nvd -> handleNoVarDeclaration(context, index, nvd, i, lvcModifiers);
            case ReturnStatement rs -> handleReturnStatement(context, index, rs, i);
            case SynchronizedStatement sync -> handleSynchronizedStatement(context, index, sync, i);
            case SwitchStatement sw -> handleSwitchStatement(context, index, sw, i);
            case ThrowStatement ts -> handleThrowStatement(context, index, ts, i);
            case TryStatement tryStatement -> handleTryStatement(context, index, tryStatement, i);
            case VarDeclaration vd -> handleVarDeclaration(context, index, vd, i, lvcModifiers);
            case WhileStatement ws -> handleWhileStatement(context, index, ws, i);
            case YieldStatement ys -> handleYieldStatement(context, index, ys, i);

            default -> throw new IllegalStateException("Unexpected value: " + statement);
        };
        return b.setLabel(label).addAnnotations(annotations).setSource(source).addComments(comments).build();
    }

    private static int countCatchBlocks(TryStatement tryStatement) {
        return (int) tryStatement.children().stream().filter(n -> n instanceof CatchBlock).count();
    }

    private static int countStatementsInNewStyleSwitch(SwitchStatement statement) {
        return statement.children().stream().mapToInt(n -> n instanceof NewCaseStatement ? 1 : 0).sum();
    }

    private static int countStatementsInOldStyleSwitch(SwitchStatement statement) {
        int n = 0;
        for (int i = 5; i < statement.size(); i++) {
            if (statement.get(i) instanceof ClassicCaseStatement ccs) {
                for (int j = 1; j < ccs.size(); j++) {
                    if (ccs.get(j) instanceof org.parsers.java.ast.Statement) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private Statement.Builder<?> handleAssertStatement(Context context, String index, AssertStatement as, int i) {
        Expression expression = parsers.parseExpression().parse(context, index,
                context.newForwardType(runtime.booleanParameterizedType()), as.get(i + 1));
        Expression message = Token.TokenType.COLON.equals(as.get(i + 2).getType())
                ? parsers.parseExpression().parse(context, index, context.newForwardType(runtime.stringParameterizedType()),
                as.get(i + 3))
                : runtime.newEmptyExpression();
        return runtime.newAssertBuilder().setExpression(expression).setMessage(message);
    }

    private Statement.Builder<?> handleBasicForStatement(Context context, String index,
                                                         BasicForStatement basicForStatement, int i) {
        ForStatement.Builder builder = runtime.newForBuilder();
        Context newContext = context.newVariableContext("for-loop");

        // initializers

        i += 2;
        switch (basicForStatement.get(i)) {
            case Delimiter d when Token.TokenType.SEMICOLON.equals(d.getType()) ->
                // no initializer
                    i++;
            case org.parsers.java.ast.Statement s -> {
                builder.addInitializer(parse(newContext, index, s));
                i += 2;
            }
            case StatementExpression nodes -> {
                // there could be more than one
                do {
                    if (basicForStatement.get(i) instanceof StatementExpression se) {
                        Expression initializer = parsers.parseExpression().parse(newContext, index,
                                newContext.emptyForwardType(), se.getFirst());
                        builder.addInitializer(initializer);
                    } else throw new UnsupportedOperationException();
                    i += 2;
                } while (Token.TokenType.COMMA.equals(basicForStatement.get(i - 1).getType()));
            }
            case null, default -> throw new UnsupportedOperationException();
        }

        // condition

        if (basicForStatement.get(i) instanceof Delimiter d && Token.TokenType.SEMICOLON.equals(d.getType())) {
            // no condition
            builder.setExpression(runtime.constantTrue());
            i++;
        } else {
            Expression condition = parsers.parseExpression().parse(newContext, index, context.emptyForwardType(),
                    basicForStatement.get(i));
            builder.setExpression(condition);
            i += 2;
        }
        // updaters

        if ((basicForStatement.get(i) instanceof Delimiter d && Token.TokenType.RPAREN.equals(d.getType()))) {
            i++; // no updaters
        } else {
            while (basicForStatement.get(i) instanceof StatementExpression se) {
                Expression updater = parsers.parseExpression().parse(newContext, index, newContext.emptyForwardType(),
                        se.getFirst());
                builder.addUpdater(updater);
                i += 2;
                if (basicForStatement.get(i - 1) instanceof Delimiter d && Token.TokenType.RPAREN.equals(d.getType())) {
                    break;
                }
            }
        }
        Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, basicForStatement.get(i));
        return builder.setBlock(block);
    }

    private Statement.Builder<?> handleBreakStatement(BreakStatement bs, int i) {
        String goToLabel;
        if (bs.size() > i + 1 && bs.get(i + 1) instanceof Identifier identifier) {
            goToLabel = identifier.getSource();
        } else {
            goToLabel = null;
        }
        return runtime.newBreakBuilder().setGoToLabel(goToLabel);
    }

    private Statement.Builder<?> handleCodeBlock(Context context, String index, CodeBlock cb) {
        Block block = parsers.parseBlock().parse(context, index, null, cb, true, 0);
        return runtime.newBlockBuilder().addStatements(block.statements());
    }

    private Statement.Builder<?> handleContinueStatement(ContinueStatement cs, int i) {
        String goToLabel;
        if (cs.size() > i + 1 && cs.get(i + 1) instanceof Identifier identifier) {
            goToLabel = identifier.getSource();
        } else {
            goToLabel = null;
        }
        return runtime.newContinueBuilder().setGoToLabel(goToLabel);
    }

    private Statement.Builder<?> handleDoStatement(Context context, String index, DoStatement ds, int i) {
        Context newContext = context.newVariableContext("do-while");
        ForwardType forwardType = context.newForwardType(runtime.booleanParameterizedType());
        Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, ds.get(i + 1));
        Expression expression = parsers.parseExpression().parse(context, index, forwardType, ds.get(i + 4));
        return runtime.newDoBuilder().setExpression(expression).setBlock(block);
    }

    private Statement.Builder<?> handleEnhancedFor(Context context, String index,
                                                   EnhancedForStatement enhancedFor, int i) {
        // kw, del, noVarDecl (objectType, vardcl), operator, name (Name), del, code block
        LocalVariableCreation loopVariableCreation;
        TypeInfo iterable = runtime.getFullyQualified(Iterable.class, false);
        ForwardType forwardType = iterable == null ? context.emptyForwardType() :
                context.newForwardType(iterable.asSimpleParameterizedType());
        Expression expression = parsers.parseExpression().parse(context, index, forwardType, enhancedFor.get(i + 4));
        Context newContext = context.newVariableContext("forEach");
        if (enhancedFor.get(i + 2) instanceof NoVarDeclaration nvd) {
            loopVariableCreation = (LocalVariableCreation) parse(newContext, index, nvd);
        } else throw new UnsupportedOperationException();
        Node n6 = enhancedFor.get(i + 6);
        Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, n6);
        return runtime.newForEachBuilder().setInitializer(loopVariableCreation).setBlock(block).setExpression(expression);
    }

    private Statement.Builder<?> handleExpressionAsStatement(Context context, String index,
                                                             ExpressionStatement es, int i) {
        StatementExpression se = (StatementExpression) es.children().get(i);
        Expression e = parsers.parseExpression().parse(context, index, context.emptyForwardType(), se.getFirst());
        return runtime.newExpressionAsStatementBuilder().setExpression(e);
    }

    private Statement.Builder<?> handleIfStatement(Context context, String index, IfStatement ifStatement, int i) {
        ForwardType forwardType = context.newForwardType(runtime.booleanParameterizedType());
        Expression expression = parsers.parseExpression().parse(context, index, forwardType, ifStatement.get(i + 2));
        Node n4 = ifStatement.get(i + 4);
        Context newContext = context.newVariableContext("ifBlock");
        Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, n4);
        Block elseBlock;
        if (ifStatement.size() > i + 5 && ifStatement.get(i + 5) instanceof KeyWord) {
            assert Token.TokenType.ELSE.equals(ifStatement.get(i + 5).getType());
            Node n6 = ifStatement.get(i + 6);
            Context newContext2 = context.newVariableContext("elseBlock");
            elseBlock = parseBlockOrStatement(newContext2, index + SECOND_BLOCK, n6);
        } else {
            elseBlock = runtime.emptyBlock();
        }
        return runtime.newIfElseBuilder().setExpression(expression).setIfBlock(block).setElseBlock(elseBlock);
    }

    private Statement.Builder<?> handleNoVarDeclaration(Context context, String index, NoVarDeclaration nvd, int i,
                                                        List<LocalVariableCreation.Modifier> lvcModifiers) {
        LocalVariableCreation.Builder builder = runtime.newLocalVariableCreationBuilder();

        ParameterizedType baseType = parsers.parseType().parse(context, nvd.get(i));
        i++;
        boolean first = true;
        while (i < nvd.size() && nvd.get(i) instanceof VariableDeclarator vd) {
            Node vd0 = vd.getFirst();
            Identifier identifier;
            ParameterizedType type;
            if (vd0 instanceof VariableDeclaratorId vdi) {
                identifier = (Identifier) vdi.getFirst();
                int arrays = (vdi.size() - 1) / 2;
                type = baseType.copyWithArrays(arrays);
            } else {
                identifier = (Identifier) vd0;
                type = baseType;
            }
            String variableName = identifier.getSource();
            LocalVariable lv = runtime.newLocalVariable(variableName, type, runtime.newEmptyExpression());
            context.variableContext().add(lv);
            if (vd.size() > 2) {
                ForwardType forwardType = context.newForwardType(type);
                Expression expression = parsers.parseExpression().parse(context, index, forwardType, vd.get(2));
                lv = runtime.newLocalVariable(variableName, type, expression);
                // replace! See TestAssignment,2 for the cause of this silly construction
                context.variableContext().add(lv);
            }
            if (first) {
                builder.setLocalVariable(lv);
                first = false;
            } else {
                builder.addOtherLocalVariable(lv);
            }
            i += 2;
        }
        lvcModifiers.forEach(builder::addModifier);
        return builder;
    }

    private Statement.Builder<?> handleReturnStatement(Context context, String index, ReturnStatement rs, int i) {
        ForwardType forwardType = context.newForwardType(context.enclosingMethod().returnType());
        Expression e;
        ++i;
        if (rs.get(i) instanceof Delimiter) {
            e = runtime.newEmptyExpression();
        } else {
            e = parsers.parseExpression().parse(context, index, forwardType, rs.get(i));
        }
        assert e != null;
        return runtime.newReturnBuilder().setExpression(e);
    }

    private Statement.Builder<?> handleSwitchStatement(Context context, String index, SwitchStatement sw, int i) {
        int no = countStatementsInOldStyleSwitch(sw);
        if (no > 0) {
            return parseOldStyleSwitch(context, index, sw, no, i);
        }
        int nn = countStatementsInNewStyleSwitch(sw);
        if (nn >= 0) {
            return parseNewStyleSwitch(context, index, sw, nn, i);
        }
        throw new UnsupportedOperationException();
    }

    private Statement.Builder<?> handleSynchronizedStatement(Context context, String index,
                                                             SynchronizedStatement st, int i) {
        Context newContext = context.newVariableContext("synchronized");
        Expression expression = parsers.parseExpression().parse(context, index, context.emptyForwardType(),
                st.get(i + 2));
        Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, st.get(i + 4));
        return runtime.newSynchronizedBuilder().setExpression(expression).setBlock(block);
    }

    private Statement.Builder<?> handleThrowStatement(Context context, String index, ThrowStatement ts, int i) {
        TypeInfo throwable = runtime.getFullyQualified(Throwable.class, false);
        ForwardType fwd = throwable != null ? context.newForwardType(throwable.asSimpleParameterizedType())
                : context.emptyForwardType();
        Expression expression = parsers.parseExpression().parse(context, index, fwd, ts.get(i + 1));
        return runtime.newThrowBuilder().setExpression(expression);
    }

    private Statement.Builder<?> handleTryStatement(Context context, String index, TryStatement tryStatement, int i) {
        org.e2immu.language.cst.api.statement.TryStatement.Builder builder = runtime.newTryBuilder();
        ++i;
        Context newContext = context.newVariableContext("tryBlock");
        if (tryStatement.get(i) instanceof ResourcesInTryBlock r) {
            int j = 1;
            int rCount = 0;
            int n = (r.size() - 1) / 2;
            while (j < r.size() && !(r.get(j) instanceof Delimiter)) {
                // resources
                String newIndex = index + "+" + StringUtil.pad(rCount, n);
                Node resourceNode = r.get(j);
                Statement resource;
                if (resourceNode instanceof org.parsers.java.ast.Statement rn) {
                    resource = parse(newContext, newIndex, rn);
                } else if (resourceNode instanceof Name) {
                    Expression e = parsers.parseExpression().parse(newContext, newIndex, context.emptyForwardType(),
                            resourceNode);
                    if (e instanceof VariableExpression ve) {
                        resource = runtime.newExpressionAsStatementBuilder()
                                .setSource(source(context.info(), newIndex, resourceNode))
                                .setExpression(ve)
                                .build();
                    } else throw new UnsupportedOperationException();
                } else throw new UnsupportedOperationException("NYI");
                builder.addResource(resource);
                j += 2;
                rCount++;
            }
            i++;
        }
        int n = countCatchBlocks(tryStatement) + 2;
        String firstIndex = index + "." + StringUtil.pad(0, n);
        Block block = parseBlockOrStatement(newContext, firstIndex, tryStatement.get(i));
        i++;
        builder.setBlock(block);
        int blockCount = 1;
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
            List<ParameterizedType> exceptionTypes = new ArrayList<>();
            // Type Operator Type ...
            while (true) {
                Node cbj = catchBlock.get(j);
                if (cbj instanceof Type type) {
                    for (Node child : type.children()) {
                        if (child instanceof Annotation a) {
                            ccBuilder.addAnnotation(parsers.parseAnnotationExpression().parse(context, a));
                        }
                    }
                    ParameterizedType pt = parsers.parseType().parse(context, type);
                    exceptionTypes.add(pt);
                    ccBuilder.addType(pt);
                } else if (cbj instanceof Identifier) {
                    break;
                } else if (!(cbj instanceof Operator operator
                             && Token.TokenType.BIT_OR.equals(operator.getType()))) {
                    throw new UnsupportedOperationException();
                }
                j++;
            }
            if (catchBlock.get(j) instanceof Identifier identifier) {
                String variableName = identifier.getSource();
                ParameterizedType commonType;
                if (exceptionTypes.isEmpty()) throw new UnsupportedOperationException();
                else if (exceptionTypes.size() == 1) {
                    commonType = exceptionTypes.getFirst();
                } else {
                    commonType = exceptionTypes.stream().skip(1)
                            .reduce(exceptionTypes.getFirst(), runtime::commonType);
                }
                LocalVariable cv = runtime.newLocalVariable(variableName, commonType);
                ccBuilder.setCatchVariable(cv);
                catchContext.variableContext().add(cv);
                j++;
            } else {
                throw new UnsupportedOperationException();
            }
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
        return builder.setFinallyBlock(finallyBlock);
    }

    private Statement.Builder<?> handleVarDeclaration(Context context, String index, VarDeclaration varDeclaration,
                                                      int i, List<LocalVariableCreation.Modifier> lvcModifiers) {
        LocalVariableCreation.Builder builder = runtime.newLocalVariableCreationBuilder();
        Identifier identifier = (Identifier) varDeclaration.get(i);
        ForwardType forwardType = context.emptyForwardType();
        Expression expression = parsers.parseExpression().parse(context, index, forwardType, varDeclaration.get(i + 2));
        String variableName = identifier.getSource();
        ParameterizedType type = expression.parameterizedType();
        LocalVariable lv = runtime.newLocalVariable(variableName, type, expression);
        context.variableContext().add(lv);
        lvcModifiers.forEach(builder::addModifier);
        return builder.setLocalVariable(lv);
    }

    private Statement.Builder<?> handleWhileStatement(Context context, String index, WhileStatement ws, int i) {
        Context newContext = context.newVariableContext("while");
        ForwardType forwardType = context.newForwardType(runtime.booleanParameterizedType());
        Expression expression = parsers.parseExpression().parse(context, index, forwardType, ws.get(i + 2));
        Block block = parseBlockOrStatement(newContext, index + FIRST_BLOCK, ws.get(i + 4));
        return runtime.newWhileBuilder().setExpression(expression).setBlock(block);
    }

    private Statement.Builder<?> handleYieldStatement(Context context, String index, YieldStatement ys, int i) {
        ForwardType forwardType = context.newForwardType(context.enclosingMethod().returnType());
        Expression e;
        ++i;
        if (ys.size() == 2) {
            assert ys.get(i) instanceof Delimiter;
            e = runtime.newEmptyExpression();
        } else {
            e = parsers.parseExpression().parse(context, index, forwardType, ys.get(i));
        }
        assert e != null;
        return runtime.newYieldBuilder().setExpression(e);
    }

    private Block parseBlockOrStatement(Context context, String index, Node node) {
        if (node instanceof CodeBlock codeBlock) {
            return parsers.parseBlock().parse(context, index, null, codeBlock);
        }
        if (node instanceof org.parsers.java.ast.Statement s) {
            Statement st = parse(context, index + FIRST_STATEMENT, s);
            return runtime.newBlockBuilder().addStatement(st).build();
        }
        throw new UnsupportedOperationException();
    }

    private Statement.Builder<?> parseNewStyleSwitch(Context context, String index, SwitchStatement statement,
                                                     int n, int start) {
        Expression selector = parsers.parseExpression().parse(context, index, context.emptyForwardType(),
                statement.get(start + 2));
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
                    if (Token.TokenType._DEFAULT.equals(nsl.getFirst().getType())) {
                        conditions.add(runtime.newEmptyExpression());
                    } else if (!Token.TokenType.CASE.equals(nsl.getFirst().getType())) {
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
                } else if (ncs.get(1) instanceof org.parsers.java.ast.Statement st) {
                    String newIndex = index + "." + StringUtil.pad(count, n) + "0";
                    entryBuilder.setStatement(parse(newContext, newIndex, st));
                } else throw new Summary.ParseException(newContext.info(), "Expect statement");
                count++;
                entries.add(entryBuilder.setWhenExpression(whenExpression).build());
            }
        }
        return runtime.newSwitchStatementNewStyleBuilder().setSelector(selector).addSwitchEntries(entries);
    }

    private Statement.Builder<?> parseOldStyleSwitch(Context context, String index, SwitchStatement statement,
                                                     int n, int start) {
        Expression selector = parsers.parseExpression().parse(context, index, context.emptyForwardType(),
                statement.get(2));
        Block.Builder builder = runtime.newBlockBuilder();
        List<SwitchStatementOldStyle.SwitchLabel> switchLabels = new ArrayList<>();
        Context newContext = context.newVariableContext("switch-old-style");
        int pos = 0;

        for (int i = start + 5; i < statement.size(); i++) {
            if (statement.get(i) instanceof ClassicCaseStatement ccs) {
                if (ccs.getFirst() instanceof ClassicSwitchLabel csl) {
                    if (Token.TokenType._DEFAULT.equals(csl.getFirst().getType())) {
                        SwitchStatementOldStyle.SwitchLabel sld
                                = runtime.newSwitchLabelOldStyle(runtime.newEmptyExpression(), pos, null,
                                runtime.newEmptyExpression());
                        switchLabels.add(sld);
                    } else {
                        assert Token.TokenType.CASE.equals(csl.getFirst().getType());
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
                    if (ccs.get(j) instanceof org.parsers.java.ast.Statement s) {
                        String newIndex = index + ".0." + StringUtil.pad(pos, n);
                        Statement st = parse(context, newIndex, s);
                        builder.addStatement(st);
                        pos++;
                    }
                }
            }
        }
        return runtime.newSwitchStatementOldStyleBuilder().setSelector(selector).setBlock(builder.build())
                .addSwitchLabels(switchLabels);
    }
}
