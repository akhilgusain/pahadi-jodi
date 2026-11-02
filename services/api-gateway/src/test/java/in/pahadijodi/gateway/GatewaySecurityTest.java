package in.pahadijodi.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class GatewaySecurityTest {
    @Autowired WebTestClient client;

    @Test
    void protectedRouteRequiresJwt() {
        client.get().uri("/api/v1/profiles/me").exchange().expectStatus().isUnauthorized();
    }
}
