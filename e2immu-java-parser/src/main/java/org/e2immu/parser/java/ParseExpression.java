package org.e2immu.parser.java;

import org.e2immu.cstapi.element.Comment;
import org.e2immu.cstapi.element.Source;
import org.e2immu.cstapi.expression.*;
import org.e2immu.cstapi.expression.Expression;
import org.e2immu.cstapi.info.FieldInfo;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.cstapi.variable.FieldReference;
import org.e2immu.cstapi.variable.Variable;
import org.e2immu.parserapi.Context;
import org.e2immu.parserapi.ForwardType;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.parsers.java.ast.MethodCall;
import org.parsers.java.ast.MethodReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.parsers.java.Token.TokenType.*;

public class ParseExpression extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseExpression.class);
    private final ParseMethodCall parseMethodCall;
    private final ParseMethodReference parseMethodReference;
    private final ParseConstructorCall parseConstructorCall;
    private final ParseLambdaExpression parseLambdaExpression;
    private final ParseType parseType;

    public ParseExpression(Runtime runtime) {
        super(runtime);
        parseType = new ParseType(runtime);
        parseMethodCall = new ParseMethodCall(runtime, this);
        parseConstructorCall = new ParseConstructorCall(runtime, this);
        parseMethodReference = new ParseMethodReference(runtime, this, parseType);
        parseLambdaExpression = new ParseLambdaExpression(runtime, this);
    }

    public Expression parse(Context context, String index, ForwardType forwardType, Node node) {
        try {
            return internalParse(context, index, forwardType, node);
        } catch (Throwable t) {
            LOGGER.error("Caught exception parsing expression at line {}, pos {}", node.getBeginLine(), node.getBeginColumn());
            throw t;
        }
    }

    private Expression internalParse(Context context, String index, ForwardType forwardType, Node node) {
        Source source = source(context.info(), index, node);
        List<Comment> comments = comments(node);

        if (node instanceof DotName dotName) {
            return parseDotName(context, index, dotName);
        }
        if (node instanceof MethodCall mc) {
            return parseMethodCall.parse(context, index, mc);
        }
        if (node instanceof LiteralExpression le) {
            return parseLiteral(le);
        }
        if (node instanceof ConditionalAndExpression || node instanceof ConditionalOrExpression) {
            return parseConditionalExpression(context, comments, source, index, (org.parsers.java.ast.Expression) node);
        }
        if (node instanceof MultiplicativeExpression me) {
            return parseMultiplicative(context, index, me);
        }
        if (node instanceof AdditiveExpression ae) {
            return parseAdditive(context, index, ae);
        }
        if (node instanceof RelationalExpression re) {
            return parseRelational(context, index, re);
        }
        if (node instanceof EqualityExpression eq) {
            return parseEquality(context, index, eq);
        }
        if (node instanceof UnaryExpression || node instanceof UnaryExpressionNotPlusMinus) {
            return parseUnaryExpression(context, index, (org.parsers.java.ast.Expression) node);
        }
        if (node instanceof Name name) {
            String nameAsString = name.getAsString();
            if (nameAsString.endsWith(".length")) {
                Variable array = parseVariable(context, nameAsString.substring(0, nameAsString.length() - 7));
                assert array != null;
                return runtime.newArrayLength(runtime.newVariableExpression(array));
            }
            Variable v = parseVariable(context, nameAsString);
            return runtime.newVariableExpressionBuilder().setVariable(v).setSource(source).addComments(comments).build();
        }
        if (node instanceof CastExpression castExpression) {
            return parseCast(context, index, comments, source, castExpression);
        }
        if (node instanceof AssignmentExpression assignmentExpression) {
            return parseAssignment(context, index, assignmentExpression, comments, source);
        }
        if (node instanceof ArrayAccess arrayAccess) {
            assert arrayAccess.size() == 4 : "Not implemented";
            Expression ae = parse(context, index, forwardType.withMustBeArray(), arrayAccess.get(0));
            ForwardType fwdInt = context.newForwardType(runtime.intParameterizedType());
            Expression ie = parse(context, index, fwdInt, arrayAccess.get(2));
            Variable variable = runtime.newDependentVariable(ae, ie);
            return runtime.newVariableExpressionBuilder().addComments(comments).setSource(source)
                    .setVariable(variable).build();
        }
        if (node instanceof Parentheses p) {
            return parseParentheses(context, index, forwardType, p);
        }
        if (node instanceof AllocationExpression ae) {
            return parseConstructorCall.parse(context, index, ae);
        }
        if (node instanceof MethodReference mr) {
            return parseMethodReference.parse(context, comments, source, index, mr);
        }
        if (node instanceof LambdaExpression le) {
            return parseLambdaExpression.parse(context, comments, source, index, forwardType, le);
        }
        if (node instanceof PostfixExpression) {
            return plusPlusMinMin(context, index, comments, source, 0, 1, false, node);
        }
        if (node instanceof PreIncrementExpression || node instanceof PreDecrementExpression) {
            return plusPlusMinMin(context, index, comments, source, 1, 0, true, node);
        }
        if (node instanceof TernaryExpression) {
            return inlineConditional(context, index, forwardType, comments, source, node);
        }
        throw new UnsupportedOperationException("node " + node.getClass());
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
        Expression target = parse(context, index, context.emptyForwardType(), assignmentExpression.get(0));
        ForwardType fwd = context.newForwardType(target.parameterizedType());
        Expression value = parse(context, index, fwd, assignmentExpression.get(2));
        MethodInfo assignmentOperator;
        MethodInfo binaryOperator;
        Node.NodeType tt = assignmentExpression.get(1).getType();
        if (ASSIGN.equals(tt)) {
            binaryOperator = null;
            assignmentOperator = null;
        } else if (Token.TokenType.PLUSASSIGN.equals(tt)) {
            binaryOperator = runtime.plusOperatorInt();
            assignmentOperator = runtime.assignPlusOperatorInt();
        } else if (MINUSASSIGN.equals(tt)) {
            binaryOperator = runtime.minusOperatorInt();
            assignmentOperator = runtime.assignMinusOperatorInt();
        } else if (STARASSIGN.equals(tt)) {
            binaryOperator = runtime.multiplyOperatorInt();
            assignmentOperator = runtime.assignMultiplyOperatorInt();
        } else if (SLASHASSIGN.equals(tt)) {
            binaryOperator = runtime.divideOperatorInt();
            assignmentOperator = runtime.assignDivideOperatorInt();
        } else throw new UnsupportedOperationException("NYI");
        return runtime.newAssignmentBuilder().setValue(value).setTarget(target)
                .setBinaryOperator(binaryOperator)
                .setAssignmentOperator(assignmentOperator)
                .setAssignmentOperatorIsPlus(false)// not relevant for +=, =
                .setPrefixPrimitiveOperator(null)
                .addComments(comments).setSource(source).build();
    }

    private Expression plusPlusMinMin(Context context, String index, List<Comment> comments, Source source,
                                      int expressionIndex, int opIndex, boolean pre, Node node) {
        ForwardType fwd = context.newForwardType(runtime.intParameterizedType());
        Expression target = parse(context, index, fwd, node.get(expressionIndex));
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
            return runtime.newAssignmentBuilder().setValue(runtime.intOne()).setTarget(target)
                    .setAssignmentOperator(assignmentOperator).setAssignmentOperatorIsPlus(isPlus)
                    .setBinaryOperator(binaryOperator)
                    .setPrefixPrimitiveOperator(pre)
                    .addComments(comments).setSource(source).build();
        } else throw new UnsupportedOperationException();
    }

    private Expression parseConditionalExpression(Context context, List<Comment> comments, Source source,
                                                  String index, org.parsers.java.ast.Expression e) {

        List<Expression> components = new ArrayList<>();
        ForwardType fwd = context.newForwardType(runtime.booleanParameterizedType());
        for (int i = 0; i < e.size(); i += 2) {
            Expression parsed = parse(context, index, fwd, e.get(i));
            components.add(parsed);
        }
        if (e instanceof ConditionalAndExpression) {
            return runtime.and(components);
        }
        if (e instanceof ConditionalOrExpression) {
            return runtime.or(components);
        }
        throw new UnsupportedOperationException();
    }

    private Expression parseParentheses(Context context, String index, ForwardType forwardType, Parentheses p) {
        Expression e = parse(context, index, forwardType, p.getNestedExpression());
        return runtime.newEnclosedExpression(e);
    }

    private Expression parseUnaryExpression(Context context, String index, org.parsers.java.ast.Expression ue) {
        MethodInfo methodInfo;
        ForwardType forwardType;
        if (ue.get(0) instanceof Operator operator) {
            methodInfo = switch (operator.getType()) {
                case PLUS -> null; // ignore!
                case MINUS -> runtime.minusOperatorInt();
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

    private VariableExpression parseDotName(Context context, String index, DotName dotName) {
        String name = dotName.get(2).getSource();
        Expression scope;
        FieldInfo fieldInfo;
        Node n0 = dotName.get(0);
        if (n0 instanceof LiteralExpression le) {
            if ("this".equals(le.getAsString())) {
                scope = runtime.newVariableExpression(runtime.newThis(context.enclosingType()));
                fieldInfo = context.enclosingType().getFieldByName(name, true);
            } else throw new UnsupportedOperationException("NYI");
        } else {
            scope = parse(context, index, context.emptyForwardType(), n0);
            throw new UnsupportedOperationException();
        }
        FieldReference fr = runtime.newFieldReference(fieldInfo, scope, fieldInfo.type()); // FIXME generics
        return runtime.newVariableExpression(fr);
    }

    private Cast parseCast(Context context, String index, List<Comment> comments, Source source, CastExpression castExpression) {
        // 0 = '(', 2 = ')'
        ParameterizedType pt = parseType.parse(context, castExpression.get(1));
        // can technically be anything
        ForwardType fwd = context.newForwardType(runtime.objectParameterizedType());
        Expression expression = parse(context, index, fwd, castExpression.get(3));
        return runtime.newCast(expression, pt);
    }

    private Variable parseVariable(Context context, String name) {
        return context.variableContext().get(name, true);
    }

    private Expression parseAdditive(Context context, String index, AdditiveExpression ae) {
        Node.NodeType token = ae.get(1).getType();

        ForwardType forwardType;
        if (token.equals(MINUS)) {
            forwardType = context.newForwardType(runtime.intParameterizedType());
        } else {
            // for plus, we could have either string, or int; with string, all bets are off
            forwardType = context.emptyForwardType();
        }
        Expression accumulated = parse(context, index, forwardType, ae.get(0));
        int i = 2;
        while (i < ae.size()) {
            Expression rhs = parse(context, index, forwardType, ae.get(i));
            MethodInfo operator;
            if (token.equals(PLUS)) {
                operator = runtime.plusOperatorInt();
            } else if (token.equals(MINUS)) {
                operator = runtime.minusOperatorInt();
            } else {
                throw new UnsupportedOperationException();
            }
            ParameterizedType pt = runtime.widestType(accumulated.parameterizedType(), rhs.parameterizedType());
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

    private Expression parseMultiplicative(Context context, String index, MultiplicativeExpression me) {
        ForwardType fwd = context.newForwardType(runtime.intParameterizedType());
        Expression accumulated = parse(context, index, fwd, me.get(0));
        int i = 2;
        while (i < me.size()) {
            Expression rhs = parse(context, index, fwd, me.get(i));
            Node.NodeType token = me.get(1).getType();
            MethodInfo operator;
            if (token.equals(STAR)) {
                operator = runtime.multiplyOperatorInt();
            } else if (token.equals(SLASH)) {
                operator = runtime.divideOperatorInt();
            } else if (token.equals(REM)) {
                operator = runtime.remainderOperatorInt();
            } else {
                throw new UnsupportedOperationException();
            }
            ParameterizedType pt = runtime.widestType(accumulated.parameterizedType(), rhs.parameterizedType());
            accumulated = runtime.newBinaryOperatorBuilder()
                    .setOperator(operator)
                    .setLhs(accumulated).setRhs(rhs)
                    .setParameterizedType(pt)
                    .setPrecedence(runtime.precedenceMultiplicative())
                    .setSource(source(context.info(), index, me))
                    .addComments(comments(me))
                    .build();
            i++;
        }
        return accumulated;
    }

    private Expression parseRelational(Context context, String index, RelationalExpression re) {
        ForwardType fwd = context.newForwardType(runtime.intParameterizedType());
        Expression lhs = parse(context, index, fwd, re.get(0));
        Expression rhs = parse(context, index, fwd, re.get(2));
        Node.NodeType token = re.get(1).getType();
        MethodInfo operator;
        if (token.equals(LE)) {
            operator = runtime.lessEqualsOperatorInt();
        } else if (token.equals(LT)) {
            operator = runtime.lessOperatorInt();
        } else if (token.equals(GE)) {
            operator = runtime.greaterEqualsOperatorInt();
        } else if (token.equals(GT)) {
            operator = runtime.greaterOperatorInt();
        } else {
            throw new UnsupportedOperationException();
        }
        return runtime.newBinaryOperatorBuilder()
                .setOperator(operator)
                .setLhs(lhs).setRhs(rhs)
                .setParameterizedType(runtime.booleanParameterizedType())
                .setPrecedence(runtime.precedenceMultiplicative())
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
        boolean isNumeric = lhs.isNumeric();
        if (token.equals(EQ)) {
            operator = isNumeric ? runtime.equalsOperatorInt() : runtime.equalsOperatorObject();
        } else if (token.equals(NE)) {
            operator = isNumeric ? runtime.notEqualsOperatorInt() : runtime.notEqualsOperatorObject();
        } else throw new UnsupportedOperationException();
        return runtime.newBinaryOperatorBuilder()
                .setOperator(operator)
                .setLhs(lhs).setRhs(rhs)
                .setParameterizedType(runtime.booleanParameterizedType())
                .setPrecedence(runtime.precedenceMultiplicative())
                .setSource(source(context.info(), index, eq))
                .addComments(comments(eq))
                .build();
    }

    private Expression parseLiteral(LiteralExpression le) {
        Node child = le.children().get(0);
        if (child instanceof IntegerLiteral il) {
            return runtime.newInt(il.getValue());
        }
        if (child instanceof BooleanLiteral bl) {
            return runtime.newBoolean("true".equals(bl.getSource()));
        }
        if (child instanceof CharacterLiteral cl) {
            char c = cl.charAt(1);
            if (c == '\\') {
                char c2 = cl.charAt(2);
                c = switch (c2) {
                    case 'b' -> '\b';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'f' -> '\f';
                    case '\'' -> '\'';
                    case '\\' -> '\\';
                    default -> throw new UnsupportedOperationException();
                };
            }
            return runtime.newChar(c);
        }
        if (child instanceof StringLiteral sl) {
            return runtime.newStringConstant(sl.getString());
        }
        throw new UnsupportedOperationException("literal expression " + le.getClass());
    }
}
