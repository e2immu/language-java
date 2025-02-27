package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.element.DetailedSources;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeNature;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.language.inspection.api.parser.TypeContext;
import org.e2immu.language.inspection.api.util.GetSetUtil;
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
    private final GetSetUtil getSetUtil;

    public ParseTypeDeclaration(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
        this.getSetUtil = new GetSetUtil(runtime);
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

    record RecordField(List<Comment> comments, Source source, FieldInfo fieldInfo, boolean varargs) {
    }

    private TypeInfo internalParse(Context context,
                                   Either<CompilationUnit, TypeInfo> packageNameOrEnclosing,
                                   TypeDeclaration td) {
        List<Comment> comments = comments(td);
        DetailedSources.Builder detailedSourcesBuilder = context.newDetailedSourcesBuilder();

        int i = 0;
        // in the annotations, we can refer to our own static fields, so we wait a little to parse them
        // See TestAnnotations,3
        List<Annotation> annotationsToParse = new ArrayList<>();
        while (true) {
            Node tdi = td.get(i);
            if (tdi instanceof Annotation a) {
                annotationsToParse.add(a);
            } else if (tdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof Annotation a) {
                        annotationsToParse.add(a);
                    }
                }
            } else if (tdi instanceof Delimiter) {
                if (!Token.TokenType.AT.equals(tdi.getType())) {
                    throw new Summary.ParseException(context.info(), "Expect @ delimiter");
                }
            } else if (!(tdi instanceof KeyWord)) {
                break;
            }
            i++;
        }
        String simpleName;
        Identifier identifier = (Identifier) td.get(i);
        simpleName = identifier.getSource();
        i++;

        // during the "scan" phase, we have already created all the TypeInfo objects
        String fqn = fullyQualifiedName(packageNameOrEnclosing, simpleName);
        TypeInfo typeInfo = (TypeInfo) context.typeContext().get(fqn, true);
        assert typeInfo != null;

        if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(typeInfo.simpleName(), source(identifier));

        Source source = source(typeInfo, "", td);
        TypeInfo.Builder builder = typeInfo.builder();
        builder.addComments(comments);
        builder.computeAccess();
        builder.setSource(source(typeInfo, null, td));
        builder.setEnclosingMethod(context.enclosingMethod());

        Context newContext = context.newSubType(typeInfo);
        newContext.typeContext().addToContext(typeInfo);

        collectNamesOfSubTypesIntoTypeContext(newContext.typeContext(), typeInfo.primaryType());

        List<Node> typeParametersToParse = new ArrayList<>();

        if (td.get(i) instanceof TypeParameters typeParameters) {
            int j = 1;
            while (j < typeParameters.size()) {
                typeParametersToParse.add(typeParameters.get(j));
                j += 2; // skip the ',' or '>' delimiter
            }
            i++;
        }

        if (!typeParametersToParse.isEmpty()) {
            TypeParameter[] typeParameters = resolveTypeParameters(typeParametersToParse, newContext, typeInfo,
                    detailedSourcesBuilder);
            for (TypeParameter typeParameter : typeParameters) {
                builder.addOrSetTypeParameter(typeParameter);
            }
        }

        TypeNature typeNature = builder.typeNature();
        List<RecordField> recordFields;
        if (td.get(i) instanceof RecordHeader rh) {
            assert typeNature.isRecord();
            recordFields = new ArrayList<>();
            for (int j = 1; j < rh.size(); j += 2) {
                Node rhj = rh.get(j);
                if (rhj instanceof Delimiter) break; // empty parameter list
                if (rhj instanceof RecordComponent rc) {
                    recordFields.add(parseRecordField(newContext, typeInfo, rc));
                } else {
                    throw new Summary.ParseException(newContext.info(), "Expected record component");
                }
            }
            if (detailedSourcesBuilder != null) {
                detailedSourcesBuilder.put(DetailedSources.END_OF_PARAMETER_LIST, source(rh.get(rh.size() - 1)));
            }
            i++;
        } else {
            recordFields = null;
        }

        builder.setParentClass(runtime.objectParameterizedType());
        if (td.get(i) instanceof ExtendsList extendsList) {
            for (int j = 1; j < extendsList.size(); j += 2) {
                ParameterizedType pt = parsers.parseType().parse(newContext, extendsList.get(j), detailedSourcesBuilder);
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
                ParameterizedType pt = parsers.parseType().parse(newContext, implementsList.get(j), detailedSourcesBuilder);
                builder.addInterfaceImplemented(pt);
            }
            i++;
        }
        newContext.typeContext().addSubTypesOfHierarchy(typeInfo);

        if (td.get(i) instanceof PermitsList permitsList) {
            for (int j = 1; j < permitsList.size(); j += 2) {
                ParameterizedType pt = parsers.parseType().parse(newContext, permitsList.get(j), detailedSourcesBuilder);
                builder.addPermittedType(pt.typeInfo());
            }
            i++;
        }

        Node body = td.get(i);
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
                                .setInitializer(runtime.newEmptyExpression())
                                .addFieldModifier(runtime.fieldModifierFinal())
                                .addFieldModifier(runtime.fieldModifierPublic())
                                .addFieldModifier(runtime.fieldModifierStatic())
                                .computeAccess();
                        // register evaluation of parameters as an object creation for the field
                        builder.addField(fieldInfo);
                        enumFields.add(fieldInfo);
                        contextForBody.variableContext().add(runtime.newFieldReference(fieldInfo));
                        if (ec.size() >= 2 && ec.get(1) instanceof InvocationArguments ia) {
                            newContext.resolver().add(fieldInfo, fieldInfo.builder(), newContext.newForwardType(typeInfo.asSimpleParameterizedType()),
                                    null, ia, newContext);
                        } else {
                            fieldInfo.builder().commit();
                        }
                    }
                }
                TypeInfo enumTypeInfo = runtime.getFullyQualified("java.lang.Enum", true);
                builder.setParentClass(runtime.newParameterizedType(enumTypeInfo, List.of(typeInfo.asSimpleParameterizedType())));
                new EnumSynthetics(runtime, typeInfo, builder).create(newContext, enumFields);
            }
            parseBody(contextForBody, body, typeNature, typeInfo, builder);
        } else if (body instanceof AnnotationTypeBody) {
            for (Node child : body.children()) {
                if (child instanceof TypeDeclaration subTd) {
                    TypeInfo subTypeInfo = parse(newContext, Either.right(typeInfo), subTd);
                    newContext.typeContext().addToContext(subTypeInfo);
                }
            }
            for (Node child : body.children()) {
                if (child instanceof AnnotationMethodDeclaration amd) {
                    MethodInfo methodInfo = parsers.parseAnnotationMethodDeclaration().parse(newContext, amd);
                    builder.addMethod(methodInfo);
                }
            }
        } else throw new UnsupportedOperationException("node " + td.get(i).getClass());

        newContext.resolver().add(builder);

        // now that we know the fields, we can safely parse the annotation expressions
        for (Annotation annotation : annotationsToParse) {
            builder.addAnnotation(parsers.parseAnnotationExpression().parse(context, annotation));
        }

        /*
        Ensure a constructor when the type is a record and there are no compact constructors.
        (those without arguments, as in 'public record SomeRecord(...) { public Record { this.field = } ... }' )
        and also no default constructor override.
        The latter condition is verified in the builder.ensureConstructor() method.
         */
        if (typeNature.isRecord()) {
            RecordSynthetics rs = new RecordSynthetics(runtime, typeInfo);
            assert recordFields != null;
            if (!haveConstructorMatchingFields(builder)) {
                MethodInfo cc = rs.createSyntheticConstructor(source, recordFields);
                builder.addConstructor(cc);
            }
            // finally, add synthetic methods if needed
            rs.createAccessors(recordFields).forEach(builder::addMethod);
        }

        if (typeNature.isInterface() || typeNature.isClass() && builder.isAbstract()) {
            getSetUtil.createSyntheticFields(typeInfo);
        }

        builder.setSource(detailedSourcesBuilder == null ? source : source.withDetailedSources(detailedSourcesBuilder.build()));
        return typeInfo;
    }

    private boolean haveConstructorMatchingFields(TypeInfo.Builder builder) {
        return builder.constructors().stream().anyMatch(mi -> {
            if (mi.parameters().size() != builder.fields().size()) return false;
            int i = 0;
            for (FieldInfo fieldInfo : builder.fields()) {
                ParameterInfo pi = mi.parameters().get(i);
                if (!pi.parameterizedType().equals(fieldInfo.type())) return false;
                i++;
            }
            return true;
        });
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
    private void collectNamesOfSubTypesIntoTypeContext(TypeContext typeContext, TypeInfo typeInfo) {
        typeContext.addToContext(typeInfo);
        // add direct children
        for (TypeInfo subType : typeInfo.subTypes()) {
            collectNamesOfSubTypesIntoTypeContext(typeContext, subType);
        }
    }

    private RecordField parseRecordField(Context context, TypeInfo typeInfo, RecordComponent rc) {
        int i = 0;
        DetailedSources.Builder detailedSourcesBuilder = context.newDetailedSourcesBuilder();

        List<AnnotationExpression> annotations = new ArrayList<>();
        while (true) {
            Node tdi = rc.get(i);
            if (tdi instanceof Annotation a) {
                AnnotationExpression ae = parsers.parseAnnotationExpression().parse(context, a);
                annotations.add(ae);
                if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(ae, source(a));
            } else if (tdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof Annotation a) {
                        AnnotationExpression ae = parsers.parseAnnotationExpression().parse(context, a);
                        annotations.add(ae);
                        if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(ae, source(a));
                    }
                }
            } else break;
            i++;
        }

        ParameterizedType pt;
        if (rc.get(i) instanceof Type type) {
            pt = parsers.parseType().parse(context, type, detailedSourcesBuilder);
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
            if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(name, source(identifier));
        } else {
            throw new Summary.ParseException(typeInfo, "Expected identifier in record component");
        }
        FieldInfo fieldInfo = runtime.newFieldInfo(name, false, ptWithVarArgs, typeInfo);
        fieldInfo.builder()
                .setSource(source(rc))
                .setInitializer(runtime.newEmptyExpression())
                .addFieldModifier(runtime.fieldModifierPrivate())
                .addFieldModifier(runtime.fieldModifierFinal())
                .addAnnotations(annotations)
                .computeAccess()
                .commit();
        Source source1 = source(fieldInfo, "", rc);
        Source source = detailedSourcesBuilder == null ? source1 : source1.withDetailedSources(detailedSourcesBuilder.build());
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
        builder.commit();
        return methodInfo;
    }

    void parseBody(Context newContext,
                   Node body,
                   TypeNature typeNature,
                   TypeInfo typeInfo,
                   TypeInfo.Builder builder) {
        List<TypeDeclaration> typeDeclarations = new ArrayList<>();
        List<FieldDeclaration> fieldDeclarations = new ArrayList<>();
        int countNormalConstructors = 0;
        int countStaticInializers = 0;

        for (Node child : body.children()) {
            if (!(child instanceof EmptyDeclaration)) {
                if (child instanceof TypeDeclaration cid) typeDeclarations.add(cid);
                else if (child instanceof ConstructorDeclaration && !(child instanceof CompactConstructorDeclaration)) {
                    ++countNormalConstructors;
                } else if (child instanceof FieldDeclaration fd) fieldDeclarations.add(fd);
            }
        }

        // FIRST, parse the subtypes (but do not add them as a subtype, that has already been done in the
        // scan phase

        for (TypeDeclaration typeDeclaration : typeDeclarations) {
            TypeInfo subTypeInfo = parse(newContext, Either.right(typeInfo), typeDeclaration);
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
                } else if (child instanceof Initializer i) {
                    boolean staticInitializer = Token.TokenType.STATIC.equals(i.get(0).getType());
                    if (staticInitializer) {
                        if (i.get(1) instanceof CodeBlock cb) {
                            Source cbSource = source(typeInfo, "", cb);
                            String name = "<static_" + (countStaticInializers++) + ">";
                            MethodInfo staticMethod = runtime.newMethod(typeInfo, name,
                                    runtime.methodTypeStaticBlock());
                            staticMethod.builder()
                                    .setReturnType(runtime.voidParameterizedType())
                                    .setAccess(runtime.accessPrivate())
                                    .commitParameters();
                            newContext.resolver().add(staticMethod, staticMethod.builder(),
                                    newContext.emptyForwardType(), null, cb, newContext);
                            builder.addMethod(staticMethod);
                        } else {
                            throw new Summary.ParseException(newContext.info(), "Unknown node in static initializer");
                        }
                    } else if (i.get(0) instanceof CodeBlock cb) {
                        Context initializerContext = newContext.newSubType(typeInfo);
                        MethodInfo constructor = runtime.newConstructor(typeInfo);
                        constructor.builder()
                                .setReturnType(runtime.parameterizedTypeReturnTypeOfConstructor())
                                .setAccess(runtime.accessPublic())
                                .commitParameters();
                        builder.addConstructor(constructor);
                        initializerContext.resolver().add(constructor, constructor.builder(),
                                initializerContext.emptyForwardType(), null, cb, initializerContext);
                        countNormalConstructors++;
                    } else throw new Summary.ParseException(newContext.info(), "Unknown initializer");
                }
            }
        }

        if (countNormalConstructors == 0 && (typeNature.isClass() || typeNature.isEnum())) {
            boolean privateEmptyConstructor = typeNature.isEnum();
            builder.addConstructor(createEmptyConstructor(typeInfo, privateEmptyConstructor));
        }

        // FINALLY, do the fields
        for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
            parsers.parseFieldDeclaration().parse(newContext, fieldDeclaration).forEach(builder::addField);
        }

        MethodInfo sam = runtime.computeMethodOverrides().computeFunctionalInterface(typeInfo);
        builder.setSingleAbstractMethod(sam);
    }

}
