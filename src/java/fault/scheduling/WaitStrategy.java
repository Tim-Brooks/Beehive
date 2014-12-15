package fault.scheduling;

/**
 * Created by timbrooks on 12/14/14.
 */
public interface WaitStrategy {

    public int executeWait(int spinCount);
}
