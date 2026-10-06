/** Trusted top-level bridge only. Type frames never receive this transport. */
export const CHUNK_CHARACTERS = 1024 * 1024;
export const MAX_MESSAGE_CHARACTERS = 128 * 1024 * 1024;
const MAX_PARTS = MAX_MESSAGE_CHARACTERS / CHUNK_CHARACTERS;
const LIFETIME_MS = 30000;

/** One ordered, bounded assembly; malformed or expired transfers cannot retain memory. */
export function createMessageAssembler(now = () => Date.now()) {
  let pending = null;
  function reset() { pending = null; }
  function accept(packet) {
    if (pending && now() - pending.started > LIFETIME_MS) reset();
    if (packet?.kind === 'native-error') reset();
    if (packet?.kind !== 'qf-transport-chunk') return packet;
    const {id,index,total,length,data} = packet;
    const invalid = typeof id !== 'string' || !/^[a-zA-Z0-9-]{1,80}$/.test(id) ||
      !Number.isSafeInteger(index) || !Number.isSafeInteger(total) || total < 1 || total > MAX_PARTS || index < 0 || index >= total ||
      !Number.isSafeInteger(length) || length < 1 || length > MAX_MESSAGE_CHARACTERS || Math.ceil(length / CHUNK_CHARACTERS) !== total ||
      typeof data !== 'string' || data.length !== Math.min(CHUNK_CHARACTERS,length-index*CHUNK_CHARACTERS);
    if (invalid) { reset(); throw new Error('Invalid browser transport chunk'); }
    if (index === 0) {
      if (pending) { reset(); throw new Error('Overlapping browser transport messages'); }
      pending = {id,total,length,index:0,parts:[],started:now()};
    }
    if (!pending || pending.id !== id || pending.index !== index || pending.total !== total || pending.length !== length) {
      reset(); throw new Error('Out-of-order browser transport chunk');
    }
    pending.parts.push(data);pending.index++;
    if (pending.index !== total) return null;
    const encoded = pending.parts.join('');reset();
    const result = JSON.parse(encoded);
    if (!result || typeof result !== 'object' || result.kind === 'qf-transport-chunk') throw new Error('Invalid browser transport payload');
    return result;
  }
  return {accept,reset};
}

export function sendMessage(transport,message,id) {
  const encoded = JSON.stringify(message);
  if (typeof encoded !== 'string' || encoded.length > MAX_MESSAGE_CHARACTERS) throw new Error('Browser message exceeds 128 Mi characters');
  if (encoded.length <= CHUNK_CHARACTERS) { transport.postMessage(message);return; }
  const total = Math.ceil(encoded.length/CHUNK_CHARACTERS);
  for(let index=0;index<total;index++)transport.postMessage({kind:'qf-transport-chunk',id,index,total,length:encoded.length,data:encoded.slice(index*CHUNK_CHARACTERS,(index+1)*CHUNK_CHARACTERS)});
}

export function createNativeTransport(raw) {
  const assembler = createMessageAssembler();
  let sequence = 0;
  const listeners = new Set();
  const cleanup = setInterval(() => assembler.accept(null),1000);
  cleanup.unref?.();
  raw.addEventListener('message',event => {
    try {
      const message = assembler.accept(event.data);
      if(message)for(const listener of listeners)listener({data:message});
    }catch(error){
      for(const listener of listeners)listener({data:{kind:'native-error',code:'INVALID_TRANSPORT',message:error.message}});
    }
  });
  globalThis.window?.addEventListener('pagehide',()=>{clearInterval(cleanup);assembler.reset();listeners.clear();},{once:true});
  return Object.freeze({
    postMessage(message){sendMessage(raw,message,'page-'+(++sequence));},
    addEventListener(type,listener){if(type!=='message')throw new Error('Unsupported native transport event');listeners.add(listener);}
  });
}
