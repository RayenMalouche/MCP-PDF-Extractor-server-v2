# PDFExtractor MCP Server

## Overview

The `PDFExtractor` is a Model Context Protocol (MCP) server that extracts content from files (e.g., PDF, Word, Markdown) located in a designated `file-to-extract` directory and converts the content into HTML using Apache Tika. Built with Spring Boot, Jetty, and the MCP SDK, it supports both HTTP/SSE and STDIO transport protocols, making it compatible with MCP-compliant clients like Claude Desktop or MCP Inspector. The server exposes a tool, `extract-file-to-html`, that accepts a filename and returns the extracted content in HTML format, along with metadata.

This project is designed for developers and systems requiring automated file content extraction and conversion to HTML, suitable for applications such as document processing, content management, or integration with AI assistants.

## Features

- **File Content Extraction**: Extracts text and metadata from various file formats (PDF, DOCX, Markdown, etc.) using Apache Tika.
- **HTML Conversion**: Converts extracted content to HTML using Tika’s `ToHTMLContentHandler`.
- **MCP Compatibility**: Supports MCP protocol with a synchronous tool (`extract-file-to-html`) for integration with MCP-compliant clients.
- **Transport Options**: Supports HTTP/SSE (port 45452) and STDIO transports for flexible integration.
- **REST Endpoint**: Provides a `/api/test-extract` endpoint for testing file extraction via HTTP POST.
- **Health Check**: Includes a `/api/health` endpoint to verify server status.
- **CORS Support**: Enables cross-origin requests for the test endpoint, facilitating web-based testing.
- **Configurable Directory**: Reads files from a configurable `file-to-extract` directory, specified in `application.properties`.

## Prerequisites

- **Java**: Version 21 or higher.
- **Maven**: Version 3.6.0 or higher for building the project.
- **Supported File Formats**: Ensure files in the `file-to-extract` directory are in formats supported by Apache Tika (e.g., PDF, DOCX, TXT, Markdown).

## Installation

1. **Clone the Repository**:
   ```bash
   git clone <repository-url>
   cd PDFExtractor
   ```

2. **Create the File Directory**:
    - The server reads files from the `file-to-extract` directory by default, as specified in `application.properties`.
    - Create this directory in the project root:
      ```bash
      mkdir file-to-extract
      ```
    - Place the files you want to extract (e.g., PDFs, Word documents) in this directory.

3. **Build the Project**:
    - Use Maven to build the project:
      ```bash
      mvn clean install
      ```

## Configuration

The server configuration is defined in `src/main/resources/application.properties`:

```properties
spring.application.name=FileExtractMcpServer
file.directory=file-to-extract
```

- **spring.application.name**: Name of the application.
- **file.directory**: Directory where input files are stored (default: `file-to-extract`). Update this property to change the directory path if needed.

## Running the Server

The server can be run in two modes: HTTP/SSE or STDIO.

### 1. **HTTP/SSE Mode**
- Default mode, suitable for web-based or MCP Inspector integration.
- Run the server:
  ```bash
  mvn spring-boot:run
  ```
- For streamable HTTP (MCP Inspector compatibility):
  ```bash
  mvn spring-boot:run -- --streamable-http
  ```
- The server starts on `http://localhost:45452` with the following endpoints:
    - **MCP Endpoint**: `http://localhost:45452/` (or `/message` for streamable HTTP).
    - **SSE Endpoint**: `http://localhost:45452/sse`.
    - **Test Endpoint**: `http://localhost:45452/api/test-extract` (POST).
    - **Health Check**: `http://localhost:45452/api/health` (GET/POST).

### 2. **STDIO Mode**
- Suitable for command-line or local MCP client integration.
- Run the server:
  ```bash
  mvn spring-boot:run -- --stdio
  ```

### Example Usage

#### Using the MCP Tool
- The `extract-file-to-html` tool accepts a JSON payload with a `filename` field.
- Example request (via MCP client or HTTP POST to `/api/test-extract`):
  ```json
  {
    "filename": "example.pdf"
  }
  ```
- Example using `curl`:
  ```bash
  curl -X POST http://localhost:45452/api/test-extract \
       -H "Content-Type: application/json" \
       -d '{"filename":"example.pdf"}'
  ```
