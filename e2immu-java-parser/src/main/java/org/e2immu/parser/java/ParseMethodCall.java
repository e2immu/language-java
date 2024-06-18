package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.expression.MethodCall;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.cst.api.variable.Variable;
import org.e2immu.parserapi.Context;
import org.e2immu.parserapi.ForwardType;
import org.parsers.java.ast.Identifier;
import org.parsers.java.ast.InvocationArguments;
import org.parsers.java.ast.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ParseMethodCall extends CommonParse {
    private final static Logger LOGGER = LoggerFactory.getLogger(ParseMethodCall.class);

    private final ParseExpression parseExpression;

    protected ParseMethodCall(Runtime runtime, ParseExpression parseExpression) {
        super(runtime);
        this.parseExpression = parseExpression;
    }

    public MethodCall parse(Context context, String index, org.parsers.java.ast.MethodCall mc) {
        MethodCall.Builder builder = runtime.newMethodCallBuilder();
        Expression object;
        int i = 0;
        String methodName;
        if (mc.get(i) instanceof Name name) {
            // TypeExpression?
            object = parseScope(context, name, name.size() - 2);
            // name and possibly scope: System.out.println id, del, id, del, id
            int nameIndex = name.size() - 1;
            if (name.get(nameIndex) instanceof Identifier nameId) {
                methodName = nameId.getSource();
            } else throw new UnsupportedOperationException();
            i++;
        } else throw new UnsupportedOperationException();
        int numArguments;
        if (mc.get(i) instanceof InvocationArguments ia) {
            numArguments = (ia.size() - 1) / 2;
        } else throw new UnsupportedOperationException();

        // now we have scope, methodName, and the number of arguments
        // find a list of candidates
        // choose the correct candidate, and evaluate arguments
        // re-evaluate scope, and determine concrete return type
        TypeInfo methodOwner = object.parameterizedType().typeInfo();
        MethodInfo methodInfo = methodOwner.findUniqueMethod(methodName, numArguments);

        ParameterizedType concreteReturnType = methodInfo.returnType();

        // parse arguments
        List<Expression> expressions;

        // (, lit expr, )  or  del mc del mc, del expr del expr, del
        expressions = new ArrayList<>();
        int p = 0;
        if(ia.size()>2) {
            for (int k = 1; k < ia.size(); k += 2) {
                ParameterInfo pi = methodInfo.parameters().get(p);
                ForwardType forwardType = context.newForwardType(pi.parameterizedType());
                Expression e = parseExpression.parse(context, index, forwardType, ia.get(k));
                expressions.add(e);
                p++;
            }
        }
        return builder.setObject(object)
                .setParameterExpressions(expressions)
                .setMethodInfo(methodInfo)
                .setConcreteReturnType(concreteReturnType)
                .setSource(source(context.info(), index, mc))
                .addComments(comments(mc))
                .build();
    }

    /*
    Type
    a.b.c.Type
    field
    Type.field
    object.field
    a.b.c.Type.field
    o1.o2.field
     */
    private Expression parseScope(Context context, Name name, int maxIndex) {
        if (maxIndex < 0) {
            return runtime.newVariableExpression(runtime.newThis(context.enclosingType()));
        }
        // field without scope, local variable, packagePrefix
        String name0 = name.get(0).getSource();
        Expression expression;
        Variable singleName = context.variableContext().get(name0, false);
        if (singleName != null) {
            expression = runtime.newVariableExpression(singleName);
        } else {
            NamedType namedType = context.typeContext().get(name0, false);
            if (namedType instanceof TypeInfo typeInfo) {
                expression = runtime.newTypeExpression(typeInfo.asSimpleParameterizedType(), runtime.diamondNo());
            } else {
                // FIXME could be a package prefix
                throw new UnsupportedOperationException();
            }
        }
        if (maxIndex <= 1) {
            return expression;
        }
        // the next ones must be fields
        Expression scope = expression;
        for (int i = 2; i < maxIndex; i += 2) {
            TypeInfo typeInfo = scope.parameterizedType().typeInfo();
            String nameI = name.get(i).getSource();
            FieldInfo fieldInfo = typeInfo.getFieldByName(nameI, true);
            FieldReference fr = runtime.newFieldReference(fieldInfo, scope, fieldInfo.type());
            scope = runtime.newVariableExpression(fr);
        }
        return scope;
    }
}

