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

	//returns true if the thread is asking for exclusive access
	public boolean isExclusive() {
		return exclusive;
	}
	
	//returns the thread that requested the lock
	public Thread caller() {
		return caller;
	}
	
	//returns the BooleanObj that determines whether the lock has been obtained
	public BooleanObj hasLock() {
		return hasLock;
	}
}