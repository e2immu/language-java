package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.expression.Lambda;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.Statement;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.parser.*;
import org.e2immu.parser.java.erasure.LambdaErasure;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ParseLambdaExpression extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseLambdaExpression.class);

    public ParseLambdaExpression(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public Expression parse(Context context,
                            List<Comment> comments,
                            Source source,
                            String index,
                            ForwardType forwardType,
                            LambdaExpression le) {
        if (forwardType.erasure()) {
            return parseInErasureMode(context, source, le);
        }

        MethodTypeParameterMap singleAbstractMethod = forwardType.computeSAM(runtime, context.genericsHelper(),
                context.enclosingType());

        Lambda.Builder builder = runtime.newLambdaBuilder();

        List<Lambda.OutputVariant> outputVariants = new ArrayList<>();

        int typeIndex = context.anonymousTypeCounters().newIndex(context.enclosingType());
        TypeInfo anonymousType = runtime.newAnonymousType(context.enclosingType(), typeIndex);
        anonymousType.builder()
                .setTypeNature(runtime.typeNatureClass())
                .setParentClass(runtime.objectParameterizedType());

        MethodInfo sam = singleAbstractMethod.methodInfo();
        MethodInfo methodInfo = runtime.newMethod(anonymousType, sam.name(), runtime.methodTypeMethod());
        MethodInfo.Builder miBuilder = methodInfo.builder();

        parseParameters(context, forwardType, le, miBuilder, outputVariants, context);
        Context newContext = context.newLambdaContext(methodInfo);
        methodInfo.parameters().forEach(newContext.variableContext()::add);
        ParameterizedType returnTypeOfLambda = singleAbstractMethod.getConcreteReturnType(runtime);

        // add all formal -> concrete of the parameters of the SAM, without the return type
        Map<NamedType, ParameterizedType> extra = new HashMap<>();
        methodInfo.parameters().forEach(pi -> {
            Map<NamedType, ParameterizedType> map = pi.parameterizedType().initialTypeParameterMap(runtime);
            extra.putAll(map);
        });
        ForwardType newForward = context.newForwardType(returnTypeOfLambda, false, extra);

        // start evaluation

        ParameterizedType concreteReturnType;
        Block methodBody;
        Node le1 = le.get(1);
        if (le1 instanceof org.parsers.java.ast.Expression e) {
            // simple function or supplier
            Expression expression = parsers.parseExpression().parse(newContext, index, newForward, e);
            concreteReturnType = expression.parameterizedType();
            Statement returnStatement = runtime.newReturnStatement(expression);
            methodBody = runtime.newBlockBuilder().addStatement(returnStatement).build();
            // returns either java.util.function.Function<T,R> or java.util.function.Supplier<R>
            boolean hasReturnValue = !concreteReturnType.isVoid();
            TypeInfo abstractFunctionalType = runtime.syntheticFunctionalType(methodInfo.parameters().size(),
                    hasReturnValue);
            List<ParameterizedType> concreteFtParams = new ArrayList<>();
            for (ParameterInfo pi : methodInfo.parameters()) {
                concreteFtParams.add(pi.parameterizedType());
            }
            if (hasReturnValue) {
                concreteFtParams.add(concreteReturnType);
            }
            ParameterizedType concreteFunctionalType = runtime.newParameterizedType(abstractFunctionalType, concreteFtParams);
            // new we have  "class $1 implements Function<Integer, String>"
            anonymousType.builder().addInterfaceImplemented(concreteFunctionalType);
        } else if (le1 instanceof CodeBlock codeBlock) {
            methodBody = parsers.parseBlock().parse(newContext, "", null, codeBlock);
            concreteReturnType = mostSpecificReturnType(context.enclosingType(), methodBody);
        } else throw new Summary.ParseException(context.info(), "Expected either expression or code block");


        miBuilder.setAccess(runtime.accessPrivate());
        miBuilder.setSynthetic(true);
        miBuilder.setMethodBody(methodBody);
        miBuilder.setReturnType(concreteReturnType);
        miBuilder.commit();

        List<ParameterizedType> types = methodInfo.parameters().stream().map(ParameterInfo::parameterizedType).toList();
        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(runtime, types, concreteReturnType);

        anonymousType.builder()
                .addMethod(methodInfo)
                .addInterfaceImplemented(functionalType)
                .setEnclosingMethod(context.enclosingMethod())
                .commit();

        return builder
                .setMethodInfo(methodInfo)
                .setOutputVariants(outputVariants)
                .addComments(comments)
                .setSource(source)
                .build();
    }

    private ParameterizedType mostSpecificReturnType(TypeInfo primaryType, Block methodBody) {
        AtomicReference<ParameterizedType> mostSpecific = new AtomicReference<>();
        methodBody.visit(statement -> {
            if (statement instanceof org.e2immu.language.cst.api.statement.ReturnStatement returnStatement) {
                Expression expression = returnStatement.expression();
                if (expression.isEmpty()) {
                    mostSpecific.set(runtime.voidParameterizedType());
                } else if (expression.isNullConstant()) {
                    if (mostSpecific.get() == null) {
                        mostSpecific.set(runtime.objectParameterizedType());
                    }
                } else {
                    ParameterizedType returnType = expression.parameterizedType();
                    mostSpecific.set(mostSpecific.get() == null ? returnType : mostSpecific.get()
                            .mostSpecific(runtime, primaryType, returnType));
                }
                return false;
            }
            return true;
        });
        return mostSpecific.get() == null ? runtime.voidParameterizedType() : mostSpecific.get();
    }

    private void parseParameters(Context context,
                                 ForwardType forwardType,
                                 LambdaExpression le,
                                 MethodInfo.Builder miBuilder,
                                 List<Lambda.OutputVariant> outputVariants,
                                 Context newContext) {
        if (!(le.get(0) instanceof LambdaLHS lhs)) {
            throw new Summary.ParseException(context.info(), "Expected lambda lhs");
        }
        Node lhs0 = lhs.get(0);
        if (lhs0 instanceof Identifier identifier) {
            // single variable, no type given. we must extract it from the forward type, which must be a functional interface
            ParameterizedType type = forwardType.type().parameters().get(0).ensureBoxed(runtime);
            String parameterName = identifier.getSource();
            ParameterInfo pi = miBuilder.addParameter(parameterName, type);
            outputVariants.add(runtime.lambdaOutputVariantEmpty());
            pi.builder().commit();
            newContext.variableContext().add(pi);
        } else if (lhs0 instanceof LambdaParameters lambdaParameters) {
            assert lambdaParameters.get(0) instanceof Delimiter d && Token.TokenType.LPAREN.equals(d.getType());
            // we have a list of parameters, with type
            int i = 1;
            while (i < lambdaParameters.size()) {
                if (lambdaParameters.get(i) instanceof LambdaParameter lp) {
                    ParameterizedType type;
                    Lambda.OutputVariant outputVariant;
                    if (lp.get(0) instanceof KeyWord kw && Token.TokenType.VAR.equals(kw.getType())) {
                        type = forwardType.type().parameters().get(1);
                        outputVariant = runtime.lambdaOutputVariantEmpty();
                    } else {
                        type = parsers.parseType().parse(context, lp.get(0));
                        outputVariant = runtime.lambdaOutputVariantTyped();
                    }
                    Identifier identifier = (Identifier) lp.get(1);
                    ParameterInfo pi = miBuilder.addParameter(identifier.getSource(), type.ensureBoxed(runtime));
                    outputVariants.add(outputVariant);
                    pi.builder().commit();
                    newContext.variableContext().add(pi);
                } else if (lambdaParameters.get(i) instanceof Delimiter) {
                    break;
                } else throw new Summary.ParseException(context.info(), "Expected LambdaParameter");
                if (Token.TokenType.RPAREN.equals(lambdaParameters.get(i + 1).getType())) break;
                i += 2;
            }
        } else {
            assert lhs0 instanceof Delimiter d && Token.TokenType.LPAREN.equals(d.getType());
            // we have a list of parameters, without type
            int i = 1;
            int paramIndex = 0;
            while (i < lhs.size()) {
                if (lhs.get(i) instanceof Identifier identifier) {
                    // FIXME we should take the types of the parameters of the sam, not of the forward type!!!
                    ParameterizedType type = forwardType.type().parameters().get(paramIndex).ensureBoxed(runtime);
                    String parameterName = identifier.getSource();
                    ParameterInfo pi = miBuilder.addParameter(parameterName, type);
                    outputVariants.add(runtime.lambdaOutputVariantEmpty());
                    pi.builder().commit();
                    newContext.variableContext().add(pi);
                } else throw new Summary.ParseException(context.info(), "Expected identifier");
                if (Token.TokenType.RPAREN.equals(lhs.get(i + 1).getType())) break;
                paramIndex++;
                i += 2;
            }
        }
        miBuilder.commitParameters();
    }

    private enum IsVoid {NO_IDEA, YES, NO, ESCAPE}

    private Expression parseInErasureMode(Context context, Source source, LambdaExpression le) {
        int numParameters;
        if (le.get(0) instanceof LambdaLHS lhs) {
            numParameters = countParameters(lhs);
        } else throw new Summary.ParseException(context.info(), "Expected LambdaLHS");
        Set<MethodResolution.Count> erasures;
        IsVoid isVoid = computeIsVoid(le);
        if (isVoid == IsVoid.NO_IDEA || isVoid == IsVoid.ESCAPE) {
            erasures = Set.of(
                    new MethodResolution.Count(numParameters, true),
                    new MethodResolution.Count(numParameters, false));
        } else {
            erasures = Set.of(new MethodResolution.Count(numParameters, isVoid == IsVoid.YES));
        }
        LOGGER.debug("Returning erasure {}", erasures);
        return new LambdaErasure(runtime, erasures, source);
    }

    private int countParameters(LambdaLHS lhs) {
        if (lhs.get(0) instanceof Identifier) return 1;
        if (lhs.get(0) instanceof LambdaParameters lp) {
            // () ->
            if (Token.TokenType.RPAREN.equals(lp.get(1).getType())) return 0;
            return (int) lp.stream().filter(n -> Token.TokenType.COMMA.equals(n.getType())).count() + 1;
        }
        // () ->
        if (Token.TokenType.RPAREN.equals(lhs.get(1).getType())) return 0;
        return (int) lhs.stream().filter(n -> Token.TokenType.COMMA.equals(n.getType())).count() + 1;
    }

    private IsVoid computeIsVoid(LambdaExpression le) {
        if (le.get(1) instanceof org.parsers.java.ast.Expression e) {
            // void expressions: void method call, but we don't have the machinery yet... luckily,
            // the Java compiler cannot handle this neither IF in direct competition (see MethodCall_25, but also _7)
            if (e instanceof MethodCall) return IsVoid.NO_IDEA;
            return IsVoid.NO;
        }
        if (le.get(1) instanceof CodeBlock codeBlock) {
            return recursiveComputeIsVoid(codeBlock);
        }
        throw new UnsupportedOperationException("? either block or expression");
    }

    private static IsVoid recursiveComputeIsVoid(Node cb) {
        int i = cb.size() - 1;
        while (i >= 0) {
            Node ni = cb.get(i);
            if (ni instanceof ReturnStatement rs) {
                return rs.get(1) instanceof Delimiter ? IsVoid.YES : IsVoid.NO;
            }
            if (ni instanceof CodeBlock cb2) {
                return recursiveComputeIsVoid(cb2);
            }
            if (!(ni instanceof ThrowStatement) && !(ni instanceof Delimiter)) {
                if (ni instanceof StatementExpression || ni instanceof ExpressionStatement) {
                    return IsVoid.YES;
                }
                if (ni instanceof IfStatement) {
                    IsVoid iv;
                    if (ni.get(4) instanceof CodeBlock cb2) {
                        iv = recursiveComputeIsVoid(cb2);
                    } else throw new UnsupportedOperationException();
                    if (iv != null) return iv;
                    if (ni.size() > 6 && ni.get(6) instanceof CodeBlock cb3) {
                        return recursiveComputeIsVoid(cb3);
                    }
                }
                if(ni instanceof TryStatement) {
                    CodeBlock cbt = ni.firstChildOfType(CodeBlock.class);
                    return recursiveComputeIsVoid(cbt);
                }
                throw new UnsupportedOperationException("IMPLEMENT! " + ni.getClass());
            }
            --i;
        }
        return null;
    }
}
