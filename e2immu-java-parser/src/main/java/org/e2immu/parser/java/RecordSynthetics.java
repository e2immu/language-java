package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Assignment;
import org.e2immu.language.cst.api.info.Access;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.variable.FieldReference;

import java.util.List;

class RecordSynthetics {
    private final Runtime runtime;
    private final TypeInfo typeInfo;

    RecordSynthetics(Runtime runtime, TypeInfo typeInfo) {
        this.runtime = runtime;
        this.typeInfo = typeInfo;
    }

    MethodInfo createSyntheticConstructor(Source source, List<ParseTypeDeclaration.RecordField> recordFields) {
        MethodInfo cc = runtime.newConstructor(typeInfo, runtime.methodTypeSyntheticConstructor());
        Block.Builder methodBody = runtime.newBlockBuilder();
        Access publicAccess = runtime.accessPublic();
        for (ParseTypeDeclaration.RecordField rf : recordFields) {
            ParameterInfo pi = cc.builder().addParameter(rf.fieldInfo().name(), rf.fieldInfo().type());
            pi.builder().setSynthetic(true).setAccess(publicAccess).setVarArgs(rf.varargs()).commit();
            Assignment assignment = runtime.newAssignmentBuilder()
                    .setValue(runtime.newVariableExpression(pi))
                    .setTarget(runtime.newVariableExpression(runtime.newFieldReference(rf.fieldInfo())))
                    .build();
            methodBody.addStatement(runtime.newExpressionAsStatement(assignment));
        }
        cc.builder()
                .commitParameters()
                .addMethodModifier(runtime.methodModifierPublic())
                .setAccess(publicAccess)
                .setSynthetic(true)
                .setMethodBody(methodBody.build())
                .setSource(source)
                .setReturnType(runtime.parameterizedTypeReturnTypeOfConstructor());
        cc.builder().commit();
        return cc;
    }

    List<MethodInfo> createAccessors(List<ParseTypeDeclaration.RecordField> recordFields) {
        return recordFields.stream().map(this::createAccessor).toList();
    }

    private MethodInfo createAccessor(ParseTypeDeclaration.RecordField recordField) {
        FieldReference fr = runtime.newFieldReference(recordField.fieldInfo());
        Block methodBody = runtime.newBlockBuilder()
                .addStatement(runtime.newReturnStatement(runtime.newVariableExpression(fr)))
                .build();
        MethodInfo methodInfo = runtime.newMethod(recordField.fieldInfo().owner(), recordField.fieldInfo().name(),
                runtime.methodTypeMethod());
        MethodInfo.Builder builder = methodInfo.builder();
        builder.setReturnType(recordField.fieldInfo().type())
                .setAccess(runtime.accessPublic())
                .addMethodModifier(runtime.methodModifierPublic())
                .setSynthetic(true)
                .commitParameters()
                .setMethodBody(methodBody)
                .commit();
        return methodInfo;
    }
}
