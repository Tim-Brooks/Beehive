package fault.utils;

/**
 * Created by timbrooks on 6/4/15.
 */
public interface ResilientPatternAction<T, C> {

    T run(C context) throws Exception;
}
