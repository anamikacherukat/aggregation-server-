import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class AggregationServer {
    private static int port = 4567;
    private static final String filePath = "weather_data.json";
    private static int lamportClock = 0;
    private static final Object lock = new Object();
    private static Map<String, Long> contentServersLastContact = new HashMap<>();
    private static final int TIMEOUT = 30 * 1000; // 30 seconds

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);

        // Start a thread to clean old entries
        new Thread(AggregationServer::cleanOldEntries).start();

        // Restore data from file
        restoreDataFromFile();

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(() -> handleRequest(clientSocket)).start();
        }
    }

    private static void handleRequest(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                clientSocket.close();
                return;
            }
            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];

            // Read headers
            Map<String, String> headers = new HashMap<>();
            String line;
            while (!(line = in.readLine()).isEmpty()) {
                int idx = line.indexOf(": ");
                if (idx > 0) {
                    headers.put(line.substring(0, idx), line.substring(idx + 2));
                }
            }

            // Update Lamport clock
            int receivedLamport = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            synchronized (lock) {
                lamportClock = Math.max(lamportClock, receivedLamport) + 1;
            }

            if (method.equals("GET")) {
                handleGETRequest(out);
            } else if (method.equals("PUT")) {
                handlePUTRequest(in, out, headers, clientSocket.getInetAddress().getHostAddress());
            } else {
                sendResponse(out, "400 Bad Request", "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleGETRequest(BufferedWriter out) throws IOException {
        String jsonData;
        synchronized (lock) {
            jsonData = getWeatherDataAsJSON();
        }

        // Send Lamport clock in response header
        String responseHeaders = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + jsonData.length() + "\r\n" +
                "Lamport-Clock: " + lamportClock + "\r\n" +
                "\r\n";

        out.write(responseHeaders);
        out.write(jsonData);
        out.flush();
    }

    private static void handlePUTRequest(BufferedReader in, BufferedWriter out, Map<String, String> headers, String clientAddress) {
        try {
            int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
            if (contentLength == 0) {
                sendResponse(out, "204 No Content", "");
                return;
            }

            char[] bodyChars = new char[contentLength];
            int read = in.read(bodyChars);
            if (read != contentLength) {
                sendResponse(out, "400 Bad Request", "");
                return;
            }
            String body = new String(bodyChars);

            // Parse JSON data
            Map<String, String> weatherData = parseJSON(body);
            if (weatherData == null) {
                sendResponse(out, "500 Internal Server Error", "");
                return;
            }

            // Store weather data
            synchronized (lock) {
                storeWeatherData(weatherData, clientAddress);
                lamportClock++;
            }

            // Update last contact time
            contentServersLastContact.put(clientAddress, System.currentTimeMillis());

            sendResponse(out, "200 OK", "");

        } catch (IOException e) {
            e.printStackTrace();
            try {
                sendResponse(out, "500 Internal Server Error", "");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    private static void sendResponse(BufferedWriter out, String status, String body) throws IOException {
        String response = "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "Lamport-Clock: " + lamportClock + "\r\n" +
                "\r\n" +
                body;
        out.write(response);
        out.flush();
    }

    private static void storeWeatherData(Map<String, String> weatherData, String clientAddress) throws IOException {
        // Read existing data
        List<Map<String, String>> dataList = loadDataList();

        // Remove existing data from the same content server
        dataList.removeIf(data -> clientAddress.equals(data.get("clientAddress")));

        // Add new data
        weatherData.put("clientAddress", clientAddress);
        dataList.add(weatherData);

        // Save data back to file
        saveDataList(dataList);
    }

    private static List<Map<String, String>> loadDataList() {
        List<Map<String, String>> dataList = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) {
            return dataList;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                Map<String, String> data = parseJSON(line);
                if (data != null) {
                    dataList.add(data);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return dataList;
    }

    private static void saveDataList(List<Map<String, String>> dataList) throws IOException {
        File tempFile = new File(filePath + ".tmp");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile))) {
            for (Map<String, String> data : dataList) {
                String jsonString = buildJSONString(data);
                bw.write(jsonString);
                bw.newLine();
            }
        }

        // Atomically replace the old file with the new file
        Path source = tempFile.toPath();
        Path target = new File(filePath).toPath();
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String getWeatherDataAsJSON() {
        List<Map<String, String>> dataList = loadDataList();
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < dataList.size(); i++) {
            String jsonString = buildJSONString(dataList.get(i));
            sb.append(jsonString);
            if (i < dataList.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static Map<String, String> parseJSON(String jsonString) {
        Map<String, String> dataMap = new HashMap<>();
        jsonString = jsonString.trim();
        if (jsonString.startsWith("{") && jsonString.endsWith("}")) {
            jsonString = jsonString.substring(1, jsonString.length() - 1);
            String[] fields = jsonString.split(",");
            for (String field : fields) {
                String[] keyValue = field.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim().replace("\"", "");
                    dataMap.put(key, value);
                }
            }
            return dataMap;
        } else {
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

    private static void cleanOldEntries() {
        while (true) {
            try {
                Thread.sleep(5000); // Check every 5 seconds
                long currentTime = System.currentTimeMillis();
                List<Map<String, String>> dataList;
                synchronized (lock) {
                    dataList = loadDataList();
                    boolean updated = false;
                    Iterator<Map<String, String>> iterator = dataList.iterator();
                    while (iterator.hasNext()) {
                        Map<String, String> data = iterator.next();
                        String clientAddress = data.get("clientAddress");
                        Long lastContact = contentServersLastContact.get(clientAddress);
                        if (lastContact == null || (currentTime - lastContact) > TIMEOUT) {
                            iterator.remove();
                            contentServersLastContact.remove(clientAddress);
                            updated = true;
                        }
                    }
                    if (updated) {
                        saveDataList(dataList);
                    }
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void restoreDataFromFile() {
        // Data is loaded on demand, no special action needed
    }
}






// import java.io.*;
// import java.net.*;
// import java.util.*;
// import org.json.JSONObject;

// public class AggregationServer {
//     private static int port = 4567;
//     private static final String filePath = "weather_data.json";
//     private static int lamportClock = 0;

//     public static void main(String[] args) throws IOException {
//         if (args.length > 0) {
//             port = Integer.parseInt(args[0]);
//         }
//         ServerSocket serverSocket = new ServerSocket(port);
//         System.out.println("Server started on port " + port);

//         while (true) {
//             Socket clientSocket = serverSocket.accept();
//             new Thread(() -> handleRequest(clientSocket)).start();
//         }
//     }

//     private static void handleRequest(Socket clientSocket) {
//         try (
//             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
//         ) {
//             String requestLine = in.readLine();
//             if (requestLine == null || requestLine.isEmpty()) {
//                 return;
//             }
//             String[] requestParts = requestLine.split(" ");
//             String method = requestParts[0];

//             // Read headers
//             Map<String, String> headers = new HashMap<>();
//             String line;
//             while (!(line = in.readLine()).isEmpty()) {
//                 int idx = line.indexOf(": ");
//                 if (idx > 0) {
//                     headers.put(line.substring(0, idx), line.substring(idx + 2));
//                 }
//             }

//             if (method.equals("GET")) {
//                 handleGETRequest(out);
//             } else if (method.equals("PUT")) {
//                 handlePUTRequest(in, out, headers);
//             } else {
//                 out.println("HTTP/1.1 400 Bad Request");
//                 out.println();
//             }
//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }

//     private static void handleGETRequest(PrintWriter out) throws IOException {
//         lamportClock++;
//         String jsonData = getWeatherDataAsJSON();

//         out.println("HTTP/1.1 200 OK");
//         out.println("Content-Type: application/json");
//         out.println("Content-Length: " + jsonData.length());
//         out.println();
//         out.println(jsonData);
//     }

//     private static void handlePUTRequest(BufferedReader in, PrintWriter out, Map<String, String> headers) {
//         try {
//             lamportClock++;
//             int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));

//             char[] body = new char[contentLength];
//             int totalRead = 0;
//             while (totalRead < contentLength) {
//                 int read = in.read(body, totalRead, contentLength - totalRead);
//                 if (read == -1) break;
//                 totalRead += read;
//             }

//             String jsonData = new String(body);
//             JSONObject weatherData = new JSONObject(jsonData);
//             storeWeatherData(weatherData);

//             out.println("HTTP/1.1 200 OK");
//             out.println();
//         } catch (Exception e) {
//             out.println("HTTP/1.1 500 Internal Server Error");
//             out.println();
//             e.printStackTrace();
//         }
//     }

//     private static void storeWeatherData(JSONObject weatherData) throws IOException {
//         try (FileWriter file = new FileWriter(filePath, true)) {
//             file.write(weatherData.toString() + "\n");
//         }
//     }

//     private static String getWeatherDataAsJSON() throws IOException {
//         StringBuilder jsonData = new StringBuilder();
//         try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
//             String line;
//             while ((line = br.readLine()) != null) {
//                 jsonData.append(line).append("\n");
//             }
//         }
//         return jsonData.toString();
//     }
// }


// // import java.io.*;
// // import java.net.*;
// // import java.util.*;
// // import org.json.JSONObject;


// // public class AggregationServer {
// //     private static int port = 4567;
// //     private static final String filePath = "weather_data.json";
// //     private static int lamportClock = 0;
// //     private static Map<String, Long> contentServersLastContact = new HashMap<>();
// //     private static final int TIMEOUT = 30 * 1000; // 30 seconds

// //     public static void main(String[] args) throws IOException {
// //         if (args.length > 0) {
// //             port = Integer.parseInt(args[0]);
// //         }
// //         ServerSocket serverSocket = new ServerSocket(port);
// //         System.out.println("Server started on port " + port);
// //         restoreDataFromFile();
        
// //         new Thread(AggregationServer::cleanOldEntries).start();

// //         while (true) {
// //             Socket clientSocket = serverSocket.accept();
// //             new Thread(() -> handleRequest(clientSocket)).start();
// //         }
// //     }

// //     private static synchronized void handleRequest(Socket clientSocket) {
// //         try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
// //              PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
// //             String inputLine;
// //             StringBuilder request = new StringBuilder();
// //             while (!(inputLine = in.readLine()).isEmpty()) {
// //                 request.append(inputLine).append("\n");
// //             }

// //             if (request.toString().startsWith("GET")) {
// //                 lamportClock++;
// //                 out.println("HTTP/1.1 200 OK");
// //                 out.println("Content-Type: application/json");
// //                 out.println();
// //                 out.println(getWeatherDataAsJSON());
// //             } else if (request.toString().startsWith("PUT")) {
// //                 lamportClock++;
// //                 handlePUTRequest(in, out);
// //             } else {
// //                 out.println("HTTP/1.1 400 Bad Request");
// //             }
// //         } catch (IOException e) {
// //             e.printStackTrace();
// //         }
// //     }

// //     private static void handlePUTRequest(BufferedReader in, PrintWriter out) {
// //         try {
// //             StringBuilder jsonData = new StringBuilder();
// //             String inputLine;
// //             while (!(inputLine = in.readLine()).isEmpty()) {
// //                 jsonData.append(inputLine);
// //             }
            
// //             JSONObject weatherData = new JSONObject(jsonData.toString());
// //             storeWeatherData(weatherData);

// //             out.println("HTTP/1.1 200 OK");

// //         } catch (Exception e) {
// //             out.println("HTTP/1.1 500 Internal Server Error");
// //         }
// //     }

// //     private static void storeWeatherData(JSONObject weatherData) throws IOException {
// //         try (FileWriter file = new FileWriter(filePath, true)) {
// //             file.write(weatherData.toString() + "\n");
// //         }
// //     }

// //     private static String getWeatherDataAsJSON() throws IOException {
// //         StringBuilder jsonData = new StringBuilder();
// //         try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
// //             String line;
// //             while ((line = br.readLine()) != null) {
// //                 jsonData.append(line).append("\n");
// //             }
// //         }
// //         return jsonData.toString();
// //     }

// //     private static void restoreDataFromFile() {
// //         // Implement data restoration in case of server crash or restart.
// //     }

// //     private static void cleanOldEntries() {
// //         while (true) {
// //             try {
// //                 Thread.sleep(TIMEOUT);
// //                 // Logic to remove old content server entries
// //             } catch (InterruptedException e) {
// //                 e.printStackTrace();
// //             }
// //         }
// //     }
// // }
