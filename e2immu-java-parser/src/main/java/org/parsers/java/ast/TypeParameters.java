/* Generated by: CongoCC Parser Generator. Do not edit.
* Generated Code for TypeParameters AST Node type
* by the ASTNode.java.ctl template
*/
package org.parsers.java.ast;

import java.util.List;
import org.parsers.java.*;
import java.util.*;
import static org.parsers.java.Token.TokenType.*;


public class TypeParameters extends BaseNode {

    public List<TypeParameter> getParameters() {
        return childrenOfType(TypeParameter.class);
    }

}


