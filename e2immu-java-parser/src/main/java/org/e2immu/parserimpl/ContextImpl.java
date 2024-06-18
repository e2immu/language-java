package org.e2immu.parserimpl;

import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.parserapi.*;
import org.e2immu.resourceapi.TypeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextImpl implements Context {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextImpl.class);

    private final Runtime runtime;
    private final TypeInfo enclosingType;
    private final MethodInfo enclosingMethod;
    private final FieldInfo enclosingField;
    private final TypeContext typeContext;
    private final VariableContext variableContext;
    private final AnonymousTypeCounters anonymousTypeCounters;
    private final ForwardType typeOfEnclosingSwitchExpression;
    private final Resolver resolver;

    public ContextImpl(Runtime runtime,
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

    @Override
    public Runtime runtime() {
        return runtime;
    }

    @Override
    public Resolver resolver() {
        return resolver;
    }

    @Override
    public TypeInfo enclosingType() {
        return enclosingType;
    }

    @Override
    public MethodInfo enclosingMethod() {
        return enclosingMethod;
    }

    @Override
    public FieldInfo enclosingField() {
        return enclosingField;
    }

    @Override
    public TypeContext typeContext() {
        return typeContext;
    }

    @Override
    public VariableContext variableContext() {
        return variableContext;
    }

    @Override
    public AnonymousTypeCounters anonymousTypeCounters() {
        return anonymousTypeCounters;
    }

    @Override
    public VariableContext dependentVariableContext() {
        return new VariableContextImpl(variableContext);
    }

    @Override
    public ForwardType typeOfEnclosingSwitchExpression() {
        return typeOfEnclosingSwitchExpression;
    }

    @Override
    public Context newCompilationUnit(Resolver resolver, TypeMap.Builder typeMap, CompilationUnit compilationUnit) {
        ImportMap importMap = new ImportMapImpl();
        TypeContext typeContext = new TypeContextImpl(typeMap, compilationUnit, importMap);
        return new ContextImpl(runtime, resolver, null, null, null,
                typeContext, new VariableContextImpl(), new AnonymousTypeCountersImpl(), null);
    }

    @Override
    public Context newVariableContext(String reason, VariableContext variableContext) {
        LOGGER.debug("Creating a new variable context for {}", reason);
        return new ContextImpl(runtime, resolver, enclosingType, enclosingMethod, enclosingField, typeContext, variableContext,
                anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }

    @Override
    public Context newVariableContextForMethodBlock(MethodInfo methodInfo, ForwardType forwardType) {
        return new ContextImpl(runtime, resolver, methodInfo.typeInfo(), methodInfo, null, typeContext,
                dependentVariableContext(), anonymousTypeCounters, forwardType);
    }

    @Override
    public Context newTypeContext(String reason) {
        LOGGER.debug("Creating a new type context for {}", reason);
        return new ContextImpl(runtime, resolver, enclosingType, enclosingMethod, enclosingField,
                new TypeContextImpl(typeContext), variableContext, anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }

    @Override
    public Context newTypeContext(FieldInfo fieldInfo) {
        return new ContextImpl(runtime, resolver, enclosingType, null, fieldInfo, typeContext, variableContext,
                anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }

    @Override
    public Context newAnonymousClassBody(TypeInfo baseType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Context newSubType(TypeInfo subType) {
        return new ContextImpl(runtime, resolver, subType, null, null, new TypeContextImpl(typeContext),
                variableContext, anonymousTypeCounters, null);
    }

    @Override
    public Context newSwitchExpressionContext(TypeInfo subType,
                                              VariableContext variableContext,
                                              ForwardType typeOfEnclosingSwitchExpression) {
        return new ContextImpl(runtime, resolver, subType, null, null, typeContext,
                variableContext, anonymousTypeCounters, typeOfEnclosingSwitchExpression);
    }

    @Override
    public Context newLambdaContext(TypeInfo subType, VariableContext variableContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ForwardType newForwardType(ParameterizedType parameterizedType) {
        return new ForwardTypeImpl(parameterizedType);
    }

    @Override
    public ForwardType emptyForwardType() {
        return new ForwardTypeImpl();
    }
}
