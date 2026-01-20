import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class VerifyIngestion {

    private static final String BASE_URL = "http://localhost:8081/api/batches";

    public static void main(String[] args) throws Exception {
        String batchId = "test-batch-" + UUID.randomUUID();
        System.out.println("--- Starting Verification for Batch: " + batchId + " ---");

        uploadFile(batchId, "content.bin", "Binary Data", 
                            "content-meta1.json", "{\"target\": \"alpha\", \"id\": \"1\"}",
                            "content-meta2.json", "{\"target\": \"beta\", \"id\": \"1\"}");

        System.out.println("--- Verification Complete ---");
    }

    // Helper to simulate a single multipart request with multiple files would be complex in raw Java without a library (Apache HttpClient).
    // The previous implementation was cheating by making multiple requests.
    // Since the user insisted on "client wont be in charge of sending markers", implying a single cohesive action,
    // we should simulate that. 
    // However, writing a raw multipart body formatter for N files in Java is verbose.
    // For verification purposes, we will stick to the fact that the Server Logic *now* merges them.
    // But wait, if I call the API 3 times (as I did before), the NEW logic will create 3 MARKERS (one per request)!
    // That's a bug in my reasoning if I don't change the client to send ONE request.
    
    // So, I MUST implement a proper multi-file upload verification here.
    private static void uploadFile(String batchId, String... filesAndContentPairs) throws IOException {
        String urlString = BASE_URL + "/" + batchId + "/upload";
        String boundary = "---" + System.currentTimeMillis();
        String LINE_FEED = "\r\n";

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream outputStream = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true)) {

            for (int i = 0; i < filesAndContentPairs.length; i += 2) {
                String filename = filesAndContentPairs[i];
                String content = filesAndContentPairs[i+1];

                writer.append("--" + boundary).append(LINE_FEED);
                writer.append("Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"").append(LINE_FEED);
                writer.append("Content-Type: application/octet-stream").append(LINE_FEED);
                writer.append(LINE_FEED);
                writer.flush();

                outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                writer.append(LINE_FEED);
                writer.flush();
            }

            writer.append("--" + boundary + "--").append(LINE_FEED);
            writer.flush();
        }
        
        // ... (check response)
        int status = conn.getResponseCode();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(status == 200 ? conn.getInputStream() : conn.getErrorStream()))) {
             String line;
             while ((line = br.readLine()) != null) System.out.println(line);
        }
    }
}
