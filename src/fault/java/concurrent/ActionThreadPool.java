package fault.java.concurrent;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.Executor;

/**
 * Created by timbrooks on 12/2/14.
 */
public class ActionThreadPool implements Executor {

    private final NavigableSet<ThreadManager> pool;

    public ActionThreadPool(int threadCount) {
        pool = new TreeSet<>(new Comparator<ThreadManager>() {
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

        for (int i = 0; i < threadCount; ++i) {
            pool.add(new ThreadManager());
        }

    }

    @Override
    public void execute(Runnable action) {
        ThreadManager nextThread = pool.pollFirst();
        nextThread.submit(action);
        pool.add(nextThread);
    }

    public void signalTaskComplete(ThreadManager threadManager) {
        threadManager.decrementScheduledCount();
        pool.remove(threadManager);
        pool.add(threadManager);
    }

    private class ThreadManager {
        private final ExchangingQueue<Runnable> queue = new ExchangingQueue<>(10);
        private final Thread thread;
        // Volatile? Or Interrupt?
        private int scheduledCount = 0;

        public ThreadManager() {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (; ; ) {
                        Runnable runnable = queue.blockingPoll();
                        runnable.run();
                        // Need to explore this strategy more.
                        if (thread.isInterrupted()) {
                            return;
                        }
                    }
                }
            });
            thread.run();
        }

        private boolean submit(Runnable task) {
            boolean offered = queue.offer(task);
            if (offered) {
                ++scheduledCount;
            }
            return offered;
        }

        private void decrementScheduledCount() {
            --scheduledCount;
        }

        private int getScheduledCount() {
            return scheduledCount;
        }

        private void shutdown() {
            thread.interrupt();
        }
    }
}
