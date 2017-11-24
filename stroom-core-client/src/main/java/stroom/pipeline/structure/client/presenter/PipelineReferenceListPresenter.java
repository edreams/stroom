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

package stroom.pipeline.structure.client.presenter;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.AlertEvent;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.pipeline.shared.FetchDocRefsAction;
import stroom.pipeline.shared.PipelineDocument;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.shared.data.SourcePipeline;
import stroom.query.api.v2.DocRef;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PipelineReferenceListPresenter extends MyPresenterWidget<DataGridView<PipelineReference>>
        implements HasDirtyHandlers {
    private static final SafeHtml ADDED = SafeHtmlUtils.fromSafeConstant("<div style=\"font-weight:500\">");
    private static final SafeHtml REMOVED = SafeHtmlUtils
            .fromSafeConstant("<div style=\"font-weight:500;text-decoration:line-through\">");
    private static final SafeHtml INHERITED = SafeHtmlUtils.fromSafeConstant("<div style=\"color:black\">");
    private static final SafeHtml END = SafeHtmlUtils.fromSafeConstant("</div>");
    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final Map<PipelineReference, State> referenceStateMap = new HashMap<>();
    private final List<PipelineReference> references = new ArrayList<>();
    private final Provider<NewPipelineReferencePresenter> newPipelineReferencePresenter;
    private final ClientDispatchAsync dispatcher;

    private Map<PipelineElementType, Map<String, PipelinePropertyType>> allPropertyTypes;
    private PipelineDocument pipeline;
    private PipelineModel pipelineModel;
    private PipelineElement currentElement;
    private PipelinePropertyType propertyType;

    @Inject
    public PipelineReferenceListPresenter(final EventBus eventBus,
                                          final Provider<NewPipelineReferencePresenter> newPipelineReferencePresenter,
                                          final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.newPipelineReferencePresenter = newPipelineReferencePresenter;
        this.dispatcher = dispatcher;

        addButton = getView().addButton(SvgPresets.NEW_ITEM);
        addButton.setTitle("New Reference");
        addButton.setEnabled(false);

        editButton = getView().addButton(SvgPresets.EDIT);
        editButton.setTitle("Edit Reference");
        editButton.setEnabled(false);

        removeButton = getView().addButton(SvgPresets.REMOVE);
        removeButton.setTitle("Remove Refefence");
        removeButton.setEnabled(false);

        addColumns();
    }

    @Override
    protected void onBind() {
        registerHandler(getView().getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit(getView().getSelectionModel().getSelected());
            }
        }));
        registerHandler(addButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onAdd(event);
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onEdit(getView().getSelectionModel().getSelected());
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onRemove();
            }
        }));
    }

    private void addColumns() {
        addPipelineColumn();
        addFeedColumn();
        addStreamTypeColumn();
        addInheritedFromColumn();

        addEndColumn();
    }

    private void addPipelineColumn() {
        // Pipeline.
        getView().addResizableColumn(new Column<PipelineReference, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getPipeline() == null) {
                    return null;
                }
                return getSafeHtmlWithState(pipelineReference, pipelineReference.getPipeline().getName());
            }
        }, "Pipeline", 200);
    }

    private void addFeedColumn() {
        // Feed.
        getView().addResizableColumn(new Column<PipelineReference, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getFeed() == null) {
                    return null;
                }
                return getSafeHtmlWithState(pipelineReference, pipelineReference.getFeed().getName());
            }
        }, "Feed", 200);
    }

    private void addStreamTypeColumn() {
        // Stream type.
        getView().addResizableColumn(new Column<PipelineReference, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getStreamType() == null) {
                    return null;
                }
                return getSafeHtmlWithState(pipelineReference, pipelineReference.getStreamType());
            }
        }, "Stream Type", 200);
    }

    private void addInheritedFromColumn() {
        // Default Value.
        getView().addResizableColumn(new Column<PipelineReference, String>(new TextCell()) {
            @Override
            public String getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getSource().getPipeline() == null
                        || pipelineReference.getSource().getPipeline().equals(pipeline)) {
                    return null;
                }
                return pipelineReference.getSource().getPipeline().getName();
            }
        }, "Inherited From", 100);
    }

    private void addEndColumn() {
        getView().addEndColumn(new EndColumn<>());
    }

    private SafeHtml getSafeHtmlWithState(final PipelineReference pipelineReference, final String string) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        final State state = referenceStateMap.get(pipelineReference);
        switch (state) {
            case ADDED:
                builder.append(ADDED);
                break;
            case REMOVED:
                builder.append(REMOVED);
                break;
            case INHERITED:
                builder.append(INHERITED);
                break;
        }

        builder.appendEscaped(string);
        builder.append(END);

        return builder.toSafeHtml();
    }

    public void setPipeline(final PipelineDocument pipeline) {
        this.pipeline = pipeline;
    }

    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    public void setCurrentElement(final PipelineElement currentElement) {
        this.currentElement = currentElement;

        // Discover the reference property type.
        this.propertyType = null;
        if (currentElement != null && allPropertyTypes != null) {
            final Map<String, PipelinePropertyType> propertyTypes = allPropertyTypes
                    .get(currentElement.getElementType());
            if (propertyTypes != null) {
                for (final PipelinePropertyType propertyType : propertyTypes.values()) {
                    if (propertyType.isPipelineReference()) {
                        this.propertyType = propertyType;
                    }
                }
            }
        }

        refresh();
    }

    public void setPropertyTypes(final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes) {
        this.allPropertyTypes = propertyTypes;
    }

    private void onAdd(final ClickEvent event) {
        if (currentElement != null) {
            final PipelineReference pipelineReference = new PipelineReference(currentElement.getId(),
                    propertyType.getName(), null, null, null);
            pipelineReference.setPropertyType(propertyType);
            pipelineReference.setSource(new SourcePipeline(pipeline));
            showEditor(pipelineReference, true);
        }
    }

    private void onEdit(final PipelineReference pipelineReference) {
        if (pipelineReference != null) {
            // Only allow edit of added references.
            final State state = referenceStateMap.get(pipelineReference);
            if (State.ADDED.equals(state)) {
                showEditor(pipelineReference, false);
            }
        }
    }

    private void showEditor(final PipelineReference pipelineReference, final boolean isNew) {
        if (pipelineReference != null) {
            final List<PipelineReference> added = pipelineModel.getPipelineData().getAddedPipelineReferences();
            if (added.contains(pipelineReference)) {
                added.remove(pipelineReference);
            }

            final NewPipelineReferencePresenter editor = newPipelineReferencePresenter.get();
            editor.read(pipelineReference);

            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        editor.write(pipelineReference);

                        if (pipelineReference.getPipeline() == null) {
                            AlertEvent.fireError(PipelineReferenceListPresenter.this,
                                    "You must specify a pipeline to use.", null);
                        } else if (pipelineReference.getFeed() == null) {
                            AlertEvent.fireError(PipelineReferenceListPresenter.this, "You must specify a feed to use.",
                                    null);
                        } else if (pipelineReference.getStreamType() == null) {
                            AlertEvent.fireError(PipelineReferenceListPresenter.this,
                                    "You must specify a stream type to use.", null);
                        } else {
                            if (!added.contains(pipelineReference)) {
                                added.add(pipelineReference);
                            }

                            setDirty(isNew || editor.isDirty());
                            refresh();
                            HidePopupEvent.fire(PipelineReferenceListPresenter.this, editor);
                        }
                    } else {
                        // User has cancelled edit so add the reference back to
                        // the list if this was an existing reference
                        if (!isNew) {
                            if (!added.contains(pipelineReference)) {
                                added.add(pipelineReference);
                            }
                        }

                        HidePopupEvent.fire(PipelineReferenceListPresenter.this, editor);
                    }
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    // Do nothing.
                }
            };

            final PopupSize popupSize = new PopupSize(300, 153, 300, 153, 2000, 153, true);
            if (isNew) {
                ShowPopupEvent.fire(this, editor, PopupType.OK_CANCEL_DIALOG, popupSize, "New Pipeline Reference",
                        popupUiHandlers);
            } else {
                ShowPopupEvent.fire(this, editor, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Pipeline Reference",
                        popupUiHandlers);
            }
        }
    }

    private void onRemove() {
        final PipelineReference selected = getView().getSelectionModel().getSelected();
        if (selected != null) {
            if (pipelineModel.getPipelineData().getAddedPipelineReferences().contains(selected)) {
                pipelineModel.getPipelineData().getAddedPipelineReferences().remove(selected);
                pipelineModel.getPipelineData().getRemovedPipelineReferences().remove(selected);

            } else {
                if (pipelineModel.getPipelineData().getRemovedPipelineReferences().contains(selected)) {
                    pipelineModel.getPipelineData().getRemovedPipelineReferences().remove(selected);
                } else {
                    pipelineModel.getPipelineData().getRemovedPipelineReferences().add(selected);
                }
            }

            setDirty(true);
            refresh();
        }
    }

    private void refresh() {
        referenceStateMap.clear();
        references.clear();
        getView().getSelectionModel().clear();

        if (currentElement != null) {
            final String id = currentElement.getId();
            if (id != null) {
                final Map<String, List<PipelineReference>> baseReferences = pipelineModel.getBaseData()
                        .getPipelineReferences().get(id);
                if (baseReferences != null) {
                    for (final List<PipelineReference> list : baseReferences.values()) {
                        for (final PipelineReference reference : list) {
                            referenceStateMap.put(reference, State.INHERITED);
                        }
                    }
                }
                for (final PipelineReference reference : pipelineModel.getPipelineData().getAddedPipelineReferences()) {
                    if (id.equals(reference.getElement())) {
                        referenceStateMap.put(reference, State.ADDED);
                    }
                }
                for (final PipelineReference reference : pipelineModel.getPipelineData()
                        .getRemovedPipelineReferences()) {
                    if (id.equals(reference.getElement())) {
                        referenceStateMap.put(reference, State.REMOVED);
                    }
                }

                references.addAll(referenceStateMap.keySet());
                Collections.sort(this.references);
            }
        }

        // See if we need to load accurate doc refs (we do this to get correct entity names for display)
        final Set<DocRef> docRefs = new HashSet<>();
        references.forEach(ref -> addPipelineReference(docRefs, ref));
        if (docRefs.size() > 0) {
            // Load entities.
            dispatcher.exec(new FetchDocRefsAction(docRefs)).onSuccess(result -> {
                final Map<DocRef, DocRef> fetchedDocRefs = result
                        .stream()
                        .collect(Collectors.toMap(Function.identity(), Function.identity()));

                for (final PipelineReference reference : references) {
                    reference.setFeed(resolve(fetchedDocRefs, reference.getFeed()));
                    reference.setPipeline(resolve(fetchedDocRefs, reference.getPipeline()));
                }

                setData(references);
            });
        } else {
            setData(references);
        }
    }

    private DocRef resolve(final Map<DocRef, DocRef> map, final DocRef docRef) {
        if (docRef == null) {
            return null;
        }

        final DocRef fetchedDocRef = map.get(docRef);
        if (fetchedDocRef != null) {
            return fetchedDocRef;
        }

        return docRef;
    }

    private void addPipelineReference(final Set<DocRef> docRefs, PipelineReference reference) {
        if (reference.getFeed() != null) {
            docRefs.add(reference.getFeed());
        }
        if (reference.getPipeline() != null) {
            docRefs.add(reference.getPipeline());
        }
    }

    private void setData(final List<PipelineReference> references) {
        getView().setRowData(0, references);
        getView().setRowCount(references.size());
        enableButtons();
    }

    protected void enableButtons() {
        addButton.setEnabled(propertyType != null);

        final PipelineReference selected = getView().getSelectionModel().getSelected();
        final State state = referenceStateMap.get(selected);

        editButton.setEnabled(State.ADDED.equals(state));
        removeButton.setEnabled(selected != null);
    }

    protected void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    private enum State {
        INHERITED, ADDED, REMOVED
    }
}
