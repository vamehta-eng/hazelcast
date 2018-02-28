/*
 * Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
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
 */

package com.hazelcast.internal.networking.nio.iobalancer;

import com.hazelcast.internal.networking.nio.MigratablePipeline;
import com.hazelcast.internal.networking.nio.NioThread;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.util.ItemCounter;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.hazelcast.test.TestCollectionUtils.setOf;
import static java.lang.Math.abs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class MonkeyMigrationStrategyTest extends HazelcastTestSupport {
    private MigrationStrategy strategy;

    private Map<NioThread, Set<MigratablePipeline>> selectorToHandlers;
    private ItemCounter<MigratablePipeline> handlerEventsCounter;
    private LoadImbalance imbalance;

    @Test
    public void imbalanceDetected_shouldReturnFalseWhenNoHandlerExist() {
        selectorToHandlers.put(imbalance.sourceSelector, Collections.<MigratablePipeline>emptySet());

        boolean imbalanceDetected = strategy.imbalanceDetected(imbalance);
        assertFalse(imbalanceDetected);
    }

    @Before
    public void setUp() {
        selectorToHandlers = new HashMap<NioThread, Set<MigratablePipeline>>();
        handlerEventsCounter = new ItemCounter<MigratablePipeline>();
        imbalance = new LoadImbalance(selectorToHandlers, handlerEventsCounter);
        imbalance.sourceSelector = mock(NioThread.class);

        this.strategy = new MonkeyMigrationStrategy();
    }

    @Test
    public void imbalanceDetected_shouldReturnTrueWhenHandlerExist() {
        MigratablePipeline handler = mock(MigratablePipeline.class);

        selectorToHandlers.put(imbalance.sourceSelector, setOf(handler));
        boolean imbalanceDetected = strategy.imbalanceDetected(imbalance);
        assertTrue(imbalanceDetected);
    }

    @Test
    public void findHandlerToMigrate_shouldWorkEvenWithASingleHandlerAvailable() {
        MigratablePipeline handler = mock(MigratablePipeline.class);

        selectorToHandlers.put(imbalance.sourceSelector, setOf(handler));
        MigratablePipeline handlerToMigrate = strategy.findPipelineToMigrate(imbalance);
        assertEquals(handler, handlerToMigrate);
    }

    @Test
    public void findHandlerToMigrate_shouldBeFair() {
        int iterationCount = 10000;
        double toleranceFactor = 0.25d;

        MigratablePipeline handler1 = mock(MigratablePipeline.class);
        MigratablePipeline handler2 = mock(MigratablePipeline.class);
        selectorToHandlers.put(imbalance.sourceSelector, setOf(handler1, handler2));

        assertFairSelection(iterationCount, toleranceFactor, handler1, handler2);
    }

    private void assertFairSelection(int iterationCount, double toleranceFactor, MigratablePipeline handler1, MigratablePipeline handler2) {
        int handler1Count = 0;
        int handler2Count = 0;
        for (int i = 0; i < iterationCount; i++) {
            MigratablePipeline candidate = strategy.findPipelineToMigrate(imbalance);
            if (candidate == handler1) {
                handler1Count++;
            } else if (candidate == handler2) {
                handler2Count++;
            } else {
                fail("No handler selected");
            }
        }
        int diff = abs(handler1Count - handler2Count);
        assertTrue(diff < (iterationCount * toleranceFactor));
    }


}