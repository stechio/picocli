package picocli.model;

import java.util.Collections;
import java.util.Iterator;

public class NoCompletionCandidates implements Iterable<String> {
    public Iterator<String> iterator() {
        return Collections.<String>emptyList().iterator();
    }
}