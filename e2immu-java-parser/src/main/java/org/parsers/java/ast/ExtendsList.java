/* Generated by: CongoCC Parser Generator. Do not edit.
* Generated Code for ExtendsList AST Node type
* by the ASTNode.java.ctl template
*/
package org.parsers.java.ast;

import java.util.List;
import org.parsers.java.*;
import java.util.*;
import static org.parsers.java.Token.TokenType.*;


public class ExtendsList extends BaseNode {

    public List<ObjectType> getTypes() {
        return childrenOfType(ObjectType.class);
    }

}


