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
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
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
    private static long count = 0;
    private static final Map<Set<String>, String> rejectedCache = new HashMap<>();
    private static final Map<Map<String, Boolean>, String> resultCache = new HashMap<>();

    public static String buildRejectedEnum(List<String> keywords) throws IOException {
        Set<String> setOfKeywords = new HashSet<>(keywords);
        if (!rejectedCache.containsKey(setOfKeywords)) {
            synchronized (lock) {
                String className = "BeehiveRejected" + count++;
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
                String className = "BeehiveResult" + count++;
                String cpath = "beehive.generator." + className;
                Class<? extends Enum<?>> enumType = new ByteBuddy()
                        .makeEnumeration(enumToFailed.keySet())
                        .implement(Failable.class)
                        .defineField("isFailure", boolean.class, Visibility.PRIVATE, FieldManifestation.FINAL)
                        .defineField("isSuccess", boolean.class, Visibility.PRIVATE, FieldManifestation.FINAL)
                        .constructor(any()).intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.to(FailableBuilder.class)))
                        .method(named("isFailure")).intercept(FieldAccessor.ofField("isFailure"))
                        .method(named("isSuccess")).intercept(FieldAccessor.ofField("isSuccess"))
                        .name(cpath)
                        .make()
                        .load(EnumBuilder.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                        .getLoaded();

                Failable failable = (Failable) enumType.getEnumConstants()[0];
                System.out.println(failable);
                System.out.println(failable.isFailure());
                System.out.println(failable.isSuccess());

//                resultCache.put(enumToFailed, cpath);
//                Path file = Paths.get("/Users/timbrooks/development/Beehive/target/classes/beehive/generator/" + className + ".class");
//                Files.write(file, enumType.getBytes());

//                Compiler.writeClassFile(cpath.replace('.', '/'), enumType.getBytes());
                return cpath;
            }
        } else {
            return resultCache.get(enumToFailed);
        }
    }

    public static class FailableBuilder {

        public static void construct(@This Object o, String name, int number) throws NoSuchFieldException, IllegalAccessException {
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

