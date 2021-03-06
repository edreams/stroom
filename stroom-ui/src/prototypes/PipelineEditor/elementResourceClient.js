import { push } from 'react-router-redux';

import { actionCreators } from './redux';

import {
  setErrorMessageAction,
  setStackTraceAction,
  setHttpErrorCodeAction,
} from 'sections/ErrorPage';

import handleStatus from 'lib/handleStatus';

const { elementsReceived, elementPropertiesReceived } = actionCreators;

const fetch = window.fetch;

export const fetchElements = () => (dispatch, getState) => {
  const state = getState();
  const jwsToken = state.authentication.idToken;

  const url = `${state.config.elementServiceUrl}/elements`;

  fetch(url, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwsToken}`,
    },
    method: 'get',
    mode: 'cors',
  })
    .then(handleStatus)
    .then(response => response.json())
    .then((elements) => {
      dispatch(elementsReceived(elements));
    })
    .catch((error) => {
      dispatch(setErrorMessageAction(error.message));
      dispatch(setStackTraceAction(error.stack));
      dispatch(setHttpErrorCodeAction(error.status));
      dispatch(push('/error'));
    });
};

export const fetchElementProperties = () => (dispatch, getState) => {
  const state = getState();
  const jwsToken = state.authentication.idToken;

  const url = `${state.config.elementServiceUrl}/elementProperties`;

  fetch(url, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwsToken}`,
    },
    method: 'get',
    mode: 'cors',
  })
    .then(handleStatus)
    .then(response => response.json())
    .then((elements) => {
      dispatch(elementPropertiesReceived(elements));
    })
    .catch((error) => {
      dispatch(setErrorMessageAction(error.message));
      dispatch(setStackTraceAction(error.stack));
      dispatch(setHttpErrorCodeAction(error.status));
      dispatch(push('/error'));
    });
};
