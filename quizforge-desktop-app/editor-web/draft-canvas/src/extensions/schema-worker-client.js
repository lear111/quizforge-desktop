import {DataValidationError} from './data-validation-core.js';

const validators = new Set();
const failure = message => new DataValidationError([{path: '/schemas', message}]);
function createBrowserWorker() {
  if (typeof Worker !== 'function' || typeof __QF_SCHEMA_WORKER_SOURCE__ !== 'string')
    throw failure('isolated schema validation is unavailable; synchronous fallback is disabled');
  const url = URL.createObjectURL(new Blob([__QF_SCHEMA_WORKER_SOURCE__], {type: 'text/javascript'}));
  try { return new Worker(url); } finally { URL.revokeObjectURL(url); }
}

/** Compile and run untrusted schemas off the UI thread. A deadline destroys the entire worker. */
export function createWorkerValidation(type, asset, {workerFactory = createBrowserWorker, timeoutMs = 1500, compileTimeoutMs = 5000, maxPending = 16} = {}) {
  let worker, stopped = false, sequence = 0, users=0, retired=false;
  const pending = new Map();
  function stop(error = failure('schema validator is closed')) {
    if (stopped) return;
    stopped = true; worker?.terminate(); validators.delete(api);
    for (const entry of pending.values()) { clearTimeout(entry.timer); entry.reject(error); }
    pending.clear();
  }
  function request(operation, args = [], extra = {}) {
    if (stopped) return Promise.reject(failure('schema validator is closed'));
    if (pending.size >= maxPending) return Promise.reject(failure('too many pending schema validations'));
    const id = ++sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => stop(failure('schema validation exceeded its time budget')), operation === 'compile' ? compileTimeoutMs : timeoutMs);
      pending.set(id, {resolve, reject, timer});
      try { worker.postMessage({id, operation, args, ...extra}); }
      catch (error) { stop(failure('schema validation transport failed: ' + error.message)); }
    });
  }
  const api = {ready: null, destroy: stop,
    retain(){if(stopped)throw failure('schema validator is closed');users++;},
    release(){users=Math.max(0,users-1);if(retired&&!users)stop();},
    retire(){retired=true;if(!users)stop();}
  };
  try { worker = workerFactory(); }
  catch (error) { stopped = true; api.ready = Promise.reject(error); api.ready.catch(() => {}); }
  if (worker) {
    worker.onmessage = ({data: message}) => {
      const entry = pending.get(message?.id); if (!entry) return;
      clearTimeout(entry.timer); pending.delete(message.id);
      if (message.error) entry.reject(new DataValidationError(message.error.issues || [{path: '/schemas', message: message.error.message}]));
      else entry.resolve(message.value);
    };
    worker.onerror = event => { event.preventDefault?.(); stop(failure('schema validation worker failed')); };
    worker.onmessageerror = () => stop(failure('schema validation worker response is invalid'));
    validators.add(api);
    api.ready = request('compile', [], {type: {id: type.id}, asset: {questionSchemaSource: asset.questionSchemaSource, answerSchemaSource: asset.answerSchemaSource}});
    api.ready.catch(error => stop(error));
  }
  for (const operation of ['question', 'answer', 'input', 'output']) api[operation] = async (...args) => {
    await api.ready; return request(operation, args);
  };
  return Object.freeze(api);
}
if (typeof window !== 'undefined') window.addEventListener('pagehide', () => {
  for (const validator of [...validators]) validator.destroy();
});
