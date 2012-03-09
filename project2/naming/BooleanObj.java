package naming;

//A wrapper for booleans that allows it to be changed
public class BooleanObj {
	private boolean bool;
	
	public BooleanObj(boolean bool) {
		this.bool = bool;
	}
	
	//returns true if the two BooleanObjs have the same bool value
	public boolean equals(Object o) {
		if (o instanceof BooleanObj)
			return bool == ((BooleanObj)o).getBool();
		return false;
	}
	
	//getter for the boolean attribute
	public boolean getBool() {
		return bool;
	}
	
	//setter for the boolean attribute
	public void setBool(boolean bool) {
		this.bool = bool;
	}
}
