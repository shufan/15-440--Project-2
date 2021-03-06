package storage;

import java.io.*;
import java.net.*;

import common.*;
import rmi.*;
import naming.*;

/** Storage server.

    <p>
    Storage servers respond to client file access requests. The files accessible
    through a storage server are those accessible under a given directory of the
    local filesystem.
 */
public class StorageServer implements Storage, Command
{
	File root;
	Skeleton<Storage> clientSkeleton;
	Skeleton<Command> commandSkeleton;
	int clientPort;
	int commandPort;
	static int DEFAULT_CLIENT_PORT = 7225;
	static int DEFAULT_COMMAND_PORT = 9325;

    /** Creates a storage server, given a directory on the local filesystem, and
        ports to use for the client and command interfaces.

        <p>
        The ports may have to be specified if the storage server is running
        behind a firewall, and specific ports are open.

        @param root Directory on the local filesystem. The contents of this
                    directory will be accessible through the storage server.
        @param client_port Port to use for the client interface, or zero if the
                           system should decide the port.
        @param command_port Port to use for the command interface, or zero if
                            the system should decide the port.
        @throws NullPointerException If <code>root</code> is <code>null</code>.
    */
    public StorageServer(File root, int client_port, int command_port)
    {
    	if(root == null) {
    		throw new NullPointerException("Root is null");
    	}
    	this.root = root;
    	InetSocketAddress clientAddr;
    	InetSocketAddress commandAddr;
    	// initializes the client port only if it is a valid port
    	if(client_port > 0) {
    		clientAddr = new InetSocketAddress(client_port);
    		clientSkeleton = new Skeleton<Storage>(
    				Storage.class, this, clientAddr);
    	} else {
    		clientSkeleton = new Skeleton<Storage>(Storage.class, this);
    	}
    	// initializes the command port only if it is a valid port
    	if(command_port > 0) {
        	commandAddr = new InetSocketAddress(command_port);
        	commandSkeleton = new Skeleton<Command>(
        			Command.class, this, commandAddr);
    	} else {
    		commandSkeleton = new Skeleton<Command>(Command.class, this);
    	}
    }

    /** Creats a storage server, given a directory on the local filesystem.

        <p>
        This constructor is equivalent to
        <code>StorageServer(root, 0, 0)</code>. The system picks the ports on
        which the interfaces are made available.

        @param root Directory on the local filesystem. The contents of this
                    directory will be accessible through the storage server.
        @throws NullPointerException If <code>root</code> is <code>null</code>.
     */
    public StorageServer(File root)
    {
    	this(root,0,0);
    }

    /** Starts the storage server and registers it with the given naming
        server.

        @param hostname The externally-routable hostname of the local host on
                        which the storage server is running. This is used to
                        ensure that the stub which is provided to the naming
                        server by the <code>start</code> method carries the
                        externally visible hostname or address of this storage
                        server.
        @param naming_server Remote interface for the naming server with which
                             the storage server is to register.
        @throws UnknownHostException If a stub cannot be created for the storage
                                     server because a valid address has not been
                                     assigned.
        @throws FileNotFoundException If the directory with which the server was
                                      created does not exist or is in fact a
                                      file.
        @throws RMIException If the storage server cannot be started, or if it
                             cannot be registered.
     */
    public synchronized void start(String hostname, Registration naming_server)
        throws RMIException, UnknownHostException, FileNotFoundException
    {
    	if(!root.exists() || root.isFile()) {
    		throw new FileNotFoundException("Directory with which the server was"
                    + "created does not exist or is in fact a file");
    	}
        clientSkeleton.start();
        commandSkeleton.start();
    	Storage clientStub = (Storage) Stub.create(
    			Storage.class, clientSkeleton, hostname);
    	Command commandStub = (Command) Stub.create(
    			Command.class, commandSkeleton, hostname);
    	Path[] files = Path.list(root);
    	Path[] duplicateFiles = naming_server.register(
    			clientStub, commandStub, files);
    	// delete all duplicate files
    	for(Path p : duplicateFiles) {
    		p.toFile(root).delete();
        	// prune all empty directories
        	deleteEmpty(new File(p.toFile(root).getParent()));
    	}
    }

    /*
     * Deletes directories if they are empty
     */
    public synchronized void deleteEmpty(File parent) {
    	// cannot delete the root
    	while(!parent.equals(root)) {
    		// if the parent directory does not have children, deletes parent
    		if(parent.list().length == 0) {
    			parent.delete();
    		} else {
    			break;
    		}
    		parent = new File(parent.getParent());
    	}
    }

