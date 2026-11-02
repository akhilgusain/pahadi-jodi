package in.pahadijodi.horoscope.guna;

import java.util.List;

public record GunaMilanReport(String ruleSetVersion, List<KootaScore> scores, double total, double max,
                              String band, List<String> doshaFlags) {}
