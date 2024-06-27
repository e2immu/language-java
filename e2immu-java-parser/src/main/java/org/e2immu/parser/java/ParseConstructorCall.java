package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.ConstructorCall;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.Diamond;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeNature;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.cst.api.variable.Variable;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.parser.java.erasure.ConstructorCallErasure;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParseConstructorCall extends CommonParse {
    private final static Logger LOGGER = LoggerFactory.getLogger(ParseConstructorCall.class);

    protected ParseConstructorCall(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public Expression parse(Context context,
                            String index,
                            ForwardType forwardType,
                            org.parsers.java.ast.AllocationExpression ae) {
        assert ae.get(0) instanceof KeyWord kw && Token.TokenType.NEW.equals(kw.getType());
        ParameterizedType typeAsIs = parsers.parseType().parse(context, ae.get(1));
        TypeInfo typeInfo = typeAsIs.typeInfo();
        assert typeInfo != null;
        ParameterizedType formalType = typeInfo.asParameterizedType(runtime);

        if (forwardType.erasure()) {
            return new ConstructorCallErasure(runtime, formalType);
        }

        Diamond diamond;
        int i = 2;
        ParameterizedType expectedConcreteType;
        if (ae.get(i) instanceof DiamondOperator) {
            diamond = runtime.diamondYes();
            if (forwardType.type() == null || forwardType.type().isVoid()) {
                expectedConcreteType = null;
            } else {
                expectedConcreteType = inferDiamond(context, typeAsIs.typeInfo(), forwardType.type());
            }
            i++;
        } else {
            diamond = typeAsIs.parameters().isEmpty() ? runtime.diamondNo() : runtime.diamondShowAll();
            expectedConcreteType = typeAsIs;
        }

        List<Comment> comments = comments(ae);
        Source source = source(context.info(), index, ae);

        if (ae.get(i) instanceof InvocationArguments ia) {
            if (i + 1 < ae.size() && ae.get(i + 1) instanceof ClassOrInterfaceBody body) {
                return anonymousType(context, comments, source, expectedConcreteType, typeAsIs, ia, body, diamond);
            }
            List<Object> unparsedArguments = new ArrayList<>();
            int j = 1;
            while (j < ia.size() && !(ia.get(j) instanceof Delimiter)) {
                unparsedArguments.add(ia.get(j));
                j += 2;
            }
            Expression constructorCall = context.methodResolution().resolveConstructor(context, comments, source,
                    index, formalType, expectedConcreteType, diamond, unparsedArguments);
            if (constructorCall == null) {
                return new ConstructorCallErasure(runtime, formalType);
            }
            return constructorCall;
        }
        if (ae.get(i) instanceof ArrayDimsAndInits ada) {
            return arrayCreation(context, index, ada, typeInfo, diamond, source, comments);
        }
        throw new Summary.ParseException(context.info(), "Expected InvocationArguments or ArrayDimsAndInits, got "
                                                         + ae.get(i).getClass());
    }

    private ConstructorCall arrayCreation(Context context,
                                          String index,
                                          ArrayDimsAndInits ada,
                                          TypeInfo typeInfo,
                                          Diamond diamond,
                                          Source source,
                                          List<Comment> comments) {
        org.e2immu.language.cst.api.expression.ArrayInitializer initializer;
        ForwardType fwdIndex = context.newForwardType(runtime.intParameterizedType());
        int j = 0;
        boolean haveDimensions = false;
        List<Expression> expressions = new ArrayList<>();
        while (j < ada.size() && !(ada.get(j) instanceof ArrayInitializer)) {
            if (Token.TokenType.LBRACKET.equals(ada.get(j).getType())) {
                j++;
                if (Token.TokenType.RBRACKET.equals(ada.get(j).getType())) {
                    // unknown array dimension
                    expressions.add(runtime.newEmptyExpression());
                    j++;
                } else if (ada.get(j) instanceof org.parsers.java.ast.Expression expression) {
                    Expression e = parsers.parseExpression().parse(context, index, fwdIndex, expression);
                    expressions.add(e);
                    j += 2;
                    haveDimensions = true;
                }
            }
        }
        ParameterizedType formalReturnType = runtime.newParameterizedType(typeInfo, expressions.size());
        MethodInfo constructor = runtime.newArrayCreationConstructor(formalReturnType);
        ParameterizedType concreteReturnType;
        if (haveDimensions) {
            initializer = null;
            concreteReturnType = formalReturnType;
        } else {
            assert j < ada.size();
            if (ada.get(j) instanceof ArrayInitializer ai) {
                LOGGER.debug("Parsing array initializer");
                ForwardType fwd = context.newForwardType(formalReturnType);
                initializer = (org.e2immu.language.cst.api.expression.ArrayInitializer) parsers.parseExpression()
                        .parse(context, index, fwd, ai);
                concreteReturnType = initializer.parameterizedType();
            } else throw new Summary.ParseException(context.info(), "Expected array initializer");
        }
        return runtime.newConstructorCallBuilder()
                .setObject(null)
                .setParameterExpressions(expressions)
                .setConstructor(constructor)
                .setConcreteReturnType(concreteReturnType)
                .setArrayInitializer(initializer)
                .setDiamond(diamond)
                .setSource(source)
                .addComments(comments)
                .build();
    }


    private ParameterizedType inferDiamond(Context context, TypeInfo formalType, ParameterizedType type) {
        if (type.typeInfo() == formalType) return type;
        ParameterizedType formalPt = formalType.asParameterizedType(runtime);
        Map<NamedType, ParameterizedType> typeParameterMap = context.genericsHelper().translateMap(formalPt, type,
                false);
        return formalPt.applyTranslation(runtime, typeParameterMap);
    }

    private ConstructorCall anonymousType(Context context,
                                          List<Comment> comments,
                                          Source source,
                                          ParameterizedType forwardType,
                                          ParameterizedType type,
                                          InvocationArguments ia,
                                          ClassOrInterfaceBody body,
                                          Diamond diamond) {
        TypeInfo anonymousType = runtime.newAnonymousType(context.enclosingType(),
                context.anonymousTypeCounters().newIndex(context.enclosingType()));
        TypeNature typeNature = runtime.typeNatureClass();
        TypeInfo.Builder builder = anonymousType.builder();
        builder.setTypeNature(typeNature);
        ParameterizedType concreteReturnType = diamond.isShowAll() ? type : forwardType;
        if (concreteReturnType.typeInfo().isInterface()) {
            builder.addInterfaceImplemented(concreteReturnType);
        } else {
            builder.setParentClass(concreteReturnType);
        }
        assert ia.size() == 2
               && Token.TokenType.LPAREN.equals(ia.get(0).getType())
               && Token.TokenType.RPAREN.equals(ia.get(1).getType());
        Context newContext = context.newAnonymousClassBody();
        parsers.parseTypeDeclaration().parseBody(newContext, body, typeNature, anonymousType,
                builder);
        newContext.resolver().resolve();
        builder.commit();
        return runtime.newConstructorCallBuilder()
                .setSource(source)
                .addComments(comments)
                .setDiamond(diamond)
                .setConcreteReturnType(concreteReturnType)
                .setParameterExpressions(List.of())
                .setAnonymousClass(anonymousType).build();
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

