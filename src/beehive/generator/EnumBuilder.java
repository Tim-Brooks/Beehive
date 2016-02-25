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
import net.uncontended.precipice.Failable;

import java.io.IOException;
import java.util.*;

public class EnumBuilder {

    private static final Object lock = new Object();
    private static long count = 0;
    private static final Map<Set<String>, String> rejectedCache = new HashMap<>();
    private static final Map<Map<String, Boolean>, String> resultCache = new HashMap<>();

    public static String buildRejectedEnum(List<String> keywords) throws IOException {
        Set<String> setOfKeywords = new HashSet<>(keywords);
        if (!rejectedCache.containsKey(setOfKeywords)) {
            synchronized (lock) {
                String className = "BeehiveEnum" + count++;
                String cpath = "beehive.generator." + className;
                DynamicType.Unloaded<? extends Enum<?>> enumType = new ByteBuddy()
                        .makeEnumeration(keywords)
                        .name(cpath)
                        .make();
                rejectedCache.put(setOfKeywords, cpath);
                Compiler.writeClassFile(cpath.replace('.', '/'), enumType.getBytes());
                return cpath;
            }
        } else {
            return rejectedCache.get(setOfKeywords);
        }
    }

    public static String buildResultEnum(Map<String, Boolean> enumToFailed) throws IOException {
        if (!resultCache.containsKey(enumToFailed)) {
            synchronized (lock) {
                String className = "BeehiveEnum" + count++;
                String cpath = "beehive.generator." + className;
                DynamicType.Unloaded<?> enumType = new ByteBuddy()
                        .makeEnumeration("d") // Fix
                        .name(cpath)
                        .implement(Failable.class)
                        .make();
                resultCache.put(enumToFailed, cpath);
                Compiler.writeClassFile(cpath.replace('.', '/'), enumType.getBytes());
                return cpath;
            }
        } else {
            return resultCache.get(enumToFailed);
        }
    }
}
}
