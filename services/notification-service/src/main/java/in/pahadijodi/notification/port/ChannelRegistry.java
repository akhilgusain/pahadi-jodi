package in.pahadijodi.notification.port;

import in.pahadijodi.notification.domain.Channel;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** The adapters available per external channel. */
public final class ChannelRegistry {
    private final Map<Channel, ChannelPort> ports = new EnumMap<>(Channel.class);

    public ChannelRegistry(Iterable<ChannelPort> all) { all.forEach(p -> ports.put(p.channel(), p)); }

    public Optional<ChannelPort> get(Channel channel) { return Optional.ofNullable(ports.get(channel)); }
}
