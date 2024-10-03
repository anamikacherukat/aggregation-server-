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

        Map<String, String> dataMap = ContentServer.parseDataFile(filePath);

        assertEquals(3, dataMap.size());
        assertEquals("12345", dataMap.get("id"));
        assertEquals("Test", dataMap.get("name"));
        assertEquals("Earth", dataMap.get("location"));

        Files.deleteIfExists(Paths.get(filePath));
    }

    @Test
    public void testParseDataFile_InvalidFile() throws IOException {
        String filePath = "invalid_data.txt";
        Files.write(Paths.get(filePath), "invalid-content".getBytes());

        Map<String, String> dataMap = ContentServer.parseDataFile(filePath);

        assertTrue(dataMap.isEmpty());

        Files.deleteIfExists(Paths.get(filePath));
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


}
