package org.august;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

public class Handler implements Runnable {
    private static final Logger logger = Logger.getLogger(Handler.class.getName());
    public static final Pattern CONNECT_PATTERN = Pattern.compile("CONNECT (.+):(.+) HTTP/(1\\.[01])",
            Pattern.CASE_INSENSITIVE);
    private final Socket clientSocket;
    private static final int BUFFER_SIZE = 8192;
    private static final int SOCKET_TIMEOUT = 5000; // 5 seconds
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public Handler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.ISO_8859_1))) {
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

        String header;
        do {
            header = reader.readLine();
        } while (!"".equals(header));

        try (Socket remoteSocket = new Socket(host, port);
             BufferedOutputStream clientOutput = new BufferedOutputStream(clientSocket.getOutputStream());
             BufferedOutputStream remoteOutput = new BufferedOutputStream(remoteSocket.getOutputStream());
             BufferedInputStream remoteInput = new BufferedInputStream(remoteSocket.getInputStream());
             BufferedInputStream clientInput = new BufferedInputStream(clientSocket.getInputStream())) {

            String response = "HTTP/" + httpVersion + " 200 Connection established\r\n" +
                    "Proxy-Agent: AugustsProxy/1.0\r\n\r\n";
            clientOutput.write(response.getBytes(StandardCharsets.ISO_8859_1));
            clientOutput.flush();

            clientSocket.setSoTimeout(SOCKET_TIMEOUT);
            remoteSocket.setSoTimeout(SOCKET_TIMEOUT);

            AtomicBoolean clientToRemoteClosed = new AtomicBoolean(false);
            AtomicBoolean remoteToClientClosed = new AtomicBoolean(false);

            executor.submit(() -> forwardData(remoteInput, clientOutput, remoteToClientClosed, clientToRemoteClosed, "Remote -> Client"));
            forwardData(clientInput, remoteOutput, clientToRemoteClosed, remoteToClientClosed, "Client -> Remote");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error in connection handling", e);
        }
    }

    private static void forwardData(InputStream input, OutputStream output, AtomicBoolean thisClosed, AtomicBoolean otherClosed, String direction) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        try {
            while (!thisClosed.get() && !otherClosed.get()) {
                try {
                    if (input.available() > 0) {
                        bytesRead = input.read(buffer);
                        if (bytesRead == -1) {
                            break;
                        }
                        output.write(buffer, 0, bytesRead);
                        output.flush();
                    } else {
                        Thread.sleep(10);
                    }
                } catch (SocketTimeoutException | InterruptedException ignored) {}
            }
        } catch (SocketException e) {
            logger.log(Level.INFO, "Connection closed for " + direction, e);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error forwarding data for " + direction, e);
        } finally {
            thisClosed.set(true);
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing resource", e);
        }
    }
}