package org.e2immu.parser.java;

import org.e2immu.cstapi.element.Comment;
import org.e2immu.cstapi.element.CompilationUnit;
import org.e2immu.cstapi.expression.AnnotationExpression;
import org.e2immu.cstapi.info.*;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.cstapi.type.TypeNature;
import org.e2immu.cstapi.type.TypeParameter;
import org.e2immu.cstapi.type.Wildcard;
import org.e2immu.parserapi.Context;
import org.e2immu.support.Either;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;

public class ParseTypeDeclaration extends CommonParse {
    private final ParseConstructorDeclaration parseConstructorDeclaration;
    private final ParseMethodDeclaration parseMethodDeclaration;
    private final ParseAnnotationMethodDeclaration parseAnnotationMethodDeclaration;
    private final ParseFieldDeclaration parseFieldDeclaration;
    private final ParseAnnotationExpression parseAnnotationExpression;
    private final ParseType parseType;

    public ParseTypeDeclaration(Runtime runtime) {
        super(runtime);
        parseMethodDeclaration = new ParseMethodDeclaration(runtime);
        parseAnnotationMethodDeclaration = new ParseAnnotationMethodDeclaration(runtime);
        parseFieldDeclaration = new ParseFieldDeclaration(runtime);
        parseConstructorDeclaration = new ParseConstructorDeclaration(runtime);
        parseType = new ParseType(runtime);
        parseAnnotationExpression = new ParseAnnotationExpression(runtime);
    }

