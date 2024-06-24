package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.expression.MethodReference;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.parsers.java.Node;
import org.parsers.java.ast.Identifier;
import org.parsers.java.ast.Type;

import java.util.List;

public class ParseMethodReference extends CommonParse {

    public ParseMethodReference(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public MethodReference parse(Context context, List<Comment> comments, Source source, String index,
                                 org.parsers.java.ast.MethodReference mr) {
        Expression scope;
        Node n0 = mr.get(0);
        if (n0 instanceof Type) {
            ParameterizedType pt = parsers.parseType().parse(context, n0);
            scope = runtime.newTypeExpression(pt, runtime.diamondNo());
        } else {
            ForwardType forwardType = context.emptyForwardType();
            scope = parsers.parseExpression().parse(context, index, forwardType, n0);
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
