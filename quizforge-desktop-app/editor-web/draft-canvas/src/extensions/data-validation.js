import {compileDataValidation as compileSynchronous, DataValidationError} from './data-validation-core.js';
import {createWorkerValidation} from './schema-worker-client.js';
export {DataValidationError, validationFailure} from './data-validation-core.js';

// Browser callers always use a terminable Worker. No Worker means fail closed, not UI-thread fallback.
export function compileDataValidation(type, asset) {
  return typeof window === 'undefined' ? compileSynchronous(type, asset) : createWorkerValidation(type, asset);
}
const then = (value, action) => value?.then ? value.then(action) : action(value);
export function checkedRules(registry, validators) {
  return Object.freeze({destroy() {registry.destroy?.(); for (const validator of validators.values()) validator.destroy?.();},
    retire(){for(const validator of validators.values())validator.retire?.();},
    invoke(type, operation, encoded) {
      const validator = validators.get(type);
      if (!validator) throw new DataValidationError([{path: '/question/type', message: 'type is not registered'}]);
      const input = JSON.parse(encoded);
      return then(validator.input(operation, input), () => {
        if (operation === 'validateAnswer' && !Object.keys(input.answer).length) return JSON.stringify({errors: [], empty: true});
        return then(registry.invoke(type, operation, encoded), response => {
          const value = JSON.parse(response);
          return then(validator.output(operation, value, input), () => JSON.stringify(value));
        });
      });
    }
  });
}
