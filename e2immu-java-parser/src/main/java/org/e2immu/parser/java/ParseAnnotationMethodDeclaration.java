package org.e2immu.parser.java;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.Summary;
import org.parsers.java.Node;
import org.parsers.java.ast.*;

public class ParseAnnotationMethodDeclaration extends CommonParse {
    private final ParseType parseType;

    public ParseAnnotationMethodDeclaration(Runtime runtime) {
        super(runtime);
        parseType = new ParseType(runtime);
    }

    public MethodInfo parse(Context context, AnnotationMethodDeclaration amd) {
        assert context.enclosingType() != null;

        int i = 0;
        if (amd.children().get(i) instanceof Modifiers) {
            i++;
        }
        MethodInfo.MethodType methodType;
        ParameterizedType returnType;
        Node typeNode = amd.children().get(i);
        if (typeNode instanceof Type type) {
            // depending on the modifiers...
            methodType = runtime.methodTypeMethod();
            returnType = parseType.parse(context, type);
            i++;
        } else throw new Summary.ParseException(context.info(), "Expect Type, got " + typeNode.getClass());
        String name;
        Node identifierNode = amd.children().get(i);
        if (identifierNode instanceof Identifier identifier) {
            name = identifier.getSource();
        } else throw new Summary.ParseException(context.info(), "Expect Identifier, got " + identifierNode.getClass());
        MethodInfo methodInfo = runtime.newMethod(context.enclosingType(), name, methodType);
        MethodInfo.Builder builder = methodInfo.builder().setReturnType(returnType);

        builder.commitParameters();

        builder.addComments(comments(amd));
        builder.setSource(source(methodInfo, null, amd));
        return methodInfo;
    }
}
