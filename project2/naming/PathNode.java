package naming;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import common.Path;

public class PathNode
{
	private HashMap<String, PathNode> children = new HashMap<String, PathNode>();
	private boolean isDir;
	private int numReaders; //keeps track of the number of readers reading this node
	private LinkedList<LockRequest> lockReqs = new LinkedList<LockRequest>();
	
	private final int EXCLUSIVE = -1;

	public PathNode() {
		children = new HashMap<String, PathNode>();
		isDir = true;
		numReaders = 0;
	}

	public HashMap<String, PathNode> getChildrenMap() {
		return children;
	}

	public boolean isDirectory() {
		return isDir;
	}

	public void setIsDir(boolean isDir) {
		this.isDir = isDir;
	}
	
	public int getNumReaders() {
		return numReaders;
	}
	
	//used parent for the while loop...
	public void lock(Path p, boolean exclusive) throws FileNotFoundException {
System.out.println("\nWant lock for: "+p.toString()+" Exclusive: "+exclusive);
		ArrayList<BooleanObj> locks = new ArrayList<BooleanObj>();
		synchronized(this) {
			//locks contains a list of locks the thread must obtain before it gets the ok
			//iterate through the components of the path
			PathNode currNode = this;
int i = 0;
			Iterator<String> compItr = p.iterator();
			while (compItr.hasNext()) {
				String nextComp = compItr.next();
				//if others are allowed to read currNode
System.out.println("Node #" + i + " numReaders: "+currNode.numReaders);
				if (currNode.numReaders != EXCLUSIVE && !writeReqWaiting()) {
					//thread obtains a lock from the currNode, adds to locks list
					currNode.numReaders++;
					locks.add(new BooleanObj(true));
System.out.println("Node #" + i + " numReaders IS NOW: "+currNode.numReaders);
				}
				else { //others can't read from currNode
					LockRequest lReq = new LockRequest(exclusive, Thread.currentThread());
					//adds to the current node's queue of locks
					currNode.lockReqs.add(lReq);
					//thread can't get a lock from currNode
					locks.add(lReq.getBoolObj());
				}
				currNode = currNode.getChildrenMap().get(nextComp);
i++;				
			}
			//handles last component of the path separately because must ensure 
			//that requests to read/write are handled in order
System.out.println(currNode.getChildrenMap().toString());
System.out.println("exclusive: "+exclusive);
System.out.println("Node #"+i+" numReaders: " + currNode.numReaders);

			//if (thread wants to write & no one's reading from currNode & no read reqs waiting)
			if (exclusive && currNode.numReaders == 0 && !writeReqWaiting()) {
				currNode.numReaders = EXCLUSIVE;
				locks.add(new BooleanObj(true));
				System.out.println("Case 1: thread req exclusive = true & no one's reading from currNode & no read reqs waiting");
				System.out.println("Node #" + i + " numReaders IS NOW: "+currNode.numReaders);
			}
			//(thread wants to read & currNode isn't locked for exclusive access & no write reqs waiting)
			else if (!exclusive && currNode.numReaders != EXCLUSIVE && !writeReqWaiting()) {
				currNode.numReaders++;
				locks.add(new BooleanObj(true));
				System.out.println("Case 2: thread req exclusive = false & currNode isn't locked for exclusive access & no write reqs waiting");
				System.out.println("Node #" + i + " numReaders IS NOW: "+currNode.numReaders);
			}
			else { //if above conditions fail, requests are added to the queue
				LockRequest lReq = new LockRequest(exclusive, Thread.currentThread());
				currNode.lockReqs.add(lReq);
				locks.add(lReq.getBoolObj());
				System.out.print("Case 3: add lockReq to queue ----- [");
				for (BooleanObj b : locks)
					System.out.print(b.getBool()+" ");
				System.out.println("]");
			}
			
		}

		//waits until the locks for each component are obtained
		BooleanObj falseBoolObj = new BooleanObj(false);

		while (locks.contains(falseBoolObj)) {
			try {
				Thread.currentThread().sleep(100);
			} catch (InterruptedException e) {
			}
			for (BooleanObj l : locks) {
				System.out.println(l);
			}
		}
		//debugging
		synchronized(this) {
			System.out.print("Locked path: "+p+" queue now looks like");
			System.out.print("[");
			for (BooleanObj b : locks)
				System.out.print(b.getBool()+" ");
			System.out.println("]");
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
	
	//returns whether there is a read request waiting at this node
	private boolean readReqWaiting() {
		for (LockRequest lReq : lockReqs) {
			if (!lReq.isExclusive())
				return true;
		}
		return false;
	}
	
	public void unlock(Path p, boolean exclusive) {
		System.out.println("\nWant to unlock: "+p);
		synchronized(this) {
			PathNode currNode = this;
			Iterator<String> compItr = p.iterator();
			while (compItr.hasNext()) {
				currNode.numReaders--;
				if (currNode.numReaders == 0)
					currNode.servicePending();
				currNode = currNode.getChildrenMap().get(compItr.next());
			}
			//if no one could write to it before, now others can
			if (exclusive) {
				System.out.println("aaaa");
				currNode.numReaders = 0;
				currNode.servicePending();
			}
			else {
				System.out.println("bbbb");
				//one less reader
				currNode.numReaders--;
				if (currNode.numReaders == 0)
					currNode.servicePending();
			}
			System.out.println(currNode.getChildrenMap().toString());
			System.out.println("exclusive: "+exclusive);
			
			System.out.println("Unlocked for path: "+p+" Num readers now:" +currNode.numReaders);
		}
	}
	
	private void servicePending() {
		System.out.println("called servicePending()");
		while (!lockReqs.isEmpty()) {
			System.out.println("got another req from lockReqs");
			LockRequest lReq = lockReqs.peek();
			//if request wants exclusive access and no one is reading this node
			System.out.println(lReq.isExclusive()+" "+numReaders);
			if(lReq.isExclusive() && numReaders == 0) {
				numReaders = EXCLUSIVE; System.out.println("in here good");}
			else if(!lReq.isExclusive() && numReaders != EXCLUSIVE) {//otherwise, are servicing reader 
				numReaders++;
			} else {
				return;
			}
			System.out.println("boolObj in servicePending"+lReq.getBoolObj());
			lReq.hasLock().setBool(true);
			lockReqs.pop();
			lReq.caller().interrupt();
		}
	}
	
	/*
	 * Gets the node of the last component in path.
	 * Throws FileNotFoundException if complete path is not in tree.
	 */
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
}
