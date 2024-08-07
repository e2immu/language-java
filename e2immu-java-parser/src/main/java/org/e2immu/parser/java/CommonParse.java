package org.e2immu.parser.java;

import org.e2immu.annotation.NotNull;
import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.info.TypeModifier;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeNature;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.support.Either;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;

import java.util.*;

public abstract class CommonParse {
    protected final Runtime runtime;
    protected final Parsers parsers;

    protected CommonParse(Runtime runtime, Parsers parsers) {
        this.runtime = runtime;
        this.parsers = parsers;
    }

    public List<Comment> comments(Node node) {
        return node.getAllTokens(true).stream().map(t -> {
            if (t instanceof SingleLineComment slc) {
                return runtime.newSingleLineComment(slc.getSource());
            }
            if (t instanceof MultiLineComment multiLineComment) {
                return runtime.newMultilineComment(multiLineComment.getSource());
            }
            return null;
        }).filter(Objects::nonNull).toList();
    }

    /*
    this implementation gives an "imperfect" parent... See e.g. parseBlock: we cannot pass on the parent during
    parsing, because we still have the builder at that point in time.
     */
    public Source source(Info info, String index, Node node) {
        return runtime.newParserSource(info, index, node.getBeginLine(), node.getBeginColumn(), node.getEndLine(),
                node.getEndColumn());
    }


    // code copied from ParseTypeDeclaration
    protected TypeParameter parseTypeParameter(Context context, Node node, Info owner, int typeParameterIndex) {
        String name;
        List<AnnotationExpression> annotations = new ArrayList<>();
        int i = 0;
        if (node instanceof Identifier) {
            name = node.getSource();
        } else if (node instanceof org.parsers.java.ast.TypeParameter tp) {
            while (tp.get(i) instanceof Annotation a) {
                annotations.add(parsers.parseAnnotationExpression().parse(context, a));
                i++;
            }
            if (tp.get(i) instanceof Identifier id) {
                name = id.getSource();
                i++;
            } else throw new Summary.ParseException(context.info(), "Expected Identifier");
        } else throw new Summary.ParseException(context.info(), "Expected Identifier or TypeParameter");
        TypeParameter typeParameter = runtime.newTypeParameter(typeParameterIndex, name, owner, List.copyOf(annotations));
        context.typeContext().addToContext(typeParameter);
        // do type bounds
        TypeParameter.Builder builder = typeParameter.builder();
        if (node instanceof org.parsers.java.ast.TypeParameter tp) {
            if (tp.get(i) instanceof TypeBound tb) {
                ParameterizedType typeBound = parsers.parseType().parse(context, tb.get(1));
                builder.addTypeBound(typeBound);
            } else throw new UnsupportedOperationException();
        }
        return builder.commit();
    }


    public static final String AWFULLY_LONG_METHOD = "?? awfully long method";

    /*
    n <= 10  >> 0..9
    n <=100  >> 00..99
    n <=1000 >> 000..999
     */
    @NotNull
    public static String pad(int i, int n) {
        String s = Integer.toString(i);
        if (n <= 10) return s;
        if (n <= 100) {
            if (i < 10) return "0" + s;
            return s;
        }
        if (n <= 1_000) {
            if (i < 10) return "00" + s;
            if (i < 100) return "0" + s;
            return s;
        }
        if (n <= 10_000) {
            if (i < 10) return "000" + s;
            if (i < 100) return "00" + s;
            if (i < 1_000) return "0" + s;
            return s;
        }
        throw new UnsupportedOperationException(AWFULLY_LONG_METHOD);
    }


    protected Map<String, TypeInfo> recursivelyFindTypes(Either<CompilationUnit, TypeInfo> parent,
                                                         TypeInfo typeInfoOrNull, Node body) {
        Map<String, TypeInfo> map = new HashMap<>();
        for (Node node : body) {
            if (node instanceof TypeDeclaration td && !(node instanceof EmptyDeclaration)) {
                handleTypeDeclaration(parent, typeInfoOrNull, td, map);
            }
        }
        return Map.copyOf(map);
    }

    /*
    We extract type nature and type modifiers here, because we'll need them in sibling subtype declarations.
     */
    private void handleTypeDeclaration(Either<CompilationUnit, TypeInfo> parent,
                                       TypeInfo typeInfoOrNull,
                                       TypeDeclaration td,
                                       Map<String, TypeInfo> map) {
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
                        if (tm != null) typeModifiers.add(tm);
                    }
                }
            } else if (child instanceof KeyWord keyWord) {
                TypeNature tn = getTypeNature(td, keyWord.getType());
                if (tn != null) {
                    assert typeNature == null;
                    typeNature = tn;
                }
                TypeModifier tm = getTypeModifier(keyWord.getType());
                if (tm != null) typeModifiers.add(tm);
            } else if (child instanceof Identifier id) {
                identifier = id;
            } else if (child instanceof ClassOrInterfaceBody || child instanceof RecordBody || child instanceof EnumBody) {
                sub = child;
                break;
            }
        }
        assert identifier != null;
        String typeName = td.firstChildOfType(Identifier.class).getSource();
        TypeInfo typeInfo;
        if (parent.isLeft()) {
            if (typeInfoOrNull != null) {
                typeInfo = typeInfoOrNull;
                assert typeInfo.simpleName().equals(typeName);
            } else {
                typeInfo = runtime.newTypeInfo(parent.getLeft(), typeName);
            }
        } else {
            TypeInfo parentType = parent.getRight();
            typeInfo = runtime.newTypeInfo(parentType, typeName);
            parentType.builder().addSubType(typeInfo);
        }
        typeInfo.builder().setTypeNature(typeNature);
        typeModifiers.forEach(typeInfo.builder()::addTypeModifier);
        map.put(typeInfo.fullyQualifiedName(), typeInfo);
        if (sub != null) {
            map.putAll(recursivelyFindTypes(Either.right(typeInfo), typeInfoOrNull, sub));
        }
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
