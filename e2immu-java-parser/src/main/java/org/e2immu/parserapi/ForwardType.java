package org.e2immu.parserapi;

import org.e2immu.cstapi.type.ParameterizedType;

public interface ForwardType {
    boolean mustBeArray();

    ForwardType withMustBeArray();

    ParameterizedType type();
}
