package fault.java.concurrent;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by timbrooks on 11/21/14.
 */
public class ExchangingQueueTest {

    private ExchangingQueue<Integer> exchangingQueue;

    @Before
    public void setUp() {
        exchangingQueue = new ExchangingQueue<>(10);
    }

    @Test
    public void testPollReturnsNullWhenQueueEmpty() {
        assertNull(exchangingQueue.poll());

        assertTrue(exchangingQueue.offer(1));

        assertNotNull(exchangingQueue.poll());
        assertNull(exchangingQueue.poll());
    }

    @Test
    public void testOfferAddsToTailAndPollRemovesFromHead() {
        exchangingQueue.offer(1);
        exchangingQueue.offer(2);
        exchangingQueue.offer(3);

        assertEquals(Integer.valueOf(1), exchangingQueue.poll());
    }

    @Test
    public void testOfferReturnsFalseIfNoSpace() {
        exchangingQueue = new ExchangingQueue<>(1);

        assertTrue(exchangingQueue.offer(1));
        assertFalse(exchangingQueue.offer(2));
    }

    @Test
    public void testOfferWillWrapAroundArray() {
        exchangingQueue = new ExchangingQueue<>(1);

        assertTrue(exchangingQueue.offer(1));
        assertEquals(Integer.valueOf(1), exchangingQueue.poll());
        assertTrue(exchangingQueue.offer(2));
    }
}
