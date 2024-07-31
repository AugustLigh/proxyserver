package org.august;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Handler implements Runnable {
    private static final Logger logger = Logger.getLogger(Handler.class.getName());
    public static final Pattern CONNECT_PATTERN = Pattern.compile("CONNECT (.+):(.+) HTTP/(1\\.[01])",
            Pattern.CASE_INSENSITIVE);
    private final Socket clientSocket;
    private static final int BUFFER_SIZE = 8192;
    private static final int SOCKET_TIMEOUT = 30000;

    public Handler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "ISO-8859-1"))) {
            String request = reader.readLine();
            logger.info("Received request: " + request);
            Matcher matcher = CONNECT_PATTERN.matcher(request);
            if (matcher.matches()) {
                handleConnect(matcher, reader);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error handling client request", e);
        } finally {
            closeQuietly(clientSocket);
        }
    }

    private void handleConnect(Matcher matcher, BufferedReader reader) throws IOException {
        String host = matcher.group(1);
        int port = Integer.parseInt(matcher.group(2));
        String httpVersion = matcher.group(3);

        // Skip the rest of the headers
        String header;
        do {
            header = reader.readLine();
        } while (!"".equals(header));

        try (Socket remoteSocket = new Socket(host, port);
             OutputStream clientOutput = clientSocket.getOutputStream();
             OutputStream remoteOutput = remoteSocket.getOutputStream();
             InputStream remoteInput = remoteSocket.getInputStream()) {

            // Send connection established response
            String response = "HTTP/" + httpVersion + " 200 Connection established\r\n" +
                    "Proxy-Agent: SimpleProxy/1.0\r\n\r\n";
            clientOutput.write(response.getBytes("ISO-8859-1"));
            clientOutput.flush();

            // Set timeouts
            clientSocket.setSoTimeout(SOCKET_TIMEOUT);
            remoteSocket.setSoTimeout(SOCKET_TIMEOUT);

            // Start bi-directional forwarding
            Thread remoteToClientThread = new Thread(() -> forwardData(remoteInput, clientOutput));
            remoteToClientThread.start();
            forwardData(clientSocket.getInputStream(), remoteOutput);

            remoteToClientThread.join();
        } catch (IOException | InterruptedException e) {
            logger.log(Level.WARNING, "Error in connection handling", e);
        }
    }

    private static void forwardData(InputStream input, OutputStream output) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        try {
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                if (input.available() < 1) {
                    output.flush();
                }
            }
        } catch (SocketException e) {
            logger.log(Level.INFO, "Connection reset", e);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error forwarding data", e);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing resource", e);
            }
        }
    }
}