package org.august;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ProxyServer {
    private static final Logger logger = Logger.getLogger(ProxyServer.class.getName());
    private final int port;
    private final ExecutorService executorService;

    public ProxyServer(int port) {
        this.port = port;
        this.executorService = Executors.newCachedThreadPool();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Proxy server started on port " + port);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(new Handler(clientSocket));
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error accepting client connection", e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not start server", e);
        } finally {
            executorService.shutdownNow();
        }
    }


}