import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class AckReceiver extends Thread {
	private DatagramSocket udpSocket;
	private StopWaitFtp parent;
	private boolean shutdown;
	
	public AckReceiver(DatagramSocket socket, StopWaitFtp ftp) {
		udpSocket = socket;
		parent = ftp;
		shutdown = false;
	}
	
	public void run() {
		DatagramPacket pkt;
        byte[] pktData = new byte[FtpSegment.MAX_SEGMENT_SIZE];
        
		while(!Thread.currentThread().isInterrupted() && !shutdown) {
            pkt = new DatagramPacket(pktData, pktData.length);
            
			try {
				udpSocket.receive(pkt);
				parent.processAck(new FtpSegment(pkt));
            }
            catch(Exception e){
				// System.out.println("ACK receiver error: " + e.getMessage());
			}
		}
	}
	
	public void shutdown() {
		shutdown = true;
	}
}