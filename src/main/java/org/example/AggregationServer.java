package org.example;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final long EXPIRATION_TIME = 300 * 100;
    private static final String OUTPUT_FILE = "weather_data.json";

    private static final Map<String, WeatherData> weatherDataMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> contentServerLastSeen = new ConcurrentHashMap<>();
    private static final LamportClock lamportClock = new LamportClock();

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Port Number is Invalid - Using default port " + DEFAULT_PORT);
            }
        }

        loadDataFromFile();

        Thread expirationThread = new Thread(new DataExpirationTask());
        expirationThread.setDaemon(true);
        expirationThread.start();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aggregation Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Error starting server on port " + port);
            e.printStackTrace();
        }
    }

    // Method to load data from the JSON file
    private static void loadDataFromFile() {
        synchronized (weatherDataMap) {
            File dataFile = new File(OUTPUT_FILE);
            File tempFile = new File(OUTPUT_FILE + ".tmp");
            File fileToLoad = null;

            if (tempFile.exists()) {
                System.out.println("Temporary data file found.");
                fileToLoad = tempFile;
            } else if (dataFile.exists()) {
                fileToLoad = dataFile;
            } else {
                System.out.println("No data file found. Starting with empty data.");
            }

            if (fileToLoad != null) {
                try {
                    String content = new String(Files.readAllBytes(fileToLoad.toPath()));
                    JSONObject jsonObject = new JSONObject(content);

                    for (String id : jsonObject.keySet()) {
                        JSONObject dataObject = jsonObject.getJSONObject(id);
                        WeatherData data = new WeatherData(dataObject);
                        weatherDataMap.put(id, data);
                        contentServerLastSeen.put(id, System.currentTimeMillis());
                    }
                    System.out.println("Data loaded from " + fileToLoad.getName());

                    if (fileToLoad.equals(tempFile)) {
                        try {
                            Files.move(tempFile.toPath(), dataFile.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                            System.out.println("Temporary file moved to " + OUTPUT_FILE);
                        } catch (IOException e) {
                            System.err.println("Failed to rename temporary file to data file after loading.");
                            e.printStackTrace();
                        }
                    }
                } catch (IOException | org.json.JSONException e) {
                    System.err.println("Failed to load data from " + fileToLoad.getName());
                    e.printStackTrace();
                }
            }
        }
    }

    // Method to save data to the JSON file
    private static void saveDataToFile() {
        synchronized (weatherDataMap) {
            JSONObject jsonObject = new JSONObject();
            for (WeatherData data : weatherDataMap.values()) {
                jsonObject.put(data.getId(), data.toJson());
            }

            File tempFile = new File(OUTPUT_FILE + ".tmp");
            File dataFile = new File(OUTPUT_FILE);

            try (FileWriter fileWriter = new FileWriter(tempFile)) {
                fileWriter.write(jsonObject.toString());
                fileWriter.flush();
                fileWriter.close();

                Files.move(tempFile.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                System.out.println("Data saved to " + OUTPUT_FILE );
            } catch (IOException e) {
                System.err.println("Failed to save data ");
                e.printStackTrace();

            }
        }
    }

    // ClientHandler class to handle client requests
    private static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            lamportClock.tick();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
                 BufferedWriter out = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream()))) {

                // Read the request line
                String requestLine = in.readLine();
                if (requestLine == null) {
                    return;
                }

                String[] requestParts = requestLine.split(" ");
                if (requestParts.length < 3) {
                    sendErrorResponse(out, 400, "Bad Request");
                    return;
                }

                String method = requestParts[0];
                String path = requestParts[1];


                Map<String, String> headers = new HashMap<>();
                String headerLine;
                while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                    String[] headerParts = headerLine.split(": ", 2);
                    if (headerParts.length == 2) {
                        headers.put(headerParts[0], headerParts[1]);
                    }
                }


                if (headers.containsKey("Lamport-Clock")) {
                    try {
                        int receivedClock = Integer.parseInt(headers.get("Lamport-Clock"));
                        lamportClock.receiveAction(receivedClock);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Lamport-Clock value.");
                    }
                }

                if ("PUT".equalsIgnoreCase(method) && "/weather.json".equals(path)) {
                    handlePutRequest(in, out, headers);
                } else if ("GET".equalsIgnoreCase(method) && "/weather.json".equals(path)) {
                    handleGetRequest(out);
                } else {
                    sendErrorResponse(out, 400, "Bad Request");
                }

            } catch (IOException e) {
                System.err.println("I/O error handling client request.");
                e.printStackTrace();
            } finally {
                lamportClock.tick();
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket.");
                    e.printStackTrace();
                }
            }
        }

        private void handlePutRequest(BufferedReader in, BufferedWriter out, Map<String, String> headers) throws IOException {
            int contentLength;
            try {
                contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
            } catch (NumberFormatException e) {
                sendErrorResponse(out, 411, "Length Required");
                return;
            }

            if (contentLength == 0) {
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }

            char[] bodyChars = new char[contentLength];
            int readChars = in.read(bodyChars);
            if (readChars != contentLength) {
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }
            String body = new String(bodyChars);

            try {
                JSONObject jsonObject = new JSONObject(body);
                String id = jsonObject.getString("id");
                WeatherData data = new WeatherData(jsonObject);

                // Update weather data map
                synchronized (weatherDataMap) {
                    boolean isNewData = !weatherDataMap.containsKey(id);
                    weatherDataMap.put(id, data);
                    contentServerLastSeen.put(id, System.currentTimeMillis());

                    // Save data to file
                    saveDataToFile();

                    // Send response
                    int statusCode = isNewData ? 201 : 200;
                    sendResponse(out, statusCode, "OK", "");
                }
            } catch (org.json.JSONException e) {
                sendErrorResponse(out, 400, "Bad Request");
            }
        }

        private void handleGetRequest(BufferedWriter out) throws IOException {
            JSONObject responseJson = new JSONObject();
            synchronized (weatherDataMap) {
                for (WeatherData data : weatherDataMap.values()) {
                    responseJson.put(data.getId(), data.toJson());
                }
            }
            String responseBody = responseJson.toString();
            sendResponse(out, 200, "OK", responseBody);
        }

        private void sendResponse(BufferedWriter out, int statusCode, String statusText, String body) throws IOException {
            lamportClock.tick();
            out.write("HTTP/1.1 " + statusCode + " " + statusText + "\r\n");
            out.write("Lamport-Clock: " + lamportClock.getValue() + "\r\n");
            out.write("Content-Length: " + body.length() + "\r\n");
            if (!body.isEmpty()) {
                out.write("Content-Type: application/json\r\n");
            }
            out.write("\r\n");
            if (!body.isEmpty()) {
                out.write(body);
            }
            out.flush();
        }

        private void sendErrorResponse(BufferedWriter out, int statusCode, String statusText) throws IOException {
            sendResponse(out, statusCode, statusText, "");
        }
    }


    private static class DataExpirationTask implements Runnable {
        @Override
        public void run() {
            while (true) {
                long currentTime = System.currentTimeMillis();
                boolean dataChanged = false;

                synchronized (weatherDataMap) {
                    Iterator<Map.Entry<String, Long>> iterator = contentServerLastSeen.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Long> entry = iterator.next();
                        String id = entry.getKey();
                        long lastSeen = entry.getValue();

                        if (currentTime - lastSeen > EXPIRATION_TIME) {
                            weatherDataMap.remove(id);
                            iterator.remove();
                            dataChanged = true;
                            System.out.println("Expired data from content server: " + id);
                        }
                    }


                    if (dataChanged) {
                        saveDataToFile();
                    }
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    System.err.println("Data expiration thread interrupted.");
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // WeatherData class
    private static class WeatherData {
        private final JSONObject jsonData;

        public WeatherData(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        public String getId() {
            return jsonData.getString("id");
        }

        public JSONObject toJson() {
            return jsonData;
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






//package org.example;
//
//import java.io.*;
//import java.net.ServerSocket;
//import java.net.Socket;
//import java.nio.file.*;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import org.json.JSONObject;
//
///**
// * This class represents an aggregation server that collects and manages weather data.
// */
//public class AggregationServer {
//    private static final int DEFAULT_PORT = 4567;
//    private static final long DATA_EXPIRATION_INTERVAL = 300 * 1000; // data expiration time in milliseconds
//    private static final String WEATHER_DATA_FILE = "weather_data.json";
//
//    private static final Map<String, WeatherData> weatherDataMap = new ConcurrentHashMap<>();
//    private static final Map<String, Long> lastContactTime = new ConcurrentHashMap<>();
//    private static final LamportClock clock = new LamportClock();
//
//    public static void main(String[] args) {
//        int port = DEFAULT_PORT;
//        try {
//            if (args.length >= 1) {
//                port = Integer.parseInt(args[0]);
//            }
//        } catch (NumberFormatException e) {
//            System.err.println("Invalid port number provided. Using default port " + DEFAULT_PORT);
//        }
//
//        loadWeatherData();
//
//        Thread cleanupThread = new Thread(new DataCleanupTask());
//        cleanupThread.setDaemon(true);
//        cleanupThread.start();
//
//        try (ServerSocket serverSocket = new ServerSocket(port)) {
//            System.out.println("Aggregation Server is up and running on port " + port);
//
//            while (true) {
//                try {
//                    Socket clientSocket = serverSocket.accept();
//                    Thread clientThread = new Thread(new ClientRequestHandler(clientSocket));
//                    clientThread.start();
//                } catch (IOException e) {
//                    System.err.println("Error accepting client connection.");
//                    e.printStackTrace();
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("Failed to start server on port " + port);
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Loads weather data from a file, or initializes empty data if no file exists.
//     */
//    private static void loadWeatherData() {
//        synchronized (weatherDataMap) {
//            File file = new File(WEATHER_DATA_FILE);
//            File tempFile = new File(WEATHER_DATA_FILE + ".tmp");
//
//            File fileToUse = file.exists() ? file : (tempFile.exists() ? tempFile : null);
//            if (fileToUse == null) {
//                System.out.println("No existing data file found. Starting fresh.");
//                return;
//            }
//
//            try {
//                String content = new String(Files.readAllBytes(fileToUse.toPath()));
//                JSONObject data = new JSONObject(content);
//                data.keySet().forEach(id -> {
//                    WeatherData weatherData = new WeatherData(data.getJSONObject(id));
//                    weatherDataMap.put(id, weatherData);
//                    lastContactTime.put(id, System.currentTimeMillis());
//                });
//                System.out.println("Loaded data from " + fileToUse.getName());
//
//                if (tempFile.exists()) {
//                    Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
//                }
//            } catch (IOException | org.json.JSONException e) {
//                System.err.println("Failed to load data from file.");
//                e.printStackTrace();
//            }
//        }
//    }
//
//    /**
//     * Saves the current weather data to a file.
//     */
//    private static void saveWeatherData() {
//        synchronized (weatherDataMap) {
//            JSONObject dataToSave = new JSONObject();
//            weatherDataMap.forEach((id, weatherData) -> dataToSave.put(id, weatherData.toJson()));
//
//            File tempFile = new File(WEATHER_DATA_FILE + ".tmp");
//            try (FileWriter writer = new FileWriter(tempFile)) {
//                writer.write(dataToSave.toString());
//                writer.flush();
//
//                File finalFile = new File(WEATHER_DATA_FILE);
//                Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
//                System.out.println("Weather data saved to " + WEATHER_DATA_FILE);
//            } catch (IOException e) {
//                System.err.println("Error saving weather data.");
//                e.printStackTrace();
//            }
//        }
//    }
//
//    /**
//     * Handles client requests in a dedicated thread.
//     */
//    private static class ClientRequestHandler implements Runnable {
//        private Socket socket;
//
//        public ClientRequestHandler(Socket socket) {
//            this.socket = socket;
//        }
//
//        public void run() {
//            clock.increment();
//            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
//
//                String line = reader.readLine();
//                if (line == null) {
//                    return;
//                }
//
//                // Process the request
//                handleRequest(line, reader, writer);
//
//            } catch (IOException e) {
//                System.err.println("I/O error while processing client request.");
//                e.printStackTrace();
//            } finally {
//                clock.increment();
//                try {
//                    socket.close();
//                } catch (IOException e) {
//                    System.err.println("Failed to close client socket.");
//                    e.printStackTrace();
//                }
//            }
//        }
//
//        /**
//         * Process the incoming request and respond accordingly.
//         */
//        private void handleRequest(String requestLine, BufferedReader reader, BufferedWriter writer) throws IOException {
//            // Placeholder for request processing logic
//        }
//    }
//
//    /**
//     * Periodically checks for expired data and removes it.
//     */
//    private static class DataCleanupTask implements Runnable {
//        public void run() {
//            while (true) {
//                try {
//                    Thread.sleep(5000); // Sleep for 5 seconds before checking again
//                    cleanUpExpiredData();
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    return;
//                }
//            }
//        }
//
//        private void cleanUpExpiredData() {
//            long currentTime = System.currentTimeMillis();
//            synchronized (weatherDataMap) {
//                Iterator<Map.Entry<String, Long>> iterator = lastContactTime.entrySet().iterator();
//                while (iterator.hasNext()) {
//                    Map.Entry<String, Long> entry = iterator.next();
//                    if (currentTime - entry.getValue() > DATA_EXPIRATION_INTERVAL) {
//                        weatherDataMap.remove(entry.getKey());
//                        iterator.remove();
//                        System.out.println("Removed expired data for content server: " + entry.getKey());
//                    }
//                }
//            }
//        }
//    }
//
//    /**
//     * Represents weather data with a JSON object.
//     */
//    private static class WeatherData {
//        private JSONObject data;
//
//        public WeatherData(JSONObject data) {
//            this.data = data;
//        }
//
//        public JSONObject toJson() {
//            return data;
//        }
//    }
//
//    /**
//     * Manages increments to ensure proper synchronization across distributed components.
//     */
//    private static class LamportClock {
//        private int value = 0;
//
//        public synchronized void increment() {
//            value++;
//        }
//
//        public synchronized void adjust(int receivedValue) {
//            value = Math.max(value, receivedValue) + 1;
//        }
//
//        public synchronized int currentValue() {
//            return value;
//        }
//    }
//}
