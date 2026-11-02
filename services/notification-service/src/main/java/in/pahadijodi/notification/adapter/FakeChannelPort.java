package in.pahadijodi.notification.adapter;

import in.pahadijodi.notification.domain.Channel;
import in.pahadijodi.notification.port.ChannelPort;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pretends to deliver. Behaviour for demos/tests: an address in {@code failAddresses} gives a retryable provider
 * error; an address starting with "invalid-" is reported as permanently invalid (e.g. an expired push token).
 */
public class FakeChannelPort implements ChannelPort {
    private static final Logger log = LoggerFactory.getLogger(FakeChannelPort.class);
    private final Channel channel;
    private final Set<String> failAddresses;

    public FakeChannelPort(Channel channel, Set<String> failAddresses) {
        this.channel = channel;
        this.failAddresses = failAddresses;
    }

    @Override public Channel channel() { return channel; }

    @Override
    public SendResult send(Message m) {
        if (m.addresses().stream().anyMatch(failAddresses::contains)) {
            return SendResult.retryable("simulated provider outage for " + channel);
        }
        List<String> invalid = m.addresses().stream().filter(a -> a.startsWith("invalid-")).toList();
        if (invalid.size() == m.addresses().size()) return SendResult.permanent("no valid address", invalid);
        log.info("[FAKE {}] to {} :: {}", channel, m.addresses(), m.title());
        return SendResult.ok("fake-" + channel.name().toLowerCase() + "-" + UUID.randomUUID(), invalid);
    }
}
