# Beehive

Beehive is a Clojure façade for the Precipice library. [Precipice](https://github.com/tbrooks8/Precipice) is a
library designed to manage access to services.

## Version

This library has not yet hit alpha. It is used in production at Staples SparX. However, the API may still change.

## Usage

```
(ns your-namespace
  (:require [uncontended.beehive.core :as beehive]))

(def service (beehive/service))

(defn submit []
  @(beehive/submit-action (fn [] (* 8 8)) 10))

(defn perform []
  (beehive/perform-action (fn [] (throw (RuntimeException.)))))
```

## License

Copyright © 2014 Tim Brooks

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.