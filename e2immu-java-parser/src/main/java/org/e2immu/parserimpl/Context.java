package org.e2immu.parserimpl;

import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;

import org.e2immu.language.inspection.api.parser.*;
import org.e2immu.language.inspection.api.resource.TypeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Context {
    private static final Logger LOGGER = LoggerFactory.getLogger(Context.class);

    private final Runtime runtime;
    private final TypeInfo enclosingType;
    private final MethodInfo enclosingMethod;
    private final FieldInfo enclosingField;
    private final TypeContext typeContext;
    private final VariableContext variableContext;
    private final AnonymousTypeCounters anonymousTypeCounters;
    private final ForwardType typeOfEnclosingSwitchExpression;
    private final Resolver resolver;

    public Context(Runtime runtime,
                   Resolver resolver,
                   TypeInfo enclosingType,
                   MethodInfo enclosingMethod,
                   FieldInfo enclosingField,
                   TypeContext typeContext,
                   VariableContext variableContext,
                   AnonymousTypeCounters anonymousTypeCounters,
                   ForwardType typeOfEnclosingSwitchExpression) {
        this.runtime = runtime;
        this.resolver = resolver;
        this.enclosingType = enclosingType;
        this.enclosingMethod = enclosingMethod;
        this.enclosingField = enclosingField;
        this.typeContext = typeContext;
        this.variableContext = variableContext;
        this.anonymousTypeCounters = anonymousTypeCounters;
        this.typeOfEnclosingSwitchExpression = typeOfEnclosingSwitchExpression;
    }

    public Info info() {
        if (enclosingField != null) return enclosingField;
        if (enclosingMethod != null) return enclosingMethod;
        return enclosingType;
    }

    public Runtime runtime() {
        return runtime;
    }


    public Resolver resolver() {
        return resolver;
    }


    public TypeInfo enclosingType() {
        return enclosingType;
    }


    public MethodInfo enclosingMethod() {
        return enclosingMethod;
    }


    public FieldInfo enclosingField() {
        return enclosingField;
    }


    public TypeContext typeContext() {
        return typeContext;
    }


    public VariableContext variableContext() {
        return variableContext;
    }


    public AnonymousTypeCounters anonymousTypeCounters() {
        return anonymousTypeCounters;
    }


    public VariableContext dependentVariableContext() {
        return variableContext.newVariableContext();
    }


    public ForwardType typeOfEnclosingSwitchExpression() {
        return typeOfEnclosingSwitchExpression;
    }

    public Context newCompilationUnit(Resolver resolver, TypeMap.Builder typeMap, CompilationUnit compilationUnit) {
        TypeContext typeContext = typeContext().newCompilationUnit(typeMap, compilationUnit);
        return new Context(runtime, resolver, null, null, null,
                typeContext, variableContext.newEmpty(), anonymousTypeCounters.newEmpty(), null);
    }

    public Context newVariableContext(String reason, VariableContext variableContext) {
        LOGGER.debug("Creating a new variable context for {}", reason);
        return new Context(runtime, resolver, enclosingType, enclosingMethod, enclosingField, typeContext, variableContext,
                anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }

    public Context newVariableContext(String reason) {
        LOGGER.debug("Creating a new variable context for {}", reason);
        VariableContext newVariableContext = variableContext.newVariableContext();
        return new Context(runtime, resolver, enclosingType, enclosingMethod, enclosingField, typeContext, newVariableContext,
                anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }

    public Context newVariableContextForMethodBlock(MethodInfo methodInfo, ForwardType forwardType) {
        return new Context(runtime, resolver, methodInfo.typeInfo(), methodInfo, null, typeContext,
                dependentVariableContext(), anonymousTypeCounters, forwardType);
    }


    public Context newTypeContext(String reason) {
        LOGGER.debug("Creating a new type context for {}", reason);
        return new Context(runtime, resolver, enclosingType, enclosingMethod, enclosingField,
             typeContext.newTypeContext(), variableContext, anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }


    public Context newTypeContext(FieldInfo fieldInfo) {
        return new Context(runtime, resolver, enclosingType, null, fieldInfo, typeContext, variableContext,
                anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }


    public Context newAnonymousClassBody(TypeInfo baseType) {
        throw new UnsupportedOperationException();
    }


    public Context newSubType(TypeInfo subType) {
        return new Context(runtime, resolver, subType, null, null, typeContext.newTypeContext(),
                variableContext, anonymousTypeCounters, null);
    }


    public Context newSwitchExpressionContext(TypeInfo subType,
                                              VariableContext variableContext,
                                              ForwardType typeOfEnclosingSwitchExpression) {
        return new Context(runtime, resolver, subType, null, null, typeContext,
                variableContext, anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }


    public Context newLambdaContext(TypeInfo subType, VariableContext variableContext) {
        throw new UnsupportedOperationException();
    }


    public ForwardType newForwardType(ParameterizedType parameterizedType) {
        return new ForwardType(parameterizedType);
    }


    public ForwardType emptyForwardType() {
        return new ForwardType();
    }
}
