package naming;

//Reprsents a Thread's request for a lock because it has to wait
public class LockRequest
{
	private boolean exclusive;
	private Thread caller;
	private BooleanObj hasLock;

	public LockRequest(boolean exclusive, Thread caller)
	{
		this.exclusive = exclusive;
		this.caller = caller;
		hasLock = new BooleanObj(false);
	}

	public boolean isExclusive() {
		return exclusive;
	}
	
	public Thread caller() {
		return caller;
	}
	
	public BooleanObj hasLock() {
		return hasLock;
	}
	
	public BooleanObj getBoolObj() {
		return hasLock;
	}
}