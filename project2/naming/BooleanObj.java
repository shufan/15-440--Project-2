package naming;

//A wrapper for booleans that allows it to be changed
public class BooleanObj {
	private boolean bool;
	
	public BooleanObj(boolean bool) {
		this.bool = bool;
	}
	
	public boolean equals(Object o) {
		if (o instanceof BooleanObj)
			return bool == ((BooleanObj)o).getBool();
		return false;
	}
	
	public boolean getBool() {
		return bool;
	}
	
	public void setBool(boolean bool) {
		this.bool = bool;
	}
}
