import java.io.*;
import java.net.*;
import java.util.*;

public class ContentServer {
    private static int lamportClock = 0;

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: ContentServer <server> <port> <file>");
            return;
        }

        String serverName = args[0];
        int port = Integer.parseInt(args[1]);
        String fileName = args[2];

        while (true) {
            lamportClock++;
            try (
                Socket socket = new Socket(serverName, port);
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                String jsonData = readWeatherDataFromFile(fileName);
                if (jsonData == null) {
                    System.out.println("Error reading weather data.");
                    Thread.sleep(10000); 
                    continue;
                }

                String request = "PUT /weather.json HTTP/1.1\r\n" +
                        "Host: " + serverName + "\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: " + jsonData.length() + "\r\n" +
                        "Lamport-Clock: " + lamportClock + "\r\n" +
                        "\r\n" +
                        jsonData;

                out.write(request);
                out.flush();

                String responseLine;
                while ((responseLine = in.readLine()) != null && !responseLine.isEmpty()) {
                    if (responseLine.startsWith("Lamport-Clock: ")) {
                        int serverLamportClock = Integer.parseInt(responseLine.substring("Lamport-Clock: ".length()));
                        lamportClock = Math.max(lamportClock, serverLamportClock) + 1;
                    }
                    System.out.println(responseLine);
                }

                Thread.sleep(10000); 

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                try {
                    Thread.sleep(10000); // Wait before retrying
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
    }

    private static String readWeatherDataFromFile(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            Map<String, String> dataMap = new HashMap<>();
            String line;
            while ((line = br.readLine()) != null) {
                int idx = line.indexOf(":");
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();
                    dataMap.put(key, value);
                }
            }
            return buildJSONString(dataMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String buildJSONString(Map<String, String> dataMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int count = 0;
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            sb.append("\"").append(entry.getKey()).append("\":\"");
            sb.append(entry.getValue()).append("\"");
            if (count < dataMap.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append("}");
        return sb.toString();
    }
}





