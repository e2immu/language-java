package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.inspection.api.parser.Context;
import org.parsers.java.ast.Annotation;
import org.parsers.java.ast.SingleMemberAnnotation;

public class ParseAnnotationExpression extends CommonParse {
    private final ParseExpression parseExpression;

    public ParseAnnotationExpression(Runtime runtime) {
        super(runtime);
        parseExpression = new ParseExpression(runtime);
    }

    public AnnotationExpression parse(Context context, Annotation a) {
        String name = a.get(1).getSource();
        TypeInfo typeInfo = (TypeInfo) context.typeContext().get(name, true);
        AnnotationExpression.Builder builder = runtime.newAnnotationExpressionBuilder().setTypeInfo(typeInfo);
        if (a instanceof SingleMemberAnnotation sma) {
            Expression expression = parseExpression.parse(context, "", context.emptyForwardType(), a.get(3));
            builder.addKeyValuePair("value", expression);
        }
        return builder.build();
    }
}
