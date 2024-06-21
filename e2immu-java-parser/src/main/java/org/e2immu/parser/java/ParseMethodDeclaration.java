package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.Access;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.MethodModifier;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.parserimpl.Context;
import org.e2immu.parserimpl.ForwardType;
import org.parsers.java.Node;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;

public class ParseMethodDeclaration extends CommonParse {
    private final ParseType parseType;
    private final ParseAnnotationExpression parseAnnotationExpression;

    public ParseMethodDeclaration(Runtime runtime) {
        super(runtime);
        parseType = new ParseType(runtime);
        parseAnnotationExpression = new ParseAnnotationExpression(runtime);
    }

    public MethodInfo parse(Context context, MethodDeclaration md) {
        int i = 0;
        List<AnnotationExpression> annotations = new ArrayList<>();
        List<MethodModifier> methodModifiers = new ArrayList<>();

        Node mdi;
        while (!((mdi = md.get(i)) instanceof ReturnType)) {
            if (mdi instanceof Annotation a) {
                annotations.add(parseAnnotationExpression.parse(context, a));
            } else if (mdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof MarkerAnnotation a) {
                        annotations.add(parseAnnotationExpression.parse(context, a));
                    } else if (node instanceof KeyWord keyWord) {
                        methodModifiers.add(modifier(keyWord));
                    }
                }
            } else if (mdi instanceof KeyWord keyWord) {
                methodModifiers.add(modifier(keyWord));
            }
            i++;
        }

        MethodInfo.MethodType methodType;
        ParameterizedType returnType;
        if (md.get(i) instanceof ReturnType rt) {
            // depending on the modifiers...
            methodType = runtime.methodTypeMethod();
            returnType = parseType.parse(context, rt);
            i++;
        } else throw new UnsupportedOperationException();
        String name;
        if (md.get(i) instanceof Identifier identifier) {
            name = identifier.getSource();
            i++;
        } else throw new UnsupportedOperationException();
        MethodInfo methodInfo = runtime.newMethod(context.enclosingType(), name, methodType);
        MethodInfo.Builder builder = methodInfo.builder().setReturnType(returnType);

        ForwardType forwardType = new ForwardType(returnType);
        Context newContext = context.newVariableContextForMethodBlock(methodInfo, forwardType);

        if (md.get(i) instanceof FormalParameters fps) {
            for (Node child : fps.children()) {
                if (child instanceof FormalParameter fp) {
                    parseFormalParameter(newContext, builder, fp);
                }
            }
            i++;
        } else throw new UnsupportedOperationException("Node " + md.get(i).getClass());
        while (i < md.size() && md.get(i) instanceof Delimiter) i++;
        if (i < md.size()) {
            if (md.get(i) instanceof CodeBlock codeBlock) {
                /*
                 delay the parsing of the code-block for a second phase, when all methods are known so that they can
                 be resolved
                 */
                newContext.resolver().add(builder, newContext.emptyForwardType(), null, codeBlock, newContext);
            } else if(md.get(i) instanceof StatementExpression se) {
                newContext.resolver().add(builder, newContext.emptyForwardType(), null, se, newContext);
            } else throw new UnsupportedOperationException();
        } else {
            builder.setMethodBody(runtime.emptyBlock());
        }
        builder.commitParameters();
        methodModifiers.forEach(builder::addMethodModifier);
        Access access = access(methodModifiers);
        Access accessCombined = newContext.enclosingType().access().combine(access);
        builder.setAccess(accessCombined);
        builder.addComments(comments(md));
        builder.setSource(source(methodInfo, null, md));
        builder.addAnnotations(annotations);
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
