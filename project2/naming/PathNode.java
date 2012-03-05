package naming;

import java.io.*;
import java.net.*;
import java.util.*;

import rmi.*;
import common.*;
import storage.*;

/** Naming server.

    <p>
    Each instance of the filesystem is centered on a single naming server. The
    naming server maintains the filesystem directory tree. It does not store any
    file data - this is done by separate storage servers. The primary purpose of
    the naming server is to map each file name (path) to the storage server
    which hosts the file's contents.

    <p>
    The naming server provides two interfaces, <code>Service</code> and
    <code>Registration</code>, which are accessible through RMI. Storage servers
    use the <code>Registration</code> interface to inform the naming server of
    their existence. Clients use the <code>Service</code> interface to perform
    most filesystem operations. The documentation accompanying these interfaces
    provides details on the methods supported.

    <p>
    Stubs for accessing the naming server must typically be created by directly
    specifying the remote network address. To make this possible, the client and
    registration interfaces are available at well-known ports defined in
    <code>NamingStubs</code>.
 */
public class NamingServer implements Service, Registration
{
	PathNode root;
	HashMap<Path, Storage> storageStubsMap;
	HashMap<Path, Command> cmdStubsMap;
	Skeleton<Registration> regisSkel;
	Skeleton<Service> servSkel;
	
    /** Creates the naming server object.

        <p>
        The naming server is not started.
     */
    public NamingServer()
    {
        root = new PathNode();
        storageStubsMap = new HashMap<Path, Storage>();
        cmdStubsMap = new HashMap<Path, Command>();
    	regisSkel= new Skeleton<Registration>(Registration.class, this, new InetSocketAddress(NamingStubs.REGISTRATION_PORT));
    	servSkel = new Skeleton<Service>(Service.class, this, new InetSocketAddress(NamingStubs.SERVICE_PORT));
    }

    /** Starts the naming server.

        <p>
        After this method is called, it is possible to access the client and
        registration interfaces of the naming server remotely.

        @throws RMIException If either of the two skeletons, for the client or
                             registration server interfaces, could not be
                             started. The user should not attempt to start the
                             server again if an exception occurs.
     */
    public synchronized void start() throws RMIException
    {
        regisSkel.start();
        servSkel.start();
    }

    /** Stops the naming server.

        <p>
        This method commands both the client and registration interface
        skeletons to stop. It attempts to interrupt as many of the threads that
        are executing naming server code as possible. After this method is
        called, the naming server is no longer accessible remotely. The naming
        server should not be restarted.
     */
    public void stop()
    {
        regisSkel.stop();
        servSkel.stop();
        stopped(null);
    }

    /** Indicates that the server has completely shut down.

        <p>
        This method should be overridden for error reporting and application
        exit purposes. The default implementation does nothing.

        @param cause The cause for the shutdown, or <code>null</code> if the
                     shutdown was by explicit user request.
     */
    protected void stopped(Throwable cause)
    {
    }

    // The following public methods are documented in Service.java.
    @Override
    public void lock(Path path, boolean exclusive) throws FileNotFoundException
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void unlock(Path path, boolean exclusive)
    {
        throw new UnsupportedOperationException("not implemented");
    }

//    //Given a Path, returns the PathNode of the last component
//    private PathNode findLastComponent(Path path) {
//        Iterator<String> pathItr = path.iterator();
//        PathNode currentNode = root;
//        while (pathItr.hasNext()) {
//        	String component = pathItr.next();
//        	if (currentNode.getChildrenMap().get(component) != null)
//        		currentNode = currentNode.getChildrenMap().get(component);
//        	else
//        		return currentNode;
//        }
//        return currentNode;
//    }
    
    @Override
    public boolean isDirectory(Path path) throws FileNotFoundException
    {
        if (!storageStubsMap.containsKey(path))
        	throw new FileNotFoundException("File was not found");

        PathNode pN = root.getLastCompNode(path);
        
        return pN.isDirectory();
    }

    @Override
    public String[] list(Path directory) throws FileNotFoundException
    {
        ArrayList<String> contents = new ArrayList<String>();
        //pN is the node of the last component of the path
        PathNode pN = root.getLastCompNode(directory);
        
        if (pN == null || !pN.isDirectory())
        	throw new FileNotFoundException("Given path does not refer to a directory");
        
        //adds all the children of pN as contents of the directory
        for (String entry : pN.getChildrenMap().keySet())
        	contents.add(entry);
        
        return (String[]) contents.toArray();
    }

    @Override
    //what do I need to do with RMI stuff?????
    public boolean createFile(Path file)
        throws RMIException, FileNotFoundException
    {
    	Path parentPath = file.parent();
    	if (!storageStubsMap.containsKey(parentPath)) //only need to check one right?????
    		throw new FileNotFoundException("Parent directory does not exist");
    	
    	//get the node of the parent directory
    	PathNode parentNode = root.getLastCompNode(parentPath);
    	if (parentNode == null)
    		return false;
    	//add the file as a child to the parent
    	PathNode pN = new PathNode();
    	pN.setIsDir(false);
    	parentNode.getChildrenMap().put(file.last(), pN);
    	
	    return true;
    }

    @Override
    public boolean createDirectory(Path directory) throws FileNotFoundException
    {
    	Path parentPath = directory.parent();
    	if (!storageStubsMap.containsKey(parentPath)) //only need to check one right?????
    		throw new FileNotFoundException("Parent directory does not exist");
    	
    	//get the node of the parent directory
    	PathNode parentNode = root.getLastCompNode(parentPath);
    	if (parentNode == null)
    		return false;
    	//add the directory as a child to the parent
    	parentNode.getChildrenMap().put(directory.last(), new PathNode());
    	
	    return true;
    }

    @Override
    public boolean delete(Path path) throws FileNotFoundException
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Storage getStorage(Path file) throws FileNotFoundException
    {
        if (!storageStubsMap.containsKey(file))
        	throw new FileNotFoundException("File does not exist");
        
        return storageStubsMap.get(file);
    }

    //still gotta do stuff with RMI
    public Path[] register(Storage client_stub, Command command_stub,
                           Path[] files)
    {
        checkForNull(client_stub, command_stub, files);
        
        if (storageStubsMap.containsValue(client_stub)) //only need to check one of the maps right?
        	throw new IllegalStateException("Storage server is already registered");
        
        ArrayList<Path> dupFiles = new ArrayList<Path>();
        //only adds paths to the tree if there are no duplicates
        for (Path p : files) {
        	if (storageStubsMap.containsKey(p))
        		dupFiles.add(p);
        	else
        		root.addFile(p.iterator());
        }
           
    	return (Path[]) dupFiles.toArray();
    }
    
    private void checkForNull(Object... objs)
    {
    	for (Object obj : objs) {
    		if (obj == null)
    			throw new NullPointerException("cannot have a null parameter");
    	}
    }
    
    
}
