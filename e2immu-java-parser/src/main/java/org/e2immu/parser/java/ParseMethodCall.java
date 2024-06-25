package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.cst.api.variable.Variable;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.parser.java.erasure.MethodCallErasure;
import org.parsers.java.Token;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ParseMethodCall extends CommonParse {
    private final static Logger LOGGER = LoggerFactory.getLogger(ParseMethodCall.class);

    protected ParseMethodCall(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public Expression parse(Context context, List<Comment> comments, Source source,
                            String index, ForwardType forwardType, org.parsers.java.ast.MethodCall mc) {
        List<Object> unparsedArguments = new ArrayList<>();
        Name name = (Name) mc.get(0);
        InvocationArguments ia = (InvocationArguments) mc.get(1);
        String methodName = name.get(name.size() - 1).getSource();
        Object unparsedObject = newNameObject(name);
        int i = 1;
        while (i < mc.size() && !(ia.get(i) instanceof Delimiter)) {
            unparsedArguments.add(ia.get(i));
            i += 2;
        }
        if (forwardType.erasure()) {
            Set<ParameterizedType> types = context.methodResolution().computeScope(context, index, methodName,
                    unparsedObject, unparsedArguments);
            LOGGER.debug("Erasure types: {}", types);
            return new MethodCallErasure(runtime, types, methodName);
        }
        // now we should have a more correct forward type!
        return context.methodResolution().resolveMethod(context, comments, source, index, forwardType, methodName,
                unparsedObject, unparsedArguments);
    }

    private Object newNameObject(Name name) {
        Name n = new Name();
        for (int i = 0; i < name.size() - 2; i++) {
            n.add(i, name.get(i));
        }
        n.setParent(name.getParent());
        n.setTokenSource(name.getTokenSource());
        n.setBeginOffset(name.getBeginOffset());
        n.setEndOffset(name.get(name.size() - 3).getEndOffset());
        return n;
    }


    /*
    Type
    a.b.c.Type
    field
    Type.field
    object.field
    a.b.c.Type.field
    o1.o2.field
     */
    private Expression parseScope(Context context, Name name, int maxIndex) {
        if (maxIndex < 0) {
            return runtime.newVariableExpression(runtime.newThis(context.enclosingType()));
        }
        // field without scope, local variable, packagePrefix
        String name0 = name.get(0).getSource();
        Expression expression;
        Variable singleName = context.variableContext().get(name0, false);
        if (singleName != null) {
            expression = runtime.newVariableExpression(singleName);
        } else {
            NamedType namedType = context.typeContext().get(name0, false);
            if (namedType instanceof TypeInfo typeInfo) {
                expression = runtime.newTypeExpression(typeInfo.asSimpleParameterizedType(), runtime.diamondNo());
            } else {
                // FIXME could be a package prefix
                throw new UnsupportedOperationException();
            }
        }
        if (maxIndex <= 1) {
            return expression;
        }
        // the next ones must be fields
        Expression scope = expression;
        for (int i = 2; i < maxIndex; i += 2) {
            TypeInfo typeInfo = scope.parameterizedType().typeInfo();
            String nameI = name.get(i).getSource();
            FieldInfo fieldInfo = typeInfo.getFieldByName(nameI, true);
            FieldReference fr = runtime.newFieldReference(fieldInfo, scope, fieldInfo.type());
            scope = runtime.newVariableExpression(fr);
        }
        return scope;
    }
}

