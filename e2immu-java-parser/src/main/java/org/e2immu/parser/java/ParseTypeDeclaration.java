package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.info.TypeModifier;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeNature;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.language.inspection.api.parser.TypeContext;
import org.e2immu.support.Either;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParseTypeDeclaration extends CommonParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseTypeDeclaration.class);

    public ParseTypeDeclaration(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public TypeInfo parse(Context context,
                          TypeInfo typeInfoOrNull,
                          Either<CompilationUnit, TypeInfo> packageNameOrEnclosing,
                          Map<String, TypeInfo> typeInfoMap,
                          TypeDeclaration td) {
        try {
            return internalParse(context, typeInfoOrNull, packageNameOrEnclosing, typeInfoMap, td);
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

    record RecordField(List<Comment> comments, Source source, FieldInfo fieldInfo, boolean varargs) {
    }

    record ConstructorCounts(int normal, int compact) {
    }

    private TypeInfo internalParse(Context context,
                                   TypeInfo typeInfoOrNull,
                                   Either<CompilationUnit, TypeInfo> packageNameOrEnclosing,
                                   Map<String, TypeInfo> typeInfoMap,
                                   TypeDeclaration td) {
        List<Comment> comments = comments(td);

        int i = 0;
        List<AnnotationExpression> annotations = new ArrayList<>();
        TypeNature typeNature = null;
        List<TypeModifier> typeModifiers = new ArrayList<>();
        while (true) {
            Node tdi = td.get(i);
            if (tdi instanceof Annotation a) {
                annotations.add(parsers.parseAnnotationExpression().parse(context, a));
            } else if (tdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof Annotation a) {
                        annotations.add(parsers.parseAnnotationExpression().parse(context, a));
                    } else if (node instanceof KeyWord keyWord) {
                        typeModifiers.add(getTypeModifier(keyWord.getType()));
                    }
                }
            } else if (tdi instanceof KeyWord keyWord) {
                TypeModifier tm = getTypeModifier(keyWord.getType());
                if (tm != null) typeModifiers.add(tm);
                TypeNature tn = getTypeNature(td, keyWord.getType());
                if (tn != null) {
                    assert typeNature == null;
                    typeNature = tn;
                }
            } else if (tdi instanceof Delimiter) {
                if (!Token.TokenType.AT.equals(tdi.getType())) {
                    throw new Summary.ParseException(context.info(), "Expect @ delimiter");
                }
            } else break;
            i++;
        }
        if (typeNature == null) throw new UnsupportedOperationException("Have not determined type nature");
        String simpleName;
        if (td.get(i) instanceof Identifier identifier) {
            simpleName = identifier.getSource();
            i++;
        } else throw new UnsupportedOperationException();
        TypeInfo typeInfo;

        if (packageNameOrEnclosing.isLeft() && typeInfoOrNull != null && typeInfoOrNull.simpleName().equals(simpleName)) {
            typeInfo = typeInfoOrNull; // we must re-use this object!!
        } else {
            String fqn = fullyQualifiedName(packageNameOrEnclosing, simpleName);
            typeInfo = typeInfoMap.get(fqn);
        }

        assert typeInfo != null;
        Source source = source(typeInfo, "", td);
        TypeInfo.Builder builder = typeInfo.builder();
        builder.addComments(comments);
        builder.setSource(source);
        typeModifiers.forEach(builder::addTypeModifier);
        builder.addAnnotations(annotations);
        builder.computeAccess();
        builder.setTypeNature(typeNature);
        builder.setSource(source(typeInfo, null, td));
        builder.setEnclosingMethod(context.enclosingMethod());

        Context newContext = context.newSubType(typeInfo);
        newContext.typeContext().addToContext(typeInfo);

        collectNamesOfSubTypesIntoTypeContext(newContext.typeContext(), typeInfoMap, typeInfo);

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

        List<RecordField> recordFields;
        if (td.get(i) instanceof RecordHeader rh) {
            assert typeNature.isRecord();
            recordFields = new ArrayList<>();
            for (int j = 1; j < rh.size(); j += 2) {
                Node rhj = rh.get(j);
                if (rhj instanceof Delimiter) break; // empty parameter list
                if (rhj instanceof RecordComponent rc) {
                    recordFields.add(parseRecordField(context, typeInfo, rc));
                } else throw new Summary.ParseException(context.info(), "Expected record component");
            }
            i++;
        } else {
            recordFields = null;
        }

        builder.setParentClass(runtime.objectParameterizedType());
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
        if (td.get(i) instanceof PermitsList permitsList) {
            for (int j = 1; j < permitsList.size(); j += 2) {
                ParameterizedType pt = parsers.parseType().parse(newContext, permitsList.get(j));
                builder.addPermittedType(pt.typeInfo());
            }
            i++;
        }

        Node body = td.get(i);
        ConstructorCounts constructorCounts;
        if (body instanceof ClassOrInterfaceBody || body instanceof RecordBody || body instanceof EnumBody) {
            Context contextForBody = newContext.newTypeBody();
            if (body instanceof RecordBody) {
                assert recordFields != null;
                for (RecordField rf : recordFields) {
                    typeInfo.builder().addField(rf.fieldInfo);
                    FieldReference fr = runtime.newFieldReference(rf.fieldInfo);
                    contextForBody.variableContext().add(fr);
                }
            } else if (body instanceof EnumBody) {
                List<FieldInfo> enumFields = new ArrayList<>();
                ParameterizedType type = typeInfo.asSimpleParameterizedType();
                for (Node child : body.children()) {
                    if (child instanceof EnumConstant ec) {
                        String name = ec.get(0).getSource();
                        FieldInfo fieldInfo = runtime.newFieldInfo(name, true, type, typeInfo);
                        fieldInfo.builder()
                                .setSynthetic(true) // to distinguish them from normal, non-enum fields
                                .addFieldModifier(runtime.fieldModifierFinal())
                                .addFieldModifier(runtime.fieldModifierPublic())
                                .addFieldModifier(runtime.fieldModifierStatic());
                        // register evaluation of parameters as an object creation for the field
                        builder.addField(fieldInfo);
                        enumFields.add(fieldInfo);
                        contextForBody.variableContext().add(runtime.newFieldReference(fieldInfo));
                        if (ec.size() >= 2 && ec.get(1) instanceof InvocationArguments ia) {
                            context.resolver().add(fieldInfo, fieldInfo.builder(), context.newForwardType(typeInfo.asSimpleParameterizedType()),
                                    null, ia, newContext);
                        }
                    }
                }
                TypeInfo enumTypeInfo = runtime.getFullyQualified("java.lang.Enum", true);
                builder.setParentClass(runtime.newParameterizedType(enumTypeInfo, List.of(typeInfo.asSimpleParameterizedType())));
                new EnumSynthetics(runtime, typeInfo, builder).create(context, enumFields);
            }
            constructorCounts = parseBody(contextForBody, typeInfoMap, body, typeNature, typeInfo, builder);
        } else if (body instanceof AnnotationTypeBody) {
            for (Node child : body.children()) {
                if (child instanceof AnnotationMethodDeclaration amd) {
                    MethodInfo methodInfo = parsers.parseAnnotationMethodDeclaration().parse(newContext, amd);
                    builder.addMethod(methodInfo);
                }
            }
            constructorCounts = new ConstructorCounts(0, 0);
        } else throw new UnsupportedOperationException("node " + td.get(i).getClass());

        context.resolver().add(builder);

        /*
        Ensure a constructor when the type is a record and there are no compact constructors.
        (those without arguments, as in 'public record SomeRecord(...) { public Record { this.field = } ... }' )
        and also no default constructor override.
        The latter condition is verified in the builder.ensureConstructor() method.
         */
        if (typeNature.isRecord()) {
            RecordSynthetics rs = new RecordSynthetics(runtime, typeInfo);
            assert recordFields != null;
            if (constructorCounts.normal == 0 && constructorCounts.compact == 0) {
                MethodInfo cc = rs.createSyntheticConstructor(source, recordFields);
                builder.addConstructor(cc);
            }
            // finally, add synthetic methods if needed
            rs.createAccessors(recordFields).forEach(builder::addMethod);
        }
        return typeInfo;
    }

    private String fullyQualifiedName(Either<CompilationUnit, TypeInfo> packageNameOrEnclosing, String simpleName) {
        String prefix;
        if (packageNameOrEnclosing.isLeft()) {
            if (packageNameOrEnclosing.getLeft().packageName().isEmpty()) {
                prefix = "";
            } else {
                prefix = packageNameOrEnclosing.getLeft().packageName() + ".";
            }
        } else {
            prefix = packageNameOrEnclosing.getRight().fullyQualifiedName() + ".";
        }
        return prefix + simpleName;
    }

    /*
     Important: we'll be creating TypeInfo objects, which we MUST re-use!
     */
    private void collectNamesOfSubTypesIntoTypeContext(TypeContext typeContext,
                                                       Map<String, TypeInfo> typeInfoMap,
                                                       TypeInfo typeInfo) {
        String fullyQualified = typeInfo.fullyQualifiedName();
        typeInfoMap.forEach((fqn, ti) -> {
            if (fqn.startsWith(fullyQualified)) {
                typeContext.addToContext(ti);
            }
        });
    }

    private RecordField parseRecordField(Context context, TypeInfo typeInfo, RecordComponent rc) {
        int i = 0;
        ParameterizedType pt;
        if (rc.get(i) instanceof Type type) {
            pt = parsers.parseType().parse(context, type);
            i++;
        } else throw new Summary.ParseException(typeInfo, "Expected type in record component");
        boolean varargs;
        ParameterizedType ptWithVarArgs;
        if (rc.get(i) instanceof Delimiter d && Token.TokenType.VAR_ARGS.equals(d.getType())) {
            varargs = true;
            ptWithVarArgs = pt.copyWithArrays(pt.arrays() + 1);
            i++;
        } else {
            varargs = false;
            ptWithVarArgs = pt;
        }
        String name;
        if (rc.get(i) instanceof Identifier identifier) {
            name = identifier.getSource();
        } else {
            throw new Summary.ParseException(typeInfo, "Expected identifier in record component");
        }
        FieldInfo fieldInfo = runtime.newFieldInfo(name, false, ptWithVarArgs, typeInfo);
        Source source = source(fieldInfo, "", rc);
        List<Comment> comments = comments(rc);
        return new RecordField(comments, source, fieldInfo, varargs);
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

    ConstructorCounts parseBody(Context newContext,
                                Map<String, TypeInfo> typeInfoMap,
                                Node body,
                                TypeNature typeNature,
                                TypeInfo typeInfo,
                                TypeInfo.Builder builder) {
        List<TypeDeclaration> typeDeclarations = new ArrayList<>();
        List<FieldDeclaration> fieldDeclarations = new ArrayList<>();
        int countCompactConstructors = 0;
        int countNormalConstructors = 0;

        for (Node child : body.children()) {
            if (!(child instanceof EmptyDeclaration)) {
                if (child instanceof TypeDeclaration cid) typeDeclarations.add(cid);
                else if (child instanceof CompactConstructorDeclaration) ++countCompactConstructors;
                else if (child instanceof ConstructorDeclaration) ++countNormalConstructors;
                else if (child instanceof FieldDeclaration fd) fieldDeclarations.add(fd);
            }
        }

        // FIRST, parse the subtypes

        for (TypeDeclaration typeDeclaration : typeDeclarations) {
            TypeInfo subTypeInfo = parse(newContext, null, Either.right(typeInfo), typeInfoMap, typeDeclaration);
            builder.addSubType(subTypeInfo);
            newContext.typeContext().addToContext(subTypeInfo);
        }

        // THEN, all sorts of methods and constructors

        for (Node child : body.children()) {
            if (!(child instanceof EmptyDeclaration)) {
                if (child instanceof MethodDeclaration md) {
                    MethodInfo methodInfo = parsers.parseMethodDeclaration().parse(newContext, md);
                    if (methodInfo != null) {
                        builder.addMethod(methodInfo);
                    } // else error
                } else if (child instanceof ConstructorDeclaration cd) {
                    MethodInfo constructor = parsers.parseMethodDeclaration().parse(newContext, cd);
                    if (constructor != null) {
                        builder.addConstructor(constructor);
                    } // else error
                }
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

        MethodInfo sam = runtime.computeMethodOverrides().computeFunctionalInterface(typeInfo);
        builder.setSingleAbstractMethod(sam);

        return new ConstructorCounts(countNormalConstructors, countCompactConstructors);
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
