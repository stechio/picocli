package picocli.util;

import java.util.Collections;
import java.util.List;

public class CollectionUtilsExt {
    public static <T> List<T> reverse(List<T> list) {
        Collections.reverse(list);
        return list;
    }
}
