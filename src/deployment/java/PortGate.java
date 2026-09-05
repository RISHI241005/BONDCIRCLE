import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accepts Vercel traffic immediately, then transparently forwards it after
 * Spring Boot has finished its database-backed startup on the internal port.
 */
public final class PortGate {
    private static final int PUBLIC_PORT = 8080;
    private static final int SPRING_PORT = 8081;
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(120);

    private PortGate() {
    }

    public static void main(String[] args) throws IOException {
        ExecutorService connections = Executors.newVirtualThreadPerTaskExecutor();
        try (ServerSocket server = new ServerSocket(PUBLIC_PORT)) {
            while (true) {
                Socket client = server.accept();
                connections.submit(() -> forwardWhenReady(client));
            }
        } finally {
            connections.shutdownNow();
        }
    }

    private static void forwardWhenReady(Socket client) {
        try (client) {
            Socket spring = awaitSpring();
            if (spring == null) {
                sendUnavailable(client.getOutputStream());
                return;
            }

            try (spring) {
                Thread upstream = Thread.startVirtualThread(
                        () -> copy(client, spring, true));
                copy(spring, client, false);
                upstream.interrupt();
            }
        } catch (IOException ignored) {
            // The browser or upstream closed the connection.
        }
    }

    private static Socket awaitSpring() {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", SPRING_PORT), 250);
                return socket;
            } catch (IOException unavailable) {
                closeQuietly(socket);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private static void copy(Socket source, Socket destination, boolean closeOutput) {
        try {
            InputStream input = source.getInputStream();
            OutputStream output = destination.getOutputStream();
            input.transferTo(output);
            output.flush();
            if (closeOutput) {
                destination.shutdownOutput();
            }
        } catch (IOException ignored) {
            // Closing either side is a normal end to HTTP and WebSocket traffic.
        }
    }

    private static void sendUnavailable(OutputStream output) throws IOException {
        byte[] body = "BondCircle is still starting. Please retry.\n"
                .getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 503 Service Unavailable\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing else to clean up.
        }
    }
}
