package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by timbrooks on 1/19/15.
 */
public class HttpKitExecutor extends AbstractServiceExecutor {

    public final ThreadPoolExecutor callbackExecutor;
    private final String name;


    public HttpKitExecutor(int concurrencyLevel, String name, CircuitBreaker circuitBreaker, ActionMetrics
            actionMetrics) {
        super(circuitBreaker, actionMetrics);

        if (name == null) {
            this.name = this.toString();
        } else {
            this.name = name;
        }
        int poolSize = Runtime.getRuntime().availableProcessors();
        callbackExecutor = new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS, new
                ArrayBlockingQueue<Runnable>
                (concurrencyLevel * 2), new ServiceThreadFactory(name));
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, long millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientCallback<T> callback, long
            millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise, long
            millisTimeout) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise,
                                               ResilientCallback<T> callback, long millisTimeout) {
        HttpKitRequest<T> request = (HttpKitRequest) action;



        return null;
    }

    @Override
    public <T> ResilientPromise<T> performAction(ResilientAction<T> action) {
        return null;
    }

    @Override
    public void shutdown() {

    }

//    (defn request
//    "Issues an async HTTP request and returns a promise object to which the value
//    of `(callback {:opts _ :status _ :headers _ :body _})` or
//    `(callback {:opts _ :error _})` will be delivered.
//
//    When unspecified, `callback` is the identity
//
//    ;; Asynchronous GET request (returns a promise)
//    (request {:url \"http://www.cnn.com\"})
//
//    ;; Asynchronous GET request with callback
//            (request {:url \"http://www.cnn.com\" :method :get}
//        (fn [{:keys [opts status body headers error] :as resp}]
//        (if error
//                (println \"Error on\" opts)
//        (println \"Success on\" opts))))
//
//        ;; Synchronous requests
//        @(request ...) or (deref (request ...) timeout-ms timeout-val)
//
//        ;; Issue 2 concurrent requests, then wait for results
//                (let [resp1 (request ...)
//        resp2 (request ...)]
//        (println \"resp1's status: \" (:status @resp1))
//        (println \"resp2's status: \" (:status @resp2)))
//
//        Output coercion:
//        ;; Return the body as a byte stream
//        (request {:url \"http://site.com/favicon.ico\" :as :stream})
//        ;; Coerce as a byte-array
//                (request {:url \"http://site.com/favicon.ico\" :as :byte-array})
//        ;; return the body as a string body
//                (request {:url \"http://site.com/string.txt\" :as :text})
//        ;; Try to automatically coerce the output based on the content-type header, currently supports :text :stream, (with automatic charset detection)
//            (request {:url \"http://site.com/string.txt\" :as :auto})
//
//                Request options:
//                :url :method :headers :timeout :query-params :form-params :as
//                :client :body :basic-auth :user-agent :filter :worker-pool"
//                        [{:keys [client timeout filter worker-pool keepalive as follow-redirects max-redirects response]
//                :as opts
//                :or {client @default-client
//                    timeout 60000
//                    follow-redirects true
//                    max-redirects 10
//                    filter IFilter/ACCEPT_ALL
//                    worker-pool default-pool
//                    response (promise)
//                    keepalive 120000
//                    as :auto}}
//                & [callback]]
//                (let [{:keys [url method headers body sslengine]} (coerce-req opts)
//                deliver-resp #(deliver response ;; deliver the result
//                        (try ((or callback identity) %1)
//                (catch Exception e
//                        ;; dump stacktrace to stderr
//                (HttpUtils/printError (str method " " url "'s callback") e)
//                ;; return the error
//                {:opts opts :error e})))
//                handler (reify IResponseHandler
//                        (onSuccess [this status headers body]
//                (if (and follow-redirects
//                        (#{301 302 303 307 308} status)) ; should follow redirect
//                        (if (>= max-redirects (count (:trace-redirects opts)))
//                (request (assoc opts ; follow 301 and 302 redirect
//                :url (.toString ^URI (.resolve (URI. url) ^String
//                        (.get headers "location")))
//                :response response
//                :method (if (#{301 302 303} status)
//                :get ;; change to :GET
//                        (:method opts))  ;; do not change
//                :trace-redirects (conj (:trace-redirects opts) url))
//                callback)
//                (deliver-resp {:opts (dissoc opts :response)
//                    :error (Exception. (str "too many redirects: "
//                    (count (:trace-redirects opts))))}))
//                (deliver-resp {:opts    (dissoc opts :response)
//                    :body    body
//                    :headers (prepare-response-headers headers)
//                    :status  status})))
//                (onThrowable [this t]
//                (deliver-resp {:opts opts :error t})))
//                listener (RespListener. handler filter worker-pool
//                ;; only the 4 support now
//                (case as :auto 1 :text 2 :stream 3 :byte-array 4))
//                    cfg (RequestConfig. method headers body timeout keepalive)]
//                    (.exec ^HttpClient client url cfg sslengine listener)
//                    response))
}
