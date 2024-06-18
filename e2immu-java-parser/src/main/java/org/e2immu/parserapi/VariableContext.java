package org.e2immu.parserapi;

import org.e2immu.cstapi.info.ParameterInfo;
import org.e2immu.cstapi.variable.FieldReference;
import org.e2immu.cstapi.variable.LocalVariable;
import org.e2immu.cstapi.variable.Variable;

public interface VariableContext {

    Variable get(String name, boolean complain);

    void add(FieldReference variable);

    void add(ParameterInfo variable);

    void add(LocalVariable variable);
}
