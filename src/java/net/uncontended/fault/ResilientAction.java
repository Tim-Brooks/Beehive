package net.uncontended.fault;

/**
 * Created by timbrooks on 11/4/14.
 */
public interface ResilientAction<T> {

    T run() throws Exception;
}
