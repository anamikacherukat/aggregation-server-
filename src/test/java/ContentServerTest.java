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
    // Writes some data to a file. Parses this data back into the application to verify the parsing logic.
    // Also deletes the file after checking
    public void testParseValidFile() throws IOException {
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
    // Writes some content onto an invalid_data file and then calls the parseFile function in contentServer.
    // An empty map if returned would indicate that the parseFile method correctly identified the file's contents as invalid and did not store any erroneous data.
    public void testParseData_FileInvalidity() throws IOException {
        String filePath = "invalid_data.txt";
        Files.write(Paths.get(filePath), "Invalid content for testing - testParseData_FileInvalidity in the content server".getBytes());
        if (Files.exists(Paths.get(filePath))) {
            System.out.println("File has been successfully created at " + filePath);
        }

        Map<String, String> dataMap = ContentServer.parseFile(filePath);

        assertTrue(dataMap.isEmpty());

         Files.deleteIfExists(Paths.get(filePath));
    }

    @Test
    // Tests whether the lamport clock correctly begins from 0 and increments by 1 tick
    public void testLamportClock_Increment() {
        lamportClock.tick();
        assertEquals(1, lamportClock.getValue());

        lamportClock.tick();
        assertEquals(2, lamportClock.getValue());
    }

    @Test
    // The LamportClock correctly updates when a higher timestamp is received.
    public void testLamportClock_Updates() {
        lamportClock.receiveAction(5);
        assertEquals(6, lamportClock.getValue());

        lamportClock.receiveAction(3);
        assertEquals(7, lamportClock.getValue());
    }

    @Test
    // The test checks whether the captured output in outContent contains the specific error message - Invalid server info.
    public void testServer_Invalidity() {
        String[] args = {"invalid-server-info", "data.txt"};

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outContent));

        ContentServer.main(args);

        assertTrue(outContent.toString().contains("Invalid server info. Expected format: <server>:<port>"));
    }

// The sendHttpRequest method establishes a socket connection to a server, constructs and sends HTTP requests manually, and retrieves responses
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
    // This test checks the response of a server when it receives a PUT request with invalid JSON data, expecting a "400 Bad Request" status. 
    public void testInvalidJson() throws IOException {
        String invalidJsonData = "{\"id\":,\"temperature\":invalid}";

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outContent));

        String putResponse = sendHttpRequest("PUT", "/weather.json", invalidJsonData, new HashMap<>());
        assertTrue(putResponse.contains("HTTP/1.1 400 Bad Request"), "Server should respond with 400 Bad Request on invalid JSON");
    }

    @Test
    //This test verifies that the ContentServer successfully starts up and can accept TCP/IP connections on localhost at port 4567 without throwing any exceptions.
    public void testServerStart() {
        assertDoesNotThrow(() -> {
            Socket socket = new Socket("localhost", 4567);
            socket.close();
        }, "ContentServer did not start up or accept connections properly.");
    }

    @Test
    // This test method checks if a server properly handles a PUT request containing incorrect JSON by verifying that it responds with an HTTP 400 Bad Request error
    public void testIncorrectJson() throws IOException {
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
    // This test  evaluates the response of the server when receiving a PUT request with valid JSON data from a content server.
    public void testPutOperationFromContentServer() throws Exception {
        String jsonData = "{\"id\":\"uniqueID\",\"data\":\"Test data\"}";
        String response = sendHttpRequest("PUT", "/weather.json", jsonData, new HashMap<>());
        assertTrue(response.contains("HTTP/1.1 200 OK") || response.contains("HTTP/1.1 201"), "Failed to PUT data");
    }


}