package rmi;
import java.io.*;
import java.lang.reflect.*;
import java.lang.reflect.Proxy;
import java.net.*;
import java.util.Arrays;

/** RMI stub factory.

    <p>
    RMI stubs hide network communication with the remote server and provide a
    simple object-like interface to their users. This class provides methods for
    creating stub objects dynamically, when given pre-defined interfaces.

    <p>
    The network address of the remote server is set when a stub is created, and
    may not be modified afterwards. Two stubs are equal if they implement the
    same interface and carry the same remote server address - and would
    therefore connect to the same skeleton. Stubs are serializable.
 */
public abstract class Stub
{
    /** Creates a stub, given a skeleton with an assigned adress.

        <p>
        The stub is assigned the address of the skeleton. The skeleton must
        either have been created with a fixed address, or else it must have
        already been started.

        <p>
        This method should be used when the stub is created together with the
        skeleton. The stub may then be transmitted over the network to enable
        communication with the skeleton.

        @param c A <code>Class</code> object representing the interface
                 implemented by the remote object.
        @param skeleton The skeleton whose network address is to be used.
        @return The stub created.
        @throws IllegalStateException If the skeleton has not been assigned an
                                      address by the user and has not yet been
                                      started.
        @throws UnknownHostException When the skeleton address is a wildcard and
                                     a port is assigned, but no address can be
                                     found for the local host.
        @throws NullPointerException If any argument is <code>null</code>.
        @throws Error If <code>c</code> does not represent a remote interface
                      - an interface in which each method is marked as throwing
                      <code>RMIException</code>, or if an object implementing
                      this interface cannot be dynamically created.
     */
    public static <T> T create(Class<T> c, Skeleton<T> skeleton) throws UnknownHostException
    {
    	//check for errors
    	if(c == null || skeleton == null) {
    		throw new NullPointerException("One or more arguments are null");
    	}
    	
    	if((skeleton.sockaddr.getAddress().isAnyLocalAddress()) && ((Integer)skeleton.sockaddr.getPort() != null) && (skeleton.sockaddr.getHostName() == null)) {
    		throw new UnknownHostException("The skeleton address is a wildcard and a port is assigned, but no address can be found for the local host");
    	}
    	
    	Method[] mthds = c.getDeclaredMethods();
        for(Method mthd : mthds) {
            Class[] exceptions = mthd.getExceptionTypes();
            if(!(Arrays.asList(exceptions).contains(RMIException.class)) || !(c.isInterface())) {
                throw new Error("C does not represent a remote interface");
            }
        }
        
        if(!((skeleton.slistener != null) && (skeleton.slistener.isAlive()))) {
        	throw new IllegalStateException("Skeleton has not been assigned an address and has not yet been started");
        }
        // create stub
    	T result = (T) (Proxy.newProxyInstance(c.getClassLoader(),new Class[] { c }, new ProxyClass<T> (skeleton, c)));
    	return result;
    }

    /** Creates a stub, given a skeleton with an assigned address and a hostname
        which overrides the skeleton's hostname.

        <p>
        The stub is assigned the port of the skeleton and the given hostname.
        The skeleton must either have been started with a fixed port, or else
        it must have been started to receive a system-assigned port, for this
        method to succeed.

        <p>
        This method should be used when the stub is created together with the
        skeleton, but firewalls or private networks prevent the system from
        automatically assigning a valid externally-routable address to the
        skeleton. In this case, the creator of the stub has the option of
        obtaining an externally-routable address by other means, and specifying
        this hostname to this method.
     * @param <T>

        @param c A <code>Class</code> object representing the interface
                 implemented by the remote object.
        @param skeleton The skeleton whose port is to be used.
        @param hostname The hostname with which the stub will be created.
        @return The stub created.
        @throws IllegalStateException If the skeleton has not been assigned a
                                      port.
        @throws NullPointerException If any argument is <code>null</code>.
        @throws Error If <code>c</code> does not represent a remote interface
                      - an interface in which each method is marked as throwing
                      <code>RMIException</code>, or if an object implementing
                      this interface cannot be dynamically created.
     */
    public static <T> T create(Class<T> c, Skeleton<T> skeleton,
                               String hostname)
    {
    	// check for errors
    	if(c == null || skeleton == null || hostname == null) {
    		throw new NullPointerException("One or more arguments are null");
    	}
    	
    	if((Integer)skeleton.sockaddr.getPort() == null) {
    		throw new IllegalStateException("Skeleton has not been assigned a port");
    	}
    	
    	Method[] mthds = c.getDeclaredMethods();
        for(Method mthd : mthds) {
            Class[] exceptions = mthd.getExceptionTypes();
            if(!(Arrays.asList(exceptions).contains(RMIException.class)) || !(c.isInterface())) {
                throw new Error("C does not represent a remote interface");
            }
        }
        // create stub
    	T result = (T) (Proxy.newProxyInstance(c.getClassLoader(),new Class[] { c }, new ProxyClass<T> (new InetSocketAddress(hostname, skeleton.sockaddr.getPort()), c)));
    	return result;
    }

