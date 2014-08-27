package naru.aweb;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
//http://docs.sun.com/source/819-3160/agauto.html

public class JsTest {
	public static void main(String args[]) {
		Context cx = Context.enter();
		try {
			Scriptable scope = cx.initStandardObjects();

			// Add a global variable "out" that is a JavaScript reflection
			// of System.out
			Object jsOut = Context.javaToJS(System.out, scope);
			ScriptableObject.putProperty(scope, "out", jsOut);

			String s = "";
			for (int i = 0; i < args.length; i++) {
				s += args[i];
			}
			Object result = cx.evaluateString(scope, s, "<cmd>", 1, null);
			System.err.println(Context.toString(result));
		} finally {
			Context.exit();
		}
	}

}
