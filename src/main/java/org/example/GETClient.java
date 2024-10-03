package org.example;
import java.io.*;
import java.net.*;
import org.json.JSONObject;
import org.json.JSONException;

public class GETClient {
    private static final LamportClock lamportClock = new LamportClock();

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Incorrect; The Correct format is : java GETClient <server:port> [station_id]");
            return;
        }

        String serverInfo = args[0];
        String stationId = args.length >= 2 ? args[1] : null;

        // Parse server information
        String[] serverParts = serverInfo.replace("http://", "").split(":");
        if (serverParts.length != 2) {
            System.err.println("Invalid server information. Expected format: <server>:<port>");
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

        lamportClock.tick();

        try (Socket socket = new Socket(serverName, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send GET request
            out.write("GET /weather.json HTTP/1.1\r\n");
            out.write("User-Agent: GETClient/1.0\r\n");
            out.write("Lamport-Clock: " + lamportClock.getValue() + "\r\n");
            out.write("\r\n");
            out.flush();

            // Read response status line
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
                statusCode = Integer.parseInt(statusParts[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid status code in response.");
                return;
            }


            String headerLine;
            int contentLength = 0;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.startsWith("Lamport-Clock: ")) {
                    try {
                        int receivedClock = Integer.parseInt(headerLine.substring(15).trim());
                        lamportClock.receiveAction(receivedClock);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Lamport-Clock value in response.");
                    }
                } else if (headerLine.startsWith("Content-Length: ")) {
                    try {
                        contentLength = Integer.parseInt(headerLine.substring(16).trim());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Content-Length value in response.");
                    }
                }
            }

            char[] bodyChars = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = in.read(bodyChars, totalRead, contentLength - totalRead);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }
            String body = new String(bodyChars, 0, totalRead);

            if (statusCode == 200) {
            
                JSONObject jsonObject = new JSONObject(body);
                if (stationId != null) {
                    if (jsonObject.has(stationId)) {
                        printData(jsonObject.getJSONObject(stationId));
                    } else {
                        System.out.println("Station ID not found.");
                    }
                } else {
                    for (String id : jsonObject.keySet()) {
                        System.out.println("Station ID: " + id);
                        printData(jsonObject.getJSONObject(id));
                        System.out.println();
                    }
                }
            } else {
                System.out.println("Error: Received status code " + statusCode);
            }

        } catch (IOException | JSONException e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            lamportClock.tick();
        }
    }

    private static void printData(JSONObject weatherData) {
        for (String key : weatherData.keySet()) {
            System.out.println(key + ": " + weatherData.get(key));
        }
    }

    // LamportClock class
    private static class LamportClock {
        private int clock = 0;

        public synchronized void tick() {
            clock++;
        }

        public synchronized void receiveAction(int receivedClock) {
            clock = Math.max(clock, receivedClock) + 1;
        }

        public synchronized int getValue() {
            return clock;
        }
    }
}
