package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.DetailedSources;
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
    public ParameterizedType parse(Context context, List<Node> nodes) {
        return parse(context, nodes, true, null);
    }

    public ParameterizedType parse(Context context, List<Node> nodes, DetailedSources.Builder detailedSourcesBuilder) {
        return parse(context, nodes, true, detailedSourcesBuilder);
    }

    public ParameterizedType parse(Context context, List<Node> nodes, boolean complain,
                                   DetailedSources.Builder detailedSourcesBuilder) {
        ParameterizedType pt = parse2(context, nodes, complain, detailedSourcesBuilder);
        if (detailedSourcesBuilder != null) detailedSourcesBuilder.put(pt, source((Node) nodes));
        return pt;
    }

    private ParameterizedType parse2(Context context, List<Node> nodes, boolean complain,
                                     DetailedSources.Builder detailedSourcesBuilder) {
        Token.TokenType tt;
        ParameterizedType pt;
        Node n0 = nodes.get(0);
        if (n0 instanceof ReferenceType rt) {
            return parse(context, rt);
        }
        if (nodes instanceof ObjectType ot) {
            pt = parseObjectType(context, ot, complain, detailedSourcesBuilder);
        } else if (n0 instanceof Identifier identifier) {
            NamedType nt = context.typeContext().get(identifier.getSource(), complain);
            pt = nt.asSimpleParameterizedType();
        } else if (n0 instanceof ObjectType ot) {
            pt = parseObjectType(context, ot, complain, detailedSourcesBuilder);
        } else if (n0 instanceof Operator o && Token.TokenType.HOOK.equals(o.getType())) {
            // ?, ? super T ==
            if (nodes.size() > 1 && nodes.get(1) instanceof WildcardBounds bounds) {
                pt = parseWildcardBounds(context, bounds, detailedSourcesBuilder);
            } else {
                // simply ?
                pt = runtime.parameterizedTypeWildcard();
            }
        } else {
            if (n0 instanceof PrimitiveArrayType pat && pat.get(0) instanceof PrimitiveType primitive
                && primitive.get(0) instanceof Primitive p) {
                tt = p.getType();
                int arrays = countArrays(pat);
                ParameterizedType parameterizedType = primitiveType(tt);
                assert parameterizedType != null;
                return parameterizedType.copyWithArrays(arrays);
            }
            if (n0 instanceof PrimitiveType primitive && primitive.get(0) instanceof Primitive p) {
                tt = p.getType();
            } else if (n0 instanceof KeyWord keyWord && nodes.size() == 1) {
                tt = keyWord.getType();
            } else tt = null;
            if (tt != null) {
                pt = primitiveType(tt);
            } else {
                pt = null;
            }
        }
        if (pt == null) {
            if (complain) throw new UnsupportedOperationException();
            return null;
        }

        int arrays = countArrays(nodes);
        if (arrays == 0) {
            return pt;
        } else {
            return pt.copyWithArrays(arrays);
        }
    }

    private ParameterizedType parseWildcardBounds(Context context, WildcardBounds bounds, DetailedSources.Builder detailedSourcesBuilder) {
        Wildcard wildcard;
        Node node = bounds.get(0);
        Node.NodeType type = node.getType();
        if (Token.TokenType.EXTENDS.equals(type)) {
            wildcard = runtime.wildcardExtends();
        } else if (Token.TokenType.SUPER.equals(type)) {
            wildcard = runtime.wildcardSuper();
        } else throw new Summary.ParseException(context.info(), "Expect super or extends");
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

    private ParameterizedType parseObjectType(Context context, ObjectType ot, boolean complain,
                                              DetailedSources.Builder detailedSourcesBuilder) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        int startNamedType = -1;
        int endNamedType = -1;
        while (i < ot.size()) {
            if (ot.get(i) instanceof Annotation) {
                // annotations have been processed in CatchClause, we'll skip
                ++i;
            } else {
                if (ot.get(i) instanceof Identifier id) {
                    if (startNamedType == -1) startNamedType = i;
                    endNamedType = i;
                    sb.append(id.getSource());
                } else {
                    throw new Summary.ParseException(context.info(), "Expected an identifier");
                }
                i++;
                if (i < ot.size() && ot.get(i) instanceof Delimiter d && Token.TokenType.DOT.equals(d.getType())) {
                    i++;
                    sb.append(".");
                } else break;
            }
        }
        String qualifiedName = sb.toString();
        if (qualifiedName.isBlank()) throw new Summary.ParseException(context.info(), "Expected a qualified name");
        NamedType nt = context.typeContext().get(qualifiedName, complain);
        if (nt == null) {
            if (complain) throw new Summary.ParseException(context.info(), "Expected non-null");
            return null;
        }
        ParameterizedType withoutTypeParameters = nt.asSimpleParameterizedType();
        if (ot.size() > i && ot.get(i) instanceof TypeArguments tas) {
            List<ParameterizedType> typeArguments = parseTypeArguments(context, tas, complain, detailedSourcesBuilder);
            if (typeArguments == null) {
                if (complain) throw new Summary.ParseException(context.info(), "Expected type arguments");
                return null;
            }
            if (!typeArguments.isEmpty()) {
                if (detailedSourcesBuilder != null) {
                    detailedSourcesBuilder.put(withoutTypeParameters.typeInfo(), source(ot, startNamedType, endNamedType));
                }
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
            if (tas.get(j) instanceof TypeArgument ta) {
                ParameterizedType arg = parse(context, ta, complain, detailedSourcesBuilder);
                typeArguments.add(arg);
            } else if (tas.get(j) instanceof Operator o && Token.TokenType.HOOK.equals(o.getType())) {
                typeArguments.add(runtime.parameterizedTypeWildcard());
            } else if (tas.get(j) instanceof Type type) {
                ParameterizedType arg = parse(context, type, complain, detailedSourcesBuilder);
                if (arg == null) {
                    if (complain) {
                        throw new Summary.ParseException(context.info(), "Expected to know type argument");
                    }
                    return null;
                }
                typeArguments.add(arg);
            } else throw new UnsupportedOperationException();
            j += 2;
        }
        return typeArguments;
    }

    private int countArrays(List<Node> nodes) {
        int arrays = 0;
        int i = 1;
        while (i < nodes.size() && nodes.get(i) instanceof Delimiter d && "[".equals(d.getSource())) {
            i += 2;
            arrays++;
        }
        return arrays;
    }
}
