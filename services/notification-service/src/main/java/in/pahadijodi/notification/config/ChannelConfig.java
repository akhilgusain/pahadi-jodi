package in.pahadijodi.notification.config;

import in.pahadijodi.notification.adapter.FakeChannelPort;
import in.pahadijodi.notification.domain.Channel;
import in.pahadijodi.notification.port.ChannelPort;
import in.pahadijodi.notification.port.ChannelRegistry;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class ChannelConfig {

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    /**
     * pj.notification.provider=fake wires fake adapters for EMAIL/SMS/PUSH/WHATSAPP (IN_APP is the inbox itself).
     * Real adapters (SES, MSG91, FCM, WhatsApp Cloud API) are not implemented yet, so "real" fails fast
     * instead of silently dropping notifications.
     */
    @Bean
    ChannelRegistry channelRegistry(NotificationProperties props, @Value("${pj.notification.provider:fake}") String provider) {
        if (!"fake".equals(provider)) {
            throw new IllegalStateException("pj.notification.provider=" + provider + ": no real channel adapters implemented yet");
        }
        var fail = new HashSet<>(props.fake().failAddresses());
        return new ChannelRegistry(Arrays.stream(Channel.values()).filter(c -> c != Channel.IN_APP)
                .<ChannelPort>map(c -> new FakeChannelPort(c, fail)).toList());
    }
}
