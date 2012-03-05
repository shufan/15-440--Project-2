package naming;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Iterator;

import common.Path;

public class PathNode
{
	private HashMap<String, PathNode> children = new HashMap<String, PathNode>();
	private boolean isDir;

	public PathNode() {
		children = new HashMap<String, PathNode>();
		isDir = true;
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
			System.out.println("last step");
			String comp = pathItr.next();
			System.out.println("THIS IS A COMPONENT: " + comp);
			//if component has already been added as this node's child, recurse
			if (children.containsKey(comp)) {
				System.out.println("GOODGOODGOODGOODGOOD" + comp);
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
