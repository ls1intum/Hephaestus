package de.tum.in.www1.hephaestus.mentor;

import okhttp3.mockwebserver.MockResponse;
import okio.Buffer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utility for creating chunked MockResponse objects where each chunk
 * corresponds exactly to one logical frame in the Vercel AI SDK protocol.
 * 
 * This ensures proper streaming behavior where each frame is sent as a 
 * separate HTTP chunk, mimicking real-world behavior of the intelligence service.
 */
public final class ChunkedChatResponseMock {

    private static final String VERCEL_AI_STREAM_HEADER = "x-vercel-ai-data-stream";
    private static final String VERCEL_AI_STREAM_VERSION = "v1";
    private static final String CONTENT_TYPE = "text/event-stream";
    private static final String TRANSFER_ENCODING = "chunked";
    private static final String CRLF = "\r\n";
    
    /**
     * Build a MockResponse whose every HTTP chunk is one frame string,
     * following the HTTP/1.1 chunked transfer encoding specification.
     * 
     * @param frames List of string frames to be sent, each as a separate chunk
     * @return A properly configured MockResponse
     */
    public static MockResponse response(List<String> frames) {
        Buffer buffer = new Buffer();

        for (String frame : frames) {
            // 1) Length of this frame in UTF-8 bytes, written as hex
            byte[] frameBytes = frame.getBytes(StandardCharsets.UTF_8);
            buffer.writeUtf8(Integer.toHexString(frameBytes.length));
            buffer.writeUtf8(CRLF);              // Chunk header delimiter

            // 2) The frame payload itself
            buffer.writeUtf8(frame);
            buffer.writeUtf8(CRLF);              // Chunk trailer
        }

        // 3) Terminating 0-length chunk to signal end of response
        buffer.writeUtf8("0");
        buffer.writeUtf8(CRLF);
        buffer.writeUtf8(CRLF);

        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", CONTENT_TYPE)
                .addHeader(VERCEL_AI_STREAM_HEADER, VERCEL_AI_STREAM_VERSION)   // Keep the real header
                .addHeader("Transfer-Encoding", TRANSFER_ENCODING)    // Tell the client to parse chunks
                .setBody(buffer);                               // Stream our handcrafted body
    }
    
    /**
     * Convenience method to create a response from varargs strings.
     * 
     * @param frames The frames to include in the response
     * @return A properly configured MockResponse
     */
    public static MockResponse response(String... frames) {
        return response(List.of(frames));
    }

    // Private constructor to prevent instantiation
    private ChunkedChatResponseMock() {}
}
