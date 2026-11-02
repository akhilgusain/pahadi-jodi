package in.pahadijodi.horoscope.guna;

/** @param cancelled true when a zero score was restored by a traditional cancellation rule */
public record KootaScore(String koota, double points, double max, boolean cancelled, String detail) {}