- Expected response:
  ```json
  {
    "status": "success",
    "message": "File content extracted successfully",
    "html": "<html>...</html>",
    "metadata": {
      "filename": "example.pdf",
      "contentType": "application/pdf"
    }
  }
  ```
- If the file is not found or extraction fails, an error response is returned:
  ```json
  {
    "status": "error",
    "message": "Failed to extract file: File not found",
    "errorType": "IOException"
  }
  ```

#### Health Check
- Verify server status:
  ```bash
  curl http://localhost:45452/api/health
  ```
- Response:
  ```json
  {
    "status": "healthy",
    "server": "File Extract MCP Server",
    "version": "1.0.0"
  }
  ```

## Project Structure

```
PDFExtractor/
├── src/
│   ├── main/
│   │   ├── java/com/mcp/RayenMalouche/pdf/v2/PDFExtractor/
│   │   │   ├── PdfExtractorApplication.java  # Main application with MCP server logic
│   │   │   ├── config/
│   │   │   │   ├── ConfigLoader.java        # Loads configuration from application.properties
│   │   ├── resources/
│   │   │   ├── application.properties        # Configuration file
│   ├── test/                                # Test sources (not included in this setup)
├── file-to-extract/                         # Directory for input files
├── pom.xml                                  # Maven configuration
├── README.md                                # This file
```

## Dependencies

- **Spring Boot**: Framework for building the application (`spring-boot-starter`, `spring-boot-starter-web`).
- **Apache Tika**: For file parsing and HTML conversion (`tika-core`, `tika-parsers-standard-package`).
- **MCP SDK**: For MCP protocol support (`io.modelcontextprotocol.sdk:mcp`).
- **Jetty**: Embedded server for HTTP/SSE transport (`jetty-server`, `jetty-ee10-servlet`).
- **Jackson**: JSON processing (`jackson-databind`).

## Limitations

- **Image Handling**: Embedded images in documents (e.g., in DOCX or PDF) are referenced in the HTML output (e.g., `src="embedded:image1.jpeg"`) but not served directly. Additional logic is needed to extract and serve images (see [Future Improvements](#future-improvements)).
- **Styling**: The generated HTML lacks CSS styling, which may affect rendering. Basic formatting (e.g., `<b>`, `<i>`) is preserved, but visual fidelity to the original document is limited.
- **File Size**: Large files may impact performance due to Tika’s parsing and HTML conversion overhead.
- **Supported Formats**: Depends on Apache Tika’s capabilities. Common formats (PDF, DOCX, TXT, Markdown) are supported, but exotic formats may require additional Tika parsers.

## Future Improvements

- **Image Support**: Extract embedded images to a directory and serve them via a dedicated endpoint (e.g., `/api/images/<filename>`).
- **CSS Styling**: Include a default stylesheet in the HTML output to improve rendering.
- **Link Validation**: Ensure table of contents and figure links resolve correctly in the HTML output.
- **Pagination**: Support streaming or paginated responses for large documents to improve performance.
- **Metadata Enrichment**: Include additional Tika metadata (e.g., word count, creation date) in the response.
- **File Upload**: Add an endpoint to upload files to the `file-to-extract` directory dynamically.

## Troubleshooting

- **File Not Found Error**:
    - Ensure the file exists in the `file-to-extract` directory.
    - Check the filename in the request matches exactly (case-sensitive).
- **Port Conflict**:
    - If port `45452` is in use, update the port in `PdfExtractorApplication.java` (line: `connector.setPort(45452)`).
- **Tika Parsing Errors**:
    - Verify the file format is supported by Tika.
    - Update Tika dependencies to the latest version if issues persist.
- **Configuration Issues**:
    - Ensure `application.properties` is in `src/main/resources/`.
    - Check that `file.directory` points to a valid directory.

## Contributing

Contributions are welcome! To contribute:
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/YourFeature`).
3. Commit changes (`git commit -m "Add YourFeature"`).
4. Push to the branch (`git push origin feature/YourFeature`).
5. Open a pull request.

Please include tests and update documentation as needed.

## Contact

For questions or support, contact the project maintainer:
- **Name**: Mohamed Rayen Malouche
- **Email**: rayenmalouche27@gmail.com