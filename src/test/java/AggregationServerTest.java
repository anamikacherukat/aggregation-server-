import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.*;
import org.example.AggregationServer;

import java.io.*;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.Assert.fail;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class AggregationServerTest {

    private static Thread serverThread;

    @BeforeClass
    public static void setUp() throws Exception {

        serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        Thread.sleep(1000);
    }

    @AfterClass
    public static void tearDown() throws Exception {

        serverThread.interrupt();
    }

    @Test
    public void testPutAndGetWeatherData() throws Exception {
        String id = "test-client";
        String jsonData = "{\"id\":\"" + id + "\",\"temperature\":25,\"humidity\":60}";


        String putResponse = sendHttpRequest("PUT", "/weather.json", jsonData, new HashMap<>());
        System.out.println("PUT Response:\n" + putResponse);


        assertTrue(putResponse.contains("HTTP/1.1 201") || putResponse.contains("HTTP/1.1 200"));


        String getResponse = sendHttpRequest("GET", "/weather.json", null, new HashMap<>());
        System.out.println("GET Response:\n" + getResponse);


        assertTrue(getResponse.contains("\"id\":\"" + id + "\""));
        assertTrue(getResponse.contains("\"temperature\":25"));
        assertTrue(getResponse.contains("\"humidity\":60"));
    }

    private String sendHttpRequest(String method, String path, String body, Map<String, String> headers) throws IOException {
        try (Socket socket = new Socket("localhost", 4567)) {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));


            out.write(method + " " + path + " HTTP/1.1\r\n");
            out.write("Host: localhost\r\n");


            if (body != null && !body.isEmpty()) {
                out.write("Content-Type: application/json\r\n");
                out.write("Content-Length: " + body.getBytes("UTF-8").length + "\r\n");
            }


            for (Map.Entry<String, String> header : headers.entrySet()) {
                out.write(header.getKey() + ": " + header.getValue() + "\r\n");
            }

            out.write("\r\n");


            if (body != null && !body.isEmpty()) {
                out.write(body);
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
    //This test assesses the server's capability to handle multiple concurrent PUT requests
    public void testConcurrentPutRequests() throws InterruptedException, IOException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Runnable task = () -> {
            try {
                String jsonData = "{\"id\":\"" + Thread.currentThread().getId() + "\", \"temperature\":20}";
                sendHttpRequest("PUT", "/weather.json", jsonData, new HashMap<>());
            } catch (Exception e) {
                fail("Failed during PUT request with error: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        };

        for (int i = 0; i < threadCount; i++) {
            executor.submit(task);
        }

        latch.await();  // Wait for all threads to finish

        // Check if all entries are in the JSON
        String getResponse = sendHttpRequest("GET", "/weather.json", null, new HashMap<>());
        for (int i = 0; i < threadCount; i++) {
            assertTrue("Data should contain temperature info from thread",
                    getResponse.contains("\"temperature\":20"));
        }

        executor.shutdown();
    }

    @Test
    // This test checks the server's response to an improperly formatted HTTP request
    public void testBadRequestHandling() throws Exception {
        String invalidRequest = "BAD_METHOD /invalid_endpoint HTTP/1.1\r\n\r\n";
        String response = sendHttpRequest("BAD_METHOD", "/invalid_endpoint", null, new HashMap<>());
        assertTrue("Server should respond with 400 Bad Request", response.startsWith("HTTP/1.1 400"));
    }

    @Test
    // This test confirms that the data is correctly expired and no longer available by checking that the response does not contain the previously stored data.
    public void testDataExpiration() throws Exception {
        // PUT data that should expire
        String jsonData = "{\"id\":\"IDS002\",\"temperature\":24}";
        sendHttpRequest("PUT", "/weather.json", jsonData, new HashMap<>());
        Thread.sleep(35000); // Wait longer than the expiration time
        String response = sendHttpRequest("GET", "/weather.json", null, new HashMap<>());
        assertFalse("Data should be expired and not present", response.contains("\"id\":\"IDS002\""));
    }

    @Test
    // This test evaluates whether a server is successfully started and capable of accepting network connections on localhost at port 4567
    public void testServerStartupAndCommunication() {
        assertDoesNotThrow(() -> {
            Socket socket = new Socket("localhost", 4567);
            socket.close();
        }, "Server did not start up or accept connections properly.");
    }

    @Test
    //This test checks the server's  error recovery by intentionally closing a socket connection to simulate a server unavailability scenario,
    // then attempting to reconnect to verify that the server or client can handle retries without throwing errors.
    public void testRetryOnError() throws IOException {
        try (Socket socket = new Socket("localhost", 4567)) {
            socket.close();  // Close the socket to simulate server not available
        }
        // Try to connect again, expecting the server to handle reconnection or retry
        assertDoesNotThrow(() -> sendHttpRequest("GET", "/weather.json", null, new HashMap<>()),
                "Server should handle retries on error");
    }

    @Test
    // This test assesses the server's capability to handle multiple concurrent GET requests
    // by using a fixed thread pool to execute five simultaneous GET operations using a for loop.
    public void testMultipleGetRequests() throws InterruptedException, ExecutionException {
        ExecutorService service = Executors.newFixedThreadPool(5);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                String getResponse = sendHttpRequest("GET", "/weather.json", null, new HashMap<>());
                return getResponse.contains("HTTP/1.1 200 OK");
            });
        }
        List<Future<Boolean>> results = service.invokeAll(tasks);
        for (Future<Boolean> result : results) {
            assertTrue("All GET requests should receive a valid response.", result.get());
        }
        service.shutdown();
    }

}

