package p5laris.gateway.domain.share.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import p5laris.gateway.domain.share.infrastructure.grpc.ShareGatewayService;

import java.net.URI;

@Controller
@RequiredArgsConstructor
public class ShareHtmlController {

    private final ShareGatewayService shareGatewayService;

    @Value("${app.public-base-url:https://p5laris.life}")
    private String publicBaseUrl;

    @Value("${app.default-share-image-url:https://cdn.p5laris.life/assets/share-cards/placeholder.png}")
    private String defaultShareImageUrl;

    @GetMapping(value = "/share/{shareId}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> getShareHtmlPage(@PathVariable String shareId) {
        var shareLink = shareGatewayService.getShareLink(shareId);

        String ogUrl = normalizeAbsoluteHttpsUrl(publicBaseUrl, "/share/" + shareId);
        String title = "Polaris";
        String description = shareLink.headline() != null && !shareLink.headline().isBlank()
                ? shareLink.headline()
                : "오늘의 기록을 함께 봐요.";

        String ogImage = normalizeAbsoluteHttpsUrlOrFallback(shareLink.imageUrl(), defaultShareImageUrl);
        String signupUrl = normalizeAbsoluteHttpsUrlOrFallback(shareLink.signupUrl(), publicBaseUrl);

        String html = buildHtml(
                title,
                description,
                ogImage,
                ogUrl,
                signupUrl
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String buildHtml(String title, String description, String imageUrl, String url, String redirectUrl) {
        String eTitle = htmlEscape(title);
        String eDesc = htmlEscape(description);
        String eImage = htmlEscape(imageUrl);
        String eUrl = htmlEscape(url);
        String eRedirect = htmlEscape(redirectUrl);

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>%s</title>
                  <meta property="og:title" content="%s" />
                  <meta property="og:description" content="%s" />
                  <meta property="og:image" content="%s" />
                  <meta property="og:url" content="%s" />
                  <meta property="og:type" content="website" />
                  <meta name="twitter:card" content="summary_large_image" />
                  <meta name="twitter:title" content="%s" />
                  <meta name="twitter:description" content="%s" />
                  <meta name="twitter:image" content="%s" />
                </head>
                <body>
                  <noscript>
                    <p>공유 페이지로 이동하려면 아래 링크를 눌러주세요.</p>
                    <p><a href="%s">%s</a></p>
                  </noscript>
                  <p>이동 중...</p>
                  <p><a href="%s">%s</a></p>
                  <script>
                    window.location.replace(%s);
                  </script>
                </body>
                </html>
                """.formatted(
                eTitle,
                eTitle,
                eDesc,
                eImage,
                eUrl,
                eTitle,
                eDesc,
                eImage,
                eRedirect, eRedirect,
                eRedirect, eRedirect,
                jsStringLiteral(redirectUrl)
        );
    }

    private String htmlEscape(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String jsStringLiteral(String url) {
        return "\"" + url.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String normalizeAbsoluteHttpsUrl(String baseUrl, String path) {
        URI base = URI.create(baseUrl);
        URI resolved = base.resolve(path);
        return resolved.toString();
    }

    private String normalizeAbsoluteHttpsUrlOrFallback(String candidate, String fallback) {
        try {
            if (candidate == null || candidate.isBlank()) {
                return fallback;
            }
            URI uri = URI.create(candidate);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return fallback;
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return fallback;
            }
            return uri.toString();
        } catch (Exception e) {
            return fallback;
        }
    }
}
