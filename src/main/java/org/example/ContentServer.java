 package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public class ContentServer {
    public static final LamportClock lamportClock = new LamportClock();

    public static void main(String[] args) {
        // Check for correct number of command-line arguments
        if (args.length < 2) {
            System.out.println("Missing Arguments!!!  Correct format: java ContentServer <server_address:port_number> <data_file_path>");
            return;
        }

        String serverInfo = args[0]; 
        String dataFilePath = args[1]; 

        // Extract server name, port 
        String[] serverParts = serverInfo.replace("http://", "").split(":");
        if (serverParts.length != 2) {
            System.err.println("Invalid server info. Expected format: <server>:<port>");
            return;
        }

        String serverName = serverParts[0]; 
        int port;
        try {
            port = Integer.parseInt(serverParts[1]); 
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number.");
            return;
        }

        lamportClock.tick(); // Increment Lamport clock before sending data

        try {
            
            Map<String, String> dataMap = parseFile(dataFilePath);
            if (!dataMap.containsKey("id")) {
                System.err.println("Data must contain an 'id' field.");
                return;
            }

            // Convert the data to a JSON object
            JSONObject jsonObject = new JSONObject(dataMap);
            String jsonData = jsonObject.toString(); 

            // socket connection to the aggregation server
            try (Socket socket = new Socket(serverName, port);
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send HTTP PUT request with the JSON data
                out.write("PUT /weather.json HTTP/1.1\r\n");
                out.write("User-Agent: ContentServer/1.0\r\n");
                out.write("Lamport-Clock: " + lamportClock.getValue() + "\r\n"); 
                out.write("Content-Type: application/json\r\n");
                out.write("Content-Length: " + jsonData.length() + "\r\n");
                out.write("\r\n");
                out.write(jsonData);
                out.flush(); 

                // Read the response from the server
                String statusLine = in.readLine(); 
                if (statusLine == null) {
                    System.err.println("No response from server.");
                    return;
                }

                String[] statusParts = statusLine.split(" ");
                if (statusParts.length < 2) {
                    System.err.println("Invalid response from server.");
                    return;
                }

                int statusCode;
                try {
                    statusCode = Integer.parseInt(statusParts[1]); // Extract status code
                } catch (NumberFormatException e) {
                    System.err.println("Invalid status code in response.");
                    return;
                }

                String headerLine;
                while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
          
                    if (headerLine.startsWith("Lamport-Clock: ")) {
                        try {
                            int receivedClock = Integer.parseInt(headerLine.substring(15).trim());
                            lamportClock.receiveAction(receivedClock);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid Lamport-Clock value in response.");
                        }
                    }
                }

                // Check the status code and print appropriate message
                if (statusCode == 200 || statusCode == 201) {
                    System.out.println("Data upload successful. Status code: " + statusCode);
                } else {
                    System.err.println("Error: Received status code " + statusCode);
                }

            }

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } finally {
            lamportClock.tick(); // Increment Lamport clock after operation
        }
    }

    // Method to parse the data file into a map of key-value pairs
    public static Map<String, String> parseFile(String filePath) {
        Map<String, String> dataMap = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath)); 

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) 
                continue; 

                // Split line into key and value at the first colon
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    dataMap.put(parts[0].trim(), parts[1].trim()); 
                } else {
                    System.err.println("Invalid line in data file: " + line); 
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading data file: " + e.getMessage());
        }
        return dataMap;
    }

    // class representing the Lamport Clock
    public static class LamportClock {
        private int clock = 0;

        // Method to increment the clock 
        public synchronized void tick() {
            clock++;
        }

        // Method to update clock based on received clock value 
        public synchronized void receiveAction(int receivedClock) {
            clock = Math.max(clock, receivedClock) + 1;
        }

        // Method to get the current clock value
        public synchronized int getValue() {
            return clock;
        }
    }
}
