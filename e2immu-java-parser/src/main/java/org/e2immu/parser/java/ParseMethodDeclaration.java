package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.DetailedSources;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.MethodModifier;
import org.e2immu.language.cst.api.info.ParameterInfo;
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

    public MethodInfo parse(Context context, Node md) {
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

    private MethodInfo internalParse(Context context, Node md) {
        int i = 0;
        List<Annotation> annotations = new ArrayList<>();
        List<MethodModifier> methodModifiers = new ArrayList<>();
        List<Node> typeParametersToParse = new ArrayList<>();
        DetailedSources.Builder detailedSourcesBuilder = context.newDetailedSourcesBuilder();

        label:
        while (true) {
            Node mdi = md.get(i);
            switch (mdi) {
                case Annotation a:
                    annotations.add(a);
                    break;
                case Modifiers modifiers:
                    for (Node node : modifiers.children()) {
                        if (node instanceof Annotation a) {
                            annotations.add(a);
                        } else if (node instanceof KeyWord keyWord) {
                            MethodModifier m = modifier(keyWord);
                            methodModifiers.add(m);
                            if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(m, source(keyWord));
                        }
                    }
                    break;
                case KeyWord keyWord:
                    MethodModifier m = modifier(keyWord);
                    methodModifiers.add(m);
                    if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(m, source(keyWord));
                    break;
                case TypeParameters nodes:
                    int j = 1;
                    while (j < nodes.size()) {
                        typeParametersToParse.add(nodes.get(j));
                        j += 2;
                    }
                    break;
                case null:
                default:
                    break label;
            }
            i++;
        }
        ReturnType rt;
        boolean constructor = md instanceof ConstructorDeclaration;
        boolean compactConstructor = md instanceof CompactConstructorDeclaration;
        if (constructor) {
            rt = null;
        } else if (md.get(i) instanceof ReturnType) {
            rt = (ReturnType) md.get(i);
            i++;
        } else throw new UnsupportedOperationException();
        String name;
        if (md.get(i) instanceof Identifier identifier) {
            name = identifier.getSource();
            if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(name, source(identifier));
            i++;
        } else throw new UnsupportedOperationException();

        MethodInfo.MethodType methodType;
        if (compactConstructor) {
            methodType = runtime.methodTypeCompactConstructor();
        } else if (constructor) {
            methodType = runtime.methodTypeConstructor();
        } else if (methodModifiers.contains(runtime.methodModifierDefault())) {
            methodType = runtime.methodTypeDefaultMethod();
        } else if (methodModifiers.contains(runtime.methodModifierStatic())) {
            methodType = runtime.methodTypeStaticMethod();
        } else if (methodModifiers.contains(runtime.methodModifierAbstract())
                   || context.enclosingType().isInterface()) {
            methodType = runtime.methodTypeAbstractMethod();
        } else {
            methodType = runtime.methodTypeMethod();
        }

        MethodInfo methodInfo = runtime.newMethod(context.enclosingType(), name, methodType);
        MethodInfo.Builder builder = methodInfo.builder();

        parseAnnotations(context, builder, annotations);

        Context contextWithTP = context.newTypeContext();

        if (!typeParametersToParse.isEmpty()) {
            TypeParameter[] typeParameters = resolveTypeParameters(typeParametersToParse, contextWithTP, methodInfo,
                    detailedSourcesBuilder);
            for (TypeParameter typeParameter : typeParameters) {
                builder.addTypeParameter(typeParameter);
            }
        }

        // we need the type parameters in the context, because the return type may be one of them/contain one
        ParameterizedType returnType = rt == null ? runtime.parameterizedTypeReturnTypeOfConstructor()
                : parsers.parseType().parse(contextWithTP, rt, detailedSourcesBuilder);
        builder.setReturnType(returnType);
        ForwardType forwardType = contextWithTP.newForwardType(returnType);
        Context newContext = contextWithTP.newVariableContextForMethodBlock(methodInfo, forwardType);

        if (md.get(i) instanceof FormalParameters fps) {
            for (Node child : fps.children()) {
                if (child instanceof FormalParameter fp) {
                    parseFormalParameter(newContext, builder, fp);
                }
            }
            if (detailedSourcesBuilder != null) {
                Delimiter delimiter = (Delimiter) fps.getLast();
                detailedSourcesBuilder.put(DetailedSources.END_OF_PARAMETER_LIST, source(delimiter));
            }
            i++;
        } else if (!compactConstructor) {
            throw new UnsupportedOperationException("Node " + md.get(i).getClass());
        } // a constructor can be a "compact" one in records
        if (md.get(i) instanceof ThrowsList throwsList) {
            for (int j = 1; j < throwsList.size(); j += 2) {
                ParameterizedType pt = parsers.parseType().parse(newContext, throwsList.get(j), detailedSourcesBuilder);
                builder.addExceptionType(pt);
            }
            i++;
        }
        while (i < md.size() && md.get(i) instanceof Delimiter) i++;

        ExplicitConstructorInvocation explicitConstructorInvocation;
        if (i < md.size() && md.get(i) instanceof org.parsers.java.ast.ExplicitConstructorInvocation eci) {
            explicitConstructorInvocation = eci;
            i++;
        } else {
            explicitConstructorInvocation = null;
        }
        Node toResolve;
        Node cdi = i >= md.size() ? null : md.get(i);
        if (compactConstructor) {
            toResolve = md; // because the statements simply follow the identifier
        } else if (cdi instanceof ExpressionStatement || cdi instanceof StatementExpression) {
            toResolve = cdi;
        } else if (cdi instanceof CodeBlock codeBlock) {
            toResolve = codeBlock;
        } else {
            toResolve = null;
        }
        if (toResolve != null || explicitConstructorInvocation != null) {
            Context resContext = context.newVariableContextForMethodBlock(methodInfo, null);
            resContext.resolver().add(methodInfo, builder, resContext.emptyForwardType(), explicitConstructorInvocation,
                    toResolve, newContext);
        } else {
            builder.setMethodBody(runtime.emptyBlock());
        }

        builder.commitParameters();
        methodModifiers.forEach(builder::addMethodModifier);
        builder.computeAccess();
        builder.addComments(comments(md));
        Source source = source(md);
        builder.setSource(detailedSourcesBuilder == null ? source : source.withDetailedSources(detailedSourcesBuilder.build()));
        return methodInfo;
    }

    private void parseFormalParameter(Context context, MethodInfo.Builder builder, FormalParameter fp) {
        ParameterizedType typeOfParameter;
        DetailedSources.Builder detailedSourcesBuilder = context.newDetailedSourcesBuilder();
        List<Annotation> annotations = new ArrayList<>();
        int i = 0;
        boolean isFinal = false;
        Node node0 = fp.get(i);
        if (node0 instanceof Modifiers) {
            for (Node modifier : node0) {
                if (modifier instanceof Annotation a) {
                    annotations.add(a);
                } else if (modifier instanceof KeyWord kw) {
                    if (Token.TokenType.FINAL.equals(kw.getType())) {
                        isFinal = true;
                    } else throw new Summary.ParseException(context.info(), "Expect 'final' as only keyword");
                } else {
                    throw new Summary.ParseException(context.info(), "Expect formal parameter's modifier to be an annotation");
                }
            }
            ++i;
        } else if (node0 instanceof Annotation a) {
            annotations.add(a);
            ++i;
        } else if (node0 instanceof KeyWord kw) {
            if (Token.TokenType.FINAL.equals(kw.getType())) {
                isFinal = true;
            } else throw new Summary.ParseException(context.info(), "Expect 'final' as only keyword");
            ++i;
        }
        Node node1 = fp.get(i);
        boolean varargs;
        if (node1 instanceof Type type) {
            ParameterizedType pt = parsers.parseType().parse(context, type, detailedSourcesBuilder);
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
            if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(parameterName, source(identifier));
        } else if (node2 instanceof VariableDeclaratorId vdi) {
            // old C-style array type 'I data[]'
            if (vdi.getFirst() instanceof Identifier identifier) {
                parameterName = identifier.getSource();
                if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(parameterName, source(identifier));
            } else throw new Summary.ParseException(context.info(), "Expect first part to be Identifier");
            int arrayCount = (int) vdi.stream().filter(n -> Token.TokenType.LBRACKET.equals(n.getType())).count();
            typeOfParameter = typeOfParameter.copyWithArrays(arrayCount);
        } else {
            throw new UnsupportedOperationException();
        }
        Source source = source(fp);
        ParameterInfo pi = builder.addParameter(parameterName, typeOfParameter);
        pi.builder().addComments(comments(fp))
                .setSource(detailedSourcesBuilder == null ? source : source.withDetailedSources(detailedSourcesBuilder.build()))
                .setVarArgs(varargs)
                .setIsFinal(isFinal);

        // now that there is a builder, we can parse the annotations
        parseAnnotations(context, pi.builder(), annotations);

        // do not commit yet!
        context.variableContext().add(pi);
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
