package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.DetailedSources;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.Wildcard;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class ParseType extends CommonParse {

    public ParseType(Runtime runtime) {
        super(runtime, null);
    }

    /*
    KeyWord
    Identifier (1 child, e.g. nodes is a ReturnType)
    ObjectType (Identifier) (Identifier, TypeArguments)
    PrimitiveType (Primitive)
    ObjectType + Delimiter + Delimiter (3 children, nodes is a ReferenceType)

     */
    public ParameterizedType parse(Context context, List<Node> nodes, DetailedSources.Builder detailedSourcesBuilder) {
        return parse(context, nodes, true, null, detailedSourcesBuilder);
    }

    public ParameterizedType parse(Context context, List<Node> nodes, boolean complain,
                                   Node varargs,
                                   DetailedSources.Builder detailedSourcesBuilder) {
        ParameterizedType pt = parse2(context, nodes, complain, varargs, detailedSourcesBuilder);
        if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(pt, source((Node) nodes));
        return pt;
    }

    private ParameterizedType parse2(Context context, List<Node> nodes, boolean complain, Node varargs,
                                     DetailedSources.Builder detailedSourcesBuilder) {
        Token.TokenType tt;
        ParameterizedType pt;
        if (nodes instanceof ReturnType rt) {
            return parse(context, rt.getFirst(), complain, varargs, detailedSourcesBuilder);
        }
        int arrays = countArrays(nodes) + (varargs != null ? 1 : 0);
        Source source;
        switch (nodes) {
            case PrimitiveArrayType pat -> {
                PrimitiveType primitiveType = pat.firstChildOfType(PrimitiveType.class);
                Primitive primitive = primitiveType.firstChildOfType(Primitive.class);
                tt = primitive.getType();
                source = source(primitive);
                pt = primitiveType(tt);
            }
            case ReferenceType rt -> {
                if (rt.getFirst() instanceof ObjectType ot) {
                    pt = parseObjectType(context, ot, complain, detailedSourcesBuilder);
                    source = source(ot);
                } else throw new UnsupportedOperationException();
            }
            case ObjectType ot -> {
                pt = parseObjectType(context, ot, complain, detailedSourcesBuilder);
                source = source(ot);
            }
            default -> {
                Node n0 = nodes.isEmpty() || nodes instanceof Node.TerminalNode ? (Node) nodes : nodes.getFirst();
                source = source(n0);
                if (n0 instanceof Name name) {
                    pt = parseObjectType(context, name, complain, detailedSourcesBuilder);
                } else if (n0 instanceof Identifier identifier) {
                    List<? extends NamedType> nts = context.typeContext().getWithQualification(identifier.getSource(), complain);
                    assert nts.size() == 1;
                    NamedType nt = nts.getLast();
                    pt = nt.asSimpleParameterizedType();
                    if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(pt, source(n0));
                } else if (n0 instanceof ObjectType ot) {
                    pt = parseObjectType(context, ot, complain, detailedSourcesBuilder);
                } else if (n0 instanceof Operator o && Token.TokenType.HOOK.equals(o.getType())) {
                    // ?, ? super T ==
                    if (nodes.size() > 1 && nodes.get(1) instanceof WildcardBounds bounds) {
                        pt = parseWildcardBounds(context, bounds, detailedSourcesBuilder);
                    } else {
                        // simply ?
                        pt = runtime.parameterizedTypeWildcard();
                        if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(pt, source(n0));
                    }
                } else {
                    if (n0 instanceof PrimitiveType primitive && primitive.getFirst() instanceof Primitive p) {
                        tt = p.getType();
                    } else if (n0 instanceof Primitive p) {
                        tt = p.getType();
                    } else if (n0 instanceof KeyWord keyWord) {
                        if (!keyWord.hasChildNodes() || nodes.size() == 1
                            || nodes.size() == 3 && nodes.get(2).getType() == Token.TokenType.CLASS) {
                            tt = keyWord.getType();
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    } else tt = null;
                    if (tt != null) {
                        pt = primitiveType(tt);
                    } else {
                        pt = null;
                    }
                    if (pt != null && detailedSourcesBuilder != null) {
                        detailedSourcesBuilder.put(pt, source(n0));
                    }
                }
            }
        }
        if (pt == null) {
            if (complain) {
                throw new UnsupportedOperationException();
            }
            return null;
        }
        if (arrays == 0) {
            return pt;
        }
        ParameterizedType withArrays = pt.copyWithArrays(arrays);
        if (detailedSourcesBuilder != null) {
            //  Source source1 = source((Node) nodes);
            //  detailedSourcesBuilder.put(withArrays, source1);
            detailedSourcesBuilder.putAssociatedObject(withArrays, pt);
            detailedSourcesBuilder.put(pt, source);
        }
        return withArrays;
    }

    private ParameterizedType parseWildcardBounds(Context context, WildcardBounds bounds, DetailedSources.Builder detailedSourcesBuilder) {
        Wildcard wildcard;
        Node node = bounds.getFirst();
        Node.NodeType type = node.getType();
        if (Token.TokenType.EXTENDS.equals(type)) {
            wildcard = runtime.wildcardExtends();
        } else if (Token.TokenType.SUPER.equals(type)) {
            wildcard = runtime.wildcardSuper();
        } else throw new Summary.ParseException(context, "Expect super or extends");
        if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(wildcard, source(node));
        ParameterizedType pt = parse(context, bounds.get(1), detailedSourcesBuilder);
        return pt.withWildcard(wildcard);
    }

    private ParameterizedType primitiveType(Token.TokenType tt) {
        return switch (tt) {
            case INT -> runtime.intParameterizedType();
            case DOUBLE -> runtime.doubleParameterizedType();
            case BYTE -> runtime.byteParameterizedType();
            case BOOLEAN -> runtime.booleanParameterizedType();
            case FLOAT -> runtime.floatParameterizedType();
            case CHAR -> runtime.charParameterizedType();
            case VOID -> runtime.voidParameterizedType();
            case SHORT -> runtime.shortParameterizedType();
            case LONG -> runtime.longParameterizedType();
            default -> null;
        };
    }

    private ParameterizedType parseObjectType(Context context, Node ot, boolean complain,
                                              DetailedSources.Builder detailedSourcesBuilder) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        int startNamedType = -1;
        Stack<Source> details = detailedSourcesBuilder == null ? null : new Stack<>();
        while (i < ot.size()) {
            if (ot.get(i) instanceof Annotation) {
                // annotations have been processed in CatchClause, we'll skip
                ++i;
            } else {
                if (ot.get(i) instanceof Identifier id) {
                    if (startNamedType == -1) startNamedType = i;
                    sb.append(id.getSource());
                    if (details != null) {
                        // source always starts at the beginning of the type, ends incrementally further
                        details.add(source(ot, id));
                    }
                } else {
                    throw new Summary.ParseException(context, "Expected an identifier");
                }
                i++;
                if (i < ot.size() && ot.get(i) instanceof Delimiter d && Token.TokenType.DOT.equals(d.getType())) {
                    i++;
                    sb.append(".");
                } else break;
            }
        }
        String qualifiedName = sb.toString();
        if (qualifiedName.isBlank()) throw new Summary.ParseException(context, "Expected a qualified name");
        List<? extends NamedType> nts = context.typeContext().getWithQualification(qualifiedName, complain);
        if (nts == null) {
            if (complain) throw new Summary.ParseException(context, "Expected non-null");
            return null;
        }
        ParameterizedType withoutTypeParameters = nts.getLast().asSimpleParameterizedType();
        if (detailedSourcesBuilder != null) {
            if (withoutTypeParameters.typeParameter() != null) {
                detailedSourcesBuilder.put(withoutTypeParameters.typeParameter(), details.pop());
            } else {
                detailedSourcesBuilder.put(withoutTypeParameters.typeInfo(), details.pop());
                List<DetailedSources.Builder.TypeInfoSource> associatedList = new ArrayList<>();
                for (int j = nts.size() - 2; j >= 0; --j) {
                    TypeInfo qualifier = (TypeInfo) nts.get(j);
                    Source source = details.pop();
                    associatedList.add(new DetailedSources.Builder.TypeInfoSource(qualifier, source));
                }
                if (!details.isEmpty()) {
                    detailedSourcesBuilder.put(withoutTypeParameters.typeInfo().packageName(), details.pop());
                }
                if (!associatedList.isEmpty()) {
                    detailedSourcesBuilder.putTypeQualification(withoutTypeParameters.typeInfo(), List.copyOf(associatedList));
                }
            }
        }
        if (ot.size() > i && ot.get(i) instanceof TypeArguments tas) {
            List<ParameterizedType> typeArguments = parseTypeArguments(context, tas, complain, detailedSourcesBuilder);
            if (typeArguments == null) {
                if (complain) throw new Summary.ParseException(context, "Expected type arguments");
                return null;
            }
            if (!typeArguments.isEmpty()) {
                return withoutTypeParameters.withParameters(List.copyOf(typeArguments));
            }
        }
        return withoutTypeParameters;
    }

    public List<ParameterizedType> parseTypeArguments(Context context, TypeArguments tas, boolean complain,
                                                      DetailedSources.Builder detailedSourcesBuilder) {
        List<ParameterizedType> typeArguments = new ArrayList<>();
        int j = 1;
        while (j < tas.size()) {
            switch (tas.get(j)) {
                case TypeArgument ta -> {
                    ParameterizedType arg = parse(context, ta, complain, null, detailedSourcesBuilder);
                    typeArguments.add(arg);
                }
                case Operator o when Token.TokenType.HOOK.equals(o.getType()) ->
                        typeArguments.add(runtime.parameterizedTypeWildcard());
                case Type type -> {
                    ParameterizedType arg = parse(context, type, complain, null, detailedSourcesBuilder);
                    if (arg == null) {
                        if (complain) {
                            throw new Summary.ParseException(context, "Expected to know type argument");
                        }
                        return null;
                    }
                    typeArguments.add(arg);
                }
                case null, default -> throw new UnsupportedOperationException();
            }
            j += 2;
        }
        return typeArguments;
    }

    private int countArrays(List<Node> nodes) {
        int arrays = 0;
        for (Node child : nodes) {
            if (child instanceof Delimiter d && "[".equals(d.getSource())) {
                arrays++;
            }
        }
        return arrays;
    }
}
