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
import _ from 'lodash';
import { createActions, handleActions } from 'redux-actions';

import {
  moveItemInTree,
  iterateNodes,
  getIsInFilteredMap,
  deleteItemFromTree,
} from '../../lib/treeUtils';

const OPEN_STATES = {
  closed: 0,
  byUser: 1,
  bySearch: 2,
};

function isOpenState(openState) {
  if (openState) {
    return openState !== OPEN_STATES.closed;
  }
  return false;
}

function getToggledState(currentState, isUser) {
  if (currentState) {
    switch (currentState) {
      case OPEN_STATES.closed:
        return isUser ? OPEN_STATES.byUser : OPEN_STATES.bySearch;
      case OPEN_STATES.bySearch:
      case OPEN_STATES.byUser:
        return OPEN_STATES.closed;
      default:
        throw new Error('Invalid current state: ' + currentState);
    }
  } else {
    return OPEN_STATES.byUser;
  }
}

const DEFAULT_EXPLORER_ID = 'default';

const actionCreators = createActions({
  RECEIVED_DOC_TREE: documentTree => ({ documentTree }),
  EXPLORER_TREE_OPENED: (explorerId, allowMultiSelect, allowDragAndDrop, typeFilter) => ({
    explorerId,
    allowMultiSelect,
    allowDragAndDrop,
    typeFilter,
  }),
  MOVE_EXPLORER_ITEM: (explorerId, itemToMove, destination) => ({ explorerId, itemToMove, destination }),
  FOLDER_OPEN_TOGGLED: (explorerId, docRef) => ({
    explorerId,
    docRef,
  }),
  SEARCH_TERM_UPDATED: (explorerId, searchTerm) => ({
    explorerId,
    searchTerm,
  }),
  DOC_REF_SELECTED:(explorerId, docRef) => ({
    explorerId,
    docRef,
  }),
  DOC_REF_OPENED: (explorerId, docRef) => ({ explorerId, docRef }),
  DOC_REF_DELETED:  (explorerId, docRef) => ({
    explorerId,
    docRef,
  }),
  DOC_REF_PICKED: (pickerId, docRef) => ({ pickerId, docRef })
})

const defaultExplorerState = {
  searchTerm: '',
  isFolderOpen: {}, // in response to user actions and searches
  isSelected: {},
  isVisible: {}, // based on search
  inSearch: {},
  inTypeFilter: {},
};

const defaultState = {
  documentTree: {}, // The hierarchy of doc refs in folders
  explorers: {},
  pickedDocRefs: {}, // Picked Doc Refs by pickerId
  isDocRefOpen: {}, // in response to user actions
  allowMultiSelect: true,
  allowDragAndDrop: true,
};

function getIsValidFilterTerm(filterTerm) {
  return !!filterTerm && filterTerm.length > 0;
}

function getIsVisibleMap(documentTree, isInTypeFilterMap, isInSearchMap) {
  const isVisible = {};

  iterateNodes(documentTree, (lineage, node) => {
    const passesSearch = isInSearchMap[node.uuid];
    const passesTypeFilter = isInTypeFilterMap[node.uuid];
    isVisible[node.uuid] = passesSearch && passesTypeFilter;
  });

  return isVisible;
}

function getFolderIsOpenMap(
  documentTree,
  isInTypeFilterMap,
  isSearching,
  isInSearchMap,
  currentIsFolderOpen,
) {
  const isFolderOpen = {};

  iterateNodes(documentTree, (lineage, node) => {
    const s = currentIsFolderOpen[node.uuid];

    if (isSearching) {
      if (isInSearchMap[node.uuid] && !isOpenState(s)) {
        // If this node is not open, but is included in the search, open it
        isFolderOpen[node.uuid] = OPEN_STATES.bySearch;
      } else if (!isInSearchMap[node.uuid] && s === OPEN_STATES.bySearch) {
        // If this node was opened by search, but no longer matches the search...close it
        isFolderOpen[node.uuid] = OPEN_STATES.closed;
      } else {
        // otherwise leave it as is
        isFolderOpen[node.uuid] = s;
      }
    } else if (s === OPEN_STATES.bySearch) {
      // If this node was opened by search, but we aren't searching any more, close it
      isFolderOpen[node.uuid] = OPEN_STATES.closed;
    } else {
      // otherwise leave it as is
      isFolderOpen[node.uuid] = s;
    }
  });

  return isFolderOpen;
}