    /** Creates a stub, given the address of a remote server.

        <p>
        This method should be used primarily when bootstrapping RMI. In this
        case, the server is already running on a remote host but there is
        not necessarily a direct way to obtain an associated stub.

        @param c A <code>Class</code> object representing the interface
                 implemented by the remote object.
        @param address The network address of the remote skeleton.
        @return The stub created.
        @throws NullPointerException If any argument is <code>null</code>.
        @throws Error If <code>c</code> does not represent a remote interface
                      - an interface in which each method is marked as throwing
                      <code>RMIException</code>, or if an object implementing
                      this interface cannot be dynamically created.
     */
    public static <T> T create(Class<T> c, InetSocketAddress address)
    {
    	// check for errors
    	if(c == null || address == null) {
    		throw new NullPointerException("One or more arguments are null");
    	}
    	
    	Method[] mthds = c.getDeclaredMethods();
        for(Method mthd : mthds) {
            Class[] exceptions = mthd.getExceptionTypes();
            if(!(Arrays.asList(exceptions).contains(RMIException.class)) || !(c.isInterface())) {
                throw new Error("C does not represent a remote interface");
            }
        }
        // create stub
    	T result = (T) (Proxy.newProxyInstance(c.getClassLoader(),new Class[] { c }, new ProxyClass<T> (address,c)));
    	return result;
    }  
}

class ProxyClass<T> implements InvocationHandler {
	InetAddress address;
	int port;
	Class<T> c;
	
	public ProxyClass(Skeleton<T> skeleton, Class<T> c) {
		this.address = skeleton.sockaddr.getAddress();
		port = skeleton.sockaddr.getPort();
		this.c = c;
	}
	
	public ProxyClass(InetSocketAddress sockaddr, Class<T> c) {
		this.address = sockaddr.getAddress();
		port = sockaddr.getPort();
		this.c = c;
	}
	
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		// stub equals
		if(method.toString().equals("public boolean java.lang.Object.equals(java.lang.Object)")) {
			Object obj = args[0];
			if(obj == null)
				return false;
			if(obj instanceof Proxy) {
				ProxyClass<T> stub1 = (ProxyClass<T>) Proxy.getInvocationHandler(proxy);
				ProxyClass<T> stub2 = (ProxyClass<T>) Proxy.getInvocationHandler(obj);
				if((stub1.c.equals(stub2.c)) && (stub1.port == stub2.port) && (stub1.address.equals(stub2.address))) {
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		}
		
		// stub hashcode
		if(method.toString().equals("public native int java.lang.Object.hashCode()")) {
			ProxyClass<T> stub = (ProxyClass<T>) Proxy.getInvocationHandler(proxy);
			return stub.address.hashCode()+port+33*c.hashCode();
		}
		
		// stub tostring
		if(method.toString().equals("public java.lang.String java.lang.Object.toString()")) {
			ProxyClass<T> stub = (ProxyClass<T>) Proxy.getInvocationHandler(proxy);
			return stub.c.toString() + stub.address.toString() + ":" + stub.port;
		}
		
		Socket clientSocket = null;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		try {
			// open stream with skeleton for method call invocation and response
			clientSocket = new Socket(address, port);
			out = new ObjectOutputStream(clientSocket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(clientSocket.getInputStream());
			MethodCall mthd = new MethodCall(method, args);
			out.writeObject(mthd);
		} catch(Exception e) {
			throw new RMIException("Could not connect to skeleton.");
		}

		// remote method call could throw an exception
		Object ret = in.readObject();
		if(ret instanceof InvocationTargetException) {
			throw ((InvocationTargetException) ret).getTargetException();
		}

		try {
			out.close();
			in.close();
			clientSocket.close();
		} catch(Exception e) {
			throw new RMIException("Could not close connection to skeleton.");
		}
		return ret;
	}
	
}
