package org.e2immu.parser.java;

import org.e2immu.cstapi.expression.AnnotationExpression;
import org.e2immu.cstapi.expression.VariableExpression;
import org.e2immu.cstapi.info.Access;
import org.e2immu.cstapi.info.FieldInfo;
import org.e2immu.cstapi.info.FieldModifier;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.cstapi.variable.FieldReference;
import org.e2immu.parserapi.Context;
import org.e2immu.parserapi.ForwardType;
import org.parsers.java.Node;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;

public class ParseFieldDeclaration extends CommonParse {
    private final ParseType parseType;
    private final ParseAnnotationExpression parseAnnotationExpression;

    public ParseFieldDeclaration(Runtime runtime) {
        super(runtime);
        parseType = new ParseType(runtime);
        parseAnnotationExpression = new ParseAnnotationExpression(runtime);
    }

    public FieldInfo parse(Context context, FieldDeclaration fd) {
        int i = 0;
        List<AnnotationExpression> annotations = new ArrayList<>();
        List<FieldModifier> fieldModifiers = new ArrayList<>();
        Node fdi;
        while (!((fdi = fd.get(i)) instanceof Type)) {
            if (fdi instanceof Annotation a) {
                annotations.add(parseAnnotationExpression.parse(context, a));
            } else if (fdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof MarkerAnnotation a) {
                        annotations.add(parseAnnotationExpression.parse(context, a));
                    } else if (node instanceof KeyWord keyWord) {
                        fieldModifiers.add(modifier(keyWord));
                    }
                }
            } else if (fd.get(i) instanceof KeyWord keyWord) {
                fieldModifiers.add(modifier(keyWord));
            }
            i++;
        }
        boolean isStatic = fieldModifiers.stream().anyMatch(FieldModifier::isStatic);
        Access access = access(fieldModifiers);
        Access accessCombined = context.enclosingType().access().combine(access);

        ParameterizedType parameterizedType;
        if (fd.get(i) instanceof Type type) {
            parameterizedType = parseType.parse(context, type);
            i++;
        } else throw new UnsupportedOperationException();
        String name;
        Expression expression;
        if (fd.get(i) instanceof VariableDeclarator vd) {
            if (vd.get(0) instanceof Identifier identifier) {
                name = identifier.getSource();
            } else throw new UnsupportedOperationException();
            if (vd.children().size() >= 3 && vd.get(2) instanceof Expression e) {
                expression = e;
            } else {
                expression = null;
            }
        } else throw new UnsupportedOperationException();


        TypeInfo owner = context.enclosingType();
        FieldInfo fieldInfo = runtime.newFieldInfo(name, isStatic, parameterizedType, owner);
        FieldInfo.Builder builder = fieldInfo.builder();
        builder.setAccess(accessCombined);
        builder.setSource(source(fieldInfo, null, fd));
        builder.addComments(comments(fd));
        builder.addAnnotations(annotations);

        fieldModifiers.forEach(builder::addFieldModifier);
        VariableExpression scope = runtime.newVariableExpression(runtime.newThis(fieldInfo.owner()));
        FieldReference fieldReference = runtime.newFieldReference(fieldInfo, scope, fieldInfo.type()); // FIXME generics
        context.variableContext().add(fieldReference);
        if (expression != null) {
            ForwardType fwd = context.newForwardType(fieldInfo.type());
            context.resolver().add(fieldInfo.builder(), fwd, null, expression, context);
        }
        return fieldInfo;
    }

    private Access access(List<FieldModifier> fieldModifiers) {
        for (FieldModifier fieldModifier : fieldModifiers) {
            if (fieldModifier.isPublic()) return runtime.accessPublic();
            if (fieldModifier.isPrivate()) return runtime.accessPrivate();
            if (fieldModifier.isProtected()) return runtime.accessProtected();
        }
        return runtime.accessPackage();
    }

    private FieldModifier modifier(KeyWord keyWord) {
        return switch (keyWord.getType()) {
            case FINAL -> runtime.fieldModifierFinal();
            case PRIVATE -> runtime.fieldModifierPrivate();
            case PROTECTED -> runtime.fieldModifierProtected();
            case PUBLIC -> runtime.fieldModifierPublic();
            case STATIC -> runtime.fieldModifierStatic();
            case TRANSIENT -> runtime.fieldModifierTransient();
            case VOLATILE -> runtime.fieldModifierVolatile();
            default -> throw new UnsupportedOperationException("Have " + keyWord.getType());
        };
    }
}
