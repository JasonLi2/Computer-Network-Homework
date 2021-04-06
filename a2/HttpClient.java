
/**
 * HttpClient Class
 * 
 * CPSC 441
 * Assignment 2
 * 
 * Jason Li
 * 10158349
 * 
 */

import java.io.*;
import java.net.*;
import java.util.logging.*;
import java.util.Scanner;

public class HttpClient {

    private static final Logger logger = Logger.getLogger("HttpClient"); // global logger
    
    public int offset = 0;
    public String httpResponseHeaderString;
    public int counter = 0;
    public String[] length1;
    public String[] length2;
    public String temp;
    public String lengthString;
    public long objLength;
    public int bytesRead = 0;

    /**
     * Default no-arg constructor
     */
	public HttpClient() {
		// nothing to do!
	}
	
    /**
     * Downloads the object specified by the parameter url.
	 *
     * @param url	URL of the object to be downloaded. It is a fully qualified URL.
     */
	public void get(String url) {
        //variables for URL parsing
        String [] urlComponents = url.split("/",2);
        String host;
        String port;
        String filePath;

        //splitting up the URL into parts
        //if the URL does contain port
        if(urlComponents[0].contains(":")) {
            String [] hostAndPort = urlComponents[0].split(":",2);
            host = hostAndPort[0];
            port = hostAndPort[1];
            filePath = "/" + urlComponents[1];
            url = hostAndPort[0] + "/" + urlComponents[1];
        }
        //if the URL does not contain the port
        else {
            host = urlComponents[0];
            port = "80";
            filePath = "/" + urlComponents[1];
        }

        //change received port from string to integer
        int portNum = Integer.parseInt(port);
        // System.out.println(host);
        // System.out.println(portNum);

        //setting up socket and streams
        Scanner is;
        PrintWriter os;

        try {
            Socket socket = new Socket(host, portNum);
            os = new PrintWriter(new DataOutputStream(socket.getOutputStream()));
            is = new Scanner(new InputStreamReader(socket.getInputStream()));

            while (true) {
                String request1 = "GET " +filePath + " HTTP/1.1\r\n";
                String request2 = "Host: " + host + ":" + port +"\r\n";
                String endOfHeader = "\r\n";

                //print HTTP request to console
                System.out.println(request1);
                System.out.println(request2);

                try {
                    String httpHeader = request1 + request2 + endOfHeader;
                    byte[] httpHeaderBytes = httpHeader.getBytes("US-ASCII");
                    socket.getOutputStream().write(httpHeaderBytes);
                    byte[] httpResponseHeaderBytes = new byte[2048];
                    byte[] httpObjectBytes = new byte[1024];

                    try {
                        //while there is still data from the stream
                        while (bytesRead != -1) {
                            socket.getInputStream().read(httpResponseHeaderBytes, offset, 1);
                            offset++;
                            httpResponseHeaderString = new String(httpResponseHeaderBytes, 0, offset, "US-ASCII");
                            //if end of header
                            if (httpResponseHeaderString.contains("\r\n\r\n")) {
                                break;
                            }
                        }
                    } catch (IOException e) {
                        System.out.println("Error downloading file");
                    }

                    //print HTTP response
                    System.out.println(httpResponseHeaderString);
                    
                    if (httpResponseHeaderString.contains("200 OK")) {
                        counter = 0;
                        //get the object size from the header
                        length1 = httpResponseHeaderString.split("Content-Length: ", 2);
                        temp = length1[1];
                        length2 = temp.split("\r", 2);
                        lengthString = length2[0];

                        int objLengthInt = Integer.parseInt(lengthString);
                        Long objLength = new Long(objLengthInt);

                        try {
                            //get file name from url
                            String [] tempArray = url.split("/", 5);
                            String fileName = tempArray[3]; 
                            // String fileName = "test.png";
                            // System.out.println(fileName);

                            //writing data from stream to file
                            File file = new File(fileName);
                            InputStream in = socket.getInputStream();
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            FileOutputStream fos = new FileOutputStream(file);
                            //while there are bytes to read
                            while (bytesRead != -1) {
                                //if the entire file has been read
                                if (counter == objLength) {
                                    break;
                                }
                                bytesRead = socket.getInputStream().read(httpObjectBytes);
                                // System.out.println("Number of bytes read = " + bytesRead);
                                baos.write(httpObjectBytes, 0, bytesRead);
                                //write to file
                                fos.write(httpObjectBytes);
                                fos.flush();
                                //increment counter with the amount of bytes read from stream
                                counter += bytesRead;
                            }
                            //clean up
                            baos.close();
                            fos.close();
                            in.close();
                            is.close();
                            os.close();
                        } catch (IOException e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                        os.flush();
                        break;
                    }
                } catch (IOException e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
