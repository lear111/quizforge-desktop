import { operationSeq } from './ordering.js';

/** Narrow semantic event channel. No Java service names or business objects are exposed. */
export function practiceChannel(getHost) {
  let sent = 0;
  return Object.freeze({
    send(event) {
      operationSeq(event.operationSeq);
      const host = getHost();
      if (!host || typeof host.onEvent !== 'function') throw new Error('练习连接尚未就绪，请稍后重试。');
      sent++; host.onEvent(JSON.stringify(event));
    },
    ready() {
      const host = getHost();
      if (!host || typeof host.ready !== 'function') throw new Error('Practice host is missing');
      host.ready();
    },
    diagnostics() { return { sent }; }
  });
}
