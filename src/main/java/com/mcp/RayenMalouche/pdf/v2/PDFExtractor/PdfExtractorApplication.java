package com.mcp.RayenMalouche.pdf.v2.PDFExtractor;

import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.mcp.RayenMalouche.pdf.v2.PDFExtractor.config.ConfigLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.xml.sax.SAXException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;


@SpringBootApplication
public class PdfExtractorApplication {
    private static String FILE_DIRECTORY;

    public static void main(String[] args) throws Exception {
        // Load configuration from application.properties
        loadConfig();
        // Check if STDIO transport is requested
        boolean useStdio = args.length > 0 && "--stdio".equals(args[0]);
        boolean useStreamableHttp = args.length > 0 && "--streamable-http".equals(args[0]);

        if (useStdio) {
            System.err.println("Starting MCP server with STDIO transport...");
            startStdioServer();
        } else {
            System.out.println("Starting MCP server with HTTP/SSE transport...");
            startHttpServer(useStreamableHttp);
        }
    }

    private static void loadConfig() {
        FILE_DIRECTORY = ConfigLoader.getProperty("file.directory", "file-to-extract");
        // Ensure the directory exists
        Path dirPath = Paths.get(FILE_DIRECTORY);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
                System.err.println("Created directory: " + FILE_DIRECTORY);
            } catch (IOException e) {
                System.err.println("Failed to create directory: " + FILE_DIRECTORY);
                e.printStackTrace(System.err);
            }
        }
        System.err.println("Configuration loaded: file.directory=" + FILE_DIRECTORY);
    }

    private static void startStdioServer() {
        try {
            System.err.println("Initializing STDIO MCP server...");

            // Create transport provider
            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new ObjectMapper());

            // Build synchronous MCP server
            McpSyncServer syncServer = McpServer.sync(transportProvider)
                    .serverInfo("file-extract-server", "1.0.0")
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .tools(true)
                            .logging()
                            .build())
                    .tools(createFileExtractTool())
                    .build();

            System.err.println("STDIO MCP server started. Awaiting requests...");

        } catch (Exception e) {
            System.err.println("Fatal error in STDIO server: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void startHttpServer(boolean streamableHttp) throws Exception {
        // Create ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();

        // Create SSE transport provider
        HttpServletSseServerTransportProvider transportProvider;
        if (streamableHttp) {
            transportProvider = new HttpServletSseServerTransportProvider(objectMapper, "/message", "/sse");
        } else {
            transportProvider = new HttpServletSseServerTransportProvider(objectMapper, "/", "/sse");
        }

        // Build synchronous MCP server
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("file-extract-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .tools(createFileExtractTool())
                .build();

        // Configure Jetty server
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("mcp-server");

        Server server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(45452);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        // Add MCP transport servlet
        context.addServlet(new ServletHolder(transportProvider), "/*");

        // Add testing servlet
        context.addServlet(new ServletHolder(new FileExtractTestServlet()), "/api/test-extract");
        context.addServlet(new ServletHolder(new HealthServlet()), "/api/health");

        server.setHandler(context);

        // Start server
        server.start();
        System.err.println("=================================");
        System.err.println("MCP File Extract Server started on port 45452");
        if (streamableHttp) {
            System.err.println("Mode: Streamable HTTP (for MCP Inspector)");
            System.err.println("MCP endpoint: http://localhost:45452/message");
        } else {
            System.err.println("Mode: Standard HTTP/SSE");
            System.err.println("MCP endpoint: http://localhost:45452/");
        }
        System.err.println("SSE endpoint: http://localhost:45452/sse");
        System.err.println("Test endpoint: http://localhost:45452/api/test-extract");
        System.err.println("Health check: http://localhost:45452/api/health");
        System.err.println("=================================");
        server.join();
    }

    private static McpServerFeatures.SyncToolSpecification createFileExtractTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-file-to-html",
                        "Extracts content from a file (PDF, Word, Markdown, etc.) and converts it to HTML",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "filename": {
                                      "type": "string",
                                      "description": "Name of the file in the file-to-extract directory"
                                    }
                                  },
                                  "required": ["filename"]
                                }
                                """
                ),
                (exchange, params) -> {
                    try {
                        String filename = (String) params.get("filename");
                        System.err.printf("Executing extract-file-to-html tool: filename=%s%n", filename);

                        String htmlContent = extractFileToHtml(filename);
                        System.err.println("File content extracted successfully");
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(htmlContent)),
                                false
                        );
                    } catch (Exception e) {
                        System.err.println("ERROR in extract-file-to-html tool: " + e.getMessage());
                        e.printStackTrace(System.err);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(
                                        String.format("""
                                                {
                                                    "status": "error",
                                                    "message": "Failed to extract file: %s",
                                                    "errorType": "%s"
                                                }""", escapeJsonString(e.getMessage()), e.getClass().getSimpleName())
                                )),
                                true
                        );
                    }
                }
        );
    }

    private static String extractFileToHtml(String filename) throws IOException, SAXException, TikaException {
        Path filePath = Paths.get(FILE_DIRECTORY, filename);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filename);
        }

        AutoDetectParser parser = new AutoDetectParser();
        ToHTMLContentHandler handler = new ToHTMLContentHandler();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

        try (FileInputStream inputStream = new FileInputStream(filePath.toFile())) {
            parser.parse(inputStream, handler, metadata);
            String htmlContent = handler.toString();
            return String.format("""
                            {
                                "status": "success",
                                "message": "File content extracted successfully",
                                "html": "%s",
                                "metadata": {
                                    "filename": "%s",
                                    "contentType": "%s"
                                }
                            }""",
                    escapeJsonString(htmlContent),
                    escapeJsonString(filename),
                    escapeJsonString(metadata.get(Metadata.CONTENT_TYPE) != null ? metadata.get(Metadata.CONTENT_TYPE) : "unknown"));
        }
    }

    public static class FileExtractTestServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");

            // Add CORS headers
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type");

            try {
                // Read JSON from request body
                StringBuilder sb = new StringBuilder();
                BufferedReader reader = req.getReader();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                // Parse JSON
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> data = mapper.readValue(sb.toString(), Map.class);
                String filename = (String) data.get("filename");

                if (filename == null) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"status\": \"error\", \"message\": \"Missing required field: filename\"}");
                    return;
                }

                String result = extractFileToHtml(filename);
                resp.getWriter().write(result);

            } catch (Exception e) {
                resp.setStatus(500);
                resp.getWriter().write(String.format("{\"status\": \"error\", \"message\": \"%s\"}",
                        escapeJsonString(e.getMessage())));
            }
        }

        @Override
        protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            // Handle CORS preflight
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
            resp.setStatus(200);
        }
    }

    public static class HealthServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            sendHealthResponse(resp);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            sendHealthResponse(resp);
        }

        private void sendHealthResponse(HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.getWriter().write("{\"status\": \"healthy\", \"server\": \"File Extract MCP Server\", \"version\": \"1.0.0\"}");
        }
    }

    private static String escapeJsonString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
