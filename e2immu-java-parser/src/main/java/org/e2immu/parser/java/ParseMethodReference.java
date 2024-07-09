package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.expression.MethodReference;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.Identifier;
import org.parsers.java.ast.Type;

import java.util.*;

public class ParseMethodReference extends CommonParse {

    public ParseMethodReference(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public MethodReference parse(Context context, List<Comment> comments, Source source, String index,
                                 ForwardType forwardType,
                                 org.parsers.java.ast.MethodReference mr) {
        Expression scope;
        Node n0 = mr.get(0);
        if (n0 instanceof Type) {
            ParameterizedType pt = parsers.parseType().parse(context, n0);
            scope = runtime.newTypeExpression(pt, runtime.diamondNo());
        } else {
            scope = parsers.parseExpression().parse(context, index, forwardType, n0);
        }
        String methodName;
        Node mr2 = mr.get(2);
        if (mr2 instanceof Identifier id) {
            methodName = id.getSource();
        } else if (Token.TokenType.NEW.equals(mr2.getType())) {
            methodName = "new";
        } else {
            throw new UnsupportedOperationException();
        }
        return context.methodResolution().resolveMethodReference(context, comments, source, index, forwardType,
                scope, methodName);
    }

}
