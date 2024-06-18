package org.e2immu.parser.java;

import org.e2immu.cstapi.element.Comment;
import org.e2immu.cstapi.element.Source;
import org.e2immu.cstapi.expression.Expression;
import org.e2immu.cstapi.expression.Lambda;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.ParameterInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.statement.Block;
import org.e2immu.cstapi.statement.Statement;
import org.e2immu.cstapi.type.NamedType;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.parserapi.Context;
import org.e2immu.parserapi.ForwardType;
import org.parsers.java.ast.Identifier;
import org.parsers.java.ast.LambdaExpression;
import org.parsers.java.ast.LambdaLHS;

import java.util.ArrayList;
import java.util.List;

public class ParseLambdaExpression extends CommonParse {
    private final ParseExpression parseExpression;

    public ParseLambdaExpression(Runtime runtime, ParseExpression parseExpression) {
        super(runtime);
        this.parseExpression = parseExpression;
    }

    public Expression parse(Context context,
                            List<Comment> comments,
                            Source source,
                            String index,
                            ForwardType forwardType,
                            LambdaExpression le) {
        Lambda.Builder builder = runtime.newLambdaBuilder();

        Context newContext = context.newVariableContext("lambda");
        List<Lambda.OutputVariant> outputVariants = new ArrayList<>();

        int typeIndex = context.anonymousTypeCounters().newIndex(context.enclosingType());
        TypeInfo anonymousType = runtime.newAnonymousType(context.enclosingType(), typeIndex);

        MethodInfo sam = forwardType.type().bestTypeInfo().singleAbstractMethod();
        MethodInfo methodInfo = runtime.newMethod(anonymousType, sam.name(), runtime.methodTypeMethod());
        MethodInfo.Builder miBuilder = methodInfo.builder();

        if (le.get(0) instanceof LambdaLHS lhs) {
            if (lhs.get(0) instanceof Identifier identifier) {
                // single variable, no type given. we must extract it from the forward type, which must be a functional interface
                ParameterizedType type = forwardType.type().parameters().get(0);
                String parameterName = identifier.getSource();
                ParameterInfo pi = miBuilder.addParameter(parameterName, type);
                outputVariants.add(runtime.lambdaOutputVariantEmpty());
                pi.builder().commit();
                newContext.variableContext().add(pi);
            } else throw new UnsupportedOperationException();
        } else throw new UnsupportedOperationException();


        ParameterizedType concreteReturnType;
        Block methodBody;
        if (le.get(1) instanceof org.parsers.java.ast.Expression e) {
            // simple function or supplier
            ForwardType fwd = newContext.emptyForwardType();
            Expression expression = parseExpression.parse(newContext, index, fwd, e);
            concreteReturnType = expression.parameterizedType();
            Statement returnStatement = runtime.newReturnStatement(expression);
            methodBody = runtime.newBlockBuilder().addStatement(returnStatement).build();
            // returns either java.util.function.Function<T,R> or java.util.function.Supplier<R>
            TypeInfo abstractFunctionalType = runtime.syntheticFunctionalType(methodInfo.parameters().size(), true);
            List<ParameterizedType> concreteFtParams = new ArrayList<>();
            for (ParameterInfo pi : methodInfo.parameters()) {
                concreteFtParams.add(pi.parameterizedType());
            }
            concreteFtParams.add(concreteReturnType);
            ParameterizedType concreteFunctionalType = runtime.newParameterizedType(abstractFunctionalType, concreteFtParams);
            // new we have  "class $1 implements Function<Integer, String>"
            anonymousType.builder().addInterfaceImplemented(concreteFunctionalType);
        } else throw new UnsupportedOperationException();


        miBuilder.setAccess(runtime.accessPrivate());
        miBuilder.setSynthetic(true);
        miBuilder.setMethodBody(methodBody);
        miBuilder.setReturnType(concreteReturnType);
        miBuilder.commit();
        anonymousType.builder().addMethod(methodInfo);
        anonymousType.builder().commit();

        return builder
                .setMethodInfo(methodInfo)
                .setOutputVariants(outputVariants)
                .addComments(comments)
                .setSource(source)
                .build();
    }
}
