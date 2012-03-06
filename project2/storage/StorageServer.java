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
	Skeleton clientSkeleton;
	Skeleton commandSkeleton;
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
    		throw new NullPointerException();
    	}
    	this.root = root;
    	InetSocketAddress clientAddr;
    	InetSocketAddress commandAddr;
    	if(client_port == 0) {
        	clientAddr = new InetSocketAddress(DEFAULT_CLIENT_PORT);
    	} else {
    		clientAddr = new InetSocketAddress(client_port);
    	}
		clientSkeleton = new Skeleton(Storage.class, this, clientAddr);
    	if(command_port == 0) {
        	commandAddr = new InetSocketAddress(DEFAULT_COMMAND_PORT);
    	} else {
        	commandAddr = new InetSocketAddress(command_port);
    	}
		commandSkeleton = new Skeleton(Command.class, this, commandAddr);
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
    	if(root == null) {
    		throw new NullPointerException();
    	}
    	this.root = root;
    	clientSkeleton = new Skeleton(Storage.class, this, new InetSocketAddress(DEFAULT_CLIENT_PORT));
    	commandSkeleton = new Skeleton(Command.class, this, new InetSocketAddress(DEFAULT_COMMAND_PORT));
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
    		throw new FileNotFoundException();
    	}
        clientSkeleton.start();
        commandSkeleton.start();
    	Storage clientStub = (Storage) Stub.create(Storage.class, clientSkeleton, hostname);
    	Command commandStub = (Command) Stub.create(Command.class, commandSkeleton, hostname);
    	Path[] files = Path.list(root);
    	Path[] duplicateFiles = naming_server.register(clientStub, commandStub, files);
    	// delete all duplicate files
    	for(Path p : duplicateFiles) {
    		p.toFile(root).delete();
        	// prune all empty directories
        	deleteEmpty(new File(p.toFile(root).getParent()));
    	}
    }

    public synchronized void deleteEmpty(File parent) {
    	while(!parent.equals(root)) {
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
    		throw new FileNotFoundException();
    	}
    	return f.length();
    }

    @Override
    public synchronized byte[] read(Path file, long offset, int length)
        throws FileNotFoundException, IOException
    {
    	File f = file.toFile(root);
    	if(!f.exists() || f.isDirectory()) {
    		throw new FileNotFoundException();
    	}
    	if((offset < 0) || (length < 0) || (offset + length > f.length())) {
    		throw new IndexOutOfBoundsException();
    	}
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
    		throw new FileNotFoundException();
    	}
    	if(offset < 0) {
    		throw new IndexOutOfBoundsException();
    	}
    	InputStream reader = new FileInputStream(f);
    	FileOutputStream writer = new FileOutputStream(f);
    	long readLength = Math.min(offset, f.length());
    	byte[] offsetBytes = new byte[(int) readLength];
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
            throw new NullPointerException();
        }
        if(file.isRoot()) {
            return false;
        }
        File parent = file.parent().toFile(root);
        parent.mkdirs();
        File f = file.toFile(root);
        try {
			return f.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
    }

    @Override
    public synchronized boolean delete(Path path)
    {
        if(path.isRoot()) {
            return false;
        }
        File f = path.toFile(root);
        if(f.isFile()) {
            return f.delete();
        } else {
            return deleteHelper(f);
        }
    }

    public boolean deleteHelper(File f) {
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
        File f = file.toFile(root);
        if(!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException();
        }
        int fileSize = (int) server.size(file);
        byte[] data = server.read(file, 0, fileSize);
        write(file, 0, data);
        return true;
    }
}
