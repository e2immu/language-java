/* Generated by: CongoCC Parser Generator. Do not edit.
* Generated Code for SwitchStatement AST Node type
* by the ASTNode.java.ctl template
*/
package org.parsers.java.ast;

import org.parsers.java.*;
import java.util.*;
import static org.parsers.java.Token.TokenType.*;


public class SwitchStatement extends BaseNode implements Statement {

    /**
    * Is this a newer style switch statement, that uses
    * the -> arrow after case/default?
    */
    public boolean isNewStyle() {
        return firstChildOfType(NewCaseStatement.class) != null;
    }

    public Expression getSelectorExpression() {
        return (Expression) get(2);
    }

}


