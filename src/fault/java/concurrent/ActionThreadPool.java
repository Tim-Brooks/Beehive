package fault.java.concurrent;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Created by timbrooks on 12/2/14.
 */
public class ActionThreadPool implements Executor {

    private final PriorityQueue<ThreadManager> pool;

    public ActionThreadPool(int threadCount) {
        pool = new PriorityQueue<>(new Comparator<ThreadManager>() {
            @Override
            public int compare(ThreadManager o1, ThreadManager o2) {
                int scheduledCount1 = o1.getScheduledCount();
                int scheduledCount2 = o2.getScheduledCount();
                if (scheduledCount1 > scheduledCount2) {
                    return 1;
                } else if (scheduledCount2 > scheduledCount1) {
                    return -1;
                }
                return 0;
            }
        });

    }

    @Override
    public void execute(Runnable action) {
        ThreadManager nextThread = pool.poll();
        nextThread.submit(action);
        pool.offer(nextThread);
    }

    private class ThreadManager {
        private int scheduledCount = 0;
        private final ExchangingQueue<Runnable> queue = new ExchangingQueue<>(10);

        private boolean submit(Runnable task) {
            boolean offered = queue.offer(task);
            if (offered) {
                ++scheduledCount;
            }
            return offered;
        }

        private int getScheduledCount() {
            return scheduledCount;
        }
    }
}
