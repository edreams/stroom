/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.pipeline.server;

import org.springframework.stereotype.Component;
import stroom.docstore.server.JsonSerialiser;
import stroom.docstore.server.Store;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.pipeline.shared.PipelineDocument;
import stroom.query.api.v2.DocRef;
import stroom.util.shared.Message;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class PipelineDocumentServiceImpl implements PipelineDocumentService {
    private final Store<PipelineDocument> store;

    @Inject
    public PipelineDocumentServiceImpl(final Store<PipelineDocument> store) throws IOException {
        this.store = store;
        store.setType(PipelineDocument.DOCUMENT_TYPE, PipelineDocument.class);
        store.setSerialiser(new JsonSerialiser<>());
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name, final String parentFolderUUID) {
        return store.createDocument(name, parentFolderUUID);
    }

    @Override
    public DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        return store.copyDocument(uuid, parentFolderUUID);
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        return store.moveDocument(uuid, parentFolderUUID);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        return store.renameDocument(uuid, name);
    }

    @Override
    public void deleteDocument(final String uuid) {
        store.deleteDocument(uuid);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public PipelineDocument readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public PipelineDocument writeDocument(final PipelineDocument document) {
        return store.writeDocument(document);
    }

    @Override
    public PipelineDocument forkDocument(final PipelineDocument document, String name, DocRef destinationFolderRef) {
        return store.forkDocument(document, name, destinationFolderRef);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef importDocument(final DocRef docRef, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        return store.importDocument(docRef, dataMap, importState, importMode);
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public String getDocType() {
        return PipelineDocument.DOCUMENT_TYPE;
    }

    @Override
    public PipelineDocument read(final String uuid) {
        return store.read(uuid);
    }

    @Override
    public PipelineDocument update(final PipelineDocument dataReceiptPolicy) {
        return store.update(dataReceiptPolicy);
    }
}
