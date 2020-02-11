package firestore_clj;

import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.SetOptions;

public class VariadicHelper {
    /*
      Helpers for disambiguating variadic methods
     */
    public static Query select(Query q, String[] args) {
        return q.select(args);
    }

    public static Query startAt(Query q, Object[] args) {
        return q.startAt(args);
    }

    public static Query startAfter(Query q, Object[] args) {
        return q.startAfter(args);
    }

    public static Query endAt(Query q, Object[] args) {
        return q.endAt(args);
    }

    public static Query endBefore(Query q, Object[] args) {
        return q.endBefore(args);
    }

    public static SetOptions mergeFields(String[] fields) {
        return SetOptions.mergeFields(fields);
    }
}
