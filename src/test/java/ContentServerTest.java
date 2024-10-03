//package test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.example.ContentServer;

import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ContentServerTest {

    private ContentServer.LamportClock lamportClock;

    @BeforeEach
    public void setup() {
        lamportClock = new ContentServer.LamportClock();
    }

    @Test
    public void testParseDataFile_ValidFile() throws IOException {
        String filePath = "test_data.txt";
        String content = "id: 12345\nname: Test\nlocation: Earth";
        Files.write(Paths.get(filePath), content.getBytes());

        Map<String, String> dataMap = ContentServer.parseFile(filePath);

        assertEquals(3, dataMap.size());
        assertEquals("12345", dataMap.get("id"));
        assertEquals("Test", dataMap.get("name"));
        assertEquals("Earth", dataMap.get("location"));

        Files.deleteIfExists(Paths.get(filePath));
    }

    @Test
    public void testParseDataFile_InvalidFile() throws IOException {
        String filePath = "invalid_data.txt";
        Files.write(Paths.get(filePath), "Invalid content for testing - testParseDataFile_InvalidFile in the content server".getBytes());
        if (Files.exists(Paths.get(filePath))) {
            System.out.println("File has been successfully created at " + filePath);
        }

        Map<String, String> dataMap = ContentServer.parseFile(filePath);

        assertTrue(dataMap.isEmpty());

        // Files.deleteIfExists(Paths.get(filePath));
    }

    @Test
    public void testLamportClock_Tick() {
        lamportClock.tick();
        assertEquals(1, lamportClock.getValue());

        lamportClock.tick();
        assertEquals(2, lamportClock.getValue());
    }

    @Test
    public void testLamportClock_ReceiveAction() {
        lamportClock.receiveAction(5);
        assertEquals(6, lamportClock.getValue());

        lamportClock.receiveAction(3);
        assertEquals(7, lamportClock.getValue());
    }

//    @Test
//    public void testMain_InvalidArguments() {
//        String[] args = {};
//
//        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
//        System.setOut(new PrintStream(outContent));
//
//        ContentServer.main(args);
//
//        assertTrue(outContent.toString().contains("Usage: java ContentServer <server:port> <data_file>"));
//    }

    @Test
    public void testMain_InvalidServerInfo() {
        String[] args = {"invalid-server-info", "data.txt"};

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outContent));

        ContentServer.main(args);

        assertTrue(outContent.toString().contains("Invalid server info. Expected format: <server>:<port>"));
    }

    private String sendHttpRequest(String method, String path, String body, Map<String, String> headers) throws IOException {
        try (Socket socket = new Socket("localhost", 4567)) {  // Assumes the server is running on localhost:4567
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            out.write(method + " " + path + " HTTP/1.1\r\n");
            out.write("Host: localhost\r\n");

            // Append headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                out.write(header.getKey() + ": " + header.getValue() + "\r\n");
            }

            // Send content if available
            if (body != null && !body.isEmpty()) {
                out.write("Content-Type: application/json\r\n");
                out.write("Content-Length: " + body.getBytes("UTF-8").length + "\r\n\r\n");
                out.write(body);
            } else {
                out.write("\r\n");
            }

            out.flush();

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append("\n");
            }

            return response.toString();
        }
    }


    @Test
    public void testPutWithInvalidJson() throws IOException {
        String invalidJsonData = "{\"id\":,\"temperature\":invalid}";

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outContent));

        String putResponse = sendHttpRequest("PUT", "/weather.json", invalidJsonData, new HashMap<>());
        assertTrue(putResponse.contains("HTTP/1.1 400 Bad Request"), "Server should respond with 400 Bad Request on invalid JSON");
    }

    @Test
    public void testServerStartupAndCommunication() {
        assertDoesNotThrow(() -> {
            Socket socket = new Socket("localhost", 4567);
            socket.close();
        }, "ContentServer did not start up or accept connections properly.");
    }

    @Test
    public void testInvalidJsonHandling() throws IOException {
        // Setup a socket to connect to the server assuming server is running on localhost:4567
        try (Socket socket = new Socket("localhost", 4567);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send an HTTP request with invalid JSON content
            writer.write("PUT /weather.json HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Content-Type: application/json\r\n");
            writer.write("Content-Length: 18\r\n\r\n");
            writer.write("{\"temp\":25, \"id\":}"); // Invalid JSON here
            writer.flush();

            // Read the response from the server
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                response.append(line);
            }

            // Check if the response contains the expected HTTP 400 error code
            assertTrue(response.toString().contains("HTTP/1.1 400 Bad Request"));
        }
    }

    @Test
    public void testPutOperationFromContentServer() throws Exception {
        String jsonData = "{\"id\":\"uniqueID\",\"data\":\"Test data\"}";
        String response = sendHttpRequest("PUT", "/weather.json", jsonData, new HashMap<>());
        assertTrue(response.contains("HTTP/1.1 200 OK") || response.contains("HTTP/1.1 201"), "Failed to PUT data");
    }


}