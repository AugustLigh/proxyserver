package org.august;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final int DEFAULT_PORT = 8080;
    private static final String PROPERTIES_FILE_PATH = "server.properties";
    private static final String SERVER_PORT_KEY = "server-port";

    public static void main(String[] args) {
        int port = loadPortFromProperties();

        if (port == DEFAULT_PORT && args.length > 0) {
            port = parsePortFromArgs(args[0]);
        }

        new ProxyServer(port).start();
    }

    private static int loadPortFromProperties() {
        Properties properties = new Properties();
        File propertiesFile = new File(PROPERTIES_FILE_PATH);

        if (!propertiesFile.exists()) {
            LOGGER.log(Level.INFO, "Properties file not found. Using default port.");
            return DEFAULT_PORT;
        }

        try (FileInputStream input = new FileInputStream(propertiesFile)) {
            properties.load(input);
            String serverPort = properties.getProperty(SERVER_PORT_KEY);
            if (serverPort != null) {
                return Integer.parseInt(serverPort);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read server-port from server.properties. Using default port.", e);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Invalid port number in server.properties. Using default port.", e);
        }
        return DEFAULT_PORT;
    }

    private static int parsePortFromArgs(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Invalid port number provided as argument. Using default port.", e);
            return DEFAULT_PORT;
        }
    }
}
