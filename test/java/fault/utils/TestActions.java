package fault.utils;

import fault.ResilientAction;

import java.util.concurrent.CountDownLatch;

/**
 * Created by timbrooks on 1/12/15.
 */
public class TestActions {

    public static ResilientAction<String> blockedAction() {
        return new ResilientAction<String>() {

            private CountDownLatch blockingLatch = new CountDownLatch(1);

            @Override
            public String run() throws Exception {
                blockingLatch.await();
                return "Failure";
            }
        };
    }

    public static ResilientAction<String> successAction(final long waitTime) {
        return new ResilientAction<String>() {
            @Override
            public String run() throws Exception {
                Thread.sleep(waitTime);
                return "Success";
            }
        };
    }
}
