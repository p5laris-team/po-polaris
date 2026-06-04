package p5laris.notification.core.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.util.Base64;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials.base64:}")
    private String firebaseCredentialsBase64;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                if (firebaseCredentialsBase64 == null || firebaseCredentialsBase64.isBlank()) {
                    log.warn("Firebase credentials base64 is empty. FirebaseApp will not be initialized. Push notifications may fail.");
                    return;
                }

                byte[] decodedBytes = Base64.getDecoder().decode(firebaseCredentialsBase64);
                ByteArrayInputStream credentialsStream = new ByteArrayInputStream(decodedBytes);

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("FirebaseApp initialized successfully.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize FirebaseApp", e);
        }
    }
}
