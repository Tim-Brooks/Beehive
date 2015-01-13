package fault.utils;

import fault.ResilientAction;

import java.util.concurrent.CountDownLatch;

/**
 * Created by timbrooks on 1/12/15.
 */
public class TestActions {

    public static ResilientAction<String> blockedAction(final CountDownLatch blockingLatch) {
        return new ResilientAction<String>() {

            @Override
            public String run() throws Exception {
                blockingLatch.await();
                return "Success";
            }
        };
    }

    public static ResilientAction<String> successAction(final long waitTime) {
        return successAction(waitTime, "Success");
    }

    public static ResilientAction<String> successAction(final long waitTime, final String result) {
        return new ResilientAction<String>() {
            @Override
            public String run() throws Exception {
                Thread.sleep(waitTime);
                return result;
            }
        };
    }
}
