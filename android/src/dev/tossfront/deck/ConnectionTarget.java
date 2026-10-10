package dev.tossfront.deck;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Per-connection certificate pinning; no global TLS or hostname overrides. */
public final class ConnectionTarget {
    public final boolean wireless;
    public final String host, fingerprint;
    public final int port;
    private ConnectionTarget(boolean wireless, String host, int port, String fingerprint) {
        this.wireless = wireless; this.host = host; this.port = port; this.fingerprint = fingerprint;
    }
    public static ConnectionTarget usb() { return new ConnectionTarget(false, "127.0.0.1", 38765, ""); }
    public static boolean validAddress(String host, int port) {
        if (host == null || port < 1024 || port > 65535 || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts = host.split("\\."); int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].length() > 1 && parts[i].startsWith("0")) return false;
            n[i] = Integer.parseInt(parts[i]); if (n[i] > 255) return false;
        }
        return n[0] == 10 || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 192 && n[1] == 168);
    }
    public static ConnectionTarget wifi(String host, int port, String fingerprint) {
        if (!validAddress(host, port) || fingerprint == null || !fingerprint.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("Invalid wireless PC identity");
        return new ConnectionTarget(true, host, port, fingerprint.toLowerCase(Locale.ROOT));
    }
    public String baseUrl() { return (wireless ? "https://" : "http://") + host + ":" + port; }
    public static String digest(X509Certificate certificate) throws Exception {
        byte[] data = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder result = new StringBuilder();
        for (byte value : data) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    public static String confirmationCode(String fingerprint) {
        if (fingerprint == null || !fingerprint.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException();
        return fingerprint.substring(0,4).toUpperCase(Locale.ROOT) + " " + fingerprint.substring(4,8).toUpperCase(Locale.ROOT) +
            " " + fingerprint.substring(8,12).toUpperCase(Locale.ROOT) + " " + fingerprint.substring(12,16).toUpperCase(Locale.ROOT);
    }
    private static X509TrustManager trust(final String expected) {
        return new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException { throw new CertificateException("Client certificates unsupported"); }
            public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                if (chain == null || chain.length == 0) throw new CertificateException("No server identity");
                chain[0].checkValidity();
                if (expected != null) {
                    try {
                        if (!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                                digest(chain[0]).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                            throw new CertificateException("PC certificate changed");
                    } catch (CertificateException failure) { throw failure; }
                    catch (Exception failure) { throw new CertificateException(failure); }
                }
            }
        };
    }
    public void secure(HttpsURLConnection connection) throws Exception {
        if (!wireless) throw new IllegalStateException();
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {trust(fingerprint)}, null);
        connection.setSSLSocketFactory(context.getSocketFactory());
        // The approved certificate is the PC identity; DHCP may change its address.
        connection.setHostnameVerifier((hostname, session) -> host.equals(hostname));
    }
    public static String probeFingerprint(String host, int port) throws Exception {
        if (!validAddress(host, port)) throw new IllegalArgumentException();
        SSLContext context = SSLContext.getInstance("TLS");
        // Inspect only the public certificate. No PIN, token or HTTP command is sent.
        context.init(null, new TrustManager[] {trust(null)}, null);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1800); socket.setSoTimeout(3500);
            try (SSLSocket tls = (SSLSocket) context.getSocketFactory().createSocket(socket, host, port, true)) {
                tls.setSoTimeout(3500); tls.startHandshake();
                return digest((X509Certificate) tls.getSession().getPeerCertificates()[0]);
            }
        }
    }
}
