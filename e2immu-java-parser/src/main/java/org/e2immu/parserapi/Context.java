package org.e2immu.parserapi;

import org.e2immu.cstapi.element.CompilationUnit;
import org.e2immu.cstapi.info.FieldInfo;
import org.e2immu.cstapi.info.Info;
import org.e2immu.cstapi.info.MethodInfo;
import org.e2immu.cstapi.info.TypeInfo;
import org.e2immu.cstapi.runtime.Runtime;
import org.e2immu.cstapi.type.ParameterizedType;
import org.e2immu.resourceapi.TypeMap;

public interface Context {

    Runtime runtime();

    default Info info() {
        if (enclosingMethod() != null) return enclosingMethod();
        if (enclosingField() != null) return enclosingField();
        return enclosingType();
    }

    TypeInfo enclosingType();

    MethodInfo enclosingMethod();

    FieldInfo enclosingField();

    TypeContext typeContext();

    VariableContext variableContext();

    AnonymousTypeCounters anonymousTypeCounters();

    Resolver resolver();

    // variable contexts

    VariableContext dependentVariableContext();

    default Context newVariableContext(String reason) {
        return newVariableContext(reason, dependentVariableContext());
    }

    ForwardType typeOfEnclosingSwitchExpression();

    Context newCompilationUnit(Resolver resolver, TypeMap.Builder typeMap, CompilationUnit compilationUnit);

    Context newVariableContext(String reason, VariableContext variableContext);

    Context newVariableContextForMethodBlock(MethodInfo methodInfo, ForwardType forwardType);

    // type contexts

    Context newTypeContext(String reason);

    Context newTypeContext(FieldInfo fieldInfo);

    Context newAnonymousClassBody(TypeInfo baseType);

    Context newSubType(TypeInfo subType);

    Context newSwitchExpressionContext(TypeInfo subType, VariableContext variableContext, ForwardType forwardType);

    Context newLambdaContext(TypeInfo subType, VariableContext variableContext);


    // factory

    ForwardType newForwardType(ParameterizedType parameterizedType);
    ForwardType emptyForwardType();

}
