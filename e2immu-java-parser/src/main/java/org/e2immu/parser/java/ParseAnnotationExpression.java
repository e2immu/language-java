package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.ast.*;

import java.util.List;

public class ParseAnnotationExpression extends CommonParse {

    public ParseAnnotationExpression(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public AnnotationExpression parse(Context context, Annotation a) {
        String name = a.get(1).getSource();
        TypeInfo typeInfo = (TypeInfo) context.typeContext().get(name, true);
        AnnotationExpression.Builder builder = runtime.newAnnotationExpressionBuilder().setTypeInfo(typeInfo);
        if (a instanceof SingleMemberAnnotation) {
            Expression expression = parsers.parseExpression().parse(context, "", context.emptyForwardType(), a.get(3));
            builder.addKeyValuePair("value", expression);
        } else if (a instanceof NormalAnnotation na) {
            // delimiter @, annotation name, ( , mvp, delimiter ',', mvp, delimiter )
            if (na.get(3) instanceof MemberValuePair mvp) {
                String key = mvp.get(0).getSource();
                Expression value = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                        mvp.get(2));
                builder.addKeyValuePair(key, value);
            } else if (na.get(3) instanceof MemberValuePairs pairs) {
                for (int j = 0; j < pairs.size(); j += 2) {
                    if (pairs.get(j) instanceof MemberValuePair mvp) {
                        String key = mvp.get(0).getSource();
                        Expression value = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                                mvp.get(2));
                        builder.addKeyValuePair(key, value);
                    } else {
                        throw new Summary.ParseException(context.info(), "Expected mvp");
                    }
                }
            } else {
                throw new Summary.ParseException(context.info(), "Expected mvp");
            }
        } else if (!(a instanceof MarkerAnnotation)) {
            throw new UnsupportedOperationException("NYI");
        }
        return builder.addComments(comments(a)).setSource(source(context.info(), null, a)).build();
    }
}
