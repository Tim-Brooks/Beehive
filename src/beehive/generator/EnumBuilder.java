/*
 * Copyright 2016 Timothy Brooks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package beehive.generator;

import clojure.lang.Compiler;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;

import java.io.IOException;
import java.util.*;

public class EnumBuilder {

    private static final Object lock = new Object();
    private static long count = 0;
    private static final Map<Set<String>, String> classCache = new HashMap<>();

    public static String build(List<String> keywords) throws IOException {
        Set<String> setOfKeywords = new HashSet<>(keywords);
        if (!classCache.containsKey(setOfKeywords)) {
            synchronized (lock) {
                String className = "BeehiveEnum" + count++;
                String cpath = "beehive.generator." + className;
                DynamicType.Unloaded<? extends Enum<?>> enumType = new ByteBuddy()
                        .makeEnumeration(keywords)
                        .name(cpath)
                        .make();
                classCache.put(setOfKeywords, cpath);
                Compiler.writeClassFile(cpath.replace('.', '/'), enumType.getBytes());
                return cpath;
            }
        } else {
            return classCache.get(setOfKeywords);
        }
    }
}
