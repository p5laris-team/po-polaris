package p5laris.character.domain.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.GetBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class S3StorageService {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    private static final String SHARE_CARD_CORS_RULE_ID = "polaris-share-card-browser-upload";

    private final String bucketName;
    private final String publicDomain;
    private final boolean corsAutoConfigureEnabled;
    private final List<String> corsAllowedOrigins;
    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final AtomicBoolean corsConfigurationAttempted = new AtomicBoolean(false);

    public S3StorageService(
            @Value("${cloud.aws.region}") String region,
            @Value("${cloud.aws.s3.bucket-name}") String bucketName,
            @Value("${cloud.aws.s3.public-domain}") String publicDomain,
            @Value("${cloud.aws.s3.cors.enabled}") boolean corsAutoConfigureEnabled,
            @Value("${cloud.aws.s3.cors.allowed-origins}") String corsAllowedOrigins) {

        this.bucketName = bucketName;
        this.publicDomain = publicDomain.endsWith("/")
                ? publicDomain.substring(0, publicDomain.length() - 1)
                : publicDomain;
        this.corsAutoConfigureEnabled = corsAutoConfigureEnabled;
        this.corsAllowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
        Region awsRegion = Region.of(region);

        this.s3Client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(awsRegion)
                .build();

        this.presigner = S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(awsRegion)
                .build();
    }

    public String toPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "";
        }
        String normalized = objectKey.trim();
        if (normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return publicDomain + "/" + normalized;
    }

    public String toObjectKey(String publicUrl) {
        URI uri = URI.create(publicUrl.trim());
        String host = publicHost();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || !host.equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("URL does not belong to configured S3 public domain");
        }
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            throw new IllegalArgumentException("URL does not contain an object key");
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    public String publicHost() {
        return URI.create(publicDomain).getHost();
    }

    public S3PresignedResult generatePresignedUrlForShareCard(String extension) {
        return generatePresignedUrlForShareCard(null, extension);
    }

    public S3PresignedResult generatePresignedUrlForShareCard(Long userId, String extension) {
        ensureCorsConfigurationAttempted();

        String safeExt = normalizeAndValidateExtension(extension);
        String fileName = userId != null && userId > 0
                ? "share-cards/" + userId + "/" + UUID.randomUUID() + "." + safeExt
                : "share-cards/" + UUID.randomUUID() + "." + safeExt;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType("image/" + (safeExt.equals("jpg") ? "jpeg" : safeExt))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        String presignedUrl = presigner.presignPutObject(presignRequest).url().toString();
        String imageUrl = publicDomain + "/" + fileName;

        return new S3PresignedResult(presignedUrl, imageUrl);
    }

    private void ensureCorsConfigurationAttempted() {
        if (!corsAutoConfigureEnabled || !corsConfigurationAttempted.compareAndSet(false, true)) {
            return;
        }
        if (corsAllowedOrigins.isEmpty()) {
            log.warn("S3 CORS 자동 설정을 건너뜁니다. 허용 origin이 비어 있습니다.");
            return;
        }

        try {
            CORSRule shareCardUploadRule = CORSRule.builder()
                    .id(SHARE_CARD_CORS_RULE_ID)
                    .allowedOrigins(corsAllowedOrigins)
                    .allowedMethods("PUT", "GET", "HEAD")
                    .allowedHeaders("*")
                    .exposeHeaders("ETag")
                    .maxAgeSeconds(3600)
                    .build();

            List<CORSRule> corsRules = new ArrayList<>(loadExistingCorsRules());
            corsRules.removeIf(rule -> SHARE_CARD_CORS_RULE_ID.equals(rule.id()));
            corsRules.add(shareCardUploadRule);

            s3Client.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(bucketName)
                    .corsConfiguration(configuration -> configuration.corsRules(corsRules))
                    .build());

            log.info("S3 버킷 CORS 설정 확인 완료. bucket={}, allowedOrigins={}", bucketName, corsAllowedOrigins);
        } catch (RuntimeException e) {
            log.warn("S3 버킷 CORS 자동 설정에 실패했습니다. 브라우저 presigned PUT 업로드가 CORS로 막힐 수 있습니다. bucket={}, allowedOrigins={}",
                    bucketName, corsAllowedOrigins, e);
        }
    }

    private List<CORSRule> loadExistingCorsRules() {
        try {
            return s3Client.getBucketCors(GetBucketCorsRequest.builder()
                    .bucket(bucketName)
                    .build()).corsRules();
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || "NoSuchCORSConfiguration".equals(e.awsErrorDetails().errorCode())) {
                return List.of();
            }
            throw e;
        }
    }

    private String normalizeAndValidateExtension(String extension) {
        if (extension == null) {
            return "png";
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(normalized)) {
            return "png";
        }
        return normalized;
    }

    public record S3PresignedResult(String presignedUrl, String imageUrl) {}
}
