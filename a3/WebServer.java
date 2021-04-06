

/**
 * WebServer Class
 * 
 * Implements a multi-threaded web server
 * supporting non-persistent connections.
 * 
 * @author 	Majid Ghaderi
 * @version	2021
 *
 */
import java.util.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.concurrent.*;
import java.io.File;
import java.io.IOException;
import java.util.logging.*;

public class WebServer extends Thread {
	
	// global logger object, configures in the driver class
    private static final Logger logger = Logger.getLogger("WebServer");
    
    ServerSocket serverSocket;
    int portNumber;
    boolean running;
    ExecutorService es;
	
	
    /**
     * Constructor to initialize the web server
     * 
     * @param port 	The server port at which the web server listens > 1024
     * 
     */
	public WebServer(int port) {
        //make sure port is valid
        if((port < 1024) || (port > 65536)) {
            System.out.println("Provided port number is invalid.");
            System.out.println("Port number must be between 1024 and 65536.");
            System.exit(0);
        }
        else {
            portNumber = port;
        }

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(2000);

            es = Executors.newFixedThreadPool(12);
            running = true;
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

	
    /**
	 * Main web server method.
	 * The web server remains in listening mode 
	 * and accepts connection requests from clients 
	 * until the shutdown method is called.
	 *
     */
	public void run() {
        Socket socket = new Socket();
        while(running) {
            try {
                socket = serverSocket.accept();
                WorkerThread wt = new WorkerThread(socket);
                es.execute(new Thread(wt));
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
	

    /**
     * Signals the web server to shutdown.
	 *
     */
	public void shutdown() {
        try {
            es.shutdown();
            running = false;
            es.shutdownNow();
        } catch (Exception e) {
            es.shutdownNow();
            running = false;
        }
    }

}