function getUpdatedExplorer(documentTree, optExplorer, searchTerm, typeFilter) {
  const explorer = optExplorer || defaultExplorerState;

  let searchRegex;

  if (getIsValidFilterTerm(searchTerm)) {
    searchRegex = new RegExp(_.escapeRegExp(searchTerm), 'i');
  }

  const searchFilterFunction = (lineage, node) => {
    if (searchRegex) {
      return searchRegex.test(node.name);
    }
    return true;
  };

  const typeFilterFunction = (lineage, node) => {
    if (getIsValidFilterTerm(typeFilter)) {
      return typeFilter === node.type;
    }
    return true;
  };

  const isSearching = getIsValidFilterTerm(searchTerm);
  const isInSearchMap = getIsInFilteredMap(documentTree, searchFilterFunction);
  const isInTypeFilterMap = getIsInFilteredMap(documentTree, typeFilterFunction);

  return {
    ...explorer,
    typeFilter,
    searchTerm,
    isVisible: getIsVisibleMap(documentTree, isInTypeFilterMap, isInSearchMap),
    isFolderOpen: getFolderIsOpenMap(
      documentTree,
      isInTypeFilterMap,
      isSearching,
      isInSearchMap,
      explorer.isFolderOpen,
    ),
    inSearch: isInSearchMap,
    inTypeFilter: isInTypeFilterMap
  };
}

function getStateAfterTreeUpdate(state, documentTree) {
  // Update all the explorers with the new tree
  const explorers = {};
  Object.entries(state.explorers).forEach((k) => {
    explorers[k[0]] = getUpdatedExplorer(documentTree, k[1], k[1].searchTerm, k[1].typeFilter);
  });

  return {
    ...state,
    documentTree,
    explorers,
  };
}

const explorerTreeReducer = handleActions(
  {
    // Receive the current state of the explorer tree
    RECEIVED_DOC_TREE: (state, action) =>
      getStateAfterTreeUpdate(state, action.payload.documentTree),

    // When an explorer is opened
    EXPLORER_TREE_OPENED: (state, action) => {
      const {
        explorerId, allowMultiSelect, allowDragAndDrop, typeFilter,
      } = action.payload;
      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: {
            ...getUpdatedExplorer(state.documentTree, undefined, '', typeFilter),
            allowMultiSelect,
            allowDragAndDrop,
          },
        },
      };
    },

    // Move Item in Explorer Tree
    MOVE_EXPLORER_ITEM: (state, action) => {
      const { itemToMove, destination } = action.payload;

      const documentTree = moveItemInTree(state.documentTree, itemToMove, destination);

      return getStateAfterTreeUpdate(state, documentTree);
    },

    // Folder Open Toggle
    FOLDER_OPEN_TOGGLED: (state, action) => {
      const { explorerId, docRef } = action.payload;

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: {
            ...state.explorers[explorerId],
            isFolderOpen: {
              ...state.explorers[explorerId].isFolderOpen,
              [docRef.uuid]: getToggledState(
                state.explorers[explorerId].isFolderOpen[docRef.uuid],
                true,
              ),
            },
          },
        },
      };
    },

    // Search Term Changed
    SEARCH_TERM_UPDATED: (state, action) => {
      const { explorerId, searchTerm } = action.payload;

      const explorer = getUpdatedExplorer(
        state.documentTree,
        state.explorers[explorerId],
        searchTerm,
        state.explorers[explorerId].typeFilter,
      );

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: explorer,
        },
      };
    },

    // Select Doc Ref
    DOC_REF_SELECTED: (state, action) => {
      const { explorerId, docRef } = action.payload;

      const explorer = state.explorers[explorerId];
      let isSelected;
      if (explorer.allowMultiSelect) {
        isSelected = {
          ...state.explorers[explorerId].isSelected,
          [docRef.uuid]: !state.explorers[explorerId].isSelected[docRef.uuid],
        };
      } else {
        isSelected = {
          [docRef.uuid]: !state.explorers[explorerId].isSelected[docRef.uuid],
        };
      }

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: {
            ...state.explorers[explorerId],
            isSelected,
          },
        },
      };
    },

    // Open Doc Ref
    DOC_REF_OPENED: (state, action) => {
      const { docRef } = action.payload;

      return {
        ...state,
        isDocRefOpen: {
          ...state.isDocRefOpen,
          [docRef.uuid]: !state.isDocRefOpen[docRef.uuid],
        },
      };
    },

    // Confirm Delete Doc Ref
    DOC_REF_DELETED: (state, action) => {
      const { docRef } = action.payload;

      const documentTree = deleteItemFromTree(state.documentTree, docRef.uuid);

      return getStateAfterTreeUpdate(state, documentTree);
    },

    // Pick Doc Ref
    DOC_REF_PICKED: (state, action) => ({
      ...state,
      pickedDocRefs: {
        ...state.pickedDocRefs,
        [action.payload.pickerId]: action.payload.docRef,
      },
    }),
  },
  defaultState,
);

export {
  DEFAULT_EXPLORER_ID,
  actionCreators,
  explorerTreeReducer,
};
