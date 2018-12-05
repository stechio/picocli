package picocli.except;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import picocli.util.CollectionUtilsExt;

/**
 * Uses cosine similarity to find matches from a candidate set for a specified input. Based on
 * code from
 * http://www.nearinfinity.com/blogs/seth_schroeder/groovy_cosine_similarity_in_grails.html
 *
 * @author Burt Beckwith
 */
public class CosineSimilarity {
    public static List<String> mostSimilar(String pattern, Iterable<String> candidates) {
        return mostSimilar(pattern, candidates, 0);
    }

    static List<String> mostSimilar(String pattern, Iterable<String> candidates,
            double threshold) {
        pattern = pattern.toLowerCase();
        SortedMap<Double, String> sorted = new TreeMap<Double, String>();
        for (String candidate : candidates) {
            double score = similarity(pattern, candidate.toLowerCase(), 2);
            if (score > threshold) {
                sorted.put(score, candidate);
            }
        }
        return CollectionUtilsExt.reverse(new ArrayList<String>(sorted.values()));
    }

    private static double similarity(String sequence1, String sequence2, int degree) {
        Map<String, Integer> m1 = countNgramFrequency(sequence1, degree);
        Map<String, Integer> m2 = countNgramFrequency(sequence2, degree);
        return dotProduct(m1, m2) / Math.sqrt(dotProduct(m1, m1) * dotProduct(m2, m2));
    }

    private static Map<String, Integer> countNgramFrequency(String sequence, int degree) {
        Map<String, Integer> m = new HashMap<String, Integer>();
        for (int i = 0; i + degree <= sequence.length(); i++) {
            String gram = sequence.substring(i, i + degree);
            m.put(gram, 1 + (m.containsKey(gram) ? m.get(gram) : 0));
        }
        return m;
    }

    private static double dotProduct(Map<String, Integer> m1, Map<String, Integer> m2) {
        double result = 0;
        for (String key : m1.keySet()) {
            result += m1.get(key) * (m2.containsKey(key) ? m2.get(key) : 0);
        }
        return result;
    }
}