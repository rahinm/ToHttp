package net.dollmar.tools;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;


import javax.net.ssl.HttpsURLConnection;


/**
 * This class implements a rudimentary TCP/IP to HTTP forwarder
 *  
 * @author Mohammad.Rahin
 *
 */
public class ToHttp {

    private static final int PORT = 11211;

    //private static final String TO_URL = "http://172.16.9.162:8080/emis/SolveXML";
    //private static final String TO_URL = "http://127.0.0.1:8000/emis/SolveXML";



    public static final String SHUTDOWN_COMMAND = "$$SHUT_DOWN$$";


    private boolean shutdownNow = false;
    private int listenerPort;
    private String forwardingUrl;

    
    public ToHttp(final int port, final String url) {
        this.listenerPort = port;
        this.forwardingUrl = url;
    }
    
    
    
    public void setShutdownNow(boolean flag) {
        shutdownNow = flag;
    }


    public boolean shouldShutdownNow() {
        return shutdownNow;
    }


    public class RequestHandler implements Runnable {

        private ToHttp parent;
        private Socket clientSocket = null;
        
        private int readTimeout = 1000;            // in milliseconds - only applies during read I/O
        private int interPacketDelay = 30;         // in milliseconds - time allowed between successive data packets  
        

        public RequestHandler(ToHttp parent) {
            this.parent = parent;
        }
        public void setClientSocket(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public HttpURLConnection connect(String urlString) throws MalformedURLException, IOException {
            HttpURLConnection conn = null;
            URL url = new URL(urlString);
            if (urlString.startsWith("https://") || urlString.startsWith("HTTPS://")) {
                conn = (HttpsURLConnection) url.openConnection();
            }
            else {
                conn = (HttpURLConnection) url.openConnection();
            }
            return conn;
        }


        public void disconnect(HttpURLConnection conn) {
            if (conn != null) {
                conn.disconnect();
            }
        }

        private String relay(String inMessage) throws Exception {
            if (inMessage == null || inMessage.length() == 0) {
                return "";
            }
            //String postData = URLEncoder.encode(inMessage, "UTF-8");
            String postData = inMessage;

            HttpURLConnection conn = connect(forwardingUrl);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setUseCaches (false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            try {
                //Send request
                DataOutputStream wr = new DataOutputStream (conn.getOutputStream ());
                wr.writeBytes (postData);
                wr.flush ();
                wr.close ();
                System.out.println(String.format("[%d] --> %d bytes --> %d bytes", Thread.currentThread().getId(), inMessage.length(), postData.length()));

                //Get Response  
                InputStream is = conn.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                String line;
                StringBuilder response = new StringBuilder(); 
                while((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }

                rd.close();
                String resp = response.toString().trim();
                // System.out.println("HTTP Response:\n" + resp);
                
                return resp; 
            }
            finally {
                disconnect(conn);
            }	        
        }


        /**
         * Reads the defined number of data bytes into the provided buffer.
         * Returns -1 if the peer is closed (i.e. eof is reached).
         * 
         * @param buffer
         * @param offset
         * @param length
         * @throws ChannelException
         */
        public int socketRead(InputStream in, byte[] buffer, int offset, int length) throws Exception {

            int bytesToRead = length;
            int arrayLoc = offset;
            while (bytesToRead > 0) { // iterate till we get everything we need
                int timeout = (bytesToRead == length) ? readTimeout : interPacketDelay;
                try {
                    clientSocket.setSoTimeout(timeout);
                }
                catch (SocketException se) {
                    throw new Exception("Failed to set socket timeout", se);
                }
                int bytesRead = 0;
                try {
                    bytesRead = in.read(buffer, arrayLoc, bytesToRead);
                }
                catch (SocketTimeoutException ste) {
                    if (bytesToRead == length) {
                        throw new Exception("Timeout occurred while receiving data", ste);
                    }
                }
                catch (IOException ioe) {
                    throw new Exception("Error while receiving data", ioe);
                }
                if (bytesRead <= 0) {
                    return (arrayLoc > offset) ? (arrayLoc - offset) : bytesRead;
                }
                bytesToRead -= bytesRead;
                arrayLoc += bytesRead;
            }
            return arrayLoc - offset;

        }

        
        
        public void run() {
            if (clientSocket != null) {
                try {
                    BufferedInputStream in = new BufferedInputStream(clientSocket.getInputStream());
                    BufferedOutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());
                    
                    byte[] request = new byte[4096];
                    int readBytes = socketRead(in, request, 0, 4096);
                    String inData = "";
                    if (readBytes > 0 )
                        inData = new String(request).trim();
                    
                    
                    // System.out.println("Input Data = " + inData);
                    if (SHUTDOWN_COMMAND.equals(inData)) {
                        // received a shutdown command, signal the parent immediately
                        System.out.println();
                        System.out.println("Received a TCP/IP listener shutdown command. Shutting down ...");
                        parent.setShutdownNow(true);
                    }
                    else {
                        String resp = relay(inData.trim());
                        out.write(resp.getBytes());
                        out.flush();
                        System.out.println(String.format("[%d] <-- %d bytes", Thread.currentThread().getId(), resp.length()));
                    }
                    try {
                        Thread.sleep(300);
                    }
                    catch (Exception e) {
                    }
                    clientSocket.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }






    private void serveRequest(Socket clientSocket) throws Exception {
        RequestHandler rh = new RequestHandler(this);
        rh.setClientSocket(clientSocket);
        new Thread(rh).start();
    }



    public void runServer() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(listenerPort, 0);
        }
        catch (IOException ioe) {
            System.err.println("Error: Failed to create a TCP/IP listener socket on port " + PORT + " " + ioe.getMessage());
            System.err.println("NOTE: SocketListener thread not started.");
            return;
        }
        System.out.println("Socket listener thread started. Listening on port " + listenerPort); 
        while(!shutdownNow) {
            try {
                Socket clientSocket = serverSocket.accept();
                serveRequest(clientSocket);
                Thread.sleep(100);  // sleep for 1 second
            }
            catch (Exception e) {
                System.err.println(e.getMessage());
            }
        } // while 
        // time to shutdown
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Error: Failed to close listener socket. " + e.getMessage());
        }
        System.out.println("TCP/IP Listener is now stopped.");
    }



    public static void main(String[] args) {
        System.out.println("ToHttp: TCP/IP <----> HTTP/HTTPS forwarder");
        if (args.length != 2) {
            System.err.println("Error: Must use 2 command-line parameters");
            System.err.println("Usage: ToHttp <listener port> <http/s url>");
            System.exit(1);
        } 
        else {
            int port = 0;
            try {
                port = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException nfe) {
                System.err.println("Error: port parameter must be numeric");
                System.exit(2);
            }
            String url = args[1];
            if (!(url.startsWith("http:") || url.startsWith("HTTP:") || url.startsWith("https:") || url.startsWith("HTTPS:"))) {
                System.err.println("Error: URL parameter must specify http or https protocol scheme");
                System.exit(2);
            }
            new ToHttp(port, url).runServer();
        }
    }
    
}


