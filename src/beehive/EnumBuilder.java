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

package beehive;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.uncontended.precipice.metrics.RollingCountMetrics;

public class EnumBuilder {

    public void build() {
        Class<?> dynamicType = new ByteBuddy()
                .makeEnumeration("BLUE", "GREEN")
                .name("RejectedType")
                .make()
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

        Enum enum1 = (Enum) dynamicType.getEnumConstants()[0];
        Enum enum2 = (Enum) dynamicType.getEnumConstants()[1];

        RollingCountMetrics metrics = new RollingCountMetrics(dynamicType);
        metrics.incrementMetricCount(enum1);
        System.out.println(metrics.getTotalMetricCount(enum1));
        System.out.println(metrics.getTotalMetricCount(enum2));
    }
}
