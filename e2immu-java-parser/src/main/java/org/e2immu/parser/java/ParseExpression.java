package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.*;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.SwitchEntry;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.cst.api.variable.LocalVariable;
import org.e2immu.language.cst.api.variable.Variable;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.util.internal.util.StringUtil;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.parsers.java.ast.ArrayInitializer;
import org.parsers.java.ast.MethodCall;
import org.parsers.java.ast.MethodReference;
import org.parsers.java.ast.SwitchExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.parsers.java.Token.TokenType.*;

public class ParseExpression extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseExpression.class);

    public ParseExpression(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public Expression parse(Context context, String index, ForwardType forwardType, Node node) {
        try {
            return internalParse(context, index, forwardType, node);
        } catch (Throwable t) {
            t.printStackTrace();
            LOGGER.error("Caught exception parsing expression at line {}, pos {}", node.getBeginLine(), node.getBeginColumn());
            throw t;
        }
    }

    private Expression internalParse(Context context, String index, ForwardType forwardType, Node node) {
        Source source = source(context.info(), index, node);
        List<Comment> comments = comments(node);

        return switch (node) {
            case AdditiveExpression ae -> parseAdditive(context, index, ae);
            case AllocationExpression ae -> parsers.parseConstructorCall().parse(context, index, forwardType, ae);
            case ArrayAccess aa -> parseArrayAccess(context, index, forwardType, aa, comments, source);
            case ArrayInitializer ai -> arrayInitializer(context, index, forwardType, comments, source, ai);
            case AssignmentExpression ae -> parseAssignment(context, index, ae, comments, source);
            case AndExpression ae -> parseMultiplicative(context, index, ae);
            case CastExpression ce -> parseCast(context, index, comments, source, ce);
            case ClassLiteral cl -> parseClassLiteral(context, cl);
            case ConditionalAndExpression ca -> parseConditionalExpression(context, comments, source, index, ca);
            case ConditionalOrExpression co -> parseConditionalExpression(context, comments, source, index, co);
            case DotName dotName -> parseDotName(context, comments, source, index, dotName);
            case DotSuper ds -> parseDotThisDotSuper(context, ds, source);
            case DotThis dt -> parseDotThisDotSuper(context, dt, source);
            case EqualityExpression eq -> parseEquality(context, index, eq);
            case ExclusiveOrExpression eo -> parseMultiplicative(context, index, eo);
            case Identifier i -> parseName(context, comments, source, i.getSource());
            case InclusiveOrExpression io -> parseMultiplicative(context, index, io);
            case InstanceOfExpression ioe -> parseInstanceOf(context, index, forwardType, comments, source, ioe);
            case LambdaExpression le ->
                    parsers.parseLambdaExpression().parse(context, comments, source, index, forwardType, le);
            case LiteralExpression le -> parseLiteral(context, le);
            case MemberValueArrayInitializer ai -> arrayInitializer(context, index, forwardType, comments, source, ai);
            case MethodCall mc -> parsers.parseMethodCall().parse(context, comments, source, index, forwardType, mc);
            case MethodReference mr ->
                    parsers.parseMethodReference().parse(context, comments, source, index, forwardType, mr);
            case MultiplicativeExpression me -> parseMultiplicative(context, index, me);
            case Name name -> {
                if (name.children().stream().allMatch(n -> n instanceof Delimiter || n instanceof Identifier)) {
                    yield parseName(context, comments, source, name.getAsString());
                }
                yield parse(context, index, forwardType, name.getFirst());
            }
            case NormalAnnotation na -> parsers.parseAnnotationExpression().parse(context, na);
            case ObjectType ot -> parseObjectType(context, index, node, ot, comments, source);
            case Parentheses p -> parseParentheses(context, index, comments, source, forwardType, p);
            case PostfixExpression pe -> plusPlusMinMin(context, index, comments, source, 0, 1, false, pe);
            case PreDecrementExpression pd -> plusPlusMinMin(context, index, comments, source, 1, 0, true, pd);
            case PreIncrementExpression pe -> plusPlusMinMin(context, index, comments, source, 1, 0, true, pe);
            case RelationalExpression re -> parseRelational(context, index, re);
            case ShiftExpression se -> parseMultiplicative(context, index, se);
            case SwitchExpression se ->
                    parseSwitchExpression(context, index, forwardType, comments, source, node.getFirst());
            case TernaryExpression te -> inlineConditional(context, index, forwardType, comments, source, te);
            case UnaryExpression ue -> parseUnaryExpression(context, index, ue);
            case UnaryExpressionNotPlusMinus ue -> parseUnaryExpression(context, index, ue);
            default -> throw new IllegalStateException("Unexpected value: " + node.getClass());
        };
    }

    private Expression parseObjectType(Context context, String index, Node node, ObjectType ot,
                                       List<Comment> comments, Source source) {
        // maybe really hard-coded, but serves ParseMethodReference, e.g. TestMethodCall0,9
        if (ot.size() == 3 && DOT.equals(ot.get(1).getType())) {
            return parseDotName(context, comments, source, index, node);
        }
        // ditto, see TestParseMethodReference
        if (ot.size() == 1 && ot.get(0) instanceof Identifier i) {
            return parseName(context, comments, source, i.getSource());
        }
        throw new UnsupportedOperationException();
    }

    private VariableExpression parseArrayAccess(Context context, String index, ForwardType forwardType,
                                                ArrayAccess arrayAccess, List<Comment> comments, Source source) {
        assert arrayAccess.size() == 4 : "Not implemented";
        Expression ae = parse(context, index, forwardType, arrayAccess.get(0));
        ForwardType fwdInt = context.newForwardType(runtime.intParameterizedType());
        Expression ie = parse(context, index, fwdInt, arrayAccess.get(2));
        Variable variable = runtime.newDependentVariable(ae, ie);
        return runtime.newVariableExpressionBuilder().addComments(comments).setSource(source)
                .setVariable(variable).build();
    }

    private VariableExpression parseDotThisDotSuper(Context context, Node node, Source source) {
        ParameterizedType type;
        if (node.getFirst() instanceof Name name) {
            type = parsers.parseType().parse(context, name);
        } else throw new Summary.ParseException(context.info(), "expected Name");
        boolean isSuper = node instanceof DotSuper;
        TypeInfo explicitType = type.bestTypeInfo();
        return runtime.newVariableExpressionBuilder()
                .setSource(source)
                .setVariable(runtime.newThis(type, explicitType, isSuper)).build();
    }

    private Expression parseInstanceOf(Context context, String index, ForwardType forwardType,
                                       List<Comment> comments, Source source, InstanceOfExpression ioe) {
        Expression expression = parse(context, index, forwardType, ioe.get(0));
        assert INSTANCEOF.equals(ioe.get(1).getType());
        ParameterizedType testType;
        LocalVariable patternVariable;
        if (ioe.get(2) instanceof NoVarDeclaration nvd) {
            testType = parsers.parseType().parse(context, nvd.get(0));
            String lvName = nvd.get(1).getSource();
            patternVariable = runtime.newLocalVariable(lvName, testType);
            context.variableContext().add(patternVariable);
        } else {
            testType = parsers.parseType().parse(context, ioe.get(2));
            patternVariable = null;
        }
        return runtime.newInstanceOfBuilder()
                .setSource(source).addComments(comments)
                .setExpression(expression).setTestType(testType).setPatternVariable(patternVariable)
                .build();
    }

    private Expression parseSwitchExpression(Context context,
                                             String index,
                                             ForwardType forwardType,
                                             List<Comment> comments, Source source, Node node) {
        Expression selector = parsers.parseExpression().parse(context, index, context.emptyForwardType(),
                node.get(2));
        int n = (int) node.children().stream()
                .filter(child -> child instanceof NewCaseStatement)
                .count();
        ForwardType selectorTypeFwd = context.newForwardType(selector.parameterizedType());
        List<SwitchEntry> entries = new ArrayList<>();
        Context newContext = context.newVariableContext("switch-expression");
        TypeInfo selectorTypeInfo = selector.parameterizedType().bestTypeInfo();
        if (selectorTypeInfo.typeNature().isEnum()) {
            selectorTypeInfo.fields().stream().filter(Info::isSynthetic)
                    .forEach(f -> newContext.variableContext().add(runtime.newFieldReference(f)));
        }
        int count = 0;
        ParameterizedType commonType = null;
        for (Node child : node) {
            if (child instanceof NewCaseStatement ncs) {
                SwitchEntry.Builder entryBuilder = runtime.newSwitchEntryBuilder();
                if (ncs.get(0) instanceof NewSwitchLabel nsl) {
                    List<Expression> conditions = new ArrayList<>();
                    if (_DEFAULT.equals(nsl.getFirst().getType())) {
                        conditions.add(runtime.newEmptyExpression());
                    } else if (!CASE.equals(nsl.getFirst().getType())) {
                        throw new Summary.ParseException(newContext.info(), "Expect 'case' or 'default'");
                    }
                    int j = 1;
                    while (j < nsl.size() - 1) {
                        Expression c = parsers.parseExpression().parse(newContext, index, selectorTypeFwd, nsl.get(j));
                        conditions.add(c);
                        Node next = nsl.get(j + 1);
                        if (!COMMA.equals(next.getType())) break;
                        j += 2;
                    }
                    entryBuilder.addConditions(conditions);
                } else throw new Summary.ParseException(newContext.info(), "Expect NewCaseStatement");
                Expression whenExpression = runtime.newEmptyExpression(); // FIXME
                Node ncs1 = ncs.get(1);
                if (ncs1 instanceof CodeBlock cb) {
                    String newIndex = index + "." + StringUtil.pad(count, n);
                    entryBuilder.setStatement(parsers.parseBlock().parse(newContext, newIndex, null, cb));
                } else if (ncs1 instanceof Statement statement) {
                    // throw statement is allowed!
                    String newIndex = index + "." + StringUtil.pad(count, n) + "0";
                    org.e2immu.language.cst.api.statement.Statement st = parsers.parseStatement()
                            .parse(newContext, newIndex, statement);
                    entryBuilder.setStatement(st);
                } else if (ncs1 instanceof org.parsers.java.ast.Expression expression) {
                    String newIndex = index + "." + StringUtil.pad(count, n) + "0";
                    Expression pe = parse(newContext, newIndex, forwardType, expression);
                    entryBuilder.setStatement(runtime.newExpressionAsStatement(pe));
                    commonType = commonType == null ? pe.parameterizedType()
                            : runtime.commonType(commonType, pe.parameterizedType());
                } else throw new Summary.ParseException(newContext.info(), "Expect statement, got " + ncs1.getClass());
                count++;
                entries.add(entryBuilder.setWhenExpression(whenExpression).build());
            }
        }
        assert commonType != null;
        ParameterizedType parameterizedType = commonType.mostSpecific(runtime, context.enclosingType().primaryType(),
                forwardType.type());
        return runtime.newSwitchExpressionBuilder().addComments(comments).setSource(source)
                .setParameterizedType(parameterizedType)
                .setSelector(selector)
                .addSwitchEntries(entries).build();
    }

    private Expression parseClassLiteral(Context context, ClassLiteral cl) {
        ParameterizedType pt = parsers.parseType().parse(context, cl);
        return runtime.newClassExpression(pt.typeInfo());
    }

    /*
    can be a type (scope of a method), or a variable
     */
    private Expression parseName(Context context, List<Comment> comments, Source source, String name) {
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            String trimmedAfterDot = name.substring(lastDot + 1).trim();
            if ("length".equals(trimmedAfterDot)) {
                String beforeDot = name.substring(0, lastDot);
                Variable array = parseVariable(context, comments, source, beforeDot);
                assert array != null;
                return runtime.newArrayLengthBuilder()
                        .addComments(comments).setSource(source)
                        .setExpression(runtime.newVariableExpression(array))
                        .build();
            }
            NamedType namedType = context.typeContext().get(name, false);
            if (namedType instanceof TypeInfo typeInfo) {
                ParameterizedType pt = runtime.newParameterizedType(typeInfo, 0);
                return runtime.newTypeExpression(pt, runtime.diamondNo());
            } else if (namedType instanceof TypeParameter) {
                throw new Summary.ParseException(context.info(), "?");
            }
            // since we don't have a type, we must have a variable
            // this will set the recursion going, from right to left
            Variable variable = parseVariable(context, comments, source, name);
            return runtime.newVariableExpressionBuilder().setVariable(variable).setSource(source).build();
        }
        Variable v = context.variableContext().get(name, false);
        if (v != null) {
            return runtime.newVariableExpressionBuilder().setVariable(v).setSource(source).addComments(comments).build();
        }
        if (context.enclosingType() != null) {
            Expression scope = runtime.newVariableExpression(runtime.newThis(context.enclosingType().asParameterizedType()));
            Variable v2 = findField(context, scope, name, false);
            if (v2 != null) {
                return runtime.newVariableExpressionBuilder().setVariable(v2).setSource(source).addComments(comments).build();
            }
        } // else: see for example parsing of annotation '...importhelper.a.Resources', line 6
        NamedType namedType = context.typeContext().get(name, false);
        if (namedType instanceof TypeInfo typeInfo) {
            ParameterizedType parameterizedType = runtime.newParameterizedType(typeInfo, 0);
            return runtime.newTypeExpression(parameterizedType, runtime.diamondShowAll());
        } else if (namedType instanceof TypeParameter) {
            throw new Summary.ParseException(context.info(), "should not be possible");
        }
        throw new Summary.ParseException(context.info(), "unknown name '" + name + "'");
    }

    private FieldReference findField(Context context, Expression expression, String name, boolean complain) {
        ParameterizedType pt = expression.parameterizedType();
        TypeInfo typeInfo = pt.bestTypeInfo();
        FieldInfo fieldInfo = findRecursively(typeInfo, name);
        if (fieldInfo == null) {
            if (complain) {
                throw new Summary.ParseException(context.info(),
                        "Cannot find field named '" + name + "' in hierarchy of " + pt);
            }
            return null;
        }
        // fieldInfo.type() is the formal type; we need the concrete type here (e.g. TestMethodCall1,2)
        // but the field can belong to a supertype of pt.typeInfo(), in which case .initialTypeParameterMap() is not
        // sufficient (e.g. TestMethodCall4,3)
        Map<NamedType, ParameterizedType> map;
        if (typeInfo == fieldInfo.owner()) {
            map = pt.initialTypeParameterMap();
        } else {
            // either fieldInfo.owner() is a super type of typeInfo,
            boolean ownerIsSuperType = typeInfo.superTypesExcludingJavaLangObject().contains(fieldInfo.owner());
            // or, fieldInfo.owner() is an enclosing type, typeInfo an inner type
            boolean ownerIsEnclosingType = typeInfo.primaryType().equals(fieldInfo.owner().primaryType());
            assert ownerIsSuperType || ownerIsEnclosingType;

            ParameterizedType superType = fieldInfo.owner().asParameterizedType();
            // we need to obtain a translation map to get the concrete types or type bounds
            map = context.genericsHelper().mapInTermsOfParametersOfSuperType(context.enclosingType(), superType);
        }
        ParameterizedType concreteType = map == null || map.isEmpty() ? fieldInfo.type()
                : fieldInfo.type().applyTranslation(runtime, map);
        return runtime.newFieldReference(fieldInfo, expression, concreteType);
    }

    private FieldInfo findRecursively(TypeInfo typeInfo, String name) {
        FieldInfo fieldInfo = typeInfo.getFieldByName(name, false);
        if (fieldInfo != null) return fieldInfo;
        if (typeInfo.parentClass() != null && !typeInfo.parentClass().isJavaLangObject()) {
            FieldInfo fi = findRecursively(typeInfo.parentClass().typeInfo(), name);
            if (fi != null) return fi;
        }
        if (typeInfo.compilationUnitOrEnclosingType().isRight()) {
            FieldInfo fi = findRecursively(typeInfo.compilationUnitOrEnclosingType().getRight(), name);
            if (fi != null) return fi;
        }
        if (typeInfo.enclosingMethod() != null) {
            return findRecursively(typeInfo.enclosingMethod().typeInfo(), name);
        }
        for (ParameterizedType interfaceImplemented : typeInfo.interfacesImplemented()) {
            FieldInfo fi = findRecursively(interfaceImplemented.typeInfo(), name);
            if (fi != null) return fi;
        }
        return null;
    }

    // result must be a variable
    private Variable parseVariable(Context context, List<Comment> comments, Source source, String name) {
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) {
            return context.variableContext().get(name, true);
        }
        String varName = name.substring(lastDot + 1);
        Expression expression = parseName(context, comments, source, name.substring(0, lastDot));
        return findField(context, expression, varName, true);
    }

    private Expression arrayInitializer(Context context, String index, ForwardType forwardType, List<Comment> comments,
                                        Source source, Node arrayInitializer) {
        List<Expression> expressions = new ArrayList<>();
        ParameterizedType commonType = null;
        ParameterizedType oneFewer = forwardType.type() == null ? null : forwardType.type().copyWithOneFewerArrays();
        ForwardType forwardTypeOneArrayLess = context.newForwardType(oneFewer);
        for (int i = 1; i < arrayInitializer.size(); i += 2) {
            Node ai = arrayInitializer.get(i);
            if (ai instanceof Delimiter) break; // empty expression list
            Expression e = parse(context, index, forwardTypeOneArrayLess, ai);
            expressions.add(e);
            commonType = commonType == null ? e.parameterizedType() : runtime.commonType(commonType, e.parameterizedType());
        }
        ParameterizedType type;
        if (forwardType.type() != null) {
            type = forwardType.type().copyWithOneFewerArrays();
        } else {
            type = commonType;
        }
        assert type != null;
        return runtime.newArrayInitializerBuilder()
                .setExpressions(expressions).setCommonType(type).setSource(source).addComments(comments)
                .build();
    }

    private Expression inlineConditional(Context context, String index, ForwardType forwardType, List<Comment> comments, Source source, Node node) {
        if (!node.get(1).getType().equals(HOOK)) throw new UnsupportedOperationException();
        if (!node.get(3).getType().equals(COLON)) throw new UnsupportedOperationException();

        Expression condition = parse(context, index, context.newForwardType(runtime.booleanParameterizedType()), node.get(0));
        Expression ifTrue = parse(context, index, forwardType, node.get(2));
        Expression ifFalse = parse(context, index, forwardType, node.get(4));
        return runtime.newInlineConditionalBuilder()
                .addComments(comments).setSource(source).setCondition(condition).setIfTrue(ifTrue).setIfFalse(ifFalse)
                .build(runtime);
    }

    private Assignment parseAssignment(Context context,
                                       String index,
                                       AssignmentExpression assignmentExpression,
                                       List<Comment> comments,
                                       Source source) {
        Expression wrappedTarget = parse(context, index, context.emptyForwardType(), assignmentExpression.getFirst());
        Expression target = unwrapEnclosed(wrappedTarget);
        if (!(target instanceof VariableExpression targetVE)) {
            throw new Summary.ParseException(context.info(), "Expected assignment target to be a variable expression");
        }
        ForwardType fwd = context.newForwardType(target.parameterizedType());
        Expression value = parse(context, index, fwd, assignmentExpression.get(2));
        MethodInfo assignmentOperator;
        MethodInfo binaryOperator;
        Node.NodeType tt = assignmentExpression.get(1).getType();
        switch (tt) {
            case Token.TokenType.ASSIGN -> {
                binaryOperator = null;
                assignmentOperator = null;
            }
            case Token.TokenType.PLUSASSIGN -> {
                // String s += c; with c a char, is legal!
                if (value.parameterizedType().isJavaLangString() || target.parameterizedType().isJavaLangString()) {
                    binaryOperator = runtime.plusOperatorString();
                    assignmentOperator = runtime.assignPlusOperatorString();
                } else {
                    binaryOperator = runtime.plusOperatorInt();
                    assignmentOperator = runtime.assignPlusOperatorInt();
                }
            }
            case Token.TokenType.MINUSASSIGN -> {
                binaryOperator = runtime.minusOperatorInt();
                assignmentOperator = runtime.assignMinusOperatorInt();
            }
            case Token.TokenType.STARASSIGN -> {
                binaryOperator = runtime.multiplyOperatorInt();
                assignmentOperator = runtime.assignMultiplyOperatorInt();
            }
            case Token.TokenType.SLASHASSIGN -> {
                binaryOperator = runtime.divideOperatorInt();
                assignmentOperator = runtime.assignDivideOperatorInt();
            }
            case Token.TokenType.REMASSIGN -> {
                binaryOperator = runtime.remainderOperatorInt();
                assignmentOperator = runtime.assignRemainderOperatorInt();
            }
            case Token.TokenType.XORASSIGN -> {
                binaryOperator = runtime.xorOperatorInt();
                assignmentOperator = runtime.assignXorOperatorInt();
            }
            case Token.TokenType.ANDASSIGN -> {
                binaryOperator = runtime.andOperatorInt();
                assignmentOperator = runtime.assignAndOperatorInt();
            }
            case Token.TokenType.ORASSIGN -> {
                binaryOperator = runtime.orOperatorInt();
                assignmentOperator = runtime.assignOrOperatorInt();
            }
            case Token.TokenType.RSIGNEDSHIFTASSIGN -> {
                binaryOperator = runtime.signedRightShiftOperatorInt();
                assignmentOperator = runtime.assignSignedRightShiftOperatorInt();
            }
            case Token.TokenType.RUNSIGNEDSHIFTASSIGN -> {
                binaryOperator = runtime.unsignedRightShiftOperatorInt();
                assignmentOperator = runtime.assignUnsignedRightShiftOperatorInt();
            }
            case Token.TokenType.LSHIFTASSIGN -> {
                binaryOperator = runtime.leftShiftOperatorInt();
                assignmentOperator = runtime.assignLeftShiftOperatorInt();
            }
            case null, default -> throw new UnsupportedOperationException("NYI");
        }
        return runtime.newAssignmentBuilder().setValue(value).setTarget(targetVE)
                .setBinaryOperator(binaryOperator)
                .setAssignmentOperator(assignmentOperator)
                .setAssignmentOperatorIsPlus(false)// not relevant for +=, =
                .setPrefixPrimitiveOperator(null)
                .addComments(comments).setSource(source).build();
    }

    private Expression unwrapEnclosed(Expression wrappedTarget) {
        if (wrappedTarget instanceof EnclosedExpression ee) {
            return unwrapEnclosed(ee.inner());
        }
        return wrappedTarget;
    }

    private Expression plusPlusMinMin(Context context, String index, List<Comment> comments, Source source,
                                      int expressionIndex, int opIndex, boolean pre, Node node) {
        ForwardType fwd = context.newForwardType(runtime.intParameterizedType());
        Expression target = parse(context, index, fwd, node.get(expressionIndex));
        if (!(target instanceof VariableExpression targetVE)) {
            throw new Summary.ParseException(context.info(), "Expected assignment target to be a variable expression");
        }
        MethodInfo binaryOperator;
        MethodInfo assignmentOperator;
        boolean isPlus;
        if (node.get(opIndex) instanceof Operator operator) {
            if (INCR.equals(operator.getType())) {
                isPlus = true;
                assignmentOperator = runtime.assignPlusOperatorInt();
                binaryOperator = runtime.plusOperatorInt();
            } else if (DECR.equals(operator.getType())) {
                isPlus = false;
                assignmentOperator = runtime.assignMinusOperatorInt();
                binaryOperator = runtime.minusOperatorInt();
            } else throw new UnsupportedOperationException();
            return runtime.newAssignmentBuilder().setValue(runtime.intOne()).setTarget(targetVE)
                    .setAssignmentOperator(assignmentOperator).setAssignmentOperatorIsPlus(isPlus)
                    .setBinaryOperator(binaryOperator)
                    .setPrefixPrimitiveOperator(pre)
                    .addComments(comments).setSource(source).build();
        } else throw new UnsupportedOperationException();
    }

    private Expression parseConditionalExpression(Context context, List<Comment> comments, Source source,
                                                  String index, Node e) {
        List<Expression> components = new ArrayList<>();
        ForwardType fwd = context.newForwardType(runtime.booleanParameterizedType());
        for (int i = 0; i < e.size(); i += 2) {
            Expression parsed = parse(context, index, fwd, e.get(i));
            components.add(parsed);
        }
        if (e instanceof ConditionalAndExpression) {
            return runtime.newAndBuilder()
                    .addComments(comments).setSource(source)
                    .addExpressions(components)
                    .build();
        }
        if (e instanceof ConditionalOrExpression) {
            return runtime.newOrBuilder()
                    .addComments(comments).setSource(source)
                    .addExpressions(components).build();
        }
        throw new UnsupportedOperationException();
    }

    private Expression parseParentheses(Context context, String index,
                                        List<Comment> comments, Source source,
                                        ForwardType forwardType, Parentheses p) {
        Expression e = parse(context, index, forwardType, p.getNestedExpression());
        return runtime.newEnclosedExpressionBuilder()
                .addComments(comments).setSource(source).setExpression(e)
                .build();
    }

    private Expression parseUnaryExpression(Context context, String index, Node ue) {
        MethodInfo methodInfo;
        ForwardType forwardType;
        if (ue.get(0) instanceof Operator operator) {
            methodInfo = switch (operator.getType()) {
                case PLUS -> null; // ignore!
                case MINUS -> runtime.unaryMinusOperatorInt();
                case BANG -> runtime.logicalNotOperatorBool();
                case TILDE -> runtime.bitWiseNotOperatorInt();
                default -> throw new UnsupportedOperationException();
            };
            forwardType = context.newForwardType(switch (operator.getType()) {
                case PLUS, TILDE, MINUS -> runtime.intParameterizedType();
                case BANG -> runtime.booleanParameterizedType();
                default -> throw new UnsupportedOperationException();
            });
        } else throw new UnsupportedOperationException();
        Expression expression = parse(context, index, forwardType, ue.get(1));
        if (methodInfo == null) {
            return expression;
        }
        return runtime.newUnaryOperator(methodInfo, expression, runtime.precedenceUnary());
    }

    private Expression parseDotName(Context context, List<Comment> comments, Source source, String index, Node dotName) {
        String name = dotName.get(2).getSource();
        Expression scope;
        FieldReference fr;
        Node n0 = dotName.getFirst();
        if (n0 instanceof LiteralExpression le) {
            if ("this".equals(le.getAsString())) {
                scope = runtime.newVariableExpression(runtime.newThis(context.enclosingType().asParameterizedType()));
                fr = findField(context, scope, name, true);
            } else throw new UnsupportedOperationException("NYI");
        } else {
            scope = parse(context, index, context.emptyForwardType(), n0);
            if ("length".equals(name)) {
                if (scope.parameterizedType().arrays() == 0) throw new UnsupportedOperationException();
                return runtime.newArrayLengthBuilder().setExpression(scope)
                        .addComments(comments).setSource(source)
                        .build();
            }
            fr = findField(context, scope, name, true);
        }
        return runtime.newVariableExpressionBuilder()
                .addComments(comments)
                .setSource(source)
                .setVariable(fr)
                .build();
    }

    private Cast parseCast(Context context, String index, List<Comment> comments, Source source, CastExpression castExpression) {
        // 0 = '(', 2 = ')'
        ParameterizedType pt = parsers.parseType().parse(context, castExpression.get(1));
        // can technically be anything
        ForwardType fwd = context.newForwardType(pt);
        Expression expression = parse(context, index, fwd, castExpression.get(3));
        return runtime.newCastBuilder().setExpression(expression).setParameterizedType(pt)
                .setSource(source).addComments(comments).build();
    }

    private Expression parseAdditive(Context context, String index, AdditiveExpression ae) {
        Node.NodeType token1 = ae.get(1).getType();
        ForwardType fwd1;
        if (token1.equals(MINUS)) {
            fwd1 = context.newForwardType(runtime.intParameterizedType());
        } else {
            // for plus, we could have either string, or int; with string, all bets are off
            fwd1 = context.emptyForwardType();
        }
        Expression accumulated = parse(context, index, fwd1, ae.getFirst());
        int i = 2;
        while (i < ae.size()) {
            Node.NodeType token = ae.get(i - 1).getType();
            ForwardType fwt;
            if (token.equals(MINUS)) {
                fwt = context.newForwardType(runtime.intParameterizedType());
            } else {
                // for plus, we could have either string, or int; with string, all bets are off
                fwt = context.emptyForwardType();
            }

            Expression rhs = parse(context, index, fwt, ae.get(i));
            MethodInfo operator;
            if (token.equals(PLUS)) {
                if (accumulated.parameterizedType().isJavaLangString() || rhs.parameterizedType().isJavaLangString()) {
                    operator = runtime.plusOperatorString();
                } else {
                    operator = runtime.plusOperatorInt();
                }
            } else if (token.equals(MINUS)) {
                operator = runtime.minusOperatorInt();
            } else {
                throw new UnsupportedOperationException();
            }
            ParameterizedType pt = runtime.widestTypeUnbox(accumulated.parameterizedType(), rhs.parameterizedType());
            accumulated = runtime.newBinaryOperatorBuilder()
                    .setOperator(operator)
                    .setLhs(accumulated).setRhs(rhs)
                    .setParameterizedType(pt)
                    .setPrecedence(runtime.precedenceAdditive())
                    .setSource(source(context.info(), index, ae))
                    .addComments(comments(ae))
                    .build();
            i += 2;
        }
        return accumulated;
    }

    private Expression parseMultiplicative(Context context, String index, Node me) {
        ForwardType fwd = context.newForwardType(runtime.intParameterizedType());
        Expression accumulated = parse(context, index, fwd, me.getFirst());
        int i = 2;
        while (i < me.size()) {
            Expression rhs = parse(context, index, fwd, me.get(i));
            Node.NodeType token = me.get(i - 1).getType();
            MethodInfo operator;
            Precedence precedence;
            switch (token) {
                case Token.TokenType.STAR -> {
                    operator = runtime.multiplyOperatorInt();
                    precedence = runtime.precedenceMultiplicative();
                }
                case Token.TokenType.SLASH -> {
                    operator = runtime.divideOperatorInt();
                    precedence = runtime.precedenceMultiplicative();
                }
                case Token.TokenType.REM -> {
                    precedence = runtime.precedenceMultiplicative();
                    operator = runtime.remainderOperatorInt();
                }
                case Token.TokenType.BIT_AND -> {
                    precedence = runtime.precedenceBitwiseAnd();
                    operator = runtime.andOperatorInt();
                }
                case Token.TokenType.BIT_OR -> {
                    precedence = runtime.precedenceBitwiseOr();
                    operator = runtime.orOperatorInt();
                }
                case Token.TokenType.XOR -> {
                    precedence = runtime.precedenceBitwiseXor();
                    operator = runtime.xorOperatorInt();
                }
                case Token.TokenType.LSHIFT -> {
                    precedence = runtime.precedenceShift();
                    operator = runtime.leftShiftOperatorInt();
                }
                case Token.TokenType.RSIGNEDSHIFT -> {
                    precedence = runtime.precedenceShift();
                    operator = runtime.signedRightShiftOperatorInt();
                }
                case Token.TokenType.RUNSIGNEDSHIFT -> {
                    precedence = runtime.precedenceShift();
                    operator = runtime.unsignedRightShiftOperatorInt();
                }
                default -> throw new UnsupportedOperationException();
            }
            ParameterizedType pt = runtime.widestTypeUnbox(accumulated.parameterizedType(), rhs.parameterizedType());
            accumulated = runtime.newBinaryOperatorBuilder()
                    .setOperator(operator)
                    .setLhs(accumulated).setRhs(rhs)
                    .setParameterizedType(pt)
                    .setPrecedence(precedence)
                    .setSource(source(context.info(), index, me))
                    .addComments(comments(me))
                    .build();
            i += 2;
        }
        return accumulated;
    }

    private Expression parseRelational(Context context, String index, RelationalExpression re) {
        ForwardType fwd = context.newForwardType(runtime.intParameterizedType());
        Expression lhs = parse(context, index, fwd, re.get(0));
        Expression rhs = parse(context, index, fwd, re.get(2));
        Node.NodeType token = re.get(1).getType();
        MethodInfo operator = switch (token) {
            case Token.TokenType.LE -> runtime.lessEqualsOperatorInt();
            case Token.TokenType.LT -> runtime.lessOperatorInt();
            case Token.TokenType.GE -> runtime.greaterEqualsOperatorInt();
            case Token.TokenType.GT -> runtime.greaterOperatorInt();
            default -> throw new UnsupportedOperationException();
        };
        return runtime.newBinaryOperatorBuilder()
                .setOperator(operator)
                .setLhs(lhs).setRhs(rhs)
                .setParameterizedType(runtime.booleanParameterizedType())
                .setPrecedence(runtime.precedenceRelational())
                .setSource(source(context.info(), index, re))
                .addComments(comments(re))
                .build();
    }

    private Expression parseEquality(Context context, String index, EqualityExpression eq) {
        ForwardType forwardType = context.emptyForwardType();
        Expression lhs = parse(context, index, forwardType, eq.get(0));
        Expression rhs = parse(context, index, forwardType, eq.get(2));
        Node.NodeType token = eq.get(1).getType();
        MethodInfo operator;
        boolean isNumeric = lhs.isNumeric() && rhs.isNumeric();
        if (token.equals(EQ)) {
            operator = isNumeric ? runtime.equalsOperatorInt() : runtime.equalsOperatorObject();
        } else if (token.equals(NE)) {
            operator = isNumeric ? runtime.notEqualsOperatorInt() : runtime.notEqualsOperatorObject();
        } else throw new UnsupportedOperationException();
        return runtime.newBinaryOperatorBuilder()
                .setOperator(operator)
                .setLhs(lhs).setRhs(rhs)
                .setParameterizedType(runtime.booleanParameterizedType())
                .setPrecedence(runtime.precedenceEquality())
                .setSource(source(context.info(), index, eq))
                .addComments(comments(eq))
                .build();
    }

    private static final long TWO32 = 0xFF_FF_FF_FFL;

    private Expression parseLiteral(Context context, LiteralExpression le) {
        Node child = le.children().getFirst();
        return switch (child) {
            case BooleanLiteral bl -> runtime.newBoolean("true".equals(bl.getSource()));
            case CharacterLiteral cl -> runtime.newChar(computeCharacterLiteral(cl));
            case FloatingPointLiteral fp -> {
                String src = fp.getSource().toLowerCase().replace("_", "");
                if (src.endsWith("f") || src.endsWith("F")) {
                    yield runtime.newFloat(Float.parseFloat(src.substring(0, src.length() - 1)));
                }
                if (src.endsWith("d") || src.endsWith("D")) {
                    yield runtime.newDouble(Double.parseDouble(src.substring(0, src.length() - 1)));
                }
                yield runtime.newDouble(Double.parseDouble(src));
            }
            case IntegerLiteral il -> {
                long l = parseLong(il.getSource());
                if (l <= Integer.MAX_VALUE) {
                    yield runtime.newInt((int) l);
                }
                if (l <= TWO32) {
                    long complement = ((~l) & TWO32);
                    int negative = (int) (-(complement + 1));
                    yield runtime.newInt(negative);
                }
                throw new UnsupportedOperationException();
            }
            case LongLiteral ll -> runtime.newLong(parseLong(ll.getSource()));
            case NullLiteral nl -> runtime.nullConstant();
            case StringLiteral sl -> runtime.newStringConstant(sl.getString());
            case ThisLiteral tl ->
                    runtime.newVariableExpression(runtime.newThis(context.enclosingType().asParameterizedType()));
            default -> {
                if (!le.children().isEmpty() && le.getFirst() instanceof KeyWord kw && SUPER.equals(kw.getType())) {
                    yield runtime.newVariableExpression(runtime.newThis(
                            context.enclosingType().parentClass().typeInfo().asParameterizedType(),
                            null, true));
                }
                throw new UnsupportedOperationException("literal expression " + le.getClass());
            }
        };
    }

    private static char computeCharacterLiteral(CharacterLiteral cl) {
        char c = cl.charAt(1);
        if (c == '\\') {
            char c2 = cl.charAt(2);
            return switch (c2) {
                case '0' -> '\0';
                case 'b' -> '\b';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'n' -> '\n';
                case 'f' -> '\f';
                case '\'' -> '\'';
                case '\\' -> '\\';
                case '"' -> '"';
                default -> throw new UnsupportedOperationException();
            };
        }
        return c;
    }

    private static long parseLong(String s) {
        String src = s.toLowerCase().replace("_", "").replace("l", "");
        if (src.startsWith("0x")) {
            return Long.parseLong(src.substring(2), 16);
        }
        if (src.startsWith("0") && src.length() > 1 && Character.isDigit(src.charAt(1))) {
            return Long.parseLong(src.substring(1), 8);
        }
        return Long.parseLong(src);
    }
}
