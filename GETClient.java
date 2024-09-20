import java.io.*;
import java.net.*;

public class GETClient {
    private static int lamportClock = 0;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: GETClient <server> <port>");
            return;
        }

        String serverName = args[0];
        int port = Integer.parseInt(args[1]);

        lamportClock++;
        try (
            Socket socket = new Socket(serverName, port);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Send GET request
            String request = "GET /weather.json HTTP/1.1\r\n" +
                    "Host: " + serverName + "\r\n" +
                    "Lamport-Clock: " + lamportClock + "\r\n" +
                    "\r\n";
            out.write(request);
            out.flush();

            // Read response
            String responseLine;
            StringBuilder responseBody = new StringBuilder();
            boolean headersEnded = false;
            while ((responseLine = in.readLine()) != null) {
                if (responseLine.isEmpty()) {
                    headersEnded = true;
                    continue;
                }
                if (headersEnded) {
                    responseBody.append(responseLine);
                } else {
                    if (responseLine.startsWith("Lamport-Clock: ")) {
                        int serverLamportClock = Integer.parseInt(responseLine.substring("Lamport-Clock: ".length()));
                        lamportClock = Math.max(lamportClock, serverLamportClock) + 1;
                    }
                }
            }

            // Display weather data
            String jsonData = responseBody.toString();
            if (jsonData.isEmpty()) {
                System.out.println("No data received.");
            } else {
                displayWeatherData(jsonData);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void displayWeatherData(String jsonData) {
        jsonData = jsonData.trim();
        if (jsonData.startsWith("[") && jsonData.endsWith("]")) {
            jsonData = jsonData.substring(1, jsonData.length() - 1); // Remove [ and ]
            String[] dataEntries = jsonData.split("\\},\\{");
            for (String entry : dataEntries) {
                entry = entry.replace("{", "").replace("}", "");
                String[] fields = entry.split(",");
                for (String field : fields) {
                    String[] keyValue = field.split(":", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].replace("\"", "").trim();
                        String value = keyValue[1].replace("\"", "").trim();
                        System.out.println(key + ": " + value);
                    }
                }
                System.out.println("--------------------------");
            }
        } else {
            System.out.println("Invalid data format.");
        }
    }
}