package p5laris.gateway.domain.user.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;

@RestController
public class GoogleCallbackController {

    @GetMapping("/oauth/google/callback")
    public void googleCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletResponse response) throws IOException {
        response.sendRedirect("/payment-test.html?code=" + code + "&state=" + state);
    }
}
