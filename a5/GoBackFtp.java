
/**
 * GoBackFtp Class
 * 
 * GoBackFtp implements a basic FTP application based on UDP data transmission.
 * It implements a Go-Back-N protocol. The window size is an input parameter.
 * 
 * @author 	Majid Ghaderi
 * @version	2021
 *
 */

import java.util.logging.*;

import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GoBackFtp {
	// global logger	
	private static final Logger logger = Logger.getLogger("GoBackFtp");

	public int winSize;
	public int timeout;

	public ConcurrentLinkedQueue<FtpSegment> queue;
	public Timer timer;

	public Socket tcpSocket;
	public DatagramSocket udpSocket;

	public int portNum;
	public boolean running = true;
	public AckReceiver receiver;
	
	public File file;
	public long fileLength;
	
	public int seqNum;
	public int serverUdpPortNum;
	public InetAddress ip;

	/**
	 * Constructor to initialize the program 
	 * 
	 * @param windowSize	Size of the window for Go-Back_N in units of segments
	 * @param rtoTimer		The time-out interval for the retransmission timer
	 */
	public GoBackFtp(int windowSize, int rtoTimer) {
		winSize = windowSize;
		timeout = rtoTimer;
		queue = new ConcurrentLinkedQueue<FtpSegment>();
	}


	/**
	 * Send the specified file to the specified remote server
	 * 
	 * @param serverName	Name of the remote server
	 * @param serverPort	Port number of the remote server
	 * @param fileName		Name of the file to be trasferred to the rmeote server
	 * @throws FtpException If unrecoverable errors happen during file transfer
	 */
	public void send(String serverName, int serverPort, String fileName) throws FtpException {
		file = new File(fileName);
		fileLength = file.length();

		//TCP handshake
		if (TcpHandshake(serverName, serverPort, fileName)) {
			//TCP handshake is successful
			try{
				ip = InetAddress.getByName(serverName);

				//start ACK receiver
				AckReceiver receiver = new AckReceiver(udpSocket, this);
				receiver.start();

				DataInputStream in = new DataInputStream(new FileInputStream(file));

				int bytesRead;
				byte[] fileData = new byte[FtpSegment.MAX_PAYLOAD_SIZE];
				byte[] extra;
				FtpSegment newSeg;

				//while there is still file to read
				while((bytesRead = in.read(fileData)) != -1) {
					//adjust size of buffer if max payload size is not reached
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

					//wait space in the queue
					while(queue.size() == winSize) {
						Thread.yield();
					}
					processSend(newSeg);
				}
				//wait for packets to be ACK'ed before shutting down
				while(!queue.isEmpty()) {
					Thread.yield();
				}
				receiver.shutdown();
				in.close();
				endTransmission();
			}
			catch (Exception e) {
				System.out.println("Creating segment error: " + e.getMessage());
			}
		}
		else { 
			System.out.println("TCP handshake failed");
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
	public synchronized void processSend(FtpSegment seg) {
		DatagramPacket pkt = FtpSegment.makePacket(seg, ip, serverUdpPortNum);
		try {
			System.out.println("send " + seg.getSeqNum());
			udpSocket.send(pkt);
			queue.add(seg);

			if (queue.size() == 1) {
				startTimer();
			}
		}
		catch (Exception e) {
			System.out.println("Processing and sending packet error: " + e.getMessage());
		}
	}

	public synchronized void processAck(FtpSegment ack) throws InterruptedException {
		System.out.println("ack " + ack.getSeqNum());
		//cancel timer
		if (queue.peek().getSeqNum() < ack.getSeqNum()) {
			timer.cancel();
		}
		//remove anything with sequence number lower than ack
		while (!queue.isEmpty() && queue.peek().getSeqNum() < ack.getSeqNum()) {
			FtpSegment temp = queue.poll();
		}
		//if the queue does not end up empty, start new timer
		if (!queue.isEmpty()) {
			startTimer();
		}
	}

	public synchronized void processTimeout() {
		// timer.cancel();
		System.out.println("timeout");
		FtpSegment[] pending = new FtpSegment[winSize];
		queue.toArray(pending);

		for(FtpSegment seg:pending) {
			DatagramPacket pkt = FtpSegment.makePacket(seg, ip, serverUdpPortNum);
			//try to retransmit the packet
			try {
				System.out.println("retx " + seg.getSeqNum());
				udpSocket.send(pkt);
			}
			catch (Exception e) {
				System.out.println("Retransamitting packet error: " + e.getMessage());
			}
		}
	}

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
	
} // end of class