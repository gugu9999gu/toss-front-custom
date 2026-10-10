import dev.tossfront.deck.ConnectionTarget;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLHandshakeException;

/** Test the exact TLS socket factory used by the native Android client. */
public final class WirelessPinTest {
    private static void probe(int port, String pin) throws Exception {
        ConnectionTarget target = ConnectionTarget.wifi("192.168.1.10", port, pin);
        HttpsURLConnection connection = (HttpsURLConnection) new URL(target.baseUrl() + "/api/health").openConnection();
        target.secure(connection);
        // Only this fixture socket uses loopback; production accepts explicit private LAN addresses.
        try (SSLSocket socket = (SSLSocket) connection.getSSLSocketFactory().createSocket("127.0.0.1", port)) {
            socket.setSoTimeout(3000); socket.startHandshake();
            socket.getOutputStream().write(("GET /api/health HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!response.startsWith("HTTP/1.0 200") || !response.contains("FrontDeck")) throw new AssertionError(response);
        }
    }
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]); String fingerprint = args[1];
        javax.net.ssl.SSLSocketFactory defaults = HttpsURLConnection.getDefaultSSLSocketFactory();
        probe(port, fingerprint);
        String wrong = (fingerprint.startsWith("0") ? "1" : "0") + fingerprint.substring(1);
        try { probe(port, wrong); throw new AssertionError("Changed PC identity accepted"); }
        catch (SSLHandshakeException expected) {}
        if (defaults != HttpsURLConnection.getDefaultSSLSocketFactory()) throw new AssertionError("Global TLS changed");
        System.out.println("Verified native TLS pin; rejected changed identity before any HTTP request.");
    }
}
