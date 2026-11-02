package in.pahadijodi.fraud.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure rule engine: facts + rule config + thresholds -> explainable score and action. No I/O. */
public class RiskEngine {

    public record RuleConfig(boolean enabled, int weight) {}
    public record Thresholds(int stepUp, int shadowLimit, int block) {
        public static Thresholds defaults() { return new Thresholds(30, 60, 80); }
    }
    public record Reason(String code, int points, String description) {}
    public record Result(int score, Action action, List<Reason> reasons) {}

    private final Thresholds thresholds;

    public RiskEngine(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public Result evaluate(Facts facts, Map<RuleCode, RuleConfig> config) {
        Map<RuleCode, RuleConfig> cfg = new EnumMap<>(RuleCode.class);
        for (RuleCode r : RuleCode.values()) cfg.put(r, new RuleConfig(true, r.defaultWeight()));
        cfg.putAll(config);

        List<Reason> reasons = new ArrayList<>();
        int sum = 0;
        for (RuleCode rule : RuleCode.values()) {
            RuleConfig c = cfg.get(rule);
            if (!c.enabled() || c.weight() <= 0) continue;
            int points = (int) Math.round(c.weight() * rule.fraction(facts));
            if (points > 0) {
                reasons.add(new Reason(rule.name(), points, rule.description()));
                sum += points;
            }
        }
        reasons.sort((a, b) -> Integer.compare(b.points(), a.points()));
        int score = Math.min(100, sum);
        return new Result(score, actionFor(score), reasons);
    }

    public Action actionFor(int score) {
        if (score >= thresholds.block()) return Action.BLOCK;
        if (score >= thresholds.shadowLimit()) return Action.SHADOW_LIMIT;
        if (score >= thresholds.stepUp()) return Action.STEP_UP_VERIFICATION;
        return Action.ALLOW;
    }
}
