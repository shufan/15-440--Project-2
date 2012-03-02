package rmi;

import java.io.Serializable;
import java.lang.reflect.Method;

public class MethodCall implements Serializable
{
	String name;
	Class[] types;
	Object[] params;

	public MethodCall(Method method, Object[] params) {
		name = method.getName();
		this.params = params;
		types = method.getParameterTypes();
	}

}
