package bsh.engine;

import java.util.*;
import javax.script.Bindings;
import javax.script.ScriptContext;
import bsh.NameSpace;
import bsh.Primitive;
import bsh.Variable;

/**
 * This implementation of Bindings is backed by a NameSpace.
 * Retrieve variables from the current NameSpace or from the
 * global bindings.  Set variables only in the current NameSpace.
 * <p>
 * This implementation requires to know the current global
 * bindings.  This is difficult to keep updated, so just set
 * it initially and don't allow it to be changed.
 */ 
class BshScriptEngineBindings implements Bindings {

   private BshScriptEngineNameSpace ns;

   BshScriptEngineBindings(ScriptContext context) {
      ns = new BshScriptEngineNameSpace(context);
   }

   BshScriptEngineNameSpace getNameSpace() {
      return ns;
   }

   /** {@inheritedDoc} */
   public int size() {
      return ns.getVariableNamesLocal().length;
   }

   /** {@inheritedDoc} */
   public boolean isEmpty() {
      return size() == 0;
   }
   
   /** {@inheritedDoc} */
   public boolean containsKey(Object key) {
      return keySet().contains(key);
   }
   
   /** {@inheritedDoc} */
   public boolean containsValue(Object value) {
      String[] vars = ns.getVariableNamesLocal();
      for (String key : vars) {
	 Object v = get(key);
	 if (v == null) 
	    if (value == null)
	       return true;
	 else
	    if (v.equals(value))
	       return true;
      }
      return false;
   }
   
   /** {@inheritedDoc} */
   public Object get(Object key) {
      if (key == null)
	 throw new NullPointerException("Variable name cannot be null");
      if (!(key instanceof String))
	 throw new ClassCastException("Variable name must be a String: "+
				      key.getClass().getName());
      try {
	 Object v = ns.getVariableLocal((String)key);
	 return Primitive.unwrap(v);
      } catch (Throwable th) {
	 throw new IllegalStateException("Error accessing variable: "+key,
					 BshScriptEngine.wrapException(th));
      } 
   }
   
   /** {@inheritedDoc} */
   public Object put(String key, Object value) {
      if (key == null)
	 throw new NullPointerException("Variable name cannot be null");
      Object v = get(key);
      try {
	 ns.setVariable(key, value, false);
      } catch (Throwable th) {
	 throw new IllegalStateException("Error setting variable: "+key,
					 BshScriptEngine.wrapException(th));
      } 
      return v;
   }
   
   /** {@inheritedDoc} */
   public Object remove(Object key) {
      Object v = get(key);
      ns.unsetVariableLocal((String)key);
      return v;
   }
   
   /** {@inheritedDoc} */
   public void putAll(Map<? extends String, ? extends Object> m) {
      for (Map.Entry<? extends String, ? extends Object> me : m.entrySet()) 
	 put(me.getKey(), me.getValue());
   }

   /** {@inheritedDoc} */
   public void clear() {
      String[] vars = ns.getVariableNamesLocal();
      for (String key : vars) 
	 remove(key);
   }

   /** {@inheritedDoc} */
   public Set<String> keySet() {
      return new HashSet<String>(Arrays.asList(ns.getVariableNamesLocal()));
   }

   /** {@inheritedDoc} */
   public Collection<Object> values() {
      String[] vars = ns.getVariableNamesLocal();
      ArrayList vals = new ArrayList(vars.length);
      for (String key : vars) {
	 Object v = get(key);
	 vals.add(v);
      }
      return vals;
   }

   /** {@inheritedDoc} */
   public Set<Map.Entry<String, Object>> entrySet() {
      String[] names = ns.getVariableNamesLocal();      
      Set<Map.Entry<String, Object>> set = 
	 new HashSet<Map.Entry<String, Object>>(names.length);
      for (String key : names) {
	 Object v = get(key);
	 set.add(new AbstractMap.SimpleImmutableEntry<String,Object>(key,v));
      }
      return Collections.unmodifiableSet(set);
   }

   public boolean equals(Object o) {
      if (o instanceof BshScriptEngineBindings) {
	 BshScriptEngineBindings bb = (BshScriptEngineBindings) o;
	 return ns == bb.ns;
      }
      return false;
   }

   public int hashCode() {
      return ns.hashCode();
   }
}
