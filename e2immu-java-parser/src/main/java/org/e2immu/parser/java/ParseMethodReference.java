package org.e2immu.parser.java;

import org.e2immu.cstapi.element.Comment;
import org.e2immu.cstapi.element.Source;
import org.e2immu.cstapi.expression.Expression;
import org.e2immu.cstapi.expression.MethodReference;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.parserapi.Context;
import org.e2immu.parserapi.ForwardType;
import org.parsers.java.Node;
import org.parsers.java.ast.Identifier;
import org.parsers.java.ast.Type;

import java.util.List;

public class ParseMethodReference extends CommonParse {
    private final ParseExpression parseExpression;
    private final ParseType parseType;

    public ParseMethodReference(Runtime runtime, ParseExpression parseExpression, ParseType parseType) {
        super(runtime);
        this.parseExpression = parseExpression;
        this.parseType = parseType;
    }

    public MethodReference parse(Context context, List<Comment> comments, Source source, String index,
                                 org.parsers.java.ast.MethodReference mr) {
        Expression scope;
        Node n0 = mr.get(0);
        if (n0 instanceof Type) {
            ParameterizedType pt = parseType.parse(context, n0);
            scope = runtime.newTypeExpression(pt, runtime.diamondNo());
        } else {
            ForwardType forwardType = context.emptyForwardType();
            scope = parseExpression.parse(context, index, forwardType, n0);
        }
        MethodInfo methodInfo;
        ParameterizedType concreteReturnType;
        if (mr.get(2) instanceof Identifier id) {
            String methodName = id.getSource();
            boolean constructor = "new".equals(methodName);
            TypeInfo type = scope.parameterizedType().bestTypeInfo();
            if (constructor) {
                methodInfo = type.findConstructor(0);
                concreteReturnType = scope.parameterizedType();
            } else {
                methodInfo = type.methodStream().filter(m -> methodName.equals(m.name())).findFirst().orElseThrow();
                concreteReturnType = methodInfo.returnType();
            }
        } else throw new UnsupportedOperationException();
        return runtime.newMethodReferenceBuilder().setSource(source).addComments(comments)
                .setMethod(methodInfo)
                .setScope(scope)
                .setConcreteReturnType(concreteReturnType)
                .build();
    }
}
