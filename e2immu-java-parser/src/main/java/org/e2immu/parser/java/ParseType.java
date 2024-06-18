package org.e2immu.parser.java;

import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.parserimpl.Context;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;

public class ParseType extends CommonParse {

    public ParseType(Runtime runtime) {
        super(runtime);
    }

    /*
    KeyWord
    Identifier (1 child, e.g. nodes is a ReturnType)
    ObjectType (Identifier) (Identifier, TypeArguments)
    PrimitiveType (Primitive)
    ObjectType + Delimiter + Delimiter (3 children, nodes is a ReferenceType)
     */
    public ParameterizedType parse(Context context, List<Node> nodes) {
        Token.TokenType tt;
        ParameterizedType pt;
        Node n0 = nodes.get(0);
        if (nodes instanceof ObjectType ot) {
            pt = parseObjectType(context, ot);
        } else if (n0 instanceof Identifier identifier) {
            NamedType nt = context.typeContext().get(identifier.getSource(), true);
            pt = nt.asSimpleParameterizedType();
        } else if (n0 instanceof ObjectType ot) {
            pt = parseObjectType(context, ot);
        } else {
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
        assert pt != null;

        int arrays = countArrays(nodes);
        if (arrays == 0) {
            return pt;
        } else {
            return pt.copyWithArrays(arrays);
        }
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

    private ParameterizedType parseObjectType(Context context, ObjectType ot) {
        if (ot.get(0) instanceof Identifier id) {
            NamedType nt = context.typeContext().get(id.getSource(), true);
            ParameterizedType withoutTypeParameters = nt.asSimpleParameterizedType();
            if (ot.size() > 1 && ot.get(1) instanceof TypeArguments tas) {
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
                if (!typeArguments.isEmpty()) {
                    return withoutTypeParameters.withParameters(List.copyOf(typeArguments));
                }
            }
            return withoutTypeParameters;
        } else throw new UnsupportedOperationException();
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
