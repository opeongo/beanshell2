/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package bsh;

/**
	EvalError indicates that we cannot continue evaluating the script
	or the script has thrown an exception.

	EvalError may be thrown for a script syntax error, an evaluation 
	error such as referring to an undefined variable, an internal error.
	<p>
	
	@see TargetError
*/
public class EvalError extends Exception 
{
	private SimpleNode node;

	// Note: no way to mutate the Throwable message, must maintain our own
	private String message;

	private CallStack callstack;

	public EvalError( String s, SimpleNode node, CallStack callstack, Throwable cause ) {
		this(s,node,callstack);
		initCause(cause);
	}

	public EvalError( String s, SimpleNode node, CallStack callstack ) {
		setMessage(s);
		this.node = node;
		// freeze the callstack for the stack trace.
		if ( callstack != null )
			this.callstack = callstack.copy();
	}

	/**
		Print the error with line number and stack trace.
	*/
	public String toString() 
	{
		String trace;
		if ( node != null )
			trace = " : at Line: "+ node.getLineNumber() 
				+ " : in file: "+ node.getSourceFile()
				+ " : "+node.getText();
		else
			// Users should not normally see this.
			trace = ": <at unknown location>";

		if ( callstack != null )
			trace = trace +"\n" + getScriptStackTrace();

		return getMessage() + trace;
	}

	/**
		Re-throw the error, prepending the specified message.
	*/
	public void reThrow( String msg ) 
		throws EvalError 
	{
		prependMessage( msg );
		throw this;
	}

	/**
		The error has trace info associated with it. 
		i.e. It has an AST node that can print its location and source text.
	*/
	SimpleNode getNode() {
		return node;
	}

	void setNode( SimpleNode node ) {
		this.node = node;
	}

	public String getErrorText() { 
		if ( node != null )
			return node.getText() ;
		else
			return "<unknown error>";
	}

	public int getErrorLineNumber() { 
		if ( node != null )
			return node.getLineNumber() ;
		else
			return -1;
	}

	public String getErrorSourceFile() {
		if ( node != null )
			return node.getSourceFile() ;
		else
			return "<unknown file>";
	}

	public String getScriptStackTrace() 
	{
		if ( callstack == null )
			return "<Unknown>";

		String trace = "";
		CallStack stack = callstack.copy();
		while ( stack.depth() > 0 ) 
		{
			NameSpace ns = stack.pop();
			SimpleNode node = ns.getNode();
			if ( ns.isMethod )
			{
				trace = trace + "\nCalled from method: " + ns.getName();
				if ( node != null )
					trace += " : at Line: "+ node.getLineNumber() 
						+ " : in file: "+ node.getSourceFile()
						+ " : "+node.getText();
			}
		}

		return trace;
	}

	/**
		@see #toString() for a full display of the information
	*/
	public String getMessage() { return message; }

	public void setMessage( String s ) { message = s; }

	/**
		Prepend the message if it is non-null.
	*/
	protected void prependMessage( String s ) 
	{ 
		if ( s == null )
			return;

		if ( message == null )
			message = s;
		else
			message = s + " : "+ message;
	}

   /**
    * Decode traceback information in a tidy format
    */
   public String getTidyScriptStackTrace() {
      StringBuilder sb = new StringBuilder();
      getTidyScriptStackTrace(this, sb);
      return sb.toString();
   }

   /**
    * Recursively process tracebacks
    */
   private static void getTidyScriptStackTrace(Throwable th, StringBuilder sb) {

      /*
       * Quit if out of the bsh error chain
       */
      if (!(th instanceof EvalError))
	 return;

      EvalError ee = (EvalError) th;

      /*
       * Recurse to reverse the order of messages (last first)
       */
      Throwable target = null;
      if (ee instanceof TargetError) {
	 target = ((TargetError)ee).getTarget();
	 getTidyScriptStackTrace(target, sb);
      }

      /*
       * In case we are at a terminal exception and getting in to an application error, 
       * add the final location information
       */
      if (!(target instanceof EvalError)) {
	 SimpleNode node = ee.node;
	 if (node != null) {
	    sb.append("\n    at ")
	       .append(node.getSourceFile()).append(":").append(node.getLineNumber())
	       .append("  '").append(node.getText()).append("'");
	 }
      }

      /*
       * In case we are lost...?
       */
      if (ee.callstack == null) {
	 sb.append("\n   <Unknown>");
	 return;
      }

      /*
       * Work through the call stack and add location info
       */
      CallStack stack = ee.callstack.copy();
      while (stack.depth() > 0) {
	 NameSpace ns = stack.pop();
	 SimpleNode node = ns.getNode();

	 if (ns.isMethod && node != null )
	    sb.append("\n    at ")
	       .append(node.getSourceFile()).append(":").append(node.getLineNumber())
	       .append("  '").append(node.getText()).append("'");
      }

      return;
   }
}

