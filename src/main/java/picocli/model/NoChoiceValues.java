package picocli.model;

import java.util.Collections;
import java.util.Iterator;

public class NoChoiceValues implements Iterable<String> {
    public Iterator<String> iterator() {
        return Collections.<String>emptyList().iterator();
    }
}