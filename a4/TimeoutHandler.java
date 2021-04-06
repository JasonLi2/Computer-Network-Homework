import java.util.TimerTask;

public class TimeoutHandler extends TimerTask {
	private StopWaitFtp parent;
	
	public TimeoutHandler(StopWaitFtp ftp) {
		parent = ftp;
	}
	public void run() {
		parent.processTimeout();
	}
}