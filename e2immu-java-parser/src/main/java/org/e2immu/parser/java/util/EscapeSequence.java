package org.e2immu.parser.java.util;

public class EscapeSequence {
    public static char escapeSequence(char c2) {
        return switch (c2) {
            case '0' -> '\0';
            case 'b' -> '\b';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'n' -> '\n';
            case 'f' -> '\f';
            case '\'' -> '\'';
            case '\\' -> '\\';
            case '"' -> '"';
            default -> {
                throw new UnsupportedOperationException();
            }
        };
    }

    // replace everything except for \<line terminator>
    public static String translateEscapeInTextBlock(String in) {
        StringBuilder sb = new StringBuilder(in.length());
        int i = 0;
        char[] chars = in.toCharArray();
        while (i < chars.length) {
            char c = chars[i];
            if (c == '\\' && i + 1 < chars.length) {
                char c2 = chars[i + 1];
                if (c2 != '\n') {
                    sb.append(escapeSequence(c2));
                    ++i;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
            ++i;
        }
        return sb.toString();
    }
}
