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
import PropTypes from 'prop-types';

import { compose, withState, lifecycle, branch, renderComponent } from 'recompose';
import { connect } from 'react-redux';

import { Button, Modal, Input, Loader } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from '../redux';

import withExplorerTree from '../withExplorerTree';
import withDocRefTypes from '../withDocRefTypes';
import DocPicker from './DocPicker';

const { docRefPicked, docExplorerOpened } = actionCreators;

const withModal = withState('isOpen', 'setIsOpen', false);

const enhance = compose(
  withExplorerTree,
  withDocRefTypes,
  connect(
    (state, props) => ({
      documentTree: state.docExplorer.explorerTree.documentTree,
      docRef: state.docExplorer.docRefPicker[props.pickerId],
      explorer: state.docExplorer.explorerTree.explorers[props.pickerId],
    }),
    {
      // actions
      docRefPicked,
      docExplorerOpened,
    },
  ),
  lifecycle({
    componentDidMount() {
      const { docExplorerOpened, pickerId, typeFilters } = this.props;
      docExplorerOpened(pickerId, false, false, typeFilters);
    },
  }),
  withModal,
  branch(
    ({ explorer }) => !explorer,
    renderComponent(() => <Loader active>Loading Explorer</Loader>),
  ),
);

const DocPickerModal = ({
  isSelected,
  documentTree,
  docRefPicked,
  docRef,
  isOpen,
  pickerId,
  typeFilters,
  setIsOpen,
  explorer,
}) => {
  const value = docRef ? docRef.name : '';

  const handleOpen = () => setIsOpen(true);

  const handleClose = () => setIsOpen(false);

  const onDocRefSelected = () => {
    Object.keys(explorer.isSelected).forEach((pickedUuid) => {
      const picked = findItem(documentTree, pickedUuid);
      docRefPicked(pickerId, picked);
    });

    handleClose();
  };

  return (
    <Modal
      trigger={<Input onFocus={handleOpen} value={`${value}...`} />}
      open={isOpen}
      onClose={handleClose}
      size="small"
      dimmer="inverted"
    >
      <Modal.Header>Select a Doc Ref</Modal.Header>
      <Modal.Content scrolling>
        <DocPicker explorerId={pickerId} typeFilters={typeFilters} />
      </Modal.Content>
      <Modal.Actions>
        <Button negative onClick={handleClose}>
          Cancel
        </Button>
        <Button
          positive
          onClick={onDocRefSelected}
          labelPosition="right"
          icon="checkmark"
          content="Choose"
        />
      </Modal.Actions>
    </Modal>
  );
};

DocPickerModal.propTypes = {
  pickerId: PropTypes.string.isRequired,
  typeFilters: PropTypes.array,
};

export default enhance(DocPickerModal);