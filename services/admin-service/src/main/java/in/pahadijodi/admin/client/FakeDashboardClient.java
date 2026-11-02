package in.pahadijodi.admin.client;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pj.admin.provider", havingValue = "fake", matchIfMissing = true)
public class FakeDashboardClient implements DashboardClient {
    @Override
    public Kpis fetch() {
        return new Kpis(12_840L, 311L, 1_906L,
                Map.of("PREMIUM", 1_204L, "PREMIUM_PLUS", 541L, "VIP", 161L),
                18_900_00L, 4_127_500_00L, 7L, 23L, List.of());
    }
}
