package org.e2immu.parserimpl;

import org.e2immu.annotation.NotNull;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.translate.TranslationMap;
import org.e2immu.language.cst.api.type.NamedType;
import org.e2immu.language.cst.api.type.ParameterizedType;

import java.util.*;
import java.util.stream.Collectors;

public class MethodTypeParameterMap {

    private final MethodInfo methodInfo;
    private final Map<NamedType, ParameterizedType> concreteTypes;

    public MethodTypeParameterMap(MethodInfo methodInfo, @NotNull Map<NamedType, ParameterizedType> concreteTypes) {
        this.methodInfo = methodInfo; // can be null, for SAMs
        this.concreteTypes = Map.copyOf(concreteTypes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        return o instanceof MethodTypeParameterMap m && methodInfo.equals(m.methodInfo);
    }

    @Override
    public int hashCode() {
        return methodInfo.hashCode();
    }

    public boolean isSingleAbstractMethod() {
        return methodInfo != null;
    }

    public ParameterizedType getConcreteReturnType(Runtime runtime) {
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");
        ParameterizedType returnType = methodInfo.returnType();
        return returnType.applyTranslation(runtime, concreteTypes);
    }

    public ParameterizedType getConcreteTypeOfParameter(Runtime runtime, int i) {
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");
        int n = methodInfo.parameters().size();
        int index;
        if (i >= n) {
            // varargs
            index = n - 1;
        } else {
            index = i;
        }

        ParameterizedType parameterizedType = methodInfo.parameters().get(index).parameterizedType();
        return parameterizedType.applyTranslation(runtime, concreteTypes);
    }

    public MethodTypeParameterMap expand(Runtime runtime, TypeInfo primaryType,
                                         Map<NamedType, ParameterizedType> mapExpansion) {
        Map<NamedType, ParameterizedType> join = new HashMap<>(concreteTypes);
        mapExpansion.forEach((k, v) -> join.merge(k, v, (v1, v2) -> v1.mostSpecific(runtime, primaryType, v2)));
        return new MethodTypeParameterMap(methodInfo, Map.copyOf(join));
    }

    @Override
    public String toString() {
        return (isSingleAbstractMethod()
                ? ("method " + methodInfo.fullyQualifiedName())
                : "No method") + ", map " + concreteTypes;
    }

    public ParameterizedType inferFunctionalType(Runtime runtime,
                                                 List<ParameterizedType> types,
                                                 ParameterizedType inferredReturnType) {
        Objects.requireNonNull(inferredReturnType);
        Objects.requireNonNull(types);
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");

        List<ParameterizedType> parameters = typeParametersComputed(runtime, methodInfo, types, inferredReturnType);
        return runtime.newParameterizedType(methodInfo.typeInfo(), parameters);
    }

    /**
     * Example: methodInfo = R apply(T t); typeInfo = Function&lt;T, R&gt;; types: one value: the concrete type for
     * parameter #0 in apply; inferredReturnType: the concrete type for R, the return type.
     *
     * @param runtime            to access inspection
     * @param methodInfo         the SAM (e.g. accept, test, apply)
     * @param types              as provided by ParseMethodReference, or ParseLambdaExpr. They represent the concrete
     *                           types of the SAM
     * @param inferredReturnType the return type of the real method
     * @return a list of type parameters for the functional type
     */


    private static List<ParameterizedType> typeParametersComputed(
            Runtime runtime,
            MethodInfo methodInfo,
            List<ParameterizedType> types,
            ParameterizedType inferredReturnType) {
        TypeInfo typeInfo = methodInfo.typeInfo();
        if (typeInfo.typeParameters().isEmpty()) return List.of();
        // Function<T, R> -> loop over T and R, and see where they appear in the apply method.
        // If they appear as a parameter, then take the type from "types" which agrees with that parameter
        // If it appears as the return type, then return "inferredReturnType"
        return typeInfo.typeParameters().stream()
                .map(typeParameter -> {
                    int cnt = 0;
                    for (ParameterInfo parameterInfo : methodInfo.parameters()) {
                        if (parameterInfo.parameterizedType().typeParameter() == typeParameter) {
                            return types.get(cnt); // this is one we know!
                        }
                        cnt++;
                    }
                    if (methodInfo.returnType().typeParameter() == typeParameter)
                        return inferredReturnType;
                    return runtime.newParameterizedType(typeParameter, 0, null);
                })
                .map(pt -> pt.ensureBoxed(runtime))
                .collect(Collectors.toList());
    }


    public boolean isAssignableFrom(MethodTypeParameterMap other) {
        if (!isSingleAbstractMethod() || !other.isSingleAbstractMethod()) throw new UnsupportedOperationException();
        if (methodInfo.equals(other.methodInfo)) return true;
        if (methodInfo.parameters().size() != other.methodInfo.parameters().size())
            return false;
        /*
        int i = 0;
        for (ParameterInfo pi : methodInspection.getParameters()) {
            ParameterInfo piOther = other.methodInspection.getParameters().get(i);
            i++;
        }
        // TODO
         */
        return methodInfo.returnType().isVoidOrJavaLangVoid() ==
               other.methodInfo.returnType().isVoidOrJavaLangVoid();
    }
/*
    // used in TypeInfo.convertMethodReferenceIntoLambda
    public MethodInfo.Builder buildCopy(Runtime runtime,
                                        TypeInfo typeInfo) {
        String methodName = methodInfo.name();
        MethodInfo copy = runtime.newMethod(typeInfo, methodName, methodInfo.methodType());
        MethodInfo.Builder copyBuilder = copy.builder();
        copyBuilder.addMethodModifier(runtime.methodModifierPUBLIC());

        for (ParameterInfo p : methodInfo.parameters()) {
            ParameterInspection.Builder newParameterBuilder = copy.newParameterInspectionBuilder(
                    p.identifier,
                    getConcreteTypeOfParameter(runtime.getPrimitives(), p.index), p.name, p.index);
            if (p.parameterInspection.get().isVarArgs()) {
                newParameterBuilder.setVarArgs(true);
            }
            copy.addParameter(newParameterBuilder);
        }
        copy.setReturnType(getConcreteReturnType(runtime.getPrimitives()));
        copy.readyToComputeFQN(runtime);
        return copy;
    }*/

    public MethodTypeParameterMap translate(TranslationMap translationMap) {
        return new MethodTypeParameterMap(methodInfo, concreteTypes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> translationMap.translateType(e.getValue()))));
    }

    public ParameterizedType parameterizedType(int pos) {
        List<ParameterInfo> parameters = methodInfo.parameters();
        if (pos < parameters.size()) return parameters.get(pos).parameterizedType();
        ParameterInfo lastOne = parameters.get(parameters.size() - 1);
        if (!lastOne.isVarArgs()) throw new UnsupportedOperationException();
        return lastOne.parameterizedType().copyWithOneFewerArrays();
    }

    /*
    CT = concreteTypes

    CT:  T in Function -> AL<LL<S>>
    F2C: T in Function -> Coll<E>
    result: E in Coll -> LL<S>

    CT:  R in Function -> Stream<? R in flatMap>
    F2C: R in Function -> Stream<E in Coll>
    result: E in Coll = R in flatMap (is of little value here)
     */
    public Map<NamedType, ParameterizedType> formalOfSamToConcreteTypes(MethodInfo actualMethod, Runtime runtime) {
        Map<NamedType, ParameterizedType> result = new HashMap<>(concreteTypes);

        TypeInfo functionType = this.methodInfo.typeInfo();
        MethodInfo sam = functionType.singleAbstractMethod();
        // match types of actual method inspection to type parameters of sam
        if (sam.returnType().isTypeParameter()) {
            NamedType f2cFrom = sam.returnType().typeParameter();
            ParameterizedType f2cTo = actualMethod.returnType();
            ParameterizedType ctTo = concreteTypes.get(f2cFrom);
            match(runtime, f2cFrom, f2cTo, ctTo, result);
        }
        if (!actualMethod.isStatic() && !functionType.typeParameters().isEmpty()) {
            NamedType f2cFrom = functionType.typeParameters().get(0);
            ParameterizedType f2cTo = actualMethod.typeInfo().asParameterizedType(runtime);
            ParameterizedType ctTo = concreteTypes.get(f2cFrom);
            match(runtime, f2cFrom, f2cTo, ctTo, result);
        }
        // TODO for-loop: make an equivalent with more type parameters MethodReference_2
        return result;
    }

    /*
    f2cFrom = T in function
    fc2To = Coll<E>
    ctTo = ArrayList<LinkedList<String>>

     */
    private void match(Runtime runtime, NamedType f2cFrom, ParameterizedType f2cTo,
                       ParameterizedType ctTo, Map<NamedType, ParameterizedType> result) {
        if (f2cTo.isAssignableFrom(runtime, ctTo)) {
            ParameterizedType concreteSuperType = ctTo.concreteSuperType(runtime, f2cTo);
            int i = 0;
            for (ParameterizedType pt : f2cTo.parameters()) {
                if (pt.isTypeParameter()) {
                    result.put(pt.typeParameter(), concreteSuperType.parameters().get(i));
                }
                i++;
            }
        }
    }


    public static MethodTypeParameterMap findSingleAbstractMethodOfInterface(Runtime runtime, ParameterizedType parameterizedType) {
        return findSingleAbstractMethodOfInterface(runtime, parameterizedType, true);
    }

    /**
     * @param complain If false, accept that we're in a functional interface, and do not complain. Only used in the recursion.
     *                 If true, then starting point of the recursion. We need a functional interface, and will complain at the end.
     * @return the combination of method and initial type parameter map
     */
    public static MethodTypeParameterMap findSingleAbstractMethodOfInterface(Runtime runtime,
                                                                             ParameterizedType parameterizedType,
                                                                             boolean complain) {
        if (parameterizedType.typeInfo() == null) return null;
        MethodInfo theMethod = parameterizedType.typeInfo().singleAbstractMethod();
        if (theMethod == null) {
            if (complain) {
                throw new UnsupportedOperationException("Cannot find a single abstract method in the interface "
                                                        + parameterizedType.detailedString());
            }
            return null;
        }
        /* if theMethod comes from a superType, we need a full type parameter map,
           e.g., BinaryOperator -> BiFunction.apply, we need concrete values for T, U, V of BiFunction
         */
        Map<NamedType, ParameterizedType> map;
        if (theMethod.typeInfo().equals(parameterizedType.typeInfo())) {
            map = parameterizedType.initialTypeParameterMap(runtime);
        } else {
            map = makeTypeParameterMap(runtime, theMethod, parameterizedType, new HashSet<>());
            assert map != null; // the method must be somewhere in the hierarchy
        }
        return new MethodTypeParameterMap(theMethod, map);
    }


    private static Map<NamedType, ParameterizedType> makeTypeParameterMap(Runtime runtime,
                                                                          MethodInfo methodInfo,
                                                                          ParameterizedType here,
                                                                          Set<TypeInfo> visited) {
        if (visited.add(here.typeInfo())) {
            if (here.typeInfo().equals(methodInfo.typeInfo())) {
                return here.initialTypeParameterMap(runtime);
            }
            for (ParameterizedType superType : here.typeInfo().interfacesImplemented()) {
                Map<NamedType, ParameterizedType> map = makeTypeParameterMap(runtime, methodInfo, superType, visited);
                if (map != null) {
                    Map<NamedType, ParameterizedType> concreteHere = here.initialTypeParameterMap(runtime);
                    Map<NamedType, ParameterizedType> newMap = new HashMap<>();
                    for (Map.Entry<NamedType, ParameterizedType> e : map.entrySet()) {
                        ParameterizedType newValue;
                        if (e.getValue().isTypeParameter()) {
                            newValue = concreteHere.get(e.getValue().typeParameter());
                        } else {
                            newValue = e.getValue();
                        }
                        newMap.put(e.getKey(), newValue);
                    }
                    return newMap;
                }
            }
        }
        return null; // not here
    }

    /*
    Starting from a formal type (List<E>), fill in a translation map given a concrete type (List<String>)
    IMPORTANT: the formal type has to have its formal parameters present, i.e., starting from TypeInfo,
    you should call this method on typeInfo.asParameterizedType(inspectionProvider) to ensure all formal
    parameters are present in this object.

    In the case of functional interfaces, this method goes via the SAM, avoiding the need of a formal implementation
    of the interface (i.e., a functional interface can have a SAM which is a function (1 argument, 1 return type)
    without explicitly implementing java.lang.function.Function)

    The third parameter decides the direction of the relation between the formal and the concrete type.
    When called from ParseMethodCallExpr, for example, 'this' is the parameter's formal parameter, and the concrete
    type has to be assignable to it.
     */

    public static Map<NamedType, ParameterizedType> translateMap(Runtime runtime,
                                                                 ParameterizedType formalType,
                                                                 ParameterizedType concreteType,
                                                                 boolean concreteTypeIsAssignableToThis) {
        if (formalType.parameters().isEmpty()) {
            if (formalType.isTypeParameter()) {
                if (formalType.arrays() > 0) {
                    if (concreteType.isFunctionalInterface()) {
                        // T[], Expression[]::new == IntFunction<Expression>
                        ParameterizedType arrayType = findSingleAbstractMethodOfInterface(runtime, concreteType)
                                .getConcreteReturnType(runtime);
                        return Map.of(formalType.typeParameter(), arrayType.copyWithFewerArrays(formalType.arrays()));
                    }
                    // T <-- String,  T[],String[] -> T <-- String, T[],String[][] -> T <- String[]
                    if (concreteType.arrays() > 0) {
                        return Map.of(formalType.typeParameter(), concreteType.copyWithFewerArrays(formalType.arrays()));
                    }
                }
                return Map.of(formalType.typeParameter(), concreteType);
            }
            // String <-- String, no translation map
            return Map.of();
        }
        assert formalType.typeInfo() != null;
        // no hope if Object or unbound wildcard is the best we have
        if (concreteType.typeInfo() == null || concreteType.isJavaLangObject()) return Map.of();

        if (formalType.isFunctionalInterface() && concreteType.isFunctionalInterface()) {
            return translationMapForFunctionalInterfaces(runtime, formalType, concreteType, concreteTypeIsAssignableToThis);
        }

        Map<NamedType, ParameterizedType> mapOfConcreteType = concreteType.initialTypeParameterMap(runtime);
        Map<NamedType, ParameterizedType> formalMap;
        if (formalType.typeInfo() == concreteType.typeInfo()) {
            // see Lambda_8 Stream<R>, R from flatmap -> Stream<T>
            formalMap = formalType.forwardTypeParameterMap(runtime);
        } else if (concreteTypeIsAssignableToThis) {
            // this is the super type (Set), concrete type is the subtype (HashSet)
            formalMap = mapInTermsOfParametersOfSuperType(runtime, concreteType.typeInfo(), formalType);
        } else {
            // concrete type is the super type, we MUST work towards the supertype!
            formalMap = mapInTermsOfParametersOfSubType(runtime, formalType.typeInfo(), concreteType);
        }
        if (formalMap == null) return mapOfConcreteType;
        return combineMaps(mapOfConcreteType, formalMap);
    }

    // TODO write tests!
    private static Map<NamedType, ParameterizedType> translationMapForFunctionalInterfaces(Runtime runtime,
                                                                                           ParameterizedType formalType,
                                                                                           ParameterizedType concreteType,
                                                                                           boolean concreteTypeIsAssignableToThis) {
        Map<NamedType, ParameterizedType> res = new HashMap<>();
        MethodTypeParameterMap methodTypeParameterMap = findSingleAbstractMethodOfInterface(runtime, formalType);
        List<ParameterInfo> methodParams = methodTypeParameterMap.methodInfo.parameters();
        MethodTypeParameterMap concreteTypeMap = findSingleAbstractMethodOfInterface(runtime, concreteType);
        List<ParameterInfo> concreteTypeAbstractParams = concreteTypeMap.methodInfo.parameters();

        if (methodParams.size() != concreteTypeAbstractParams.size()) {
            throw new UnsupportedOperationException("Have different param sizes for functional interface " +
                                                    formalType.detailedString() + " method " +
                                                    methodTypeParameterMap.methodInfo.fullyQualifiedName() + " and " +
                                                    concreteTypeMap.methodInfo.fullyQualifiedName());
        }
        for (int i = 0; i < methodParams.size(); i++) {
            ParameterizedType abstractTypeParameter = methodParams.get(i).parameterizedType();
            ParameterizedType concreteTypeParameter = concreteTypeMap.getConcreteTypeOfParameter(runtime, i);
            res.putAll(translateMap(runtime, abstractTypeParameter, concreteTypeParameter,
                    concreteTypeIsAssignableToThis));
        }
        // and now the return type
        ParameterizedType myReturnType = methodTypeParameterMap.getConcreteReturnType(runtime);
        ParameterizedType concreteReturnType = concreteTypeMap.getConcreteReturnType(runtime);
        res.putAll(translateMap(runtime, myReturnType, concreteReturnType, concreteTypeIsAssignableToThis));
        return res;
    }

    /*
 StringMap<V> -> HashMap<K,V> -> Map<K, V>

 M2: K(map) -> K(hashmap), M1: K(hashmap) -> String
  */
    public static Map<NamedType, ParameterizedType> combineMaps(Map<NamedType, ParameterizedType> m1, Map<NamedType, ParameterizedType> m2) {
        assert m1 != null;
        return m2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().isTypeParameter() ? m1.getOrDefault(e.getValue().typeParameter(), e.getValue()) : e.getValue(),
                (v1, v2) -> {
                    throw new UnsupportedOperationException();
                }, LinkedHashMap::new));
    }

    public static Map<NamedType, ParameterizedType> mapInTermsOfParametersOfSuperType(Runtime runtime,
                                                                                      TypeInfo ti,
                                                                                      ParameterizedType superType) {
        assert superType.typeInfo() != ti;
        if (ti.parentClass() != null) {
            if (ti.parentClass().typeInfo() == superType.typeInfo()) {
                Map<NamedType, ParameterizedType> forward = superType.forwardTypeParameterMap(runtime);
                Map<NamedType, ParameterizedType> formal = ti.parentClass().initialTypeParameterMap(runtime);
                return combineMaps(forward, formal);
            }
            Map<NamedType, ParameterizedType> map = mapInTermsOfParametersOfSuperType(runtime,
                    ti.parentClass().typeInfo(), superType);
            if (map != null) {
                return combineMaps(ti.parentClass().initialTypeParameterMap(runtime), map);
            }
        }
        for (ParameterizedType implementedInterface : ti.interfacesImplemented()) {
            if (implementedInterface.typeInfo() == superType.typeInfo()) {
                Map<NamedType, ParameterizedType> forward = superType.forwardTypeParameterMap(runtime);
                Map<NamedType, ParameterizedType> formal = implementedInterface.initialTypeParameterMap(runtime);
                return combineMaps(formal, forward);
            }
            Map<NamedType, ParameterizedType> map = mapInTermsOfParametersOfSuperType(runtime,
                    implementedInterface.typeInfo(), superType);
            if (map != null) {
                return combineMaps(implementedInterface.initialTypeParameterMap(runtime), map);
            }
        }
        return null; // not in this branch of the recursion
    }

    // practically the duplicate of the previous, except that we should parameterize initialTypeParameterMap as well to collapse them
    public static Map<NamedType, ParameterizedType> mapInTermsOfParametersOfSubType(Runtime runtime,
                                                                                    TypeInfo ti,
                                                                                    ParameterizedType superType) {
        assert superType.typeInfo() != ti;
        if (ti.parentClass() != null) {
            if (ti.parentClass().typeInfo() == superType.typeInfo()) {
                return ti.parentClass().forwardTypeParameterMap(runtime);
            }
            Map<NamedType, ParameterizedType> map = mapInTermsOfParametersOfSubType(runtime,
                    ti.parentClass().typeInfo(), superType);
            if (map != null) {
                return combineMaps(map, ti.parentClass().forwardTypeParameterMap(runtime));
            }
        }
        for (ParameterizedType implementedInterface : ti.interfacesImplemented()) {
            if (implementedInterface.typeInfo() == superType.typeInfo()) {
                return implementedInterface.forwardTypeParameterMap(runtime);
            }
            Map<NamedType, ParameterizedType> map = mapInTermsOfParametersOfSubType(runtime,
                    implementedInterface.typeInfo(), superType);
            if (map != null) {
                return combineMaps(map, implementedInterface.forwardTypeParameterMap(runtime));
            }
        }
        return null; // not in this branch of the recursion
    }

}
