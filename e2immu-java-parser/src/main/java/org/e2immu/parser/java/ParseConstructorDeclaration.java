package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.Access;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.MethodModifier;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.Node;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ParseConstructorDeclaration extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseConstructorDeclaration.class);

    public ParseConstructorDeclaration(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public MethodInfo parse(Context context, ConstructorDeclaration cd) {
        try {
            return internalParse(context, cd);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            LOGGER.error("Caught exception parsing constructor in type {}", context.info());
            context.summary().addParserError(re);
            context.summary().addType(context.enclosingType().primaryType(), false);
            return null;
        }
    }

    private MethodInfo internalParse(Context context, ConstructorDeclaration cd) {
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
        String name;
        if (cd.get(i) instanceof Identifier identifier) {
            boolean compact = cd instanceof CompactConstructorDeclaration;
            methodType = compact ? runtime.methodTypeCompactConstructor() : runtime.methodTypeConstructor();
            name = identifier.getSource();
            i++;
        } else {
            throw new Summary.ParseException(context.info(), "Expected Identifier, got " + cd.get(i).getClass());
        }
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
        } else if (cd instanceof CompactConstructorDeclaration) {
            // a compact constructor does not have arguments
            assert methodType.isCompactConstructor();
        } else {
            throw new UnsupportedOperationException("Node " + cd.get(i).getClass());
        }
        ExplicitConstructorInvocation explicitConstructorInvocation;
        while (i < cd.size() && cd.get(i) instanceof Delimiter) i++;
        if (cd.get(i) instanceof org.parsers.java.ast.ExplicitConstructorInvocation eci) {
            explicitConstructorInvocation = eci;
            i++;
        } else {
            explicitConstructorInvocation = null;
        }
        Node toResolve;
        if (cd instanceof CompactConstructorDeclaration) {
            toResolve = cd; // because the statements simply follow the identifier
        } else if (cd.get(i) instanceof ExpressionStatement est) {
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
            typeOfParameter = parsers.parseType().parse(context, type);
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
