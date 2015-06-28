;; Copyright 2014 Timothy Brooks
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns beehive.async
  (:require [clojure.core.async :refer [>!!]]))


(defn return-channels [{:keys [success timed-out error failed any]}]
  (fn [future]
    (when any
      (>!! any future))
    (condp = (:status future)
      :success (when success (>!! success future))
      :timed-out (do (when timed-out (>!! timed-out future))
                     (when failed (>!! failed future)))
      :error (do (when error (>!! error future))
                 (when failed (>!! failed future))))))