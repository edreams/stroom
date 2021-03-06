/*
 * Copyright 2018 Crown Copyright
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

package stroom.refdata;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.shared.Clearable;
import stroom.pipeline.factory.Element;
import stroom.refdata.store.RefDataStoreModule;
import stroom.task.TaskHandler;

public class ReferenceDataModule extends AbstractModule {

    @Override
    protected void configure() {

        final Multibinder<Element> elementBinder = Multibinder.newSetBinder(binder(), Element.class);
        elementBinder.addBinding().to(ReferenceDataFilter.class);

        bind(ReferenceDataLoader.class).to(ReferenceDataLoaderImpl.class);
        bind(ContextDataLoader.class).to(ContextDataLoaderImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(EffectiveStreamCache.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.refdata.ContextDataLoadTaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.refdata.ReferenceDataLoadTaskHandler.class);

        install(new RefDataStoreModule());
    }
}