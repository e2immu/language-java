/* Generated by: CongoCC Parser Generator. Do not edit.
* Generated Code for PackageDeclaration AST Node type
* by the ASTNode.java.ctl template
*/
package org.parsers.java.ast;

import org.parsers.java.*;
import java.util.*;
import static org.parsers.java.Token.TokenType.*;


public class PackageDeclaration extends BaseNode {

    public String getName() {
        return firstChildOfType(Name.class).toString();
    }

}


