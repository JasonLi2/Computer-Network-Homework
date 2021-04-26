import java.util.TimerTask;

public class TimeoutHandler extends TimerTask {
	private GoBackFtp parent;
	
	public TimeoutHandler(GoBackFtp ftp) {
		parent = ftp;
	}
	public void run() {
		parent.processTimeout();
	}
}