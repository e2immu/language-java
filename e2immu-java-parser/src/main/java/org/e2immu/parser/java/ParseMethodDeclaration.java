package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ParseMethodDeclaration extends CommonParse {
    private final Logger LOGGER = LoggerFactory.getLogger(ParseTypeDeclaration.class);

    public ParseMethodDeclaration(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public MethodInfo parse(Context context, MethodDeclaration md) {
        try {
            return internalParse(context, md);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            LOGGER.error("Caught exception parsing method in type {}", context.info());
            context.summary().addParserError(re);
            context.summary().addType(context.enclosingType().primaryType(), false);
            return null;
        }
    }

    private MethodInfo internalParse(Context context, MethodDeclaration md) {
        int i = 0;
        List<AnnotationExpression> annotations = new ArrayList<>();
        List<MethodModifier> methodModifiers = new ArrayList<>();
        List<Node> typeParametersToParse = new ArrayList<>();
        Node mdi;
        while (!((mdi = md.get(i)) instanceof ReturnType)) {
            if (mdi instanceof Annotation a) {
                annotations.add(parsers.parseAnnotationExpression().parse(context, a));
            } else if (mdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof MarkerAnnotation a) {
                        annotations.add(parsers.parseAnnotationExpression().parse(context, a));
                    } else if (node instanceof KeyWord keyWord) {
                        methodModifiers.add(modifier(keyWord));
                    }
                }
            } else if (mdi instanceof KeyWord keyWord) {
                methodModifiers.add(modifier(keyWord));
            } else if (mdi instanceof TypeParameters) {
                int j = 1;
                while (j < mdi.size()) {
                    typeParametersToParse.add(mdi.get(j));
                    j += 2;
                }
            }
            i++;
        }
        if (md.get(i) instanceof ReturnType rt) {
            i++;
        } else throw new UnsupportedOperationException();
        String name;
        if (md.get(i) instanceof Identifier identifier) {
            name = identifier.getSource();
            i++;
        } else throw new UnsupportedOperationException();
        MethodInfo methodInfo = runtime.newMethod(context.enclosingType(), name, runtime.methodTypeMethod());
        MethodInfo.Builder builder = methodInfo.builder();

        Context contextWithTP = context.newTypeContext();

        int tpIndex = 0;
        for (Node unparsedTypeParameter : typeParametersToParse) {
            TypeParameter typeParameter = parseTypeParameter(contextWithTP, unparsedTypeParameter, methodInfo, tpIndex);
            contextWithTP.typeContext().addToContext(typeParameter);
            builder.addTypeParameter(typeParameter);
            tpIndex++;
        }
        // we need the type parameters in the context, because the return type may be one of them/contain one
        ParameterizedType returnType = parsers.parseType().parse(contextWithTP, rt);
        builder.setReturnType(returnType);

        ForwardType forwardType = contextWithTP.newForwardType(returnType);
        Context newContext = contextWithTP.newVariableContextForMethodBlock(methodInfo, forwardType);

        if (md.get(i) instanceof FormalParameters fps) {
            for (Node child : fps.children()) {
                if (child instanceof FormalParameter fp) {
                    parseFormalParameter(newContext, builder, fp);
                }
            }
            i++;
        } else throw new UnsupportedOperationException("Node " + md.get(i).getClass());
        if (md.get(i) instanceof ThrowsList throwsList) {
            for (int j = 1; j < throwsList.size(); j += 2) {
                ParameterizedType pt = parsers.parseType().parse(newContext, throwsList.get(j));
                builder.addExceptionType(pt);
            }
            i++;
        }
        while (i < md.size() && md.get(i) instanceof Delimiter) i++;
        if (i < md.size()) {
            if (md.get(i) instanceof CodeBlock codeBlock) {
                /*
                 delay the parsing of the code-block for a second phase, when all methods are known so that they can
                 be resolved
                 */
                newContext.resolver().add(builder, newContext.emptyForwardType(), null, codeBlock, newContext);
            } else if (md.get(i) instanceof StatementExpression se) {
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

    // code copied from ParseTypeDeclaration
    private TypeParameter parseTypeParameter(Context context, Node node, MethodInfo owner, int typeParameterIndex) {
        String name;
        if (node instanceof Identifier) {
            name = node.getSource();
        } else if (node instanceof org.parsers.java.ast.TypeParameter tp) {
            name = tp.get(0).getSource();
        } else throw new UnsupportedOperationException();
        TypeParameter typeParameter = runtime.newTypeParameter(typeParameterIndex, name, owner);
        context.typeContext().addToContext(typeParameter);
        // do type bounds
        TypeParameter.Builder builder = typeParameter.builder();
        if (node instanceof org.parsers.java.ast.TypeParameter tp) {
            if (tp.get(1) instanceof TypeBound tb) {
                ParameterizedType typeBound = parsers.parseType().parse(context, tb.get(1));
                builder.addTypeBound(typeBound);
            } else throw new UnsupportedOperationException();
        }
        return builder.commit();
    }

    private void parseFormalParameter(Context context, MethodInfo.Builder builder, FormalParameter fp) {
        ParameterizedType typeOfParameter;
        List<AnnotationExpression> annotations = new ArrayList<>();
        int i = 0;
        Node node0 = fp.get(i);
        if (node0 instanceof Modifiers) {
            for (Node modifier : node0) {
                if (modifier instanceof Annotation a) {
                    annotations.add(parsers.parseAnnotationExpression().parse(context, a));
                } else {
                    throw new Summary.ParseException(context.info(), "Expect formal parameter's modifier to be an annotation");
                }
            }
            ++i;
        } else if (node0 instanceof Annotation a) {
            annotations.add(parsers.parseAnnotationExpression().parse(context, a));
            ++i;
        }
        Node node1 = fp.get(i);
        boolean varargs;
        if (node1 instanceof Type type) {
            ParameterizedType pt = parsers.parseType().parse(context, type);
            if (fp.get(i + 1) instanceof Delimiter d && Token.TokenType.VAR_ARGS.equals(d.getType())) {
                ++i;
                typeOfParameter = pt.copyWithArrays(pt.arrays() + 1);
                varargs = true;
            } else {
                typeOfParameter = pt;
                varargs = false;
            }
            ++i;
        } else {
            throw new Summary.ParseException(context.info(), "Expect formal parameter's type");
        }
        String parameterName;
        Node node2 = fp.get(i);
        if (node2 instanceof Identifier identifier) {
            parameterName = identifier.getSource();
        } else {
            throw new UnsupportedOperationException();
        }
        ParameterInfo pi = builder.addParameter(parameterName, typeOfParameter);
        ParameterInfo.Builder piBuilder = pi.builder();
        piBuilder.addAnnotations(annotations);
        piBuilder.setVarArgs(varargs);
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
