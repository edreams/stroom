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

import React from 'react';
import ReactDOM from 'react-dom';
import { toClass, compose } from 'recompose';
import { Provider } from 'react-redux';
import { ConnectedRouter } from 'react-router-redux';

import { DragDropContext } from 'react-dnd';
import HTML5Backend from 'react-dnd-html5-backend';

import Routes from 'startup/Routes';
import store from 'startup/store';
import { history } from 'startup/middleware';

import './styles/main.css';

const DndRoutes = compose(DragDropContext(HTML5Backend), toClass)(Routes);

ReactDOM.render(
  <Provider store={store}>
    <ConnectedRouter history={history}>
      <DndRoutes />
    </ConnectedRouter>
  </Provider>,
  document.getElementById('root'),
);
