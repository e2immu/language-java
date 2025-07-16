package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.*;
import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.element.RecordPattern;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.info.TypeModifier;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.SwitchEntry;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeNature;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.parser.java.util.JavaDocParser;
import org.e2immu.support.Either;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.parsers.java.ast.MultiLineComment;
import org.parsers.java.ast.SingleLineComment;

import java.util.*;

public abstract class CommonParse {
    protected final Runtime runtime;
    protected final Parsers parsers;

    protected CommonParse(Runtime runtime, Parsers parsers) {
        this.runtime = runtime;
        this.parsers = parsers;
    }

    /*
    Note: we're not using Node.getAllTokens(), because that method recurses down unconditionally
     */
    public List<Comment> comments(Node node) {
        return comments(node, null, null, null);
    }

    public List<Comment> comments(Node node, Context context, Info info, Info.Builder<?> infoBuilder) {
        Node.TerminalNode tn = firstTerminal(node);
        if (tn != null) {
            return tn.precedingUnparsedTokens().stream()
                    .map(t -> {
                        if (t instanceof SingleLineComment slc) {
                            return runtime.newSingleLineComment(source(slc), slc.getSource());
                        }
                        if (t instanceof MultiLineComment multiLineComment) {
                            if (multiLineComment.getSource().startsWith("/**")) {
                                return parseJavaDoc(multiLineComment, source(multiLineComment), context, info, infoBuilder);
                            }
                            return runtime.newMultilineComment(source(multiLineComment), multiLineComment.getSource());
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }
        return List.of();
    }

    private Comment parseJavaDoc(MultiLineComment multiLineComment,
                                 Source source,
                                 Context context,
                                 Info info,
                                 Info.Builder<?> infoBuilder) {
        JavaDoc javaDoc = new JavaDocParser(runtime).extractTags(multiLineComment.getSource(), source);
        if (context != null) {
            context.resolver().addJavadoc(info, infoBuilder, context, javaDoc);
        }
        return javaDoc;
    }


    private Node.TerminalNode firstTerminal(Node node) {
        if (node instanceof Node.TerminalNode tn) return tn;
        for (Node child : node) {
            Node.TerminalNode tn = firstTerminal(child);
            if (tn != null) return tn;
        }
        return null;
    }

    /*
    this implementation gives an "imperfect" parent... See e.g. parseBlock: we cannot pass on the parent during
    parsing, because we still have the builder at that point in time.
     */
    public Source source(String index, Node node) {
        return runtime.newParserSource(index, node.getBeginLine(), node.getBeginColumn(), node.getEndLine(),
                node.getEndColumn());
    }

    // meant for detailed sources
    public Source source(Node node) {
        return runtime.newParserSource("", node.getBeginLine(), node.getBeginColumn(),
                node.getEndLine(), node.getEndColumn());
    }

    public Source source(Node beginNode, Node endNodeIncl) {
        return runtime.newParserSource("", beginNode.getBeginLine(), beginNode.getBeginColumn(),
                endNodeIncl.getEndLine(), endNodeIncl.getEndColumn());
    }

    // meant for detailed sources
    public Source source(Node node, int start, int end) {
        Node s = node.get(start);
        Node e = node.get(end);
        return runtime.newParserSource("", s.getBeginLine(), s.getBeginColumn(),
                e.getEndLine(), e.getEndColumn());
    }

    // code copied from ParseTypeDeclaration
    private TypeParameter parseTypeParameter(Context context, Node node, Info owner, int typeParameterIndex,
                                             DetailedSources.Builder detailedSourcesBuilder) {
        String name;
        List<AnnotationExpression> annotations = new ArrayList<>();
        int i = 0;
        if (node instanceof Identifier) {
            name = node.getSource();
        } else if (node instanceof org.parsers.java.ast.TypeParameter tp) {
            while (tp.get(i) instanceof Annotation a) {
                // FIXME for now, we're not delaying the evaluation of annotations here to the resolution phase
                annotations.add(parsers.parseAnnotationExpression().parseDirectly(context, a));
                i++;
            }
            if (tp.get(i) instanceof Identifier id) {
                name = id.getSource();
                i++;
            } else throw new Summary.ParseException(context, "Expected Identifier");
        } else throw new Summary.ParseException(context, "Expected Identifier or TypeParameter");
        TypeParameter typeParameter = runtime.newTypeParameter(comments(node), source(node), List.copyOf(annotations),
                typeParameterIndex, name, owner);
        context.typeContext().addToContext(typeParameter);
        // do type bounds
        TypeParameter.Builder builder = typeParameter.builder();
        if (node instanceof org.parsers.java.ast.TypeParameter tp) {
            if (i < tp.size()) {
                if (tp.get(i) instanceof TypeBound tb) {
                    ParameterizedType typeBound = parsers.parseType().parse(context, tb.get(1), false,
                            detailedSourcesBuilder);
                    if (typeBound == null) {
                        return null;
                    }
                    builder.addTypeBound(typeBound);
                } else throw new UnsupportedOperationException();
            }
        }
        return builder.commit();
    }

    protected Map<String, TypeInfo> recursivelyFindTypes(Either<CompilationUnit, TypeInfo> parent,
                                                         TypeInfo typeInfoOrNull,
                                                         Node body,
                                                         boolean addDetailedSources) {
        Map<String, TypeInfo> map = new HashMap<>();
        for (Node node : body) {
            if (node instanceof TypeDeclaration td && !(node instanceof EmptyDeclaration)) {
                handleTypeDeclaration(parent, typeInfoOrNull, td, map, addDetailedSources);
            }
        }
        return Map.copyOf(map);
    }

    /*
    We extract type nature and type modifiers here, because we'll need them in sibling subtype declarations.
     */
    private void handleTypeDeclaration(Either<CompilationUnit, TypeInfo> enclosing,
                                       TypeInfo typeInfoOrNull,
                                       TypeDeclaration td,
                                       Map<String, TypeInfo> map,
                                       boolean addDetailedSources) {
        String typeName = td.firstChildOfType(Identifier.class).getSource();
        TypeInfo typeInfo;
        if (enclosing.isLeft()) {
            if (typeInfoOrNull != null) {
                typeInfo = typeInfoOrNull;
                assert typeInfo.simpleName().equals(typeName);
            } else {
                typeInfo = runtime.newTypeInfo(enclosing.getLeft(), typeName);
            }
        } else {
            TypeInfo enclosingType = enclosing.getRight();
            typeInfo = runtime.newTypeInfo(enclosingType, typeName);
            enclosingType.builder().addSubType(typeInfo);
        }
        Node sub = handleTypeModifiers(td, typeInfo, addDetailedSources);
        map.put(typeInfo.fullyQualifiedName(), typeInfo);
        if (sub != null) {
            map.putAll(recursivelyFindTypes(Either.right(typeInfo), typeInfoOrNull, sub, addDetailedSources));
        }
    }

    // also called for local type declarations
    protected Node handleTypeModifiers(TypeDeclaration td, TypeInfo typeInfo, boolean addDetailedSources) {
        DetailedSources.Builder detailedSourcesBuilder = addDetailedSources ? runtime.newDetailedSourcesBuilder() : null;

        Identifier identifier = null;
        Node sub = null;
        TypeNature typeNature = null;
        List<TypeModifier> typeModifiers = new ArrayList<>();
        for (Node child : td.children()) {
            if (child instanceof Modifiers modifiers) {
                for (Node child2 : modifiers) {
                    if (child2 instanceof KeyWord keyWord) {
                        TypeNature tn = getTypeNature(td, keyWord.getType());
                        if (tn != null) {
                            assert typeNature == null;
                            typeNature = tn;
                        }
                        TypeModifier tm = getTypeModifier(keyWord.getType());
                        if (tm != null) {
                            typeModifiers.add(tm);
                            if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(tm, source(keyWord));
                        }
                    }
                }
            } else if (child instanceof KeyWord keyWord) {
                TypeNature tn = getTypeNature(td, keyWord.getType());
                if (tn != null) {
                    assert typeNature == null;
                    typeNature = tn;
                    if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(typeNature, source(keyWord));
                }
                TypeModifier tm = getTypeModifier(keyWord.getType());
                if (tm != null) {
                    typeModifiers.add(tm);
                    if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(tm, source(keyWord));
                }
            } else if (child instanceof Identifier id) {
                identifier = id;
            } else if (child instanceof ClassOrInterfaceBody
                    || child instanceof AnnotationTypeBody
                    || child instanceof RecordBody
                    || child instanceof EnumBody) {
                sub = child;
                break;
            }
        }
        assert identifier != null;

        Source source = source(td);
        typeInfo.builder()
                .setTypeNature(typeNature)
                .setSource(detailedSourcesBuilder == null ? source
                        : source.withDetailedSources(detailedSourcesBuilder.build()));
        typeModifiers.forEach(typeInfo.builder()::addTypeModifier);
        return sub;
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


    protected TypeParameter[] resolveTypeParameters(List<Node> typeParametersToParse, Context contextWithTP, Info owner,
                                                    DetailedSources.Builder detailedSourcesBuilder) {
        int infiniteLoopProtection = typeParametersToParse.size() + 2;
        boolean resolved = false;
        TypeParameter[] typeParameters = new TypeParameter[typeParametersToParse.size()];
        while (infiniteLoopProtection >= 0 && !resolved) {
            resolved = true;
            int tpIndex = 0;
            for (Node unparsedTypeParameter : typeParametersToParse) {
                if (typeParameters[tpIndex] == null) {
                    TypeParameter typeParameter = parseTypeParameter(contextWithTP, unparsedTypeParameter, owner,
                            tpIndex, detailedSourcesBuilder);
                    if (typeParameter != null) {
                        // paresTypeParameter has added it to the contextWithTP
                        typeParameters[tpIndex] = typeParameter;
                    } else {
                        resolved = false;
                    }
                }
                tpIndex++;
            }
            infiniteLoopProtection--;
        }
        if (infiniteLoopProtection <= 0) {
            throw new UnsupportedOperationException("Hit infinite loop protection");
        }
        return typeParameters;
    }


    protected void parseAnnotations(Context context, Info.Builder<?> builder, List<Annotation> annotations) {
        int annotationIndex = 0;
        for (Annotation annotation : annotations) {
            builder.addAnnotation(parsers.parseAnnotationExpression().parse(context, builder, annotation,
                    annotationIndex++));
        }
    }


    protected void parseNewSwitchLabel(String index, NewSwitchLabel nsl, Context newContext, SwitchEntry.Builder entryBuilder, ForwardType selectorTypeFwd) {
        org.e2immu.language.cst.api.expression.Expression whenExpression = runtime.newEmptyExpression();
        List<org.e2immu.language.cst.api.expression.Expression> conditions = new ArrayList<>();
        int j = 0;
        while (j < nsl.size()) {
            Node node = nsl.get(j);
            switch (node) {
                case LocalVariableDeclaration lvd -> {
                    RecordPattern recordPattern = parsers.parseRecordPattern()
                            .parseLocalVariableDeclaration(newContext, lvd, 0, null);
                    entryBuilder.setPatternVariable(recordPattern);
                }
                case org.parsers.java.ast.RecordPattern rp -> {
                    RecordPattern recordPattern = parsers.parseRecordPattern().parseRecordPattern(newContext, rp);
                    entryBuilder.setPatternVariable(recordPattern);
                }
                case WhenClause whenClause -> {
                    ForwardType booleanTypeFwd = newContext.newForwardType(runtime.booleanParameterizedType());
                    whenExpression = parsers.parseExpression().parse(newContext, index, booleanTypeFwd, whenClause.get(1));
                }
                case KeyWord kw -> {
                    if (Token.TokenType._DEFAULT.equals(kw.getType())) {
                        conditions.add(runtime.newEmptyExpression());
                    } // else: ignore: case, (, ), ...
                }
                default -> {
                    Expression c = parsers.parseExpression().parse(newContext, index, selectorTypeFwd, node);
                    conditions.add(c);
                }
            }
            Node next = nsl.get(j + 1);
            if (Token.TokenType.COMMA.equals(next.getType())) {
                j += 2;
            } else if (Token.TokenType.LAMBDA.equals(next.getType())) {
                break;
            } else {
                j++;
            }
        }
        entryBuilder.addConditions(conditions);
        entryBuilder.setWhenExpression(whenExpression);
    }


    // code structurally similar to code in ParseType.parseObjectType
    protected List<DetailedSources.Builder.TypeInfoSource> computeTypeInfoSources(List<? extends NamedType> nts, Node node) {
        List<DetailedSources.Builder.TypeInfoSource> list = new ArrayList<>(nts.size());
        int i = nts.size() - 2; // last one is the type itself, not a qualified type
        int j = node.size() - 3; // last one is the simple name of the type itself, then a delimiter
        while (i >= 0) {
            TypeInfo typeInfo = (TypeInfo) nts.get(i);
            Source source = source(node, 0, j);
            list.add(new DetailedSources.Builder.TypeInfoSource(typeInfo, source));
            if (typeInfo.isPrimaryType()) break;
            i--;
            j -= 2;
        }
        return List.copyOf(list);
    }
}
