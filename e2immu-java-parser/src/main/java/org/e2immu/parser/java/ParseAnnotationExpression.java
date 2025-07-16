package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.DetailedSources;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.Node;
import org.parsers.java.Token;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;

/*
IMPORTANT: there are three variants of the parsing code.
2 are in this class: parseDirectly (for annotations in statements and expressions, in the resolution phase) and
onlyLiterals (for simple annotations in the declaration phase).
1 is in ParseHelper (parse in the resolution phase).
 */
public class ParseAnnotationExpression extends CommonParse {

    public ParseAnnotationExpression(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public AnnotationExpression parse(Context context, Info.Builder<?> infoBuilder, Annotation a, int index) {
        String name = a.get(1).getSource();
        List<? extends NamedType> nts = context.typeContext().getWithQualification(name, true);
        DetailedSources.Builder detailedSourcesBuilder = context.newDetailedSourcesBuilder();
        TypeInfo typeInfo = (TypeInfo) nts.getLast();
        if (detailedSourcesBuilder != null) {
            detailedSourcesBuilder.put(typeInfo, source(a.get(1)));
            if (nts.size() > 1 && !typeInfo.isPrimaryType()) {
                List<DetailedSources.Builder.TypeInfoSource> typeInfoSources = computeTypeInfoSources(nts, a.get(1));
                detailedSourcesBuilder.putTypeQualification(typeInfo, typeInfoSources);
            }
        }
        Source source = source(a);
        AnnotationExpression.Builder builder = runtime.newAnnotationExpressionBuilder()
                .setTypeInfo(typeInfo)
                .addComments(comments(a))
                .setSource(detailedSourcesBuilder == null ? source
                        : source.withDetailedSources(detailedSourcesBuilder.build()));
        if (!(a instanceof MarkerAnnotation) && !onlyLiterals(context, a, builder)) {
            context.resolver().addAnnotationTodo(infoBuilder, typeInfo, builder, index, a, context);
        }
        /*
        Unless the annotation is a MarkerAnnotation (without key-value pairs), or it contains only literals,
        the annotation expression returned
        will be a temporary one, until the resolution phase where it will be overwritten before committing the builder.
        We want it in place, however, because before resolution, in GetSetUtil, we create synthetic fields and
        rely on the presence of @GetSet, @Modified.
         */
        return builder.build();
    }

    private boolean onlyLiterals(Context context, Annotation a, AnnotationExpression.Builder builder) {

        if (a instanceof SingleMemberAnnotation) {
            if (a.get(3) instanceof LiteralExpression literalExpression) {
                Expression expression = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                        literalExpression);
                builder.addKeyValuePair("value", expression);
                return true;
            }
            return false;
        }

        if (a instanceof NormalAnnotation na) {
            // delimiter @, annotation name, ( , mvp, delimiter ',', mvp, delimiter )
            Node na3 = na.get(3);
            if (na3 instanceof MemberValuePair mvp) {
                String key = mvp.getFirst().getSource();
                if (mvp.get(2) instanceof LiteralExpression literalExpression) {
                    Expression value = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                            literalExpression);
                    builder.addKeyValuePair(key, value);
                } else {
                    return false;
                }
            } else if (na3 instanceof MemberValuePairs pairs) {
                for (int j = 0; j < pairs.size(); j += 2) {
                    if (pairs.get(j) instanceof MemberValuePair mvp) {
                        if (mvp.get(2) instanceof LiteralExpression literalExpression) {
                            String key = mvp.getFirst().getSource();
                            Expression value = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                                    literalExpression);
                            builder.addKeyValuePair(key, value);
                        } else {
                            return false;
                        }
                    } else {
                        throw new Summary.ParseException(context, "Expected mvp, but got "
                                + pairs.get(j).getClass() + ", src '" + pairs.get(j).getSource() + "'");
                    }
                }
            } else if (!(na3 instanceof Delimiter d && Token.TokenType.RPAREN.equals(d.getType()))) {
                throw new Summary.ParseException(context, "Expected mvp, but got " + na3.getClass() + ", src '" + na3.getSource() + "'");
            }
        } else if (!(a instanceof MarkerAnnotation)) {
            throw new UnsupportedOperationException("NYI");
        }
        return true;
    }

    public AnnotationExpression parseDirectly(Context context, Annotation a) {
        String name = a.get(1).getSource();
        List<? extends NamedType> nts = context.typeContext().getWithQualification(name, true);
        DetailedSources.Builder detailedSourcesBuilder = context.newDetailedSourcesBuilder();
        TypeInfo typeInfo = (TypeInfo) nts.getLast();
        if (detailedSourcesBuilder != null) {
            detailedSourcesBuilder.put(typeInfo, source(a.get(1)));
            if (nts.size() > 1 && !typeInfo.isPrimaryType()) {
                List<DetailedSources.Builder.TypeInfoSource> typeInfoSources = computeTypeInfoSources(nts, a.get(1));
                detailedSourcesBuilder.putTypeQualification(typeInfo, typeInfoSources);
            }
        }
        AnnotationExpression.Builder builder = runtime.newAnnotationExpressionBuilder().setTypeInfo(typeInfo);
        if (a instanceof SingleMemberAnnotation) {
            Expression expression = parsers.parseExpression().parse(context, "", context.emptyForwardType(), a.get(3));
            builder.addKeyValuePair("value", expression);
        } else if (a instanceof NormalAnnotation na) {
            // delimiter @, annotation name, ( , mvp, delimiter ',', mvp, delimiter )
            if (na.get(3) instanceof MemberValuePair mvp) {
                String key = mvp.getFirst().getSource();
                Expression value = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                        mvp.get(2));
                builder.addKeyValuePair(key, value);
            } else if (na.get(3) instanceof MemberValuePairs pairs) {
                for (int j = 0; j < pairs.size(); j += 2) {
                    if (pairs.get(j) instanceof MemberValuePair mvp) {
                        String key = mvp.getFirst().getSource();
                        Expression value = parsers.parseExpression().parse(context, "", context.emptyForwardType(),
                                mvp.get(2));
                        builder.addKeyValuePair(key, value);
                    } else {
                        throw new Summary.ParseException(context, "Expected mvp");
                    }
                }
            } else {
                throw new Summary.ParseException(context, "Expected mvp");
            }
        } else if (!(a instanceof MarkerAnnotation)) {
            throw new UnsupportedOperationException("NYI");
        }
        Source source = source(a);
        return builder.addComments(comments(a))
                .setSource(detailedSourcesBuilder == null ? source : source.withDetailedSources(detailedSourcesBuilder.build()))
                .build();
    }
}
