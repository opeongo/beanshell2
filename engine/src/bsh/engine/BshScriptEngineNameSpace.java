package bsh.engine;

import java.util.Arrays;
import java.util.HashSet;
import javax.script.Bindings;
import javax.script.ScriptContext;
import bsh.Modifiers;
import bsh.NameSpace;
import bsh.Primitive;
import bsh.UtilEvalError;
import bsh.Variable;

/**
 * This is a special top-level namespace for the BeanShell JSR-233
 * scripting engine interface.  This namespace provides transparent
 * read-only access to the global scope variables.
 * 
 * Use this class in conjuction with {@link BshScriptEngineBindings} 
 * that exposes the variable bindings to the {@link ScriptEngine} interface.
 */
class BshScriptEngineNameSpace extends NameSpace {

   static Modifiers finalMod;
   static {
      finalMod = new Modifiers();
      finalMod.addModifier(Modifiers.FIELD, "final");
   }

   private Bindings global;
   private ScriptContext context;

   BshScriptEngineNameSpace(ScriptContext context) {
      super((NameSpace) null, "ScriptEngine");
      this.context = context;
   }

   @Override
   public String[] getVariableNames() {
      HashSet<String> all = new HashSet<String>();
      Bindings global = context.getBindings(ScriptContext.GLOBAL_SCOPE);
      if (global != null)
	 all.addAll(global.keySet());
      all.addAll(Arrays.asList(super.getVariableNames()));
      return (String[]) all.toArray(new String[0]);
   }

   public boolean isLocalVariable(String name) {
      Object o = null;
      try {
	 o = super.getVariableImpl(name, false);
      } catch (UtilEvalError ute) {
	 System.err.println("Variable lookup failure for: "+name);
      }
      return o != null;
   }

   public void unsetVariableLocal(String name) {
      super.unsetVariable(name);
   }

   public void unsetVariable(String name) {
      if (isLocalVariable(name))
	 super.unsetVariable(name);
      else {
	 Bindings global = context.getBindings(ScriptContext.GLOBAL_SCOPE);
	 if (global != null && global.containsKey(name)) {
	    global.remove(name);
	    nameSpaceChanged();
	 }
      }
   }


   /**
    * Get the variable names that are local to ScriptContext.ENGINE_SCOPE
    * only (not in ScriptContext.GLOBAL_SCOPE).
    */
   public String[] getVariableNamesLocal() {
      return super.getVariableNames();
   }

   /**
    * Get the variable that is local to ScriptContext.ENGINE_SCOPE
    * only (not in ScriptContext.GLOBAL_SCOPE).
    */
   public Object getVariableLocal(String name) 
      throws UtilEvalError {
      Variable var = super.getVariableImpl(name, true);
      return unwrapVariable(var);
   }

   @Override
   protected Variable getVariableImpl(String name, boolean recurse)
      throws UtilEvalError {

      Variable var = super.getVariableImpl(name, recurse);

      /*
       * If the variable does not exist in the current NameSpace,
       * then check in the GLOBAL_SCOPE
       */
      if (var == null && recurse) {
	 Bindings global = context.getBindings(ScriptContext.GLOBAL_SCOPE);
	 if (global != null) {
	    Object value = global.get(name);

	    /*
	     * If we found a value in the global scope, then 
	     * wrap it as a final variable that cannot be 
	     * reassigned to another value.
	     */
	    if (value != null) 
	       var = new GlobalVariable(name, (Class) null, value, (Modifiers) null);
	 }
      }
      return var;
    }

   private class GlobalVariable extends Variable {
      GlobalVariable(String name, Class type, Object value, Modifiers mods) 
	 throws UtilEvalError {
	 super(name, type, value, mods);
      }
      public void setValue(Object value, int ctx) 
	 throws UtilEvalError {
	 super.setValue(value, ctx);
	 Bindings global = context.getBindings(ScriptContext.GLOBAL_SCOPE);
	 if (global != null) {
	    global.put(getName(), unwrapVariable(this));
	 }
      }
   }
}