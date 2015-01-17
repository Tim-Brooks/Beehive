package fault;

/**
 * Created by timbrooks on 1/17/15.
 */
interface ResilientCallback<T> {

    public void run(ResilientPromise<T> resultPromse);
}
