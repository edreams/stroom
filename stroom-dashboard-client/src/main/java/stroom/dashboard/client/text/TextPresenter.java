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
 */

package stroom.dashboard.client.text;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.HtmlPresenter;
import stroom.pipeline.shared.FetchDataAction;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchDataWithPipelineAction;
import stroom.pipeline.shared.PipelineDocument;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.Stream;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.Highlight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TextPresenter extends AbstractComponentPresenter<TextPresenter.TextView> implements TextUiHandlers {
    public static final ComponentType TYPE = new ComponentType(2, "text", "Text");
    private final Provider<EditorPresenter> rawPresenterProvider;
    private final Provider<HtmlPresenter> htmlPresenterProvider;
    private final ClientDispatchAsync dispatcher;
    private final ClientSecurityContext securityContext;
    private TextComponentSettings textSettings;
    private List<FetchDataAction> fetchDataQueue;
    private Timer delayedFetchDataTimer;
    private Long currentStreamId;
    private Long currentEventId;
    private Set<String> currentHighlightStrings;
    private boolean playButtonVisible;

    private TablePresenter currentTablePresenter;

    private EditorPresenter rawPresenter;
    private HtmlPresenter htmlPresenter;

    private boolean isHtml;

    @Inject
    public TextPresenter(final EventBus eventBus, final TextView view,
                         final Provider<TextSettingsPresenter> settingsPresenterProvider,
                         final Provider<EditorPresenter> rawPresenterProvider, final Provider<HtmlPresenter> htmlPresenterProvider, final ClientDispatchAsync dispatcher,
                         final ClientSecurityContext securityContext) {
        super(eventBus, view, settingsPresenterProvider);
        this.rawPresenterProvider = rawPresenterProvider;
        this.htmlPresenterProvider = htmlPresenterProvider;
        this.dispatcher = dispatcher;
        this.securityContext = securityContext;

        view.setUiHandlers(this);
    }

    private void showData(final String data, final String classification, final Set<String> highlightStrings,
                          final boolean isHtml) {
        final List<Highlight> highlights = getHighlights(data, highlightStrings);

        // Defer showing data to be sure that the data display has been made
        // visible first.
        Scheduler.get().scheduleDeferred(() -> {
            // Determine if we should show tha play button.
            playButtonVisible = !isHtml
                    && securityContext.hasAppPermission(PipelineDocument.STEPPING_PERMISSION);

            // Show the play button if we have fetched input data.
            getView().setPlayVisible(playButtonVisible);

            getView().setClassification(classification);
            if (isHtml) {
                if (htmlPresenter == null) {
                    htmlPresenter = htmlPresenterProvider.get();
                }

                getView().setContent(htmlPresenter.getView());
                htmlPresenter.setHtml(data);
            } else {
                if (rawPresenter == null) {
                    rawPresenter = rawPresenterProvider.get();
                    rawPresenter.setReadOnly(true);
                    rawPresenter.getLineNumbersOption().setOn(false);
                }

                getView().setContent(rawPresenter.getView());

                rawPresenter.setText(data);
                rawPresenter.format();
                rawPresenter.setHighlights(highlights);
                rawPresenter.setControlsVisible(playButtonVisible);
            }
        });
    }

    private List<Highlight> getHighlights(final String input, final Set<String> highlightStrings) {
        // final StringBuilder output = new StringBuilder(input);

        final List<Highlight> highlights = new ArrayList<>();

        // See if we are going to add highlights.
        if (highlightStrings != null && highlightStrings.size() > 0) {
            final char[] inputChars = input.toLowerCase().toCharArray();
            final int inputLength = inputChars.length;

            // Find out where the highlight elements need to be placed.
            for (final String highlight : highlightStrings) {
                final char[] highlightChars = highlight.toLowerCase().toCharArray();
                final int highlightLength = highlightChars.length;

                boolean inElement = false;
                boolean inEscapedElement = false;
                int lineNo = 1;
                int colNo = 0;
                for (int i = 0; i < inputLength; i++) {
                    final char inputChar = inputChars[i];

                    if (inputChar == '\n') {
                        lineNo++;
                        colNo = 0;
                    }

                    if (!inElement && !inEscapedElement) {
                        if (inputChar == '<') {
                            inElement = true;
                        } else if (inputChar == '&' && i + 3 < inputLength && inputChars[i + 1] == 'l'
                                && inputChars[i + 2] == 't' && inputChars[i + 3] == ';') {
                            inEscapedElement = true;
                        } else {
                            // If we aren't in an element or escaped element
                            // then try to match.
                            boolean found = false;
                            for (int j = 0; j < highlightLength && i + j < inputLength; j++) {
                                final char highlightChar = highlightChars[j];
                                if (inputChars[i + j] != highlightChar) {
                                    break;
                                } else if (j == highlightLength - 1) {
                                    found = true;
                                }
                            }

                            if (found) {
                                final Highlight hl = new Highlight(1, lineNo, colNo, 1, lineNo,
                                        colNo + highlightLength);
                                highlights.add(hl);

                                i += highlightLength;
                            }
                        }
                    } else if (inElement && inputChar == '>') {
                        inElement = false;

                    } else if (inEscapedElement && inputChar == '&' && i + 3 < inputLength && inputChars[i + 1] == 'g'
                            && inputChars[i + 2] == 't' && inputChars[i + 3] == ';') {
                        inEscapedElement = false;
                    }

                    colNo++;
                }
            }
        }

        Collections.sort(highlights);

        return highlights;
    }

    @Override
    public void setComponents(final Components components) {
        super.setComponents(components);
        registerHandler(components.addComponentChangeHandler(event -> {
            if (textSettings != null) {
                final Component component = event.getComponent();
                if (textSettings.getTableId() == null) {
                    if (component instanceof TablePresenter) {
                        currentTablePresenter = (TablePresenter) component;
                        update(currentTablePresenter);
                    }
                } else if (EqualsUtil.isEquals(textSettings.getTableId(), event.getComponentId())) {
                    if (component instanceof TablePresenter) {
                        currentTablePresenter = (TablePresenter) component;
                        update(currentTablePresenter);
                    }
                }
            }
        }));
    }

    private void update(final TablePresenter tablePresenter) {
        currentStreamId = null;
        currentEventId = null;
        currentHighlightStrings = null;

        boolean updating = false;

        if (tablePresenter != null) {
            final String streamId = tablePresenter.getSelectedStreamId();
            final String eventId = tablePresenter.getSelectedEventId();

            if (streamId != null && eventId != null) {
                currentStreamId = getLong(streamId);
                currentEventId = getLong(eventId);
                currentHighlightStrings = tablePresenter.getHighlights();

                if (currentStreamId != null && currentEventId != null) {
                    final String permissionCheck = checkPermissions();
                    if (permissionCheck != null) {
                        isHtml = false;
                        showData(permissionCheck, null, null, isHtml);
                        updating = true;

                    } else {
                        FetchDataAction fetchDataAction;
                        if (textSettings.getPipeline() != null) {
                            fetchDataAction = new FetchDataWithPipelineAction(currentStreamId, currentEventId,
                                    textSettings.getPipeline(), textSettings.isShowAsHtml());
                        } else {
                            fetchDataAction = new FetchDataAction(currentStreamId, currentEventId,
                                    textSettings.isShowAsHtml());
                        }

                        ensureFetchDataQueue();
                        fetchDataQueue.add(fetchDataAction);
                        delayedFetchDataTimer.cancel();
                        delayedFetchDataTimer.schedule(250);
                        updating = true;
                    }
                }
            }
        }

        // If we aren't updating the data display then clear it.
        if (!updating) {
            showData("", null, null, isHtml);
        }
    }

    private Long getLong(final String string) {
        if (string != null) {
            try {
                return Long.valueOf(string);
            } catch (final NumberFormatException e) {
                // Ignore.
            }
        }

        return null;
    }

    private String checkPermissions() {
        if (!securityContext.hasAppPermission(Stream.VIEW_DATA_PERMISSION)) {
            if (!securityContext.hasAppPermission(Stream.VIEW_DATA_WITH_PIPELINE_PERMISSION)) {
                return "You do not have permission to display this item";
            } else if (textSettings.getPipeline() == null) {
                return "You must choose a pipeline to display this item";
            }
        }

        return null;
    }

    private void ensureFetchDataQueue() {
        if (fetchDataQueue == null) {
            fetchDataQueue = new ArrayList<>();
            delayedFetchDataTimer = new Timer() {
                @Override
                public void run() {
                    final FetchDataAction action = fetchDataQueue.get(fetchDataQueue.size() - 1);
                    fetchDataQueue.clear();

                    dispatcher.exec(action).onSuccess(result -> {
                        // If we are queueing more actions then don't update
                        // the text.
                        if (fetchDataQueue.size() == 0) {
                            String data = "The data has been deleted or reprocessed since this index was built";
                            String classification = null;
                            boolean isHtml = false;
                            if (result != null) {
                                if (result instanceof FetchDataResult) {
                                    final FetchDataResult fetchDataResult = (FetchDataResult) result;
                                    data = fetchDataResult.getData();
                                    classification = result.getClassification();
                                    isHtml = fetchDataResult.isHtml();
                                } else {
                                    data = "";
                                    classification = null;
                                    isHtml = false;
                                }
                            }

                            TextPresenter.this.isHtml = isHtml;
                            showData(data, classification, currentHighlightStrings, isHtml);
                        }
                    });
                }
            };
        }
    }

    @Override
    public void read(final ComponentConfig componentData) {
        super.read(componentData);
        textSettings = getSettings();
    }

    @Override
    public void link() {
        final String tableId = textSettings.getTableId();
        String newTableId = getComponents().validateOrGetFirstComponentId(tableId, TablePresenter.TYPE.getId());

        // If we can't get the same table id then set to null so that changes to any table can be listened to.
        if (!EqualsUtil.isEquals(tableId, newTableId)) {
            newTableId = null;
        }

        textSettings.setTableId(newTableId);
        update(currentTablePresenter);
    }

    @Override
    public void changeSettings() {
        super.changeSettings();
        update(currentTablePresenter);
    }

    @Override
    public ComponentType getType() {
        return TYPE;
    }

    private TextComponentSettings getSettings() {
        ComponentSettings settings = getComponentData().getSettings();
        if (settings == null || !(settings instanceof TextComponentSettings)) {
            settings = createSettings();
            getComponentData().setSettings(settings);
        }

        return (TextComponentSettings) settings;
    }

    private ComponentSettings createSettings() {
        return new TextComponentSettings();
    }

    @Override
    public void beginStepping() {
        BeginPipelineSteppingEvent.fire(this, currentStreamId, currentEventId, null, null, null);
    }

    public interface TextView extends View, HasUiHandlers<TextUiHandlers> {
        void setContent(View view);

        void setClassification(String classification);

        void setPlayVisible(boolean visible);
    }
}
