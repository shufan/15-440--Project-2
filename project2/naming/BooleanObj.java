package naming;

public class BooleanObj {
	boolean bool;
	
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
		System.out.println("SET TO " + bool);
		this.bool = bool;
	}
}
