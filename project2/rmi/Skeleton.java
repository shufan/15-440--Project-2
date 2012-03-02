package rmi;

import java.net.*;
import java.util.Arrays;
import java.lang.reflect.*;
import java.io.*;

/** RMI skeleton

    <p>
    A skeleton encapsulates a multithreaded TCP server. The server's clients are
    intended to be RMI stubs created using the <code>Stub</code> class.

    <p>
    The skeleton class is parametrized by a type variable. This type variable
    should be instantiated with an interface. The skeleton will accept from the
    stub requests for calls to the methods of this interface. It will then
    forward those requests to an object. The object is specified when the
    skeleton is constructed, and must implement the remote interface. Each
    method in the interface should be marked as throwing
    <code>RMIException</code>, in addition to any other exceptions that the user
    desires.

    <p>
    Exceptions may occur at the top level in the listening and service threads.
    The skeleton's response to these exceptions can be customized by deriving
    a class from <code>Skeleton</code> and overriding <code>listen_error</code>
    or <code>service_error</code>.
*/
public class Skeleton<T>
{
    Class<T> sclass;
    T server;
    public InetSocketAddress sockaddr;
    public ListenerThread slistener;
    /** Creates a <code>Skeleton</code> with no initial server address. The
        address will be determined by the system when <code>start</code> is
        called. Equivalent to using <code>Skeleton(null)</code>.

        <p>
        This constructor is for skeletons that will not be used for
        bootstrapping RMI - those that therefore do not require a well-known
        port.

        @param c An object representing the class of the interface for which the
                 skeleton server is to handle method call requests.
        @param server An object implementing said interface. Requests for method
                      calls are forwarded by the skeleton to this object.
        @throws Error If <code>c</code> does not represent a remote interface -
                      an interface whose methods are all marked as throwing
                      <code>RMIException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>server</code> is <code>null</code>.
     */    
    public Skeleton(Class<T> c, T server)
    {
        // error-checking; nullpointer and remote interface
        if(c == null || server == null) {
            throw new NullPointerException();
        }
        Method[] mthds = c.getDeclaredMethods();
        for(Method mthd : mthds) {
            Class[] exceptions = mthd.getExceptionTypes();
            if(!(Arrays.asList(exceptions).contains(RMIException.class)) || !(c.isInterface())) {
                throw new Error("C does not represent a remote interface");
            }
        }
        // creates skeleton
        sclass = c;
        this.server = server;
        sockaddr = new InetSocketAddress(50000);
    }

    /** Creates a <code>Skeleton</code> with the given initial server address.

        <p>
        This constructor should be used when the port number is significant.

        @param c An object representing the class of the interface for which the
                 skeleton server is to handle method call requests.
        @param server An object implementing said interface. Requests for method
                      calls are forwarded by the skeleton to this object.
        @param address The address at which the skeleton is to run. If
                       <code>null</code>, the address will be chosen by the
                       system when <code>start</code> is called.
        @throws Error If <code>c</code> does not represent a remote interface -
                      an interface whose methods are all marked as throwing
                      <code>RMIException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>server</code> is <code>null</code>.
     */
    public Skeleton(Class<T> c, T server, InetSocketAddress address)
    {
        // error-checking; nullpointer and remote interface
        if(c == null || server == null) {
            throw new NullPointerException();
        }
        Method[] mthds = c.getDeclaredMethods();
        for(Method mthd : mthds) {
            Class[] exceptions = mthd.getExceptionTypes();
            if(!(Arrays.asList(exceptions).contains(RMIException.class)) || !(c.isInterface())) {
                throw new Error("C does not represent a remote interface");
            }
        }
        // creates skeleton
        sclass = c;
        this.server = server;
        sockaddr = address;
    }
    
    public Object invoke(Object input) throws RMIException {
        try {
            MethodCall methodCalled = (MethodCall) input;
            String name = methodCalled.name;
            Method mthd = server.getClass().getMethod(name, methodCalled.types);
            return mthd.invoke(server, methodCalled.params);
        } catch(Exception e) {
            if(e instanceof InvocationTargetException) {
            	return e;
            } else {
            	throw new RMIException("Could not invoke method call.");
            }
        }
    }
    /** Called when the listening thread exits.

        <p>
        The listening thread may exit due to a top-level exception, or due to a
        call to <code>stop</code>.

        <p>
        When this method is called, the calling thread owns the lock on the
        <code>Skeleton</code> object. Care must be taken to avoid deadlocks when
        calling <code>start</code> or <code>stop</code> from different threads
        during this call.

        <p>
        The default implementation does nothing.

        @param cause The exception that stopped the skeleton, or
                     <code>null</code> if the skeleton stopped normally.
     */
    protected void stopped(Throwable cause)
    {
    	if(cause != null) {
    		cause.printStackTrace();
    	}
    }

