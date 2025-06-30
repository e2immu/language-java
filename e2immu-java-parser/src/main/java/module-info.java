module org.e2immu.language.java.parser {
    requires org.e2immu.util.external.support;
    requires org.e2immu.util.internal.util;
    requires org.e2immu.language.cst.api;
    requires org.e2immu.language.inspection.api;

    requires org.slf4j;

    exports org.e2immu.parser.java;
    exports org.parsers.java;
    exports org.parsers.java.ast;
}