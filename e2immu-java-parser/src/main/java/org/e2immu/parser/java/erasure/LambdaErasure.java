package org.e2immu.parser.java.erasure;

import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Precedence;
import org.e2immu.language.cst.api.output.OutputBuilder;
import org.e2immu.language.cst.api.output.Qualification;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class LambdaErasure extends ErasureExpressionImpl {
    private final Set<Count> counts;
    private final Runtime runtime;
    private final Source source;

    public LambdaErasure(Runtime runtime, Set<Count> counts, Source source) {
        Objects.requireNonNull(counts);
        Objects.requireNonNull(source);
        this.counts = counts;
        this.source = source;
        this.runtime = runtime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LambdaErasure that)) return false;
        return Objects.equals(counts, that.counts) && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(counts, source);
    }

    public record Count(int parameters, boolean isVoid) {
    }

    @Override
    public Set<ParameterizedType> erasureTypes() {
        return counts.stream()
                .map(count -> runtime.syntheticFunctionalType(count.parameters, count.isVoid))
                .map(ti -> ti.asParameterizedType(runtime))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public ParameterizedType parameterizedType() {
        return null;
    }

    @Override
    public Precedence precedence() {
        return runtime.precedenceBottom();
    }

    @Override
    public OutputBuilder print(Qualification qualification) {
        return runtime.newOutputBuilder().add(runtime.newText("<Lambda Erasure at " + source + ": " + counts + ">"));
    }

    public Set<Count> counts() {
        return counts;
    }
}
