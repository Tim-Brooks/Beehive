package beehive.java;

import clojure.lang.ILookup;
import clojure.lang.Keyword;

public class BeehiveRejected extends RuntimeException implements ILookup {

    private final Keyword reason;
    private final Keyword rejectedReason = Keyword.intern("rejected-reason");

    public BeehiveRejected(Keyword reason) {
        this.reason = reason;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object defaultValue) {
        if (key == rejectedReason) {
            return reason;
        }
        return defaultValue;
    }
}
