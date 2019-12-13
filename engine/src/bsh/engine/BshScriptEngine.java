package bsh.engine;

import java.io.*;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import javax.script.*;
import bsh.*;
import static javax.script.ScriptContext.*;

public class BshScriptEngine extends AbstractScriptEngine
   implements Invocable {

	private BshScriptEngineFactory factory;
	private bsh.Interpreter interpreter;
   private static Charset utf8 = Charset.forName("UTF-8");

	public BshScriptEngine() {
	   this(null);
	}

	public BshScriptEngine(BshScriptEngineFactory factory) {
	   this.factory = factory;
	   getInterpreter(); // go ahead and prime the interpreter now
	   context.setBindings(new BshScriptEngineBindings(context),
			       ScriptContext.ENGINE_SCOPE);
	}

	protected Interpreter getInterpreter()
	{
		if ( interpreter == null ) {
			this.interpreter = new bsh.Interpreter();
			interpreter.setNameSpace(null); // should always be set by context
		}

		return interpreter;
	}

	public Object eval( String script, ScriptContext scriptContext )
		throws ScriptException
	{
		return evalSource( script, scriptContext );
	}

	public Object eval( Reader reader, ScriptContext scriptContext )
		throws ScriptException
	{
		return evalSource( reader, scriptContext );
	}

	/*
		This is the primary implementation method.
		We respect the String/Reader difference here in BeanShell because
		BeanShell will do a few extra things in the string case... e.g.
		tack on a trailing ";" semicolon if necessary.
	*/
	private Object evalSource( Object source, ScriptContext scriptContext )
		throws ScriptException
	{
		bsh.NameSpace contextNameSpace = getEngineNameSpace( scriptContext );
		Interpreter bsh = getInterpreter();
		bsh.setNameSpace( contextNameSpace );

		// This is a big hack, convert writer to PrintStream
		try {
		bsh.setOut( new PrintStream(
					    new WriterOutputStream( scriptContext.getWriter() ), false, "UTF-8" ) );
		bsh.setErr( new PrintStream(
					    new WriterOutputStream( scriptContext.getErrorWriter() ) , false, "UTF-8") );
		} catch (Exception ex) {
		   throw new IllegalStateException("Could not set output streams", ex);
		}
		try {
			if ( source instanceof Reader )
				return bsh.eval( (Reader) source );
			else
				return bsh.eval( (String) source );
		} catch (Throwable th) {
		   throw wrapException(th);
		}
	}

   
   public static ScriptException wrapException(Throwable e) {

      ScriptException se = null;

      Throwable th = e;
      if (th instanceof TargetError) {
	 while (th instanceof TargetError)
	    th = ((TargetError)th).getTarget();
      }

      if (th instanceof ParseException) {
	 // Syntax error
	 StringBuilder sb = new StringBuilder("A BeanShell parsing error occured.  The syntax of your command was incorrect.  A typical problem is a missing operator (e.g. + * ,) or parenthesis.  Sometimes the syntax problem maybe located a line or two away from where the parser reported the error. Carefully check and correct the syntax and try again.\n\nCaused by:\n   ")
	    .append(th.getMessage());

	 if (e instanceof TargetError) {
	    sb.append("\nScript traceback:")
	    .append(((EvalError)e).getTidyScriptStackTrace()).append("\n");
	 }

	 se = new ScriptException(sb.toString());

      } else if (th instanceof InterpreterError) {
	 // The interpreter had a fatal problem
	 StringBuilder sb = new StringBuilder("The BeanShell interpreter had an internal error.\n\nCaused by:\n   ");
	 sb.append(th.toString());

	 if (e instanceof TargetError) {
	    sb.append("\nScript traceback:")
	    .append(((EvalError)e).getTidyScriptStackTrace()).append("\n");
	 }

	 se = new ScriptException(sb.toString());;
	 se.initCause(e);

      } else if (th instanceof EvalError) {
	 // The script couldn't be evaluated properly

	 EvalError ee = (EvalError) th;

	 StringBuilder sb = new StringBuilder("An error occured while the BeanShell script was being evaluated.  This type of error is usually caused by mistyping a variable or class name, or by using an incorrect data type (a string where a number is required), or by having the wrong number or type of arguments to a method or constructor.  Correct your script and try again.\n\nCaused by:\n   ")
	    .append(th.getMessage())
	    .append("\nScript traceback:")
	    .append(((EvalError)e).getTidyScriptStackTrace()).append("\n");

	 se = new ScriptException(sb.toString());
	 se.initCause(e);

      } else if (e instanceof TargetError) {
	 // Application error

	 TargetError te = (TargetError) e;

	 StringBuilder sb = new StringBuilder("An error occured in the application software.  This type of error is usually caused by incorrect dataset names or other invalid parameter values that are passed into methods or constructors, or having invalid data in the input files.  Correct your script or input data files and try again.\n\nCaused by:\n   ")
	    .append(th.getMessage())
	    .append("\nScript traceback:")
	    .append(((EvalError)e).getTidyScriptStackTrace()).append("\n");
		   
	 se = new ScriptException(sb.toString());
	 se.initCause(th);
      } else {
	 // Uncaught?
	 se = new ScriptException("An unexpected error has occured while processing a script.");
	 se.initCause(e);
      }
      return se;
   }


   /*
     Check the context for an existing global namespace embedded
     in the script context engine scope.  If none exists, ininitialize the
     context with one.
   */
   private static NameSpace getEngineNameSpace(ScriptContext scriptContext) {
      Bindings b = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
      if (b == null) {
	 throw new IllegalStateException("BshEngine has null bindings");
      } else if (b instanceof BshScriptEngineBindings) 
	 return ((BshScriptEngineBindings)b).getNameSpace();
      else
	 throw new IllegalStateException("Invalid bindings class: "+b.getClass().getName());
   }

    public void setContext(ScriptContext ctxt) {
       throw new IllegalArgumentException("Cannot change script context");
    }

    public void setBindings(Bindings bindings, int scope) {
       if (scope == ScriptContext.ENGINE_SCOPE) 
	  throw new IllegalArgumentException("Cannot change engine scope bindings");
       super.setBindings(bindings, scope);
    }


   public Bindings createBindings() {
      return new BshScriptEngineBindings(context);
   }

    public ScriptEngineFactory getFactory()
	{
		if ( factory == null )
			factory = new BshScriptEngineFactory();
		return factory;
	}

	/**
	 * Calls a procedure compiled during a previous script execution, which is
	 * retained in the state of the <code>ScriptEngine<code>.
	 *
	 * @param name The name of the procedure to be called.
	 * @param thiz If the procedure is a member  of a class defined in the script
	 * and thiz is an instance of that class returned by a previous execution or
	 * invocation, the named method is called through that instance. If classes are
	 * not supported in the scripting language or if the procedure is not a member
	 * function of any class, the argument must be <code>null</code>.
	 * @param args Arguments to pass to the procedure.  The rules for converting
	 * the arguments to scripting variables are implementation-specific.
	 *
	 * @return The value returned by the procedure.  The rules for converting the
	 *         scripting variable returned by the procedure to a Java Object are
	 *         implementation-specific.
	 *
	 * @throws javax.script.ScriptException if an error occurrs during invocation
	 * of the method.
	 * @throws NoSuchMethodException if method with given name or matching argument
	 * types cannot be found.
	 * @throws NullPointerException if method name is null.
	 */
	public Object invokeMethod( Object thiz, String name, Object... args ) throws ScriptException, NoSuchMethodException
	{
		if ( ! (thiz instanceof bsh.This) )
			throw new ScriptException( "Illegal objec type: " +thiz.getClass() );

		bsh.This bshObject = (bsh.This)thiz;

		try {
			return bshObject.invokeMethod( name, args );
		} catch (Throwable th) {
		   throw wrapException(th);
		}
	}

	/**
	 * Same as invokeMethod(Object, String, Object...) with <code>null</code> as the
	 * first argument.  Used to call top-level procedures defined in scripts.
	 *
	 * @param args Arguments to pass to the procedure
	 *
	 * @return The value returned by the procedure
	 *
	 * @throws javax.script.ScriptException if an error occurrs during invocation
	 * of the method.
	 * @throws NoSuchMethodException if method with given name or matching
	 * argument types cannot be found.
	 * @throws NullPointerException if method name is null.
	 */
	public Object invokeFunction( String name, Object... args )
		throws ScriptException, NoSuchMethodException
	{
		return invokeMethod( getGlobal(), name, args );
	}

		/**
	 * Returns an implementation of an interface using procedures compiled in the
	 * interpreter. The methods of the interface may be implemented using the
	 * <code>invoke</code> method.
	 *
	 * @param clasz The <code>Class</code> object of the interface to return.
	 *
	 * @return An instance of requested interface - null if the requested interface
	 *         is unavailable, i. e. if compiled methods in the
	 *         <code>ScriptEngine</code> cannot be found matching the ones in the
	 *         requested interface.
	 *
	 * @throws IllegalArgumentException if the specified <code>Class</code> object
	 * does not exist or is not an interface.
	 */
	public <T> T getInterface( Class<T> clasz )
	{
		// try {
			return (T) getGlobal().getInterface( clasz );
		// } catch ( UtilEvalError utilEvalError ) {
		// 	utilEvalError.printStackTrace();
		// 	return null;
		// }
	}

	/**
	 * Returns an implementation of an interface using member functions of a
	 * scripting object compiled in the interpreter. The methods of the interface
	 * may be implemented using invoke(Object, String, Object...) method.
	 *
	 * @param thiz The scripting object whose member functions are used to
	 * implement the methods of the interface.
	 * @param clasz The <code>Class</code> object of the interface to return.
	 *
	 * @return An instance of requested interface - null if the requested
	 *         interface is unavailable, i. e. if compiled methods in the
	 *         <code>ScriptEngine</code> cannot be found matching the ones in the
	 *         requested interface.
	 *
	 * @throws IllegalArgumentException if the specified <code>Class</code> object
	 * does not exist or is not an interface, or if the specified Object is null
	 * or does not represent a scripting object.
	 */
	public <T> T getInterface( Object thiz, Class<T> clasz )
	{
		if ( !(thiz instanceof bsh.This) )
			throw new IllegalArgumentException(
				"invalid object type: "+thiz.getClass() );

		// try {
			bsh.This bshThis = (bsh.This)thiz;
			return (T) bshThis.getInterface( clasz );
		// } catch ( UtilEvalError utilEvalError ) {
		// 	utilEvalError.printStackTrace( System.err );
		// 	return null;
		// }
	}

	private bsh.This getGlobal()
	{
		// requires 2.0b5 to make getThis() public
		return getEngineNameSpace( getContext() ).getThis( getInterpreter() );
	}

	/*
		This is a total hack.  We need to introduce a writer to the
		Interpreter.

		Tom Moore - July 14, 2014
		Even bigger hack to change the byte stream back in to characters
		to then feed in to the writer.
	*/
	class WriterOutputStream extends OutputStream
	{
		Writer writer;
	   ByteBuffer bb;
	   CharsetDecoder decoder;
	   CharBuffer cb;
	   CoderResult result;
		WriterOutputStream( Writer writer )
		{
			this.writer = writer;
			bb = ByteBuffer.allocate(100);
			decoder = utf8.newDecoder();
			cb = CharBuffer.allocate(100);
		}

		public void write( int b ) throws IOException
		{
		   bb.put((byte) (b & 0xff));
		   if (bb.position() == bb.limit() || b == 10 || b == 13) 
		      decode(false);
		}

	   private void decode(boolean eol) throws IOException {
	      bb.flip();
	      result = decoder.decode(bb, cb, eol);
	      if (eol)
		 decoder.flush(cb);
	      if (result.isError())
		 System.err.println("Interpreter string decoding error: "+result.isError());
	      if (cb.position() > 0) {
		 cb.flip();
		 String s = cb.toString();
		 for (int i=0; i<s.length(); i++) 
		    writer.write(s.codePointAt(i));
		 cb.clear();
	      }
	      bb.compact();
	      if (eol) {
		 bb.clear();
		 decoder.reset();
	      }
	   }

		public void flush() throws IOException
		{
		   decode(true);
			writer.flush();
		}

		public void close() throws IOException
		{
			writer.close();
		}
	}

}
