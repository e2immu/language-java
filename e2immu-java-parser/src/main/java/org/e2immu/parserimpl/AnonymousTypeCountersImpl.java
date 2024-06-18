package org.e2immu.parserimpl;

import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.parserapi.AnonymousTypeCounters;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AnonymousTypeCountersImpl implements AnonymousTypeCounters {
    final Map<TypeInfo, AtomicInteger> anonymousClassCounter = new HashMap<>();

    @Override
    public synchronized int newIndex(TypeInfo typeInfo) {
        return anonymousClassCounter.computeIfAbsent(typeInfo, t -> new AtomicInteger()).incrementAndGet();
    }
}
