package org.e2immu.parser.java;

import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.parser.Context;
import org.e2immu.language.inspection.api.parser.ForwardType;
import org.e2immu.parser.java.erasure.MethodCallErasure;
import org.parsers.java.Node;
import org.parsers.java.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ParseMethodCall extends CommonParse {
    private final static Logger LOGGER = LoggerFactory.getLogger(ParseMethodCall.class);

    protected ParseMethodCall(Runtime runtime, Parsers parsers) {
        super(runtime, parsers);
    }

    public Expression parse(Context context, List<Comment> comments, Source source,
                            String index, ForwardType forwardType, org.parsers.java.ast.MethodCall mc) {
        List<Object> unparsedArguments = new ArrayList<>();
        Node name = mc.get(0);
        assert name instanceof Name || name instanceof DotName;
        String methodName = name.get(name.size() - 1).getSource();
        Name unparsedObject = newNameObject(name);

        InvocationArguments ia = (InvocationArguments) mc.get(1);
        int i = 1;
        while (i < ia.size() && !(ia.get(i) instanceof Delimiter)) {
            unparsedArguments.add(ia.get(i));
            i += 2;
        }
        if (forwardType.erasure()) {
            Set<ParameterizedType> types = context.methodResolution().computeScope(context, index, methodName,
                    unparsedObject, unparsedArguments);
            LOGGER.debug("Erasure types: {}", types);
            ParameterizedType common;
            ParameterizedType first = types.stream().findFirst().orElseThrow();
            if (types.size() > 1) {
                common = types.stream().reduce(first, runtime::commonType);
            } else {
                common = first;
            }
            return new MethodCallErasure(runtime, source, types, common, methodName);
        }
        // now we should have a more correct forward type!
        return context.methodResolution().resolveMethod(context, comments, source, index, forwardType, methodName,
                unparsedObject,
                unparsedObject == null ? runtime.noSource() : source(unparsedObject),
                unparsedArguments);
    }

    private Name newNameObject(Node name) {
        if (name.size() == 1) return null;
        Name n = new Name();
        for (int i = 0; i < name.size() - 2; i++) {
            n.add(i, name.get(i));
        }
        n.setParent(name.getParent());
        n.setTokenSource(name.getTokenSource());
        n.setBeginOffset(name.getBeginOffset());
        n.setEndOffset(name.get(name.size() - 3).getEndOffset());
        return n;
    }
}

