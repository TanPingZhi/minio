import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class VerifyIngestion {

    private static final String BASE_URL = "http://localhost:8081/api/batches/upload";

    public static void main(String[] args) throws Exception {
        System.out.println("--- Starting Verification (Server handles Batch ID and Metadata) ---");

        uploadFiles("content.bin", "Binary Data Content");

        System.out.println("--- Verification Complete ---");
    }

    private static void uploadFiles(String... filesAndContentPairs) throws IOException {
        String boundary = "---" + System.currentTimeMillis();
        String LINE_FEED = "\r\n";

        URL url = new URL(BASE_URL);
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
        
        int status = conn.getResponseCode();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(status == 200 ? conn.getInputStream() : conn.getErrorStream()))) {
             String line;
             while ((line = br.readLine()) != null) {
                 System.out.println(line);
             }
        }
    }
}
