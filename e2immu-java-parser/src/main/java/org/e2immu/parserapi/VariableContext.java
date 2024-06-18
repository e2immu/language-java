package org.e2immu.parserapi;

import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.variable.FieldReference;
import org.e2immu.language.cst.api.variable.LocalVariable;
import org.e2immu.language.cst.api.variable.Variable;

public interface VariableContext {

    Variable get(String name, boolean complain);

    void add(FieldReference variable);

    void add(ParameterInfo variable);

    void add(LocalVariable variable);
}
