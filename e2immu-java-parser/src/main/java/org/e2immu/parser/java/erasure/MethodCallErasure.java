package org.e2immu.parser.java.erasure;

import org.e2immu.language.cst.api.expression.Precedence;
import org.e2immu.language.cst.api.output.OutputBuilder;
import org.e2immu.language.cst.api.output.Qualification;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;

import java.util.Objects;
import java.util.Set;

public class MethodCallErasure extends ErasureExpressionImpl {
    private final Set<ParameterizedType> returnTypes;
    private final String methodName;

    public MethodCallErasure(Runtime runtime, Set<ParameterizedType> returnTypes, String methodName) {
        super(runtime);
        this.returnTypes = returnTypes;
        this.methodName = methodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodCallErasure that)) return false;
        return Objects.equals(returnTypes, that.returnTypes) && Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnTypes, methodName);
    }

    @Override
    public Set<ParameterizedType> erasureTypes() {
        return returnTypes;
    }

    public String methodName() {
        return methodName;
    }

    @Override
    public Precedence precedence() {
        return runtime.precedenceArrayAccess();
    }

    @Override
    public OutputBuilder print(Qualification qualification) {
        String s = "<method call erasure of " + methodName + ", returning " + returnTypes + ">";
        return runtime.newOutputBuilder().add(runtime.newText(s));
    }
}
