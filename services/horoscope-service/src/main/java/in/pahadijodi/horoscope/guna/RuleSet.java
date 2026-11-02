package in.pahadijodi.horoscope.guna;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/** Versioned seed tables for Ashtakoot (classpath rules/guna-v1.json). Data, not code, so variants are a file swap. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleSet(
        String version, List<String> rashiNames, List<String> nakshatraNames,
        List<Integer> varnaByRashi, List<String> lordByRashi,
        List<String> vashyaGroupByRashi, Map<String, List<String>> vashyaSplit, double vashyaSplitDegree,
        List<String> vashyaOrder, List<List<Double>> vashyaMatrix,
        List<String> yoniAnimalByNakshatra, List<String> yoniOrder, List<List<Integer>> yoniMatrix,
        List<String> ganaByNakshatra, List<String> ganaOrder, List<List<Integer>> ganaMatrixBoyRowGirlCol,
        List<String> nadiByNakshatra, Map<String, Friendship> friendship) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Friendship(List<String> friends, List<String> neutral, List<String> enemies) {}

    public static RuleSet load(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            RuleSet rs = new ObjectMapper().readValue(in, RuleSet.class);
            rs.validate();
            return rs;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Guna rule set " + resource, e);
        }
    }

    void validate() {
        require(rashiNames.size() == 12 && varnaByRashi.size() == 12 && lordByRashi.size() == 12 && vashyaGroupByRashi.size() == 12, "12 rashi tables");
        require(nakshatraNames.size() == 27 && yoniAnimalByNakshatra.size() == 27 && ganaByNakshatra.size() == 27 && nadiByNakshatra.size() == 27, "27 nakshatra tables");
        require(yoniMatrix.size() == yoniOrder.size() && yoniOrder.size() == 14, "yoni 14x14");
        for (var row : yoniMatrix) require(row.size() == 14, "yoni row size");
        require(vashyaMatrix.size() == 5 && ganaMatrixBoyRowGirlCol.size() == 3, "vashya 5x5 and gana 3x3");
    }

    private static void require(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("Invalid rule set: " + what);
    }
}
