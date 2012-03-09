package naming;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
	ConcurrentHashMap<Path, Set<Storage>> pathStorageMap;
	ConcurrentHashMap<Storage, Command> storageCmdMap;
	Skeleton<Registration> regisSkel;
	Skeleton<Service> servSkel;

    /** Creates the naming server object.

        <p>
        The naming server is not started.
     */
    public NamingServer()
    {
    	PathNode rootnode = new PathNode();
    	rootnode.setCurrPath(new Path("/"));
        root = rootnode;

        pathStorageMap = new ConcurrentHashMap<Path, Set<Storage>>();
        storageCmdMap = new ConcurrentHashMap<Storage, Command>();
    	regisSkel= new Skeleton<Registration>(Registration.class, this, 
    			new InetSocketAddress(NamingStubs.REGISTRATION_PORT));
    	servSkel = new Skeleton<Service>(Service.class, this, 
    			new InetSocketAddress(NamingStubs.SERVICE_PORT));
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
    	checkForNull(path, exclusive);
		if (!isValidPath(path))
			throw new FileNotFoundException("Path does not point to a valid " +
					"file/directory");
        root.lock(path, exclusive);
    }

    @Override
    public void unlock(Path path, boolean exclusive)
    {
    	checkForNull(path, exclusive);
		if (!isValidPath(path))
			throw new IllegalArgumentException("Path does not point to a " +
					"valid file/directory");
        Path toCopy = root.unlock(path, exclusive);
        //replication done in unlock (when access is given to a reader or 
        //writer
        Set<Storage> hasFile = pathStorageMap.get(path);
        //writer request, so select one storage to keep copy of the file, 
        //delete file elsewhere
        if(exclusive) {
        	if(hasFile != null) {
        		//choose one storage with the file to keep the file on
        		Iterator<Storage> iter = hasFile.iterator();
        		Storage keptCopy = iter.next();
        		//delete on all other storages with the file
        		while(iter.hasNext()) {
        			Command command_stub = storageCmdMap.get(iter.next());
        			try {
						command_stub.delete(path);
					} catch (RMIException e) {
					}
        		}
        		Set<Storage> updatedHasFiles = Collections.newSetFromMap(
        				new ConcurrentHashMap<Storage, Boolean>());
        		updatedHasFiles.add(keptCopy);
        		pathStorageMap.put(path, updatedHasFiles);
        	}
        } else {
        //read request, so if time to make a copy, file to copy is returned 
        //by read
        	if(toCopy != null) {
        		Set<Storage> allServers = storageCmdMap.keySet();
        		Iterator<Storage> iter = allServers.iterator();
        		Storage copyFrom = hasFile.iterator().next();
        		while(iter.hasNext()) {
        			Storage s = iter.next();
        			if(!hasFile.contains(s)) {
        				Command command_stub = storageCmdMap.get(s);
        				try {
							command_stub.copy(toCopy, copyFrom);
						} catch (Exception e) {
						}
        				pathStorageMap.get(toCopy).add(s);
        				break;
        			}
        		}

        	}
        }
    }

    // returns whether a given path is valid
	private boolean isValidPath(Path p) {
		try {
			root.getLastCompNode(p);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}

    @Override
    public boolean isDirectory(Path path) throws FileNotFoundException
    {
    	checkForNull(path);
    	if(path.isRoot())
    		return true;
        return root.getLastCompNode(path).isDirectory();
    }

    @Override
    public String[] list(Path directory) throws FileNotFoundException
    {
        ArrayList<String> contents = new ArrayList<String>();
        //pN is the node of the last component of the path
        PathNode pN = root.getLastCompNode(directory);

        if (pN == null || !pN.isDirectory())
        	throw new FileNotFoundException("Given path does not refer to a " +
        			"directory");

        //adds all the children of pN as contents of the directory
        for (String entry : pN.getChildrenMap().keySet())
        	contents.add(entry);
        String[] ret = new String[contents.size()];
        int index = 0;
        for(String s : contents) {
        	ret[index] = s;
        	index++;
        }
        return ret;
    }

    @Override
    public boolean createFile(Path file)
        throws RMIException, FileNotFoundException
    {
    	checkForNull(file);
    	if(file.isRoot()) {
    		return false;
    	}
    	Path parentPath = file.parent();
    	//get the node of the parent directory
    	PathNode parentNode = root.getLastCompNode(parentPath);
    	if (parentNode == null || !parentNode.isDirectory()) {
    		throw new FileNotFoundException();
    	}
    	//add the file as a child to the parent
    	if(parentNode.getChildrenMap().get(file.last()) != null) {
    		return false;
    	}
    	PathNode pN = new PathNode();
    	pN.setCurrPath(file);
    	pN.setIsDir(false);
    	parentNode.getChildrenMap().put(file.last(), pN);
    	//adds the file to random storage server
    	if(storageCmdMap.size() >= 1) {
    		Command cmd = storageCmdMap.get(
    				storageCmdMap.keySet().iterator().next());
    		try {
    			cmd.create(file);
    		} catch (RMIException e) {
    			return false;
    		}
    	    return true;
    	} else {
    		throw new IllegalStateException();
    	}
    }

    @Override
    public boolean createDirectory(Path directory) throws FileNotFoundException
    {
    	checkForNull(directory);
    	if(directory.isRoot()) {
    		return false;
    	}
    	//get the node of the parent directory
    	PathNode parentNode = root.getLastCompNode(directory.parent());
    	if(!parentNode.isDirectory()) {
    		throw new FileNotFoundException();
    	}
    	//add the directory as a child to the parent
    	if(parentNode.getChildrenMap().get(directory.last()) != null) {
    		return false;
    	}
    	PathNode pN = new PathNode();
    	pN.setCurrPath(directory);
    	parentNode.getChildrenMap().put(directory.last(), pN);
    	return true;
    }

    @Override
    public boolean delete(Path path) throws FileNotFoundException
    {
    	boolean result = true;
    	if (path.isRoot())
    		return false;
    	//get node of parent directory
    	PathNode parentNode = root.getLastCompNode(path.parent());
    	if (parentNode == null)
    		throw new FileNotFoundException("Parent directory does not exist");
    	//if parent directory contains the last component of path
    	if (parentNode.getChildrenMap().containsKey(path.last())) {
    		if(isDirectory(path)) {
    			//deletes from tree
    			Set<Storage> storagesToDeleteFrom = Collections.newSetFromMap(
    					new ConcurrentHashMap<Storage, Boolean>());
    			//get all file paths in the directory
    			ArrayList<Path> files = new ArrayList<Path>();
    			Iterator<String> pathIter = path.iterator();
    			root.getFilesWithin(pathIter, files);
    			//deletes file from all storages that contain it
        		for(Path f : files) {
        			storagesToDeleteFrom.addAll(pathStorageMap.get(f));
        		}
        		for(Storage s : storagesToDeleteFrom) {
        			Command command_stub = storageCmdMap.get(s);
        			try {
						if(!command_stub.delete(path)) {
							result = false;
						}
					} catch (RMIException e) {
						return false;
					}
            		pathStorageMap.remove(path);
        		}
        		parentNode.getChildrenMap().remove(path.last());
        		return result;
        	} else {
        		//deletes from tree
        		parentNode.getChildrenMap().remove(path.last());
        		//deletes from storage servers
        		Set<Storage> hasFile = pathStorageMap.get(path);
        		if(hasFile.isEmpty()) {
        			return true;
        		}
        		for(Storage s : hasFile) {
            		Command cmd = storageCmdMap.get(s);
            		try {
        				result = cmd.delete(path);
        			} catch (RMIException e) {
        				return false;
        			}
            		pathStorageMap.remove(path);
        		}
        	}
    	}
    	else
    		throw new FileNotFoundException("Given file/directory does not " +
    				"exist");
		return result;
    }

    @Override
    public Storage getStorage(Path file) throws FileNotFoundException
    {
    	checkForNull(file);

        if (!pathStorageMap.containsKey(file) || 
        		pathStorageMap.get(file).isEmpty()) {
        	throw new FileNotFoundException("File does not exist");
        }
        //retrieves storage stub from the pathStorageMap
        Set<Storage> hasFile = pathStorageMap.get(file);

        if(!hasFile.iterator().hasNext()) {
        	throw new FileNotFoundException();
        } else {
        	return hasFile.iterator().next();
        }
    }

    public Path[] register(Storage client_stub, Command command_stub,
                           Path[] files)
    {
        checkForNull(client_stub, command_stub, files);
          if (storageCmdMap.containsKey(client_stub))
        	throw new IllegalStateException("Storage server is " +
        			"already registered");
        ArrayList<Path> dupFiles = new ArrayList<Path>();
        //only adds paths to the tree if there are no duplicates
        for (Path p : files) {
        	if(!p.isRoot()) {
        		if (!root.addFile(p.iterator())) {
            		dupFiles.add(p);
            	} else {
            		//not a duplicate, adds the storage server to the map
            		if(pathStorageMap.containsKey(p)) {
            			pathStorageMap.get(p).add(client_stub);
            		} else {
            			Set<Storage> hasFile = Collections.newSetFromMap(
            					new ConcurrentHashMap<Storage, Boolean>());
            			hasFile.add(client_stub);
                        pathStorageMap.put(p,hasFile);
            		}
            	}
        	}
        }
        storageCmdMap.put(client_stub,command_stub);
        //builds array of duplicate files so it can be returned
        Path[] ret = new Path[dupFiles.size()];
        int index = 0;
        for(Path p : dupFiles) {
        	ret[index] = p;
        	index ++;
        }

    	return ret;
    }

    //checks parameters for null values, throws NullPointerException if nulls
    private void checkForNull(Object... objs)
    {
    	for (Object obj : objs) {
    		if (obj == null)
    			throw new NullPointerException("cannot have a null parameter");
    	}
    }

}
