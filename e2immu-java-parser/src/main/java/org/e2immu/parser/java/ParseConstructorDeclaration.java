package org.e2immu.parser.java;

import org.e2immu.cstapi.info.Access;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.MethodModifier;
import org.e2immu.cstapi.info.ParameterInfo;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.parserapi.Context;
import org.parsers.java.Node;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;

public class ParseConstructorDeclaration extends CommonParse {
    private final ParseType parseType;
    private final ParseExpression parseExpression;

    public ParseConstructorDeclaration(Runtime runtime) {
        super(runtime);
        parseExpression = new ParseExpression(runtime);
        parseType = new ParseType(runtime);
    }

    public MethodInfo parse(Context context, ConstructorDeclaration cd) {
        int i = 0;
        List<MethodModifier> methodModifiers = new ArrayList<>();
        if (cd.get(i) instanceof Modifiers modifiers) {
            for (Node node : modifiers.children()) {
                if (node instanceof KeyWord keyWord) {
                    methodModifiers.add(modifier(keyWord));
                }
            }
            i++;
        } else if (cd.get(i) instanceof KeyWord keyWord) {
            methodModifiers.add(modifier(keyWord));
            i++;
        }

        MethodInfo.MethodType methodType;
        ParameterizedType returnType;
        if (cd.get(i) instanceof Identifier) {
            methodType = runtime.methodTypeConstructor();
            returnType = runtime.parameterizedTypeReturnTypeOfConstructor();
        } else throw new UnsupportedOperationException();
        String name;
        if (cd.get(i) instanceof Identifier identifier) {
            name = identifier.getSource();
            i++;
        } else throw new UnsupportedOperationException();
        MethodInfo methodInfo = runtime.newMethod(context.enclosingType(), name, methodType);
        MethodInfo.Builder builder = methodInfo.builder()
                .setReturnType(runtime.parameterizedTypeReturnTypeOfConstructor());
        if (cd.get(i) instanceof FormalParameters fps) {
            for (Node child : fps.children()) {
                if (child instanceof FormalParameter fp) {
                    parseFormalParameter(context, builder, fp);
                }
            }
            i++;
        } else throw new UnsupportedOperationException("Node " + cd.get(i).getClass());
        ExplicitConstructorInvocation explicitConstructorInvocation;
        while (i < cd.size() && cd.get(i) instanceof Delimiter) i++;
        if (cd.get(i) instanceof org.parsers.java.ast.ExplicitConstructorInvocation eci) {
                explicitConstructorInvocation = eci;
            i ++;
        } else {
            explicitConstructorInvocation = null;
        }
        Node toResolve;
        if (cd.get(i) instanceof ExpressionStatement est) {
            toResolve = est;
        } else if (cd.get(i) instanceof CodeBlock codeBlock) {
            toResolve = codeBlock;
        } else {
            toResolve = null;
        }
        Context newContext = context.newVariableContextForMethodBlock(methodInfo, null);
        context.resolver().add(builder, context.emptyForwardType(), explicitConstructorInvocation, toResolve,
                newContext);
        builder.commitParameters();
        methodModifiers.forEach(builder::addMethodModifier);
        Access access = access(methodModifiers);
        Access accessCombined = context.enclosingType().access().combine(access);
        builder.setAccess(accessCombined);
        builder.addComments(comments(cd));
        builder.setSource(source(methodInfo, null, cd));
        return methodInfo;
    }

    private void parseFormalParameter(Context context, MethodInfo.Builder builder, FormalParameter fp) {
        ParameterizedType typeOfParameter;
        Node node0 = fp.get(0);
        if (node0 instanceof Type type) {
            typeOfParameter = parseType.parse(context, type);
        } else {
            throw new UnsupportedOperationException();
        }
        String parameterName;
        Node node1 = fp.get(1);
        if (node1 instanceof Identifier identifier) {
            parameterName = identifier.getSource();
        } else throw new UnsupportedOperationException();
        ParameterInfo pi = builder.addParameter(parameterName, typeOfParameter);
        ParameterInfo.Builder piBuilder = pi.builder();
        // do not commit yet!
        context.variableContext().add(pi);
    }


    private Access access(List<MethodModifier> methodModifiers) {
        for (MethodModifier methodModifier : methodModifiers) {
            if (methodModifier.isPublic()) return runtime.accessPublic();
            if (methodModifier.isPrivate()) return runtime.accessPrivate();
            if (methodModifier.isProtected()) return runtime.accessProtected();
        }
        return runtime.accessPackage();
    }

    private MethodModifier modifier(KeyWord keyWord) {
        return switch (keyWord.getType()) {
            case FINAL -> runtime.methodModifierFinal();
            case PRIVATE -> runtime.methodModifierPrivate();
            case PROTECTED -> runtime.methodModifierProtected();
            case PUBLIC -> runtime.methodModifierPublic();
            case STATIC -> runtime.methodModifierStatic();
            case SYNCHRONIZED -> runtime.methodModifierSynchronized();
            case ABSTRACT -> runtime.methodModifierAbstract();
            case _DEFAULT -> runtime.methodModifierDefault();
            default -> throw new UnsupportedOperationException("Have " + keyWord.getType());
        };
    }
}
