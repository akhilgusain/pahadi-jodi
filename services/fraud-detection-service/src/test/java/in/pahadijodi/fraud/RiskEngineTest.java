package in.pahadijodi.fraud;

import static org.junit.jupiter.api.Assertions.*;

import in.pahadijodi.fraud.engine.*;
import in.pahadijodi.fraud.engine.RiskEngine.RuleConfig;
import in.pahadijodi.fraud.engine.RiskEngine.Thresholds;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskEngineTest {
    private final RiskEngine engine = new RiskEngine(Thresholds.defaults());

    @Test
    void cleanUserScoresZeroAndIsAllowed() {
        var r = engine.evaluate(Facts.none(), Map.of());
        assertEquals(0, r.score());
        assertEquals(Action.ALLOW, r.action());
        assertTrue(r.reasons().isEmpty());
    }

    @Test
    void singleDisposableEmailIsAllowedButExplained() {
        var r = engine.evaluate(new Facts(0, 0, true, false, false, 0, 0, 0, 0), Map.of());
        assertEquals(20, r.score());
        assertEquals(Action.ALLOW, r.action());
        assertEquals("DISPOSABLE_EMAIL", r.reasons().get(0).code());
    }

    @Test
    void thresholdsMapScoresToActions() {
        assertEquals(Action.ALLOW, engine.actionFor(29));
        assertEquals(Action.STEP_UP_VERIFICATION, engine.actionFor(30));
        assertEquals(Action.STEP_UP_VERIFICATION, engine.actionFor(59));
        assertEquals(Action.SHADOW_LIMIT, engine.actionFor(60));
        assertEquals(Action.SHADOW_LIMIT, engine.actionFor(79));
        assertEquals(Action.BLOCK, engine.actionFor(80));
    }

    @Test
    void ipVelocityAndDeviceSharingScaleWithAccountCount() {
        var two = engine.evaluate(new Facts(2, 2, false, false, false, 0, 0, 0, 0), Map.of());
        var three = engine.evaluate(new Facts(3, 3, false, false, false, 0, 0, 0, 0), Map.of());
        assertEquals(13 + 15, two.score());      // 0.5*25 rounded + 0.5*30
        assertEquals(25 + 30, three.score());
        assertEquals(Action.STEP_UP_VERIFICATION, three.action());
    }

    @Test
    void combinedSignalsCapAtHundredAndReasonsAreSortedByPoints() {
        var r = engine.evaluate(new Facts(5, 5, true, true, true, 6, 20, 5, 1), Map.of());
        assertEquals(100, r.score());
        assertEquals(Action.BLOCK, r.action());
        for (int i = 1; i < r.reasons().size(); i++) {
            assertTrue(r.reasons().get(i - 1).points() >= r.reasons().get(i).points());
        }
    }

    @Test
    void disabledRuleAndCustomWeightAreHonoured() {
        var facts = new Facts(0, 0, true, false, true, 0, 0, 0, 0);
        var r = engine.evaluate(facts, Map.of(
                RuleCode.DISPOSABLE_EMAIL, new RuleConfig(false, 20),
                RuleCode.DUPLICATE_PHOTO, new RuleConfig(true, 50)));
        assertEquals(50, r.score());
        assertEquals(1, r.reasons().size());
        assertEquals("DUPLICATE_PHOTO", r.reasons().get(0).code());
    }

    @Test
    void messageBlastNeedsAtLeastFiveDistinctRecipients() {
        assertEquals(0, engine.evaluate(new Facts(0, 0, false, false, false, 0, 4, 0, 0), Map.of()).score());
        assertTrue(engine.evaluate(new Facts(0, 0, false, false, false, 0, 15, 0, 0), Map.of()).score() == 30);
    }

    @Test
    void chargebackAloneIsStepUpAndPaymentFailuresNeedTwo() {
        assertEquals(Action.STEP_UP_VERIFICATION, engine.evaluate(new Facts(0, 0, false, false, false, 0, 0, 0, 1), Map.of()).action());
        assertEquals(0, engine.evaluate(new Facts(0, 0, false, false, false, 0, 0, 1, 0), Map.of()).score());
        assertEquals(13, engine.evaluate(new Facts(0, 0, false, false, false, 0, 0, 2, 0), Map.of()).score());
    }
}
