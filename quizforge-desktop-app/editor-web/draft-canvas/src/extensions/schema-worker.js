import {compileDataValidation} from './data-validation-core.js';

// Only trusted validator code runs here. No extension page/rules code or native bridge is supplied.
let validator = null;
self.onmessage = ({data: message}) => {
  let value, error;
  try {
    if (message.operation === 'compile') validator = compileDataValidation(message.type, message.asset);
    else {
      if (!validator || !['question', 'answer', 'input', 'output'].includes(message.operation)) throw new TypeError('Invalid validation operation');
      validator[message.operation](...message.args);
    }
    value = true;
  } catch (failure) {
    error = {code: failure.code || 'DATA_VALIDATION_FAILED', message: failure.message, issues: failure.issues};
  }
  self.postMessage({id: message.id, value, error});
};
