/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.entity;

import org.junit.Before;
import org.junit.Test;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;

public class TestEntityServiceImpl extends AbstractCoreIntegrationTest {
    @Inject
    private EntityServiceImplTestTransactionHelper helper;

    @Before
    public void init() {
        helper.init();
    }

    @Test
    public void test1() {
        helper.test1();
    }

    @Test
    public void test2() {
        helper.test2();
    }
}
