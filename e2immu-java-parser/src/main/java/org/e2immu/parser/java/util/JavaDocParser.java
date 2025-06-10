package org.e2immu.parser.java.util;

import org.e2immu.language.cst.api.element.JavaDoc;
import org.e2immu.language.cst.api.element.Source;
import org.e2immu.language.cst.api.runtime.Runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record JavaDocParser(Runtime runtime) {
    private static final Pattern BLOCK_TAG = Pattern.compile("^(/\\*\\*|\\s*\\*\\s*)@(\\p{Alpha}+)");
    private static final Pattern INLINE_TAG = Pattern.compile("\\{@(\\p{Alpha}+)\\s([^}]+)}");

    public JavaDoc extractTags(String comment, Source sourceOfComment) {
        List<JavaDoc.Tag> tags = new ArrayList<>();
        int lineCount = 0;
        StringBuilder modifiedComment = new StringBuilder();
        for (String line : comment.split("\\n")) {
            Matcher blockTagMatchar = BLOCK_TAG.matcher(line);
            String restOfLine;
            if (blockTagMatchar.find()) {
                JavaDoc.TagIdentifier identifier = JavaDoc.identifier(blockTagMatchar.group(2));
                int endOfTag = blockTagMatchar.end(2);
                modifiedComment.append(blockTagMatchar.group(1));
                if (identifier != null) {
                    int args = identifier.argumentsAsBlockTag();
                    int end;
                    String content;
                    Source sourceOfReference;
                    if (args > 0) {
                        int nextSpace = line.indexOf(' ', endOfTag + 1);
                        if (nextSpace == -1) {
                            content = line.substring(endOfTag + 1);
                        } else {
                            content = line.substring(endOfTag + 1, nextSpace);
                        }
                        end = endOfTag + 1 + content.length();
                        // note that there is an offset of 1
                        sourceOfReference = subSource(sourceOfComment, lineCount, endOfTag + 2, end);
                    } else {
                        content = "";
                        end = endOfTag;
                        sourceOfReference = null;
                    }
                    Source sourceOfTag = subSource(sourceOfComment, lineCount, blockTagMatchar.start(2), end);
                    JavaDoc.Tag blockTag = runtime.newJavaDocTag(identifier, content, null, sourceOfTag,
                            sourceOfReference, true);
                    modifiedComment.append("{").append(tags.size()).append("} ");
                    tags.add(blockTag);
                    restOfLine = end + 1 >= line.length() ? "" : line.substring(end + 1);
                } else {
                    restOfLine = endOfTag >= line.length() ? "" : line.substring(endOfTag);
                    modifiedComment.append(blockTagMatchar.group());
                }
            } else {
                restOfLine = line;
            }
            Matcher inlineTagMatcher = INLINE_TAG.matcher(restOfLine);
            while (inlineTagMatcher.find()) {
                String content = inlineTagMatcher.group(2);
                JavaDoc.TagIdentifier identifier = JavaDoc.identifier(inlineTagMatcher.group(1));
                Source sourceOfTag = subSource(sourceOfComment, lineCount, inlineTagMatcher.start(), inlineTagMatcher.end());
                Source sourceOfReference = subSource(sourceOfComment, lineCount, inlineTagMatcher.start(2),
                        inlineTagMatcher.end(2));
                JavaDoc.Tag tag = runtime().newJavaDocTag(identifier, content, null, sourceOfTag,
                        sourceOfReference, false);
                inlineTagMatcher.appendReplacement(modifiedComment, "{" + tags.size() + "}");
                tags.add(tag);
            }
            inlineTagMatcher.appendTail(modifiedComment);
            modifiedComment.append('\n');
            ++lineCount;
        }
        return runtime.newJavaDoc(sourceOfComment, modifiedComment.toString(), List.copyOf(tags));
    }

    private Source subSource(Source main, int line, int begin, int end) {
        int absoluteLine = main.beginLine() + line;
        return runtime.newParserSource(main.index(), absoluteLine, begin, absoluteLine, end);
    }
}
