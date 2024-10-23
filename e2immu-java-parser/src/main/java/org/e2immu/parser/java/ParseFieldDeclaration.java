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

    public List<FieldInfo> parse(Context context, FieldDeclaration fd) {
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
        TypeInfo owner = context.enclosingType();
        org.e2immu.language.cst.api.expression.Expression scope;
        if (isStatic) {
            scope = runtime.newTypeExpression(owner.asSimpleParameterizedType(), runtime.diamondNo());
        } else {
            scope = runtime.newVariableExpression(runtime.newThis(owner.asParameterizedType()));
        }

        ParameterizedType parameterizedType;
        if (fd.get(i) instanceof Type type) {
            parameterizedType = parsers.parseType().parse(context, type);
            i++;
        } else throw new UnsupportedOperationException();
        List<FieldInfo> fields = new ArrayList<>();
        boolean first = true;
        while (i < fd.size() && fd.get(i) instanceof VariableDeclarator vd) {
            fields.add(makeField(context, fd, vd, isStatic, parameterizedType, owner, fieldModifiers, annotations,
                    scope, first));
            i += 2;
            first = false;
        }
        return fields;
    }

    private FieldInfo makeField(Context context,
                                FieldDeclaration fd,
                                VariableDeclarator vd,
                                boolean isStatic,
                                ParameterizedType parameterizedType,
                                TypeInfo owner,
                                List<FieldModifier> fieldModifiers,
                                List<AnnotationExpression> annotations,
                                org.e2immu.language.cst.api.expression.Expression scope,
                                boolean first) {
        ParameterizedType type;
        Node vd0 = vd.get(0);
        Identifier identifier;
        if (vd0 instanceof VariableDeclaratorId vdi) {
            identifier = (Identifier) vdi.get(0);
            int arrays = (vdi.size() - 1) / 2;
            type = parameterizedType.copyWithArrays(arrays);
        } else {
            identifier = (Identifier) vd0;
            type = parameterizedType;
        }
        String name = identifier.getSource();
        Expression expression;
        if (vd.children().size() >= 3 && vd.get(2) instanceof Expression e) {
            expression = e;
        } else {
            expression = null;
        }

        FieldInfo fieldInfo = runtime.newFieldInfo(name, isStatic, type, owner);
        FieldInfo.Builder builder = fieldInfo.builder();
        fieldModifiers.forEach(builder::addFieldModifier);
        builder.computeAccess();
        builder.setSource(source(fieldInfo, null, vd));
        if (first) {
            builder.addComments(comments(fd));
        } // else: comment is only on the first field in the sequence, see e.g. TestFieldComments in java-parser
        builder.addComments(comments(vd));
        builder.addAnnotations(annotations);

        FieldReference fieldReference = runtime.newFieldReference(fieldInfo, scope, fieldInfo.type());
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
