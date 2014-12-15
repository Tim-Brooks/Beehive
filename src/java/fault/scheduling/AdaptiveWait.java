package fault.scheduling;

import java.util.concurrent.locks.LockSupport;

/**
 * Created by timbrooks on 12/14/14.
 */
public final class AdaptiveWait implements WaitStrategy {
    @Override
    public int executeWait(int spinCount) {
        int currentSpin = --spinCount;
        if (0 == currentSpin) {
            currentSpin = 1000;
            LockSupport.parkNanos(1);
        } else if (50 > currentSpin) {
            Thread.yield();
        }
        return currentSpin;
    }
}
