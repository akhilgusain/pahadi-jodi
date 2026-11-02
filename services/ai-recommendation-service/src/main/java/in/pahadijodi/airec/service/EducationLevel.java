package in.pahadijodi.airec.service;

/** Coarse education bucket from free text (used for embeddings and the education alignment score). */
public enum EducationLevel {
    UNKNOWN, SCHOOL, DIPLOMA, GRADUATE, POSTGRADUATE, DOCTORATE;

    public static EducationLevel of(String text) {
        if (text == null || text.isBlank()) return UNKNOWN;
        String t = text.toLowerCase().replace(".", "").replace(" ", "");
        if (t.contains("phd") || t.contains("doctorate") || t.contains("md") && !t.contains("mdes")) return DOCTORATE;
        if (t.contains("mtech") || t.contains("mba") || t.contains("msc") || t.contains("mca") || t.contains("masters")
                || t.contains("postgrad") || t.contains("ma") && t.length() <= 4 || t.contains("mcom") || t.contains("llm")) return POSTGRADUATE;
        if (t.contains("btech") || t.contains("be") && t.length() <= 4 || t.contains("bsc") || t.contains("bca") || t.contains("bcom")
                || t.contains("ba") && t.length() <= 4 || t.contains("bachelor") || t.contains("graduat") || t.contains("llb") || t.contains("mbbs")
                || t.contains("bba")) return GRADUATE;
        if (t.contains("diploma") || t.contains("iti") || t.contains("polytechnic")) return DIPLOMA;
        if (t.contains("12th") || t.contains("10th") || t.contains("intermediate") || t.contains("highschool") || t.contains("school")) return SCHOOL;
        return UNKNOWN;
    }
}
