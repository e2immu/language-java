/* Generated by: CongoCC Parser Generator. Do not edit.
* Generated Code for Expression AST Node type
* by the ASTNode.java.ftl template
*/
package org.parsers.java.ast;

import org.parsers.java.*;
import java.util.*;


public interface Expression extends Node {

    default boolean isAssignableTo() {
        return false;
    }

    default String getAsString() {
        return toString();
    }

}