    public TypeInfo parse(Context context,
                          Either<CompilationUnit, TypeInfo> packageNameOrEnclosing,
                          TypeDeclaration td) {
        List<Comment> comments = comments(td);

        int i = 0;
        List<AnnotationExpression> annotations = new ArrayList<>();
        TypeNature typeNature = null;
        List<TypeModifier> typeModifiers = new ArrayList<>();
        Node tdi;
        while (!((tdi = td.get(i)) instanceof Identifier)) {
            if (tdi instanceof Annotation a) {
                annotations.add(parseAnnotationExpression.parse(context, a));
            } else if (tdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof MarkerAnnotation a) {
                        annotations.add(parseAnnotationExpression.parse(context, a));
                    } else if (node instanceof KeyWord keyWord) {
                        typeModifiers.add(getTypeModifier(keyWord.getType()));
                    }
                }
            }
            if (tdi instanceof KeyWord keyWord) {
                TypeModifier tm = getTypeModifier(keyWord.getType());
                if (tm != null) typeModifiers.add(tm);
                TypeNature tn = getTypeNature(td, keyWord.getType());
                if (tn != null) {
                    assert typeNature == null;
                    typeNature = tn;
                }
            }
            i++;
        }
        if (typeNature == null) throw new UnsupportedOperationException("Have not determined type nature");
        String simpleName;
        if (td.get(i) instanceof Identifier identifier) {
            simpleName = identifier.getSource();
            i++;
        } else throw new UnsupportedOperationException();
        TypeInfo typeInfo;
        if (packageNameOrEnclosing.isLeft()) {
            typeInfo = runtime.newTypeInfo(packageNameOrEnclosing.getLeft(), simpleName);
        } else {
            typeInfo = runtime.newTypeInfo(packageNameOrEnclosing.getRight(), simpleName);
        }
        TypeInfo.Builder builder = typeInfo.builder();
        builder.addComments(comments);
        typeModifiers.forEach(builder::addTypeModifier);
        builder.addAnnotations(annotations);
        builder.computeAccess();
        builder.setTypeNature(typeNature);
        builder.setSource(source(typeInfo, null, td));

        if (td.get(i) instanceof ExtendsList extendsList) {
            for (Node child : extendsList.children()) {

            }
            i++;
        }

        if (td.get(i) instanceof ImplementsList implementsList) {
            for (Node child : implementsList.children()) {

            }
            i++;
        }

        Context newContext = context.newSubType(typeInfo);
        newContext.typeContext().addToContext(typeInfo);

        if (td.get(i) instanceof TypeParameters typeParameters) {
            int j = 1;
            int typeParameterIndex = 0;
            while (j < typeParameters.size()) {
                TypeParameter typeParameter = parseTypeParameter(newContext, typeParameters.get(j), typeInfo,
                        typeParameterIndex);
                typeInfo.builder().addTypeParameter(typeParameter);
                j += 2; // skip the ',' or '>' delimiter
                typeParameterIndex++;
            }
            i++;
        }

        Node body = td.get(i);
        if (body instanceof ClassOrInterfaceBody) {
            List<TypeDeclaration> typeDeclarations = new ArrayList<>();
            List<FieldDeclaration> fieldDeclarations = new ArrayList<>();
            int countCompactConstructors = 0;
            int countNormalConstructors = 0;

            for (Node child : body.children()) {
                if (child instanceof TypeDeclaration cid) typeDeclarations.add(cid);
                else if (child instanceof CompactConstructorDeclaration) ++countCompactConstructors;
                else if (child instanceof ConstructorDeclaration) ++countNormalConstructors;
                else if (child instanceof FieldDeclaration fd) fieldDeclarations.add(fd);
            }

            // FIRST, do subtypes

            for (TypeDeclaration typeDeclaration : typeDeclarations) {
                TypeInfo subTypeInfo = parse(newContext, Either.right(typeInfo), typeDeclaration);
                builder.addSubType(subTypeInfo);
                newContext.typeContext().addToContext(subTypeInfo);
            }

            // THEN, all sorts of methods and constructors

            for (Node child : body.children()) {
                if (child instanceof MethodDeclaration md) {
                    MethodInfo methodInfo = parseMethodDeclaration.parse(newContext, md);
                    builder.addMethod(methodInfo);
                } else if (child instanceof ConstructorDeclaration cd) {
                    MethodInfo constructor = parseConstructorDeclaration.parse(newContext, cd);
                    builder.addConstructor(constructor);
                }
            }

            if (countNormalConstructors == 0 && (typeNature.isClass() || typeNature.isEnum())) {
                boolean privateEmptyConstructor = typeNature.isEnum();
                builder.addConstructor(createEmptyConstructor(typeInfo, privateEmptyConstructor));
            }

            // FINALLY, do the fields
            for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
                FieldInfo field = parseFieldDeclaration.parse(newContext, fieldDeclaration);
                builder.addField(field);
            }
        } else if (body instanceof AnnotationTypeBody) {
            for (Node child : body.children()) {
                if (child instanceof AnnotationMethodDeclaration amd) {
                    MethodInfo methodInfo = parseAnnotationMethodDeclaration.parse(newContext, amd);
                    builder.addMethod(methodInfo);
                }
            }
        } else throw new UnsupportedOperationException("node " + td.get(i).getClass());

        MethodInfo sam = runtime.computeMethodOverrides().computeFunctionalInterface(typeInfo);
        builder.setSingleAbstractMethod(sam);

        context.resolver().add(builder);
        return typeInfo;
    }

    private MethodInfo createEmptyConstructor(TypeInfo typeInfo, boolean privateEmptyConstructor) {
        MethodInfo methodInfo = runtime.newConstructor(typeInfo, runtime.methodTypeSyntheticConstructor());
        MethodInfo.Builder builder = methodInfo.builder();
        builder.setReturnType(runtime.parameterizedTypeReturnTypeOfConstructor());
        builder.setMethodBody(runtime.emptyBlock());
        builder.addMethodModifier(privateEmptyConstructor
                ? runtime.methodModifierPrivate() : runtime.methodModifierPublic());
        builder.setSynthetic(true);
        builder.computeAccess();
        return methodInfo;
    }

    private TypeParameter parseTypeParameter(Context context, Node node, TypeInfo owner, int typeParameterIndex) {
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
                Wildcard wildcard;
                ParameterizedType typeBound = parseType.parse(context, tb.get(1));
                builder.addTypeBound(typeBound);
            } else throw new UnsupportedOperationException();
        }
        return builder.commit();
    }

    private TypeNature getTypeNature(TypeDeclaration td, Token.TokenType tt) {
        return switch (tt) {
            case CLASS -> runtime.typeNatureClass();
            case INTERFACE -> td instanceof AnnotationTypeDeclaration
                    ? runtime.typeNatureAnnotation() : runtime.typeNatureInterface();
            case ENUM -> runtime.typeNatureEnum();
            case RECORD -> runtime.typeNatureRecord();
            default -> null;
        };
    }

    private TypeModifier getTypeModifier(Token.TokenType tt) {
        return switch (tt) {
            case PUBLIC -> runtime.typeModifierPublic();
            case PRIVATE -> runtime.typeModifierPrivate();
            case PROTECTED -> runtime.typeModifierProtected();
            case FINAL -> runtime.typeModifierFinal();
            case SEALED -> runtime.typeModifierSealed();
            case ABSTRACT -> runtime.typeModifierAbstract();
            case NON_SEALED -> runtime.typeModifierNonSealed();
            case STATIC -> runtime.typeModifierStatic();
            default -> null;
        };
    }
}
