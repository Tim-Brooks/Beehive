package fault.metrics;

import fault.concurrent.LongAdder;

/**
 * Created by timbrooks on 6/1/15.
 */
public class Second {

    private final LongAdder successes = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder circuitOpen = new LongAdder();
    private final LongAdder queueFull = new LongAdder();
    private final LongAdder maxConcurrencyExceeded = new LongAdder();
}
