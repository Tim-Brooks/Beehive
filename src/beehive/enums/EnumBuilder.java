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
package beehive.enums;

import clojure.lang.Compiler;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.uncontended.precipice.Failable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class EnumBuilder {

    private static final Object lock = new Object();
    private static long rejectedCount = 0;
    private static long resultCount = 0;
    private static final Map<Set<String>, String> rejectedCache = new HashMap<>();
    private static final Map<Set<String>, String> resultCache = new HashMap<>();

    static {
        rejectedCache.put(new HashSet<String>(), "net.uncontended.precipice.rejected.Unrejectable");
    }

    public static String buildRejectedEnum(List<String> keywords) throws IOException {
        Set<String> setOfKeywords = new HashSet<>(keywords);
        if (!rejectedCache.containsKey(setOfKeywords)) {
            synchronized (lock) {
                String className = "BeehiveRejected" + rejectedCount++;
                String cpath = "beehive.enums." + className;
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

    public static String buildResultEnum(List<String> enums) throws IOException {
        Set<String> setOfEnums = new HashSet<>(enums);
        if (!resultCache.containsKey(setOfEnums)) {
            synchronized (lock) {
                String className = "BeehiveResult" + resultCount++;
                String cpath = "beehive.enums." + className;
                DynamicType.Unloaded<? extends Enum<?>> enumType = new ByteBuddy()
                        .makeEnumeration(enums)
                        .implement(Failable.class)
                        .defineField("isFailure", boolean.class, Visibility.PRIVATE, FieldManifestation.FINAL)
                        .defineField("isSuccess", boolean.class, Visibility.PRIVATE, FieldManifestation.FINAL)
                        .constructor(any()).intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.to(FailableBuilder.class)))
                        .method(named("isFailure")).intercept(FieldAccessor.ofField("isFailure"))
                        .method(named("isSuccess")).intercept(FieldAccessor.ofField("isSuccess"))
                        .name(cpath)
                        .make();
                resultCache.put(setOfEnums, cpath);
                Compiler.writeClassFile(cpath.replace('.', '/'), enumType.getBytes());
                return cpath;
            }
        } else {
            return resultCache.get(setOfEnums);
        }
    }

    public static class FailableBuilder {

        public static void construct(@This Object o, String name, int ordinal) throws NoSuchFieldException, IllegalAccessException {
            boolean isFailure = name.endsWith("_F");
            Field failureField = o.getClass().getDeclaredField("isFailure");
            failureField.setAccessible(true);
            failureField.set(o, isFailure);
            Field successField = o.getClass().getDeclaredField("isSuccess");
            successField.setAccessible(true);
            successField.set(o, !isFailure);
        }
    }
}

