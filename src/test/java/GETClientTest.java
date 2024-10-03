//package org.assignment2;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.example.GETClient;
import org.junit.jupiter.api.Test;


public class GETClientTest {

    @Test
    public void testMain_NoArguments() {

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        GETClient.main(new String[]{});

        assertTrue(outContent.toString().contains("Correct format: java GETClient <server:port> [station_id]"));

        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
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
    public void testServerStartupAndCommunication() {
        assertDoesNotThrow(() -> {
            Socket socket = new Socket("localhost", 4567);
            socket.close();
        }, "Client did not start up or accept connections properly.");
    }

    @Test
    public void testMain_InvalidServerInfo() {

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));


        GETClient.main(new String[]{"invalid-server-info"});

        assertTrue(errContent.toString().contains("Invalid server information. Expected format: <server>:<port>"));


        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
    }

    @Test
    public void testMain_InvalidPort() {

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        GETClient.main(new String[]{"localhost:invalid-port"});

        assertTrue(errContent.toString().contains("Invalid port number."));


        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
    }

    @Test
    public void testMultipleGetRequests() throws InterruptedException, ExecutionException {
        ExecutorService service = Executors.newFixedThreadPool(5);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                String response = sendHttpRequest("GET", "/weather.json", null, new HashMap<>());
                return response.contains("HTTP/1.1 200 OK");
            });
        }
        List<Future<Boolean>> results = service.invokeAll(tasks);
        for (Future<Boolean> result : results) {
            assertTrue(result.get(), "GET operation failed for one or more clients");
        }
        service.shutdown();
    }

}



//import java.io.ByteArrayOutputStream;
//import java.io.FileDescriptor;
//import java.io.FileOutputStream;
//import java.io.PrintStream;
//
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import org.junit.jupiter.api.Test;
//
//import org.example.GETClient;
//
//public class GETClientTest {
//
//    @Test
//    public void testMain_NoArguments() {
//
//        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
//        System.setOut(new PrintStream(outContent));
//
//        GETClient.main(new String[]{});
//
//        assertTrue(outContent.toString().contains("Correct format: java GETClient <server:port> [station_id]"));
//
//        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
//    }
//
//    @Test
//    public void testMain_InvalidServerInfo() {
//
//        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
//        System.setErr(new PrintStream(errContent));
//
//
//        GETClient.main(new String[]{"invalid-server-info"});
//
//        assertTrue(errContent.toString().contains("Invalid server information. Expected format: <server>:<port>"));
//
//
//        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
//    }
//
//    @Test
//    public void testMain_InvalidPort() {
//
//        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
//        System.setErr(new PrintStream(errContent));
//
//        GETClient.main(new String[]{"localhost:invalid-port"});
//
//        assertTrue(errContent.toString().contains("Invalid port number."));
//
//
//        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
//    }
//}
