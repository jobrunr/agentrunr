package ai.javaclaw.speech;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "speech.provider", havingValue = "openai")
public class OpenAiSpeechToTextService implements SpeechToTextService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiSpeechToTextService.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public OpenAiSpeechToTextService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${speech.openai.model:whisper-1}") String model,
            @Value("${speech.openai.base-url:https://api.openai.com/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String transcribe(InputStream audioStream) {
        LOGGER.info("Transcribing audio via OpenAI Whisper (model: {})", model);

        try {
            byte[] audioBytes = audioStream.readAllBytes();
            String boundary = UUID.randomUUID().toString();
            byte[] body = buildMultipartBody(boundary, audioBytes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/audio/transcriptions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new SpeechToTextException("OpenAI API returned status " + response.statusCode() + ": " + response.body());
            }

            String text = extractTextField(response.body());
            if (text == null || text.isBlank()) {
                throw new SpeechToTextException("OpenAI returned empty transcription");
            }

            LOGGER.info("OpenAI transcription completed successfully");
            return text.trim();

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SpeechToTextException("Failed to call OpenAI transcription API", e);
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] audioBytes) {
        String crlf = "\r\n";

        byte[] header = ("--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"file\"; filename=\"voice.ogg\"" + crlf
                + "Content-Type: audio/ogg" + crlf + crlf).getBytes();

        byte[] footer = (crlf + "--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"model\"" + crlf + crlf
                + model + crlf
                + "--" + boundary + "--" + crlf).getBytes();

        byte[] body = new byte[header.length + audioBytes.length + footer.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(audioBytes, 0, body, header.length, audioBytes.length);
        System.arraycopy(footer, 0, body, header.length + audioBytes.length, footer.length);

        return body;
    }

    private String extractTextField(String json) throws IOException {
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "text".equals(parser.currentName())) {
                    parser.nextToken();
                    return parser.getValueAsString();
                }
            }
        }
        return null;
    }
}
