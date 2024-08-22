package org.e2immu.parser.java;

import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.VariableExpression;
import org.e2immu.language.cst.api.info.Access;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.FieldModifier;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.parsers.java.Node;
import org.parsers.java.ast.*;

import java.util.ArrayList;
import java.util.List;

public class ParseFieldDeclaration extends CommonParse {

    public ParseFieldDeclaration(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public FieldInfo parse(Context context, FieldDeclaration fd) {
        int i = 0;
        List<AnnotationExpression> annotations = new ArrayList<>();
        List<FieldModifier> fieldModifiers = new ArrayList<>();
        Node fdi;
        while (!((fdi = fd.get(i)) instanceof Type)) {
            if (fdi instanceof Annotation a) {
                annotations.add(parsers.parseAnnotationExpression().parse(context, a));
            } else if (fdi instanceof Modifiers modifiers) {
                for (Node node : modifiers.children()) {
                    if (node instanceof Annotation a) {
                        annotations.add(parsers.parseAnnotationExpression().parse(context, a));
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
            parameterizedType = parsers.parseType().parse(context, type);
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
        org.e2immu.language.cst.api.expression.Expression scope;
        if(isStatic) {
            scope = runtime.newTypeExpression(fieldInfo.owner().asSimpleParameterizedType(), runtime.diamondNo());
        } else {
            scope = runtime.newVariableExpression(runtime.newThis(fieldInfo.owner()));
        }
        FieldReference fieldReference = runtime.newFieldReference(fieldInfo, scope, fieldInfo.type()); // FIXME generics
        context.variableContext().add(fieldReference);
        if (expression != null) {
            ForwardType fwd = context.newForwardType(fieldInfo.type());
            context.resolver().add(fieldInfo, fieldInfo.builder(), fwd, null, expression, context);
        } else {
            fieldInfo.builder().commit();
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
