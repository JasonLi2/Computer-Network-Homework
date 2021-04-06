import java.io.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.net.Socket;
import java.lang.StringBuilder;
import java.util.concurrent.TimeUnit;
import java.util.Date;

public class WorkerThread implements Runnable {
    BufferedReader is; //input stream
    DataOutputStream os; //output stream
    Socket socket = new Socket();
    String[] requestBuffer;
    boolean goodRequest = false;
    boolean get = false;
    boolean requestLen = false;
    String request;
    String message;

    //constructor for WorkerThread
    public WorkerThread(Socket s) {
        socket = s;
        try {
            is = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            os = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    //receving and sending message to HTTP GET
    public void run() {
        System.out.println("Connection established");
        // System.out.println("Worker working ...");

        //reading the header of the request
        try {
            request = is.readLine();
            requestBuffer = request.split(" ");

            //see if request is formatted correctly
            if (requestBuffer.length == 3) {
                requestLen = true;
            }
            if (requestBuffer[0].equals("GET")) {
                get = true;
            }

            //if the GET request format is incorrect
            if(!requestLen || !get || !(requestBuffer[2].equals("HTTP/1.1") || requestBuffer[2].equals("HTTP/1.0"))) {
                Date date = new Date();
                message = "HTTP/1.1 400 Bad Request\r\n"  + "Date: " + date + "\r\n" + "Server: " + "WebServer" + "\r\n" + "Connection: close" + "\r\n";
                os.writeBytes(message);
                //clean up
                os.flush();
                os.close();
                is.close();
            }
            //if the GET request format is correct
            else {
                StringBuilder sb = new StringBuilder(requestBuffer[1]);
                if (sb.charAt(0) == '/') {
                    sb.deleteCharAt(0);
                }

                String temp = sb.toString();
                Path path = Paths.get(temp);

                //see if the requested file exists
                if (!Files.exists(path)) {
                    Date date = new Date();
                    message = "HTTP/1.1 404 Bad File Not Found\r\n"  + "Date: " + date + "\r\n" + "Server: " + "Server_A2" + "\r\n" + "Connection: close\r\n" + "\r\n";
                    os.writeBytes(message);
                    //clean up
                    os.flush();
                    os.close();
                    is.close();
                }
                //Request is good
                else {
                    //get Last-Modified
                    FileTime ft = Files.getLastModifiedTime(path);
                    long time = ft.to(TimeUnit.MILLISECONDS);

                    //get date the file was modified and the current date
                    Date modifiedDate = new Date(time);
                    Date currentDate = new Date();
                    //get the file type
                    String fileType = Files.probeContentType(path);

                    byte[] fileBuffer = Files.readAllBytes(path);
                    byte[] buffer;
                    int len = fileBuffer.length;
                    long counter = 0;

                    //send 200 OK response
                    message = "HTTP/1.1 200 OK\r\n" + "Date: " + currentDate + "\r\n" + "Server: " + "WebServer" + "\r\n" + "Last-Modified: " + modifiedDate + "\r\n" + "Content-Length: " + len + "\r\n" + "Content-Type: " + fileType + "\r\n" + "Connection: close\r\n" + "\r\n";
                    
                    byte[] messageBuffer = message.getBytes();
                    byte[] sendBytes = new byte[messageBuffer.length + len];

                    buffer = new byte[32768];
                    counter += len;

                    //combining message and file into same byte buffer
                    System.arraycopy(messageBuffer, 0, sendBytes, 0, messageBuffer.length);
                    System.arraycopy(fileBuffer, 0, sendBytes, messageBuffer.length, len);

                    //send the HTTP response message and file
                    os.write(sendBytes);
                    
                    //clean up
                    os.flush();
                    os.close();
                    is.close();
                }
            }
            System.out.println(message);
            socket.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            try {
                socket.close();
            } catch (Exception f) {
            }
        }
    }
}