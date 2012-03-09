package naming;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import common.Path;

public class PathNode
{
	HashMap<String, PathNode> children = new HashMap<String, PathNode>();
	private Path currPath;
	private boolean isDir;
	//keep track of number of reads for replication, resets at 20
	private int readCount;  
	//keeps track of the number of readers reading this node
	private int numReaders; 
	private LinkedList<LockRequest> lockReqs = new LinkedList<LockRequest>();
	//EXCLUSIVE is a flag indicating that a thread has exclusive access
	private final int EXCLUSIVE = -1;

	public PathNode() {
		children = new HashMap<String, PathNode>();
		isDir = true;
		numReaders = 0;
		readCount = 0;
	}

	//returns subdirectories and files of the current node
	public HashMap<String, PathNode> getChildrenMap() {
		return children;
	}

	//returns true if the node is a directory
	public boolean isDirectory() {
		return isDir;
	}

	//sets whether or not the node is a directory
	public void setIsDir(boolean isDir) {
		this.isDir = isDir;
	}
	
	//sets the current path of the node
	public void setCurrPath(Path file) {
		this.currPath = file;
	}
	
	//returns the number of readers reading from the node
	public int getNumReaders() {
		return numReaders;
	}
	
	public void lock(Path p, boolean exclusive) throws FileNotFoundException {
		//locks contains a list of locks the thread must obtain 
		//before it gets the ok
		ArrayList<BooleanObj> locks = new ArrayList<BooleanObj>();
		synchronized(this) {
			//iterate through the components of the path
			PathNode currNode = this;
			Iterator<String> compItr = p.iterator();
			while (compItr.hasNext()) {
				String nextComp = compItr.next();
				//if others are allowed to read currNode
				if (currNode.numReaders != EXCLUSIVE && !writeReqWaiting()) {
					//thread obtains a lock from the currNode, adds to locks 
					//list
					currNode.numReaders++;
					locks.add(new BooleanObj(true));
				}
				else { //others can't read from currNode
					LockRequest lReq = new LockRequest(exclusive, 
							Thread.currentThread());
					//adds to the current node's queue of locks
					currNode.lockReqs.add(lReq);
					//thread can't get a lock from currNode
					locks.add(lReq.hasLock());
				}
				currNode = currNode.getChildrenMap().get(nextComp);
			}
			//handles last component of the path separately because must 
			//ensure that requests to read/write are handled in order

			//if (thread wants to write & no one's reading from currNode 
			//& no read reqs waiting)
			if (exclusive && currNode.numReaders == 0 && !writeReqWaiting()) {
				currNode.numReaders = EXCLUSIVE;
				locks.add(new BooleanObj(true));
			}
			//(thread wants to read & currNode isn't locked for exclusive 
			//access & no write reqs waiting)
			else if (!exclusive && currNode.numReaders != EXCLUSIVE && 
					!writeReqWaiting()) {
				currNode.numReaders++;
				locks.add(new BooleanObj(true));
			}
			else { //if above conditions fail, requests are added to the queue
				LockRequest lReq = new LockRequest(exclusive, 
						Thread.currentThread());
				currNode.lockReqs.add(lReq);
				locks.add(lReq.hasLock());
			}
			
		}

		//waits until the locks for each component are obtained
		BooleanObj falseBoolObj = new BooleanObj(false);

		while (locks.contains(falseBoolObj)) {
			try {
				Thread.currentThread();
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}
	
	//returns whether there is a write request waiting at this node
	private boolean writeReqWaiting() {
		for (LockRequest lReq : lockReqs) {
			if (lReq.isExclusive())
				return true;
		}
		return false;
	}
	
	public Path unlock(Path p, boolean exclusive) {
		synchronized(this) {
			PathNode currNode = this;
			//iterates through each component of the path
			Iterator<String> compItr = p.iterator();
			while (compItr.hasNext()) {
				//unlocking, so decreases the component's number of readers 
				//by one
				currNode.numReaders--;
				readCount++;
				//if node has no more readers, it can service pending requests
				if (currNode.numReaders == 0)
					currNode.servicePending();
				currNode = currNode.getChildrenMap().get(compItr.next());
			}
			//if no one could write to it before, now others can
			if (exclusive) {
				currNode.numReaders = 0;
				currNode.servicePending();
			}
			else {
				//one less reader
				currNode.numReaders--;
				readCount ++;
				if (currNode.numReaders == 0)
					currNode.servicePending();
			}
			if(currNode.currPath != null && readCount >= 20) {
				readCount = 0;
				return currNode.currPath;
			}
		}
		return null;
	}
	
	//services threads waiting to obtain a lock
	private void servicePending() {
		while (!lockReqs.isEmpty()) {
			LockRequest lReq = lockReqs.peek();
			//if request wants exclusive access and no one is reading this node
			if(lReq.isExclusive() && numReaders == 0)
				numReaders = EXCLUSIVE;
			//otherwise, are servicing reader 
			else if(!lReq.isExclusive() && numReaders != EXCLUSIVE) 
				numReaders++;
			else
				return;
			lReq.hasLock().setBool(true);
			lockReqs.pop();
			lReq.caller().interrupt();
		}
	}
	
	//gets the node of the last component in path.
	//throws FileNotFoundException if complete path is not in tree.
	public PathNode getLastCompNode(Path p) throws FileNotFoundException {
		PathNode currentNode = this;
		//iterates through components of p
		Iterator<String> componentItr = p.iterator();
		while (componentItr.hasNext()) {
			String component = componentItr.next();
			//if tree contains the component, keeps iterating
			if (currentNode.children.containsKey(component))
				currentNode = currentNode.getChildrenMap().get(component);
			else
				throw new FileNotFoundException("Path was not found");
		}
		return currentNode;
	}

	//adds a given path to the current directory tree
	public boolean addFile(Iterator<String> pathItr) {
		//iterates through the components of the path
		if (pathItr.hasNext()) {
			String comp = pathItr.next();
			//if component has already been added as this node's child, recurse
			if (children.containsKey(comp)) {
				PathNode compNode = children.get(comp);
				return compNode.addFile(pathItr);
			}
			//else, now at a component that has not been added yet
			else {
				//while more components left in path, add them to the tree
				boolean moreComp = true;
				PathNode currNode = this;
				do {
					PathNode pN = new PathNode();
					pN.setCurrPath(new Path(currNode.currPath,comp));
					//adds subdirectories and files to the node's children
					currNode.getChildrenMap().put(comp, pN);
					if (!pathItr.hasNext()) {
						pN.setIsDir(false);
						moreComp = false;
					} else {
						currNode = currNode.getChildrenMap().get(comp);
						comp = pathItr.next();
					}
				} while (moreComp);
				return true;
			}
		}
		return false;
	}

	//returns files stored at a path, given a pointer to the beginning of 
	//the path
	public void getFilesWithin(Iterator<String> iter, ArrayList<Path> files) {
		if(iter.hasNext()) {
			String name = iter.next();
			children.get(name).getFilesWithin(iter, files);
		} else {
			getFilesWithinHelper(files);
		}
	}

	//a helper method for the getFilesWithin method
	private void getFilesWithinHelper(ArrayList<Path> files) {
		if(!isDir) {
			files.add(currPath);
		} else {
			for(PathNode pN : children.values()) {
				pN.getFilesWithinHelper(files);
			}
		}
	}
}