    /** Called when an exception occurs at the top level in the listening
        thread.

        <p>
        The intent of this method is to allow the user to report exceptions in
        the listening thread to another thread, by a mechanism of the user's
        choosing. The user may also ignore the exceptions. The default
        implementation simply stops the server. The user should not use this
        method to stop the skeleton. The exception will again be provided as the
        argument to <code>stopped</code>, which will be called later.

        @param exception The exception that occurred.
        @return <code>true</code> if the server is to resume accepting
                connections, <code>false</code> if the server is to shut down.
     */
    protected boolean listen_error(Exception exception)
    {
        return false;    
    }

    /** Called when an exception occurs at the top level in a service thread.

        <p>
        The default implementation does nothing.

        @param exception The exception that occurred.
     */
    protected void service_error(RMIException exception)
    {
    	if(!exception.getClass().equals(EOFException.class)) {
        	exception.printStackTrace();
    	}
    }

    /** Starts the skeleton server.

        <p>
        A thread is created to listen for connection requests, and the method
        returns immediately. Additional threads are created when connections are
        accepted. The network address used for the server is determined by which
        constructor was used to create the <code>Skeleton</code> object.

        @throws RMIException When the listening socket cannot be created or
                             bound, when the listening thread cannot be created,
                             or when the server has already been started and has
                             not since stopped.
     */
    public synchronized void start() throws RMIException
    {
    	// check for conditions to throw RMIException
        if((slistener != null) && slistener.isAlive()) {
            throw new RMIException("Server has already been started and has not since stopped.");
        } else {
            try {
                slistener = new ListenerThread(new ServerSocket(sockaddr.getPort()));
                slistener.start();
            } catch(Exception e) {
                throw new RMIException("Listening socket could not be created or bound, or listening thread could not be created.");
            }
        }
    }

    /** Stops the skeleton server, if it is already running.

        <p>
        The listening thread terminates. Threads created to service connections
        may continue running until their invocations of the <code>service</code>
        method return. The server stops at some later time; the method
        <code>stopped</code> is called at that point. The server may then be
        restarted.
     */
    public synchronized void stop()
    {
    	// set run state to false and close socket connection
    	if(slistener != null && slistener.isAlive()) {
    		slistener.run = false;
            try {
            	if(slistener.listenerSocket != null && !slistener.listenerSocket.isClosed()) {
                	slistener.listenerSocket.close();
            	}
            } catch(Exception e) {
            	listen_error(e);
            }
            // waits for thread to terminate with or without exception
    		try {
                slistener.join();
                stopped(null);
            } catch(Exception e) {
                stopped(e);
            }
    	}
    }
    
    public class ListenerThread extends Thread {
        
        boolean run;
        public ServerSocket listenerSocket;
        Socket clientSocket;
        
        public ListenerThread(ServerSocket listenerSocket) {
        	this.listenerSocket = listenerSocket;
        	run = true;
        }
                
        public void run() {
        	while(run) {
        		try {
        			//begin listening for requests
        			clientSocket = listenerSocket.accept();
        			ResponseThread rt = new ResponseThread(clientSocket);
                    rt.start();
        		} catch(IOException e) {
        			if(run) {
            			listen_error(e);
        			}
        		}
            }
        }
    }
    
    public class ResponseThread extends Thread {
    	boolean run;
    	Socket clientSocket;
    	ObjectInputStream in;
    	ObjectOutputStream out;
    	
    	public ResponseThread(Socket clientSocket) {
    		this.clientSocket = clientSocket;
    	}
    	
    	public void run() {
    		try {
    			if(!clientSocket.isClosed()) {
    				//initialized object streams for passing methodcall and returning results
    				out = new ObjectOutputStream(clientSocket.getOutputStream());
        			out.flush();
        			in = new ObjectInputStream(clientSocket.getInputStream());
        			
        			Object methodcall = in.readObject();
        			out.writeObject(invoke(methodcall));
    			}
    		} catch(Exception e) {
    			if(e.getClass().equals(EOFException.class) || e.getClass().equals(SocketException.class)) {
    				//ignore
    			} else {
    				e.printStackTrace();
        			service_error(new RMIException("Exception thrown in service response."));
    			}
    		}
    		
    		try {
    			if(out != null) {
        			out.close();
    			}
    			if(in != null) {
        			in.close();
    			}
    			if(!clientSocket.isClosed()) {
        			clientSocket.close();
    			}
    		} catch(Exception e) {
   				//ignore  		
    		}
    	}
    }
}