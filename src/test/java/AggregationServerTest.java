import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.example.AggregationServer;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

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
}
