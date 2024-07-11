package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.expression.MethodReference;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.language.inspection.api.parser.MethodResolution;
import org.e2immu.parser.java.erasure.LambdaErasure;
import org.e2immu.support.Either;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.Identifier;
import org.parsers.java.ast.ObjectType;
import org.parsers.java.ast.Type;

import java.util.*;

public class ParseMethodReference extends CommonParse {

    public ParseMethodReference(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public Expression parse(Context context, List<Comment> comments, Source source, String index,
                            ForwardType forwardType,
                            org.parsers.java.ast.MethodReference mr) {
        Expression scope;
        Node n0 = mr.get(0);
        if (n0 instanceof Type) {
            // BEWARE! even if n0 represents a variable, we may end up in this branch
            ParameterizedType pt = parsers.parseType().parse(context, n0, false);
            if (pt != null) {
                scope = runtime.newTypeExpression(pt, runtime.diamondNo());
            } else if (n0 instanceof ObjectType ot) {
                // try again, cannot be a type
                scope = parsers.parseExpression().parse(context, index, forwardType, ot);
            } else throw new UnsupportedOperationException("NYI");
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
        if (forwardType.erasure()) {
            Either<Set<MethodResolution.Count>, Expression> either = context.methodResolution()
                    .computeMethodReferenceErasureCounts(context, comments, source, scope, methodName);
            if (either.isRight()) return either.getRight();
            return new LambdaErasure(runtime, either.getLeft(), source);
        }

        return context.methodResolution().resolveMethodReference(context, comments, source, index, forwardType,
                scope, methodName);
    }

}
