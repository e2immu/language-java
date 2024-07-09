package org.e2immu.parser.java;

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
        return parse(context, nodes, true);
    }

    public ParameterizedType parse(Context context, List<Node> nodes, boolean complain) {
        Token.TokenType tt;
        ParameterizedType pt;
        Node n0 = nodes.get(0);
        if (n0 instanceof ReferenceType rt) {
            return parse(context, rt);
        }
        if (nodes instanceof ObjectType ot) {
            pt = parseObjectType(context, ot, complain);
        } else if (n0 instanceof Identifier identifier) {
            NamedType nt = context.typeContext().get(identifier.getSource(), complain);
            pt = nt.asSimpleParameterizedType();
        } else if (n0 instanceof ObjectType ot) {
            pt = parseObjectType(context, ot, complain);
        } else if (n0 instanceof Operator o && Token.TokenType.HOOK.equals(o.getType())) {
            // ?, ? super T ==
            if (nodes.size() > 1 && nodes.get(1) instanceof WildcardBounds bounds) {
                pt = parseWildcardBounds(context, bounds);
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

    private ParameterizedType parseWildcardBounds(Context context, WildcardBounds bounds) {
        Wildcard wildcard;
        Node.NodeType type = bounds.get(0).getType();
        if (Token.TokenType.EXTENDS.equals(type)) {
            wildcard = runtime.wildcardExtends();
        } else if (Token.TokenType.SUPER.equals(type)) {
            wildcard = runtime.wildcardSuper();
        } else throw new Summary.ParseException(context.info(), "Expect super or extends");
        ParameterizedType pt = parse(context, bounds.get(1));
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

    private ParameterizedType parseObjectType(Context context, ObjectType ot, boolean complain) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        while (i < ot.size()) {
            if (ot.get(i) instanceof Identifier id) {
                sb.append(id.getSource());
            } else throw new Summary.ParseException(context.info(), "Expected an identifier");
            i++;
            if (i < ot.size() && ot.get(i) instanceof Delimiter d && Token.TokenType.DOT.equals(d.getType())) {
                i++;
                sb.append(".");
            } else break;
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
            List<ParameterizedType> typeArguments = parseTypeArguments(context, tas);
            if (!typeArguments.isEmpty()) {
                return withoutTypeParameters.withParameters(List.copyOf(typeArguments));
            }
        }
        return withoutTypeParameters;
    }

    public List<ParameterizedType> parseTypeArguments(Context context, TypeArguments tas) {
        List<ParameterizedType> typeArguments = new ArrayList<>();
        int j = 1;
        while (j < tas.size()) {
            if (tas.get(j) instanceof TypeArgument ta) {
                ParameterizedType arg = parse(context, ta);
                typeArguments.add(arg);
            } else if (tas.get(j) instanceof Operator o && Token.TokenType.HOOK.equals(o.getType())) {
                typeArguments.add(runtime.parameterizedTypeWildcard());
            } else if (tas.get(j) instanceof Type type) {
                ParameterizedType arg = parse(context, type);
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