    /** Stops the storage server.

        <p>
        The server should not be restarted.
     */
    public void stop()
    {
    	clientSkeleton.stop();
    	commandSkeleton.stop();
    	stopped(null);
    }

    /** Called when the storage server has shut down.

        @param cause The cause for the shutdown, if any, or <code>null</code> if
                     the server was shut down by the user's request.
     */
    protected void stopped(Throwable cause)
    {
    }

    // The following methods are documented in Storage.java.
    @Override
    public synchronized long size(Path file) throws FileNotFoundException
    {
    	File f = file.toFile(root);
    	if(!f.exists() || f.isDirectory()) {
    		throw new FileNotFoundException("File cannot be found or refers to"
                     + "a directory");
    	}
    	return f.length();
    }

    @Override
    public synchronized byte[] read(Path file, long offset, int length)
        throws FileNotFoundException, IOException
    {
    	File f = file.toFile(root);
    	if(!f.exists() || f.isDirectory()) {
    		throw new FileNotFoundException("File cannot be found or refers to"
                     + "a directory");
    	}
    	if((offset < 0) || (length < 0) || (offset + length > f.length())) {
    		throw new IndexOutOfBoundsException("Sequence specified is outside"
                    + "of the bounds of the file, or length is negative");
    	}
    	// reads from the file using FileInputStream and returns the content
    	InputStream reader = new FileInputStream(f);
    	byte[] output = new byte[length];
    	reader.read(output, (int) offset, length);
    	return output;
    }

    @Override
    public synchronized void write(Path file, long offset, byte[] data)
        throws FileNotFoundException, IOException
    {
    	File f = file.toFile(root);
    	if(!f.exists() || f.isDirectory()) {
    		throw new FileNotFoundException("File cannot be found or refers to"
                     + "a directory");
    	}
    	if(offset < 0) {
    		throw new IndexOutOfBoundsException("The offset is negative");
    	}
    	InputStream reader = new FileInputStream(f);
    	FileOutputStream writer = new FileOutputStream(f);
    	//determinds how many bytes are to be read
    	long readLength = Math.min(offset, f.length());
    	byte[] offsetBytes = new byte[(int) readLength];
    	//reads from the data and writes to the file
    	reader.read(offsetBytes);
    	writer.write(offsetBytes, 0, (int) readLength);
		long fillLength = offset - f.length();
    	if(fillLength > 0) {
    		for(int i = 0; i < (int) fillLength; i ++) {
        		writer.write(0);
    		}
    	}
        writer.write(data);
    }

    // The following methods are documented in Command.java.
    @Override
    public synchronized boolean create(Path file)
    {
        if(file == null) {
            throw new NullPointerException("Given a null argument");
        }
        if(file.isRoot()) {
            return false;
        }
        //obtains the parent of the given file
        File parent = file.parent().toFile(root);
        //ensures the parent is a directory
        if(!parent.isDirectory()) {
        	delete(file.parent());
        }
        parent.mkdirs();
        //creates the file
        File f = file.toFile(root);
        try {
			return f.createNewFile();
		} catch (IOException e) {
			return false;
		}
    }

    @Override
    public synchronized boolean delete(Path path)
    {
    	//cannot delete the root
        if(path.isRoot()) {
            return false;
        }
        //deletes the file
        File f = path.toFile(root);
        if(f.isFile()) {
            return f.delete();
        } else {
            return deleteHelper(f);
        }
    }

    //a helper method for the delete method
    private boolean deleteHelper(File f) {
        if(f.isDirectory()) {
            File[] subfiles = f.listFiles();
            for(File subf : subfiles) {
                if(!deleteHelper(subf)) {
                    return false;
                }
            }
        }
        return f.delete();
    }

    @Override
    public synchronized boolean copy(Path file, Storage server)
        throws RMIException, FileNotFoundException, IOException
    {
    	//deletes the given file if it already exists on the server
        File f = file.toFile(root);
        if(f.exists()) {
        	delete(file);
        }
        //creates the file on this server and copies bytes over from the other
        create(file);
        long fileSize = server.size(file);
        long offset = 0;
        while(offset < fileSize) {
        	int bytesToCopy = (int)Math.min(
        			Integer.MAX_VALUE, fileSize - offset);
        	byte[] data = server.read(file, offset, bytesToCopy);
        	write(file,offset,data);
        	offset += bytesToCopy;
        }
        return true;
    }
}
