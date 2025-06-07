package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Element;
import org.e2immu.language.cst.api.element.JavaDoc;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.Statement;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.ParseHelper;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParseHelperImpl implements ParseHelper {
    private final Parsers parsers;
    private final Runtime runtime;

    public ParseHelperImpl(Runtime runtime) {
        this(runtime, new Parsers(runtime));
    }

    public ParseHelperImpl(Runtime runtime, Parsers parsers) {
        this.parsers = parsers;
        this.runtime = runtime;
    }

    @Override
    public List<AnnotationExpression.KV> parseAnnotationExpression(TypeInfo annotationType, Object annotation, Context context) {
        List<AnnotationExpression.KV> kvs = new ArrayList<>();
        if (annotation instanceof SingleMemberAnnotation sma) {
            Expression expression = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                    sma.get(3));
            kvs.add(runtime.newAnnotationExpressionKeyValuePair("value", expression));
        } else if (annotation instanceof NormalAnnotation na) {
            // delimiter @, annotation name, ( , mvp, delimiter ',', mvp, delimiter )
            if (na.get(3) instanceof MemberValuePair mvp) {
                String key = mvp.getFirst().getSource();
                Expression value = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                        mvp.get(2));
                kvs.add(runtime.newAnnotationExpressionKeyValuePair(key, value));
            } else if (na.get(3) instanceof MemberValuePairs pairs) {
                for (int j = 0; j < pairs.size(); j += 2) {
                    if (pairs.get(j) instanceof MemberValuePair mvp) {
                        String key = mvp.getFirst().getSource();
                        ParameterizedType returnType = findReturnType(annotationType, key);
                        Expression value = parsers.parseExpression().parse(context, "",
                                context.newForwardType(returnType), mvp.get(2));
                        kvs.add(runtime.newAnnotationExpressionKeyValuePair(key, value));
                    } else {
                        throw new Summary.ParseException(context.info(), "Expected mvp");
                    }
                }
            } else {
                throw new Summary.ParseException(context.info(), "Expected mvp");
            }
        } else {
            throw new UnsupportedOperationException("NYI");
        }
        return kvs;
    }

    // key ~ method in annotations
    private ParameterizedType findReturnType(TypeInfo annotationType, String key) {
        MethodInfo methodInfo = annotationType.findUniqueMethod(key, 0);
        return methodInfo.returnType();
    }

    @Override
    public Expression parseExpression(Context context, String index, ForwardType forward, Object expression) {
        if (expression instanceof InvocationArguments ia) {
            TypeInfo enumType = forward.type().typeInfo();
            // we'll have to parse a constructor, new C(invocation arguments)
            return parsers.parseConstructorCall().parseEnumConstructor(context, index, enumType, ia);
        }
        return parsers.parseExpression().parse(context, index, forward, (Node) expression);
    }

    @Override
    public void resolveMethodInto(MethodInfo.Builder builder,
                                  Context context,
                                  ForwardType forwardType,
                                  Object unparsedEci,
                                  Object expression) {
        org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation eci;
        if (unparsedEci == null) {
            eci = null;
        } else {
            eci = parseEci(context, unparsedEci);
        }
        Element e;
        if (expression instanceof CompactConstructorDeclaration ccd) {
            int j = 0;
            while (!Token.TokenType.LBRACE.equals(ccd.get(j).getType())) j++;
            j++;
            if (ccd.get(j) instanceof org.parsers.java.ast.Statement s) {
                e = parseStatements(context, s, 0);
            } else if (ccd.get(j) instanceof Delimiter) {
                e = runtime.emptyBlock();
            } else throw new Summary.ParseException(context.info(), "Expected either empty block, or statements");
        } else if (expression instanceof CodeBlock codeBlock) {
            e = parsers.parseBlock().parse(context, "", null, codeBlock, false,
                    eci == null ? 0 : 1);
        } else {
            e = parseStatements(context, forwardType, expression, eci != null);
        }
        if (e instanceof Block b) {
            Block bWithEci;
            if (eci == null) {
                bWithEci = b;
            } else {
                bWithEci = runtime.newBlockBuilder().addStatement(eci).addStatements(b.statements()).build();
            }
            builder.setMethodBody(bWithEci);
        } else if (e instanceof Statement s) {
            Block.Builder bb = runtime.newBlockBuilder();
            if (eci != null) bb.addStatement(eci);
            builder.setMethodBody(bb.addStatement(s).build());
        } else if (e == null && eci != null) {
            builder.setMethodBody(runtime.newBlockBuilder().addStatement(eci).build());
        } else {
            // in Java, we must have a block
            throw new UnsupportedOperationException();
        }
    }

    /*
    we have and the potential "ECI" to deal with, and the fact that sometimes, multiple
    ExpressionStatements can replace a CodeBlock (no idea why, but we need to deal with it)
    See TestExplicitConstructorInvocation.
     */
    private Element parseStatements(Context context, ForwardType forwardType, Object expression, boolean haveEci) {
        int start = haveEci ? 1 : 0;
        if (expression instanceof org.parsers.java.ast.Statement est) {
            return parseStatements(context, est, start);
        }
        if (expression != null) {
            return parseExpression(context, "" + start, forwardType, expression);
        }
        return null;
    }

    private Statement parseStatements(Context context, org.parsers.java.ast.Statement first, int start) {
        Statement firstStatement = parsers.parseStatement().parse(context, "" + start, first);

        List<org.parsers.java.ast.Statement> siblings = new ArrayList<>();
        while (first.nextSibling() instanceof org.parsers.java.ast.Statement next) {
            siblings.add(next);
            first = next;
        }
        if (siblings.isEmpty()) {
            return firstStatement;
        }
        Block.Builder b = runtime.newBlockBuilder();
        b.addStatement(firstStatement);
        for (org.parsers.java.ast.Statement es : siblings) {
            ++start;
            Statement s2 = parsers.parseStatement().parse(context, "" + start, es);
            b.addStatement(s2);
        }
        return b.build();
    }

    private org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation parseEci(Context context,
                                                                                         Object eciObject) {
        ExplicitConstructorInvocation unparsedEci = (ExplicitConstructorInvocation) eciObject;
        org.e2immu.language.cst.api.statement.ExplicitConstructorInvocation eci;
        ConstructorDeclaration cd = (ConstructorDeclaration) unparsedEci.getParent();
        List<Comment> comments = parsers.parseStatement().comments(cd);
        Source source = parsers.parseStatement().source("0", cd);
        boolean isSuper = Token.TokenType.SUPER.equals(unparsedEci.getFirst().getType());
        List<Expression> parameterExpressions = parseArguments(context, unparsedEci.get(1));
        MethodInfo eciMethod;
        if (isSuper) {
            ParameterizedType parent = context.enclosingType().parentClass();
            eciMethod = null;
            while (parent != null) {
                TypeInfo typeInfo = parent.typeInfo();
                eciMethod = findCompatibleConstructor(typeInfo, parameterExpressions);
                if (eciMethod != null) break;
                parent = typeInfo.parentClass();
            }
        } else {
            eciMethod = findCompatibleConstructor(context.enclosingType(), parameterExpressions);
        }
        if (eciMethod == null) {
            throw new UnsupportedOperationException("Cannot find compatible constructor in explicit constructor invocation of  "
                                                    + context.enclosingMethod());
        }
        eci = runtime.newExplicitConstructorInvocationBuilder()
                .addComments(comments)
                .setSource(source)
                .setIsSuper(isSuper)
                .setMethodInfo(eciMethod)
                .setParameterExpressions(parameterExpressions)
                .build();
        return eci;
    }

    private MethodInfo findCompatibleConstructor(TypeInfo typeInfo, List<Expression> parameterExpressions) {
        return typeInfo.constructors().stream()
                .filter(c -> compatible(c.parameters(), parameterExpressions))
                .findFirst().orElse(null);
    }

    private boolean compatible(List<ParameterInfo> formal, List<Expression> arguments) {
        int f = formal.size();
        int a = arguments.size();
        if (f == a || f > 0 && formal.getLast().isVarArgs() && a >= f - 1) {
            int i = 0;
            for (Expression expression : arguments) {
                ParameterInfo pi = formal.get(Math.min(i, formal.size() - 1));
                // isAssignable from at erased level, because we did not have the forward type yet when evaluating
                // the expressions.
                ParameterizedType erasedFormal = pi.parameterizedType().erased();
                ParameterizedType erasedArgument = expression.parameterizedType().erased();
                if (!erasedFormal.isAssignableFrom(runtime, erasedArgument)) {
                    if (!pi.isVarArgs() || !erasedFormal.copyWithOneFewerArrays().isAssignableFrom(runtime,
                            expression.parameterizedType())) {
                        return false;
                    }
                }
                i++;
            }
            return true;
        }
        return false;
    }

    // arguments of ECI
    private List<Expression> parseArguments(Context context, Node node) {
        assert node instanceof InvocationArguments;
        List<Expression> expressions = new ArrayList<>();
        for (int k = 1; k < node.size(); k += 2) {
            Node nodeK = node.get(k);
            if (nodeK instanceof Delimiter) break;
            Expression e = parsers.parseExpression().parse(context, "0", context.emptyForwardType(), nodeK);
            expressions.add(e);
        }
        return List.copyOf(expressions);
    }

    @Override
    public JavaDoc.Tag parseJavaDocReferenceInTag(Context context, Info info, JavaDoc.Tag tag) {
        int hash = tag.content().indexOf('#');
        String packageOrType = hash < 0 ? tag.content() : tag.content().substring(0, hash);
        String memberDescriptor = hash < 0 ? null : tag.content().substring(hash + 1);
        NamedType namedType;
        if (hash == 0) {
            namedType = info.typeInfo();
        } else {
            namedType = context.typeContext().get(packageOrType, false);
        }
        if (namedType instanceof TypeInfo typeInfo) {
            if (memberDescriptor == null) {
                return tag.withResolvedReference(typeInfo);
            }
            int open = memberDescriptor.indexOf('(');
            String member;
            List<String> parameterTypes;
            if (open < 0) {
                member = memberDescriptor.trim();
                parameterTypes = null;
                FieldInfo fieldInfo = typeInfo.getFieldByName(member, false);
                if (fieldInfo != null) return tag.withResolvedReference(fieldInfo);
            } else {
                member = memberDescriptor.substring(0, open);
                int close = memberDescriptor.indexOf(')', open + 1);
                String parametersString = memberDescriptor.substring(open + 1, close);
                if (parametersString.trim().isEmpty()) {
                    parameterTypes = List.of();
                } else {
                    String[] paramStrings = parametersString.split(",\\s*");
                    parameterTypes = new ArrayList<>(Arrays.asList(paramStrings));
                }
            }
            MethodInfo methodInfo;
            if (member.equals(typeInfo.simpleName())) {
                methodInfo = typeInfo.constructors().stream()
                        .filter(c -> parameterTypes == null || parameterTypesMatch(c.parameters(), parameterTypes))
                        .findFirst().orElse(null);
            } else {
                methodInfo = typeInfo.methodStream()
                        .filter(m -> member.equals(m.name()))
                        .filter(m -> parameterTypes == null || parameterTypesMatch(m.parameters(), parameterTypes))
                        .findFirst().orElse(null);
            }
            return tag.withResolvedReference(methodInfo);
        }
        return tag;
    }

    private boolean parameterTypesMatch(List<ParameterInfo> parameters, List<String> parameterTypes) {
        if (parameters.size() != parameterTypes.size()) return false;
        for (ParameterInfo pi : parameters) {
            String typeString = parameterTypes.get(pi.index());
            if (!parameterTypeMatch(pi.parameterizedType(), typeString)) return false;
        }
        return true;
    }

    private boolean parameterTypeMatch(ParameterizedType parameterizedType, String typeString) {
        if (parameterizedType.typeParameter() != null) {
            String print1 = parameterizedType.print(runtime.qualificationFullyQualifiedNames(),
                    false, runtime.diamondNo()).toString();
            if (print1.equals(typeString)) return true;
            // type parameter, maybe written as T... instead of T[]
            return parameterizedType.arrays() > 0 && parameterizedType.print(runtime.qualificationFullyQualifiedNames(),
                    true, runtime.diamondNo()).toString().equals(typeString);
        }
        String typeInfoFqn = parameterizedType.typeInfo().fullyQualifiedName();
        if (typeString.equals(typeInfoFqn)) return true;
        if (typeString.equals(parameterizedType.typeInfo().simpleName())) return true;
        return typeString.equals(parameterizedType.typeInfo().fromPrimaryTypeDownwards());
    }
}
