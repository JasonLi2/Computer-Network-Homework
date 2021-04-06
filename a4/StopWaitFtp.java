import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

import java.util.logging.*;

public class StopWaitFtp {
	
	private static final Logger logger = Logger.getLogger("StopWaitFtp"); // global logger

	public int timeout;
	private Timer timer;
	public ArrayList<FtpSegment> pending = new ArrayList<FtpSegment>();

	public Socket tcpSocket;
	public DatagramSocket udpSocket;
	public long fileLength;

	public int serverUdpPortNum;
	public int seqNum;

	public InetAddress ip;
	public FtpSegment justSentSeg;

	/**
	 * Constructor to initialize the program 
	 * 
	 * @param timeout		The time-out interval for the retransmission timer
	 */
	public StopWaitFtp(int timeout) {
		this.timeout = timeout;
	}


	/**
	 * Send the specified file to the specified remote server
	 * 
	 * @param serverName	Name of the remote server
	 * @param serverPort	Port number of the remote server
	 * @param fileName		Name of the file to be trasferred to the rmeote server
	 * @throws FtpException If anything goes wrong while sending the file
	 */
	public void send(String serverName, int serverPort, String fileName) throws FtpException {
		File file = new File(fileName);
		fileLength = file.length();

		//TCP handshake is successful
		if (TcpHandshake(serverName, serverPort, fileName)) {
			try {
				ip = InetAddress.getByName(serverName);
				
				//start ACK receiver
				AckReceiver receiver = new AckReceiver(udpSocket, this);
				receiver.start();

				DataInputStream in = new DataInputStream(new FileInputStream(file));

				int bytesRead;
				byte[] fileData = new byte[FtpSegment.MAX_PAYLOAD_SIZE];
				byte[] extra;
				FtpSegment newSeg;

				//while there is still file left to read
				while((bytesRead = in.read(fileData)) != -1) {
					//adjust size of buffer in max payload size is not reached
					if (bytesRead < FtpSegment.MAX_PAYLOAD_SIZE) {
						extra = new byte[bytesRead];
						System.arraycopy(fileData, 0, extra, 0, bytesRead);
						newSeg = new FtpSegment(seqNum, extra);
					}
					//read max payload size amount of bytes
					else {
						newSeg = new FtpSegment(seqNum, fileData);
					}
					seqNum++;

					//wait for previous packet to be ACK'ed
					while (pending.size() != 0) {
						Thread.yield();
					}
					processSend(newSeg);
					//save newSeg in case of timeout
					justSentSeg = new FtpSegment(newSeg);
				}
				//wait for all packets to be ACK'ed before shutting down
				while (pending.size() != 0) {
					Thread.yield();
				}
				//clean up
				receiver.shutdown();
				in.close();
				endTransmission();
				// System.out.println("Shutting down");
			}
			catch (Exception e) {
				System.out.println("Creating segment error: " + e.getMessage());
			}
		}
		else { 
			System.out.println("TCP handshake failed");
		}
	}

	//method will clean up the ports and sockets and end the connection with the server
	public void endTransmission() {
		DataOutputStream out;

		try {
			out = new DataOutputStream(tcpSocket.getOutputStream());
			out.writeByte(0);
			out.close();
			udpSocket.close();
			tcpSocket.close();
		}
		catch (Exception e) {
			System.out.println("Failed to end transmission");
			System.out.println(e.getMessage());
		}
	}

	//convert the segment created from the input file into a datagram packet and send over UDP
	public void processSend(FtpSegment seg) {
		DatagramPacket pkt = FtpSegment.makePacket(seg, ip, serverUdpPortNum);
		try {
			System.out.println("send " + seg.getSeqNum());
			udpSocket.send(pkt);
			pending.add(seg);

			if (pending.size() != 0) {
				startTimer();
			}
		}
		catch (Exception e) {
			System.out.println("Processing and sending packet error: " + e.getMessage());
		}
	}

	//method to handle the case where timeout occurs
	public void processTimeout() {
		// timer.cancel();
		System.out.println("timeout");
		DatagramPacket pkt = FtpSegment.makePacket(justSentSeg, ip, serverUdpPortNum);
		//try to retransmit the packet
		try {
			System.out.println("retx " + justSentSeg.getSeqNum());
			udpSocket.send(pkt);
		}
		catch (Exception e) {
			System.out.println("Retransamitting packet error: " + e.getMessage());
		}
	}

	//method to start the timer for keeping track of timeouts
	private void startTimer() {
		if (timer != null) {
			try {
				timer.cancel();
			}
			catch (Exception e) {
				System.out.println("Timer already cancelled");
			}
		}
		timer = new Timer(true);
		timer.scheduleAtFixedRate(new TimeoutHandler(this), timeout, timeout);
	}

	public void processAck(FtpSegment ack) {
		System.out.println("ack " + ack.getSeqNum());
		if (ack.getSeqNum() > justSentSeg.getSeqNum()) {
			timer.cancel();
			while (pending.size() != 0) {
				pending.clear();
			}
		}
	}

	//takes care of performing the handshake for the initial TCP connection 
	public boolean TcpHandshake(String serverName, int serverPort, String fileName) {
		boolean success = false;
		DataOutputStream out;
		DataInputStream inHandshake;

		try {
			tcpSocket = new Socket(serverName, serverPort);
			out = new DataOutputStream(tcpSocket.getOutputStream());
			inHandshake = new DataInputStream(tcpSocket.getInputStream());

			udpSocket = new DatagramSocket(tcpSocket.getLocalPort());

			out.writeUTF(fileName);
			out.flush();
			out.writeLong(fileLength);
			out.flush();
			out.writeInt(udpSocket.getLocalPort());
			out.flush();

			serverUdpPortNum = inHandshake.readInt();
			seqNum = inHandshake.readInt();

			if (seqNum >= 0) {
				success = true;
			}
			// out.close();
			// inHandshake.close();
		}
		catch (IOException e) {
			System.out.println("TCP handshake error: " + e.getMessage());
		}
		return success;
	}

} // end of class