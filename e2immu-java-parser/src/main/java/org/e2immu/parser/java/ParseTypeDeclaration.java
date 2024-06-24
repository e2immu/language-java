package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.info.TypeModifier;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeNature;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.api.type.Wildcard;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.support.Either;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ParseTypeDeclaration extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseTypeDeclaration.class);

    public ParseTypeDeclaration(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public TypeInfo parse(Context context,
                          Either<CompilationUnit, TypeInfo> packageNameOrEnclosing,
                          TypeDeclaration td) {
        try {
            return internalParse(context, packageNameOrEnclosing, td);
        } catch (Summary.FailFastException ffe) {
            throw ffe;
        } catch (RuntimeException re) {
            Object where = packageNameOrEnclosing.isLeft() ? packageNameOrEnclosing.getLeft()
                    : packageNameOrEnclosing.getRight();
            LOGGER.error("Caught exception parsing type in {}", where);
            context.summary().addParserError(re);
            return null;
        }
    }

    private TypeInfo internalParse(Context context,
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
                annotations.add(parsers.parseAnnotationExpression().parse(context, a));
            } else if (tdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof MarkerAnnotation a) {
                        annotations.add(parsers.parseAnnotationExpression().parse(context, a));
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

        if (td.get(i) instanceof ExtendsList extendsList) {
            for (int j = 1; j < extendsList.size(); j += 2) {
                ParameterizedType pt = parsers.parseType().parse(newContext, extendsList.get(j));
                if (typeNature.isInterface()) {
                    builder.addInterfaceImplemented(pt);
                } else {
                    builder.setParentClass(pt);
                }
            }
            i++;
        }

        if (td.get(i) instanceof ImplementsList implementsList) {
            for (int j = 1; j < implementsList.size(); j += 2) {
                ParameterizedType pt = parsers.parseType().parse(newContext, implementsList.get(j));
                builder.addInterfaceImplemented(pt);
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
                    MethodInfo methodInfo = parsers.parseMethodDeclaration().parse(newContext, md);
                    if (methodInfo != null) {
                        builder.addMethod(methodInfo);
                    } // else error
                } else if (child instanceof ConstructorDeclaration cd) {
                    MethodInfo constructor = parsers.parseConstructorDeclaration().parse(newContext, cd);
                    if (constructor != null) {
                        builder.addConstructor(constructor);
                    } // else error
                }
            }

            if (countNormalConstructors == 0 && (typeNature.isClass() || typeNature.isEnum())) {
                boolean privateEmptyConstructor = typeNature.isEnum();
                builder.addConstructor(createEmptyConstructor(typeInfo, privateEmptyConstructor));
            }

            // FINALLY, do the fields
            for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
                FieldInfo field = parsers.parseFieldDeclaration().parse(newContext, fieldDeclaration);
                builder.addField(field);
            }
        } else if (body instanceof AnnotationTypeBody) {
            for (Node child : body.children()) {
                if (child instanceof AnnotationMethodDeclaration amd) {
                    MethodInfo methodInfo = parsers.parseAnnotationMethodDeclaration().parse(newContext, amd);
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
                ParameterizedType typeBound = parsers.parseType().parse(context, tb.get(1));
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
