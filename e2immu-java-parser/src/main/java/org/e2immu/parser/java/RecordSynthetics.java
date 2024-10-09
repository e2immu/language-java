package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Assignment;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.ReturnStatement;
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
        int count = 0;
        for (ParseTypeDeclaration.RecordField rf : recordFields) {
            ParameterInfo pi = cc.builder().addParameter(rf.fieldInfo().name(), rf.fieldInfo().type());
            pi.builder().setSynthetic(true).setAccess(publicAccess).setVarArgs(rf.varargs()).commit();
            Assignment assignment = runtime.newAssignmentBuilder()
                    .setValue(runtime.newVariableExpression(pi))
                    .setTarget(runtime.newVariableExpression(runtime.newFieldReference(rf.fieldInfo())))
                    .build();
            Source statementSource = runtime.newParserSource(cc, "" + count, rf.source().beginLine(),
                    rf.source().beginPos(), rf.source().endLine(), rf.source().endPos());
            methodBody.addStatement(runtime.newExpressionAsStatementBuilder()
                    .setExpression(assignment).setSource(statementSource).build());
            ++count;
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

    private MethodInfo createAccessor(ParseTypeDeclaration.RecordField rf) {
        MethodInfo methodInfo = runtime.newMethod(rf.fieldInfo().owner(), rf.fieldInfo().name(),
                runtime.methodTypeMethod());
        FieldReference fr = runtime.newFieldReference(rf.fieldInfo());
        Source source = runtime.newParserSource(methodInfo, "0", rf.source().beginLine(),
                rf.source().beginPos(), rf.source().endLine(), rf.source().endPos());
        ReturnStatement rs = runtime.newReturnBuilder().setExpression(runtime.newVariableExpression(fr)).setSource(source).build();
        Block methodBody = runtime.newBlockBuilder().addStatement(rs).build();
        MethodInfo.Builder builder = methodInfo.builder();
        builder.setReturnType(rf.fieldInfo().type())
                .setAccess(runtime.accessPublic())
                .addMethodModifier(runtime.methodModifierPublic())
                .setSynthetic(true)
                .commitParameters()
                .setMethodBody(methodBody)
                .addOverrides(runtime.computeMethodOverrides().overrides(methodInfo))
                .commit();
        runtime.setGetSetField(methodInfo, rf.fieldInfo());
        return methodInfo;
    }
}
