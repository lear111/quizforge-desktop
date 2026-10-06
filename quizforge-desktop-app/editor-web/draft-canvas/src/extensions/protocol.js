/** Data-only boundary, shared by the sandbox client and trusted dispatcher. */
export function validateArguments(args) {
  if (!Array.isArray(args) || args.length > 4) throw new TypeError('接口参数必须是最多四项的数组');
  const ancestors = new Set();
  function visit(value, depth) {
    if (depth > 32) throw new TypeError('接口参数嵌套过深');
    if (value === null || typeof value === 'string' || typeof value === 'boolean') return;
    if (typeof value === 'number' && Number.isFinite(value)) return;
    if (typeof value !== 'object' || ancestors.has(value)) throw new TypeError('接口只接受有限数值及 JSON 数据');
    if (!Array.isArray(value) && Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null)
      throw new TypeError('接口只接受普通 JSON 对象');
    ancestors.add(value);
    for (const item of Object.values(value)) visit(item, depth + 1);
    ancestors.delete(value);
  }
  visit(args, 0);
  const encoded = JSON.stringify(args);
  if (encoded.length > 128 * 1024 * 1024) throw new TypeError('接口参数过大');
  return JSON.parse(encoded);
}

/** Arity and primitive shapes are checked before any native capability executes. */
export function validateMethodArguments(method, args) {
  const objects=new Set(['editor.update','answer.update','content.resolve','content.edit','ui.configure','layout.configure','whiteboard.setAppearance']);
  const strings=new Set(['bank.addQuestion','sources.add','learning.setMode','whiteboard.setTool']);
  const indices=new Set(['navigation.goTo','sources.remove','sources.open']);
  const numbers=new Set(['whiteboard.setZoom','whiteboard.zoomBy']);
  const single=objects.has(method)||strings.has(method)||indices.has(method)||numbers.has(method);
  if(args.length!==(single?1:0))throw new TypeError('接口参数数量不匹配：'+method);
  const value=args[0];
  if(objects.has(method)&&(!value||typeof value!=='object'||Array.isArray(value)))throw new TypeError('接口需要 JSON 对象：'+method);
  if(strings.has(method)&&(typeof value!=='string'||!value.trim()))throw new TypeError('接口需要非空文本：'+method);
  if(indices.has(method)&&(!Number.isSafeInteger(value)||value<0))throw new TypeError('索引必须为非负整数');
  if(numbers.has(method)&&(!Number.isFinite(value)||value<=0))throw new TypeError('缩放参数必须为正数');
}

/** Leave a revoked frame connected only until its accepted operation reply is delivered. */
export function replaceChildrenRetainingFrames(root, ...nodes) {
  for (const child of [...root.childNodes]) {
    if (child.nodeType === 1 && (child.matches('.qf-frame-retiring') || child.querySelector('.qf-frame-retiring'))) {
      child.dataset.qfRetiredRoot = '';
      child.style.cssText = 'position:absolute!important;visibility:hidden!important;pointer-events:none!important;';
      child.removeAttribute('id');
      child.querySelectorAll('[id]').forEach(node => node.removeAttribute('id'));
    } else child.remove();
  }
  root.append(...nodes);
}
