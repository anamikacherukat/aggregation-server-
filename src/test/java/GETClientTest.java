import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.example.GETClient;

public class GETClientTest {

    @Test
    public void testMain_NoArguments() {

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        GETClient.main(new String[]{});

        assertTrue(outContent.toString().contains("Correct format: java GETClient <server:port> [station_id]"));

        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
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
}
