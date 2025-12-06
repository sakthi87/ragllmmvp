import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * Simple HTTP server to serve static files (for restricted environments without Python)
 * Usage: java SimpleHttpServer <port> <directory>
 */
public class SimpleHttpServer {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        String directory = args.length > 1 ? args[1] : ".";
        
        Path basePath = Paths.get(directory).toAbsolutePath();
        ServerSocket server = new ServerSocket(port);
        
        System.out.println("Serving files from: " + basePath);
        System.out.println("Server started on http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop");
        
        while (true) {
            try (Socket client = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String request = in.readLine();
                
                if (request == null) continue;
                
                String[] parts = request.split(" ");
                if (parts.length < 2) continue;
                
                String path = parts[1];
                if (path.equals("/")) path = "/index.html";
                
                Path filePath = basePath.resolve(path.substring(1)).normalize();
                
                if (!filePath.startsWith(basePath)) {
                    sendError(client, 403, "Forbidden");
                    continue;
                }
                
                if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    sendFile(client, filePath);
                } else {
                    // Try index.html for directories
                    Path indexPath = basePath.resolve("index.html");
                    if (Files.exists(indexPath)) {
                        sendFile(client, indexPath);
                    } else {
                        sendError(client, 404, "Not Found");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
    
    private static void sendFile(Socket client, Path filePath) throws IOException {
        String contentType = getContentType(filePath);
        byte[] content = Files.readAllBytes(filePath);
        
        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: " + contentType);
        out.println("Content-Length: " + content.length);
        out.println();
        out.flush();
        
        client.getOutputStream().write(content);
        client.getOutputStream().flush();
    }
    
    private static void sendError(Socket client, int code, String message) throws IOException {
        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
        out.println("HTTP/1.1 " + code + " " + message);
        out.println("Content-Type: text/plain");
        out.println();
        out.println(message);
        out.flush();
    }
    
    private static String getContentType(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".html")) return "text/html";
        if (fileName.endsWith(".js")) return "application/javascript";
        if (fileName.endsWith(".css")) return "text/css";
        if (fileName.endsWith(".json")) return "application/json";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}

