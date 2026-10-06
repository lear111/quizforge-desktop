(() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __commonJS = (cb, mod) => function __require() {
    try {
      return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
    } catch (e) {
      throw mod = 0, e;
    }
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toESM = (mod, isNodeMode, target2) => (target2 = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
    // If the importer is in node compatibility mode or this is not an ESM
    // file that has been converted to a CommonJS file using a Babel-
    // compatible transform (i.e. "__esModule" has not been set), then set
    // "default" to the CommonJS "module.exports" for node compatibility.
    isNodeMode || !mod || !mod.__esModule ? __defProp(target2, "default", { value: mod, enumerable: true }) : target2,
    mod
  ));

  // node_modules/ajv/dist/compile/codegen/code.js
  var require_code = __commonJS({
    "node_modules/ajv/dist/compile/codegen/code.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.regexpCode = exports.getEsmExportName = exports.getProperty = exports.safeStringify = exports.stringify = exports.strConcat = exports.addCodeArg = exports.str = exports._ = exports.nil = exports._Code = exports.Name = exports.IDENTIFIER = exports._CodeOrName = void 0;
      var _CodeOrName = class {
      };
      exports._CodeOrName = _CodeOrName;
      exports.IDENTIFIER = /^[a-z$_][a-z$_0-9]*$/i;
      var Name = class extends _CodeOrName {
        constructor(s) {
          super();
          if (!exports.IDENTIFIER.test(s))
            throw new Error("CodeGen: name must be a valid identifier");
          this.str = s;
        }
        toString() {
          return this.str;
        }
        emptyStr() {
          return false;
        }
        get names() {
          return { [this.str]: 1 };
        }
      };
      exports.Name = Name;
      var _Code = class extends _CodeOrName {
        constructor(code) {
          super();
          this._items = typeof code === "string" ? [code] : code;
        }
        toString() {
          return this.str;
        }
        emptyStr() {
          if (this._items.length > 1)
            return false;
          const item = this._items[0];
          return item === "" || item === '""';
        }
        get str() {
          var _a;
          return (_a = this._str) !== null && _a !== void 0 ? _a : this._str = this._items.reduce((s, c) => `${s}${c}`, "");
        }
        get names() {
          var _a;
          return (_a = this._names) !== null && _a !== void 0 ? _a : this._names = this._items.reduce((names, c) => {
            if (c instanceof Name)
              names[c.str] = (names[c.str] || 0) + 1;
            return names;
          }, {});
        }
      };
      exports._Code = _Code;
      exports.nil = new _Code("");
      function _(strs, ...args) {
        const code = [strs[0]];
        let i = 0;
        while (i < args.length) {
          addCodeArg(code, args[i]);
          code.push(strs[++i]);
        }
        return new _Code(code);
      }
      exports._ = _;
      var plus = new _Code("+");
      function str(strs, ...args) {
        const expr = [safeStringify(strs[0])];
        let i = 0;
        while (i < args.length) {
          expr.push(plus);
          addCodeArg(expr, args[i]);
          expr.push(plus, safeStringify(strs[++i]));
        }
        optimize(expr);
        return new _Code(expr);
      }
      exports.str = str;
      function addCodeArg(code, arg) {
        if (arg instanceof _Code)
          code.push(...arg._items);
        else if (arg instanceof Name)
          code.push(arg);
        else
          code.push(interpolate(arg));
      }
      exports.addCodeArg = addCodeArg;
      function optimize(expr) {
        let i = 1;
        while (i < expr.length - 1) {
          if (expr[i] === plus) {
            const res = mergeExprItems(expr[i - 1], expr[i + 1]);
            if (res !== void 0) {
              expr.splice(i - 1, 3, res);
              continue;
            }
            expr[i++] = "+";
          }
          i++;
        }
      }
      function mergeExprItems(a, b) {
        if (b === '""')
          return a;
        if (a === '""')
          return b;
        if (typeof a == "string") {
          if (b instanceof Name || a[a.length - 1] !== '"')
            return;
          if (typeof b != "string")
            return `${a.slice(0, -1)}${b}"`;
          if (b[0] === '"')
            return a.slice(0, -1) + b.slice(1);
          return;
        }
        if (typeof b == "string" && b[0] === '"' && !(a instanceof Name))
          return `"${a}${b.slice(1)}`;
        return;
      }
      function strConcat(c1, c2) {
        return c2.emptyStr() ? c1 : c1.emptyStr() ? c2 : str`${c1}${c2}`;
      }
      exports.strConcat = strConcat;
      function interpolate(x) {
        return typeof x == "number" || typeof x == "boolean" || x === null ? x : safeStringify(Array.isArray(x) ? x.join(",") : x);
      }
      function stringify(x) {
        return new _Code(safeStringify(x));
      }
      exports.stringify = stringify;
      function safeStringify(x) {
        return JSON.stringify(x).replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029");
      }
      exports.safeStringify = safeStringify;
      function getProperty(key) {
        return typeof key == "string" && exports.IDENTIFIER.test(key) ? new _Code(`.${key}`) : _`[${key}]`;
      }
      exports.getProperty = getProperty;
      function getEsmExportName(key) {
        if (typeof key == "string" && exports.IDENTIFIER.test(key)) {
          return new _Code(`${key}`);
        }
        throw new Error(`CodeGen: invalid export name: ${key}, use explicit $id name mapping`);
      }
      exports.getEsmExportName = getEsmExportName;
      function regexpCode(rx) {
        return new _Code(rx.toString());
      }
      exports.regexpCode = regexpCode;
    }
  });

  // node_modules/ajv/dist/compile/codegen/scope.js
  var require_scope = __commonJS({
    "node_modules/ajv/dist/compile/codegen/scope.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.ValueScope = exports.ValueScopeName = exports.Scope = exports.varKinds = exports.UsedValueState = void 0;
      var code_1 = require_code();
      var ValueError = class extends Error {
        constructor(name) {
          super(`CodeGen: "code" for ${name} not defined`);
          this.value = name.value;
        }
      };
      var UsedValueState;
      (function(UsedValueState2) {
        UsedValueState2[UsedValueState2["Started"] = 0] = "Started";
        UsedValueState2[UsedValueState2["Completed"] = 1] = "Completed";
      })(UsedValueState || (exports.UsedValueState = UsedValueState = {}));
      exports.varKinds = {
        const: new code_1.Name("const"),
        let: new code_1.Name("let"),
        var: new code_1.Name("var")
      };
      var Scope = class {
        constructor({ prefixes, parent: parent2 } = {}) {
          this._names = {};
          this._prefixes = prefixes;
          this._parent = parent2;
        }
        toName(nameOrPrefix) {
          return nameOrPrefix instanceof code_1.Name ? nameOrPrefix : this.name(nameOrPrefix);
        }
        name(prefix) {
          return new code_1.Name(this._newName(prefix));
        }
        _newName(prefix) {
          const ng = this._names[prefix] || this._nameGroup(prefix);
          return `${prefix}${ng.index++}`;
        }
        _nameGroup(prefix) {
          var _a, _b;
          if (((_b = (_a = this._parent) === null || _a === void 0 ? void 0 : _a._prefixes) === null || _b === void 0 ? void 0 : _b.has(prefix)) || this._prefixes && !this._prefixes.has(prefix)) {
            throw new Error(`CodeGen: prefix "${prefix}" is not allowed in this scope`);
          }
          return this._names[prefix] = { prefix, index: 0 };
        }
      };
      exports.Scope = Scope;
      var ValueScopeName = class extends code_1.Name {
        constructor(prefix, nameStr) {
          super(nameStr);
          this.prefix = prefix;
        }
        setValue(value, { property, itemIndex }) {
          this.value = value;
          this.scopePath = (0, code_1._)`.${new code_1.Name(property)}[${itemIndex}]`;
        }
      };
      exports.ValueScopeName = ValueScopeName;
      var line = (0, code_1._)`\n`;
      var ValueScope = class extends Scope {
        constructor(opts) {
          super(opts);
          this._values = {};
          this._scope = opts.scope;
          this.opts = { ...opts, _n: opts.lines ? line : code_1.nil };
        }
        get() {
          return this._scope;
        }
        name(prefix) {
          return new ValueScopeName(prefix, this._newName(prefix));
        }
        value(nameOrPrefix, value) {
          var _a;
          if (value.ref === void 0)
            throw new Error("CodeGen: ref must be passed in value");
          const name = this.toName(nameOrPrefix);
          const { prefix } = name;
          const valueKey = (_a = value.key) !== null && _a !== void 0 ? _a : value.ref;
          let vs = this._values[prefix];
          if (vs) {
            const _name = vs.get(valueKey);
            if (_name)
              return _name;
          } else {
            vs = this._values[prefix] = /* @__PURE__ */ new Map();
          }
          vs.set(valueKey, name);
          const s = this._scope[prefix] || (this._scope[prefix] = []);
          const itemIndex = s.length;
          s[itemIndex] = value.ref;
          name.setValue(value, { property: prefix, itemIndex });
          return name;
        }
        getValue(prefix, keyOrRef) {
          const vs = this._values[prefix];
          if (!vs)
            return;
          return vs.get(keyOrRef);
        }
        scopeRefs(scopeName, values = this._values) {
          return this._reduceValues(values, (name) => {
            if (name.scopePath === void 0)
              throw new Error(`CodeGen: name "${name}" has no value`);
            return (0, code_1._)`${scopeName}${name.scopePath}`;
          });
        }
        scopeCode(values = this._values, usedValues, getCode) {
          return this._reduceValues(values, (name) => {
            if (name.value === void 0)
              throw new Error(`CodeGen: name "${name}" has no value`);
            return name.value.code;
          }, usedValues, getCode);
        }
        _reduceValues(values, valueCode, usedValues = {}, getCode) {
          let code = code_1.nil;
          for (const prefix in values) {
            const vs = values[prefix];
            if (!vs)
              continue;
            const nameSet = usedValues[prefix] = usedValues[prefix] || /* @__PURE__ */ new Map();
            vs.forEach((name) => {
              if (nameSet.has(name))
                return;
              nameSet.set(name, UsedValueState.Started);
              let c = valueCode(name);
              if (c) {
                const def = this.opts.es5 ? exports.varKinds.var : exports.varKinds.const;
                code = (0, code_1._)`${code}${def} ${name} = ${c};${this.opts._n}`;
              } else if (c = getCode === null || getCode === void 0 ? void 0 : getCode(name)) {
                code = (0, code_1._)`${code}${c}${this.opts._n}`;
              } else {
                throw new ValueError(name);
              }
              nameSet.set(name, UsedValueState.Completed);
            });
          }
          return code;
        }
      };
      exports.ValueScope = ValueScope;
    }
  });

  // node_modules/ajv/dist/compile/codegen/index.js
  var require_codegen = __commonJS({
    "node_modules/ajv/dist/compile/codegen/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.or = exports.and = exports.not = exports.CodeGen = exports.operators = exports.varKinds = exports.ValueScopeName = exports.ValueScope = exports.Scope = exports.Name = exports.regexpCode = exports.stringify = exports.getProperty = exports.nil = exports.strConcat = exports.str = exports._ = void 0;
      var code_1 = require_code();
      var scope_1 = require_scope();
      var code_2 = require_code();
      Object.defineProperty(exports, "_", { enumerable: true, get: function() {
        return code_2._;
      } });
      Object.defineProperty(exports, "str", { enumerable: true, get: function() {
        return code_2.str;
      } });
      Object.defineProperty(exports, "strConcat", { enumerable: true, get: function() {
        return code_2.strConcat;
      } });
      Object.defineProperty(exports, "nil", { enumerable: true, get: function() {
        return code_2.nil;
      } });
      Object.defineProperty(exports, "getProperty", { enumerable: true, get: function() {
        return code_2.getProperty;
      } });
      Object.defineProperty(exports, "stringify", { enumerable: true, get: function() {
        return code_2.stringify;
      } });
      Object.defineProperty(exports, "regexpCode", { enumerable: true, get: function() {
        return code_2.regexpCode;
      } });
      Object.defineProperty(exports, "Name", { enumerable: true, get: function() {
        return code_2.Name;
      } });
      var scope_2 = require_scope();
      Object.defineProperty(exports, "Scope", { enumerable: true, get: function() {
        return scope_2.Scope;
      } });
      Object.defineProperty(exports, "ValueScope", { enumerable: true, get: function() {
        return scope_2.ValueScope;
      } });
      Object.defineProperty(exports, "ValueScopeName", { enumerable: true, get: function() {
        return scope_2.ValueScopeName;
      } });
      Object.defineProperty(exports, "varKinds", { enumerable: true, get: function() {
        return scope_2.varKinds;
      } });
      exports.operators = {
        GT: new code_1._Code(">"),
        GTE: new code_1._Code(">="),
        LT: new code_1._Code("<"),
        LTE: new code_1._Code("<="),
        EQ: new code_1._Code("==="),
        NEQ: new code_1._Code("!=="),
        NOT: new code_1._Code("!"),
        OR: new code_1._Code("||"),
        AND: new code_1._Code("&&"),
        ADD: new code_1._Code("+")
      };
      var Node = class {
        optimizeNodes() {
          return this;
        }
        optimizeNames(_names, _constants) {
          return this;
        }
      };
      var Def = class extends Node {
        constructor(varKind, name, rhs) {
          super();
          this.varKind = varKind;
          this.name = name;
          this.rhs = rhs;
        }
        render({ es5, _n }) {
          const varKind = es5 ? scope_1.varKinds.var : this.varKind;
          const rhs = this.rhs === void 0 ? "" : ` = ${this.rhs}`;
          return `${varKind} ${this.name}${rhs};` + _n;
        }
        optimizeNames(names, constants) {
          if (!names[this.name.str])
            return;
          if (this.rhs)
            this.rhs = optimizeExpr(this.rhs, names, constants);
          return this;
        }
        get names() {
          return this.rhs instanceof code_1._CodeOrName ? this.rhs.names : {};
        }
      };
      var Assign = class extends Node {
        constructor(lhs, rhs, sideEffects) {
          super();
          this.lhs = lhs;
          this.rhs = rhs;
          this.sideEffects = sideEffects;
        }
        render({ _n }) {
          return `${this.lhs} = ${this.rhs};` + _n;
        }
        optimizeNames(names, constants) {
          if (this.lhs instanceof code_1.Name && !names[this.lhs.str] && !this.sideEffects)
            return;
          this.rhs = optimizeExpr(this.rhs, names, constants);
          return this;
        }
        get names() {
          const names = this.lhs instanceof code_1.Name ? {} : { ...this.lhs.names };
          return addExprNames(names, this.rhs);
        }
      };
      var AssignOp = class extends Assign {
        constructor(lhs, op, rhs, sideEffects) {
          super(lhs, rhs, sideEffects);
          this.op = op;
        }
        render({ _n }) {
          return `${this.lhs} ${this.op}= ${this.rhs};` + _n;
        }
      };
      var Label = class extends Node {
        constructor(label) {
          super();
          this.label = label;
          this.names = {};
        }
        render({ _n }) {
          return `${this.label}:` + _n;
        }
      };
      var Break = class extends Node {
        constructor(label) {
          super();
          this.label = label;
          this.names = {};
        }
        render({ _n }) {
          const label = this.label ? ` ${this.label}` : "";
          return `break${label};` + _n;
        }
      };
      var Throw = class extends Node {
        constructor(error) {
          super();
          this.error = error;
        }
        render({ _n }) {
          return `throw ${this.error};` + _n;
        }
        get names() {
          return this.error.names;
        }
      };
      var AnyCode = class extends Node {
        constructor(code) {
          super();
          this.code = code;
        }
        render({ _n }) {
          return `${this.code};` + _n;
        }
        optimizeNodes() {
          return `${this.code}` ? this : void 0;
        }
        optimizeNames(names, constants) {
          this.code = optimizeExpr(this.code, names, constants);
          return this;
        }
        get names() {
          return this.code instanceof code_1._CodeOrName ? this.code.names : {};
        }
      };
      var ParentNode = class extends Node {
        constructor(nodes = []) {
          super();
          this.nodes = nodes;
        }
        render(opts) {
          return this.nodes.reduce((code, n) => code + n.render(opts), "");
        }
        optimizeNodes() {
          const { nodes } = this;
          let i = nodes.length;
          while (i--) {
            const n = nodes[i].optimizeNodes();
            if (Array.isArray(n))
              nodes.splice(i, 1, ...n);
            else if (n)
              nodes[i] = n;
            else
              nodes.splice(i, 1);
          }
          return nodes.length > 0 ? this : void 0;
        }
        optimizeNames(names, constants) {
          const { nodes } = this;
          let i = nodes.length;
          while (i--) {
            const n = nodes[i];
            if (n.optimizeNames(names, constants))
              continue;
            subtractNames(names, n.names);
            nodes.splice(i, 1);
          }
          return nodes.length > 0 ? this : void 0;
        }
        get names() {
          return this.nodes.reduce((names, n) => addNames(names, n.names), {});
        }
      };
      var BlockNode = class extends ParentNode {
        render(opts) {
          return "{" + opts._n + super.render(opts) + "}" + opts._n;
        }
      };
      var Root = class extends ParentNode {
      };
      var Else = class extends BlockNode {
      };
      Else.kind = "else";
      var If = class _If extends BlockNode {
        constructor(condition, nodes) {
          super(nodes);
          this.condition = condition;
        }
        render(opts) {
          let code = `if(${this.condition})` + super.render(opts);
          if (this.else)
            code += "else " + this.else.render(opts);
          return code;
        }
        optimizeNodes() {
          super.optimizeNodes();
          const cond = this.condition;
          if (cond === true)
            return this.nodes;
          let e = this.else;
          if (e) {
            const ns = e.optimizeNodes();
            e = this.else = Array.isArray(ns) ? new Else(ns) : ns;
          }
          if (e) {
            if (cond === false)
              return e instanceof _If ? e : e.nodes;
            if (this.nodes.length)
              return this;
            return new _If(not(cond), e instanceof _If ? [e] : e.nodes);
          }
          if (cond === false || !this.nodes.length)
            return void 0;
          return this;
        }
        optimizeNames(names, constants) {
          var _a;
          this.else = (_a = this.else) === null || _a === void 0 ? void 0 : _a.optimizeNames(names, constants);
          if (!(super.optimizeNames(names, constants) || this.else))
            return;
          this.condition = optimizeExpr(this.condition, names, constants);
          return this;
        }
        get names() {
          const names = super.names;
          addExprNames(names, this.condition);
          if (this.else)
            addNames(names, this.else.names);
          return names;
        }
      };
      If.kind = "if";
      var For = class extends BlockNode {
      };
      For.kind = "for";
      var ForLoop = class extends For {
        constructor(iteration) {
          super();
          this.iteration = iteration;
        }
        render(opts) {
          return `for(${this.iteration})` + super.render(opts);
        }
        optimizeNames(names, constants) {
          if (!super.optimizeNames(names, constants))
            return;
          this.iteration = optimizeExpr(this.iteration, names, constants);
          return this;
        }
        get names() {
          return addNames(super.names, this.iteration.names);
        }
      };
      var ForRange = class extends For {
        constructor(varKind, name, from, to) {
          super();
          this.varKind = varKind;
          this.name = name;
          this.from = from;
          this.to = to;
        }
        render(opts) {
          const varKind = opts.es5 ? scope_1.varKinds.var : this.varKind;
          const { name, from, to } = this;
          return `for(${varKind} ${name}=${from}; ${name}<${to}; ${name}++)` + super.render(opts);
        }
        get names() {
          const names = addExprNames(super.names, this.from);
          return addExprNames(names, this.to);
        }
      };
      var ForIter = class extends For {
        constructor(loop, varKind, name, iterable) {
          super();
          this.loop = loop;
          this.varKind = varKind;
          this.name = name;
          this.iterable = iterable;
        }
        render(opts) {
          return `for(${this.varKind} ${this.name} ${this.loop} ${this.iterable})` + super.render(opts);
        }
        optimizeNames(names, constants) {
          if (!super.optimizeNames(names, constants))
            return;
          this.iterable = optimizeExpr(this.iterable, names, constants);
          return this;
        }
        get names() {
          return addNames(super.names, this.iterable.names);
        }
      };
      var Func = class extends BlockNode {
        constructor(name, args, async) {
          super();
          this.name = name;
          this.args = args;
          this.async = async;
        }
        render(opts) {
          const _async = this.async ? "async " : "";
          return `${_async}function ${this.name}(${this.args})` + super.render(opts);
        }
      };
      Func.kind = "func";
      var Return = class extends ParentNode {
        render(opts) {
          return "return " + super.render(opts);
        }
      };
      Return.kind = "return";
      var Try = class extends BlockNode {
        render(opts) {
          let code = "try" + super.render(opts);
          if (this.catch)
            code += this.catch.render(opts);
          if (this.finally)
            code += this.finally.render(opts);
          return code;
        }
        optimizeNodes() {
          var _a, _b;
          super.optimizeNodes();
          (_a = this.catch) === null || _a === void 0 ? void 0 : _a.optimizeNodes();
          (_b = this.finally) === null || _b === void 0 ? void 0 : _b.optimizeNodes();
          return this;
        }
        optimizeNames(names, constants) {
          var _a, _b;
          super.optimizeNames(names, constants);
          (_a = this.catch) === null || _a === void 0 ? void 0 : _a.optimizeNames(names, constants);
          (_b = this.finally) === null || _b === void 0 ? void 0 : _b.optimizeNames(names, constants);
          return this;
        }
        get names() {
          const names = super.names;
          if (this.catch)
            addNames(names, this.catch.names);
          if (this.finally)
            addNames(names, this.finally.names);
          return names;
        }
      };
      var Catch = class extends BlockNode {
        constructor(error) {
          super();
          this.error = error;
        }
        render(opts) {
          return `catch(${this.error})` + super.render(opts);
        }
      };
      Catch.kind = "catch";
      var Finally = class extends BlockNode {
        render(opts) {
          return "finally" + super.render(opts);
        }
      };
      Finally.kind = "finally";
      var CodeGen = class {
        constructor(extScope, opts = {}) {
          this._values = {};
          this._blockStarts = [];
          this._constants = {};
          this.opts = { ...opts, _n: opts.lines ? "\n" : "" };
          this._extScope = extScope;
          this._scope = new scope_1.Scope({ parent: extScope });
          this._nodes = [new Root()];
        }
        toString() {
          return this._root.render(this.opts);
        }
        // returns unique name in the internal scope
        name(prefix) {
          return this._scope.name(prefix);
        }
        // reserves unique name in the external scope
        scopeName(prefix) {
          return this._extScope.name(prefix);
        }
        // reserves unique name in the external scope and assigns value to it
        scopeValue(prefixOrName, value) {
          const name = this._extScope.value(prefixOrName, value);
          const vs = this._values[name.prefix] || (this._values[name.prefix] = /* @__PURE__ */ new Set());
          vs.add(name);
          return name;
        }
        getScopeValue(prefix, keyOrRef) {
          return this._extScope.getValue(prefix, keyOrRef);
        }
        // return code that assigns values in the external scope to the names that are used internally
        // (same names that were returned by gen.scopeName or gen.scopeValue)
        scopeRefs(scopeName) {
          return this._extScope.scopeRefs(scopeName, this._values);
        }
        scopeCode() {
          return this._extScope.scopeCode(this._values);
        }
        _def(varKind, nameOrPrefix, rhs, constant) {
          const name = this._scope.toName(nameOrPrefix);
          if (rhs !== void 0 && constant)
            this._constants[name.str] = rhs;
          this._leafNode(new Def(varKind, name, rhs));
          return name;
        }
        // `const` declaration (`var` in es5 mode)
        const(nameOrPrefix, rhs, _constant) {
          return this._def(scope_1.varKinds.const, nameOrPrefix, rhs, _constant);
        }
        // `let` declaration with optional assignment (`var` in es5 mode)
        let(nameOrPrefix, rhs, _constant) {
          return this._def(scope_1.varKinds.let, nameOrPrefix, rhs, _constant);
        }
        // `var` declaration with optional assignment
        var(nameOrPrefix, rhs, _constant) {
          return this._def(scope_1.varKinds.var, nameOrPrefix, rhs, _constant);
        }
        // assignment code
        assign(lhs, rhs, sideEffects) {
          return this._leafNode(new Assign(lhs, rhs, sideEffects));
        }
        // `+=` code
        add(lhs, rhs) {
          return this._leafNode(new AssignOp(lhs, exports.operators.ADD, rhs));
        }
        // appends passed SafeExpr to code or executes Block
        code(c) {
          if (typeof c == "function")
            c();
          else if (c !== code_1.nil)
            this._leafNode(new AnyCode(c));
          return this;
        }
        // returns code for object literal for the passed argument list of key-value pairs
        object(...keyValues) {
          const code = ["{"];
          for (const [key, value] of keyValues) {
            if (code.length > 1)
              code.push(",");
            code.push(key);
            if (key !== value || this.opts.es5) {
              code.push(":");
              (0, code_1.addCodeArg)(code, value);
            }
          }
          code.push("}");
          return new code_1._Code(code);
        }
        // `if` clause (or statement if `thenBody` and, optionally, `elseBody` are passed)
        if(condition, thenBody, elseBody) {
          this._blockNode(new If(condition));
          if (thenBody && elseBody) {
            this.code(thenBody).else().code(elseBody).endIf();
          } else if (thenBody) {
            this.code(thenBody).endIf();
          } else if (elseBody) {
            throw new Error('CodeGen: "else" body without "then" body');
          }
          return this;
        }
        // `else if` clause - invalid without `if` or after `else` clauses
        elseIf(condition) {
          return this._elseNode(new If(condition));
        }
        // `else` clause - only valid after `if` or `else if` clauses
        else() {
          return this._elseNode(new Else());
        }
        // end `if` statement (needed if gen.if was used only with condition)
        endIf() {
          return this._endBlockNode(If, Else);
        }
        _for(node, forBody) {
          this._blockNode(node);
          if (forBody)
            this.code(forBody).endFor();
          return this;
        }
        // a generic `for` clause (or statement if `forBody` is passed)
        for(iteration, forBody) {
          return this._for(new ForLoop(iteration), forBody);
        }
        // `for` statement for a range of values
        forRange(nameOrPrefix, from, to, forBody, varKind = this.opts.es5 ? scope_1.varKinds.var : scope_1.varKinds.let) {
          const name = this._scope.toName(nameOrPrefix);
          return this._for(new ForRange(varKind, name, from, to), () => forBody(name));
        }
        // `for-of` statement (in es5 mode replace with a normal for loop)
        forOf(nameOrPrefix, iterable, forBody, varKind = scope_1.varKinds.const) {
          const name = this._scope.toName(nameOrPrefix);
          if (this.opts.es5) {
            const arr = iterable instanceof code_1.Name ? iterable : this.var("_arr", iterable);
            return this.forRange("_i", 0, (0, code_1._)`${arr}.length`, (i) => {
              this.var(name, (0, code_1._)`${arr}[${i}]`);
              forBody(name);
            });
          }
          return this._for(new ForIter("of", varKind, name, iterable), () => forBody(name));
        }
        // `for-in` statement.
        // With option `ownProperties` replaced with a `for-of` loop for object keys
        forIn(nameOrPrefix, obj, forBody, varKind = this.opts.es5 ? scope_1.varKinds.var : scope_1.varKinds.const) {
          if (this.opts.ownProperties) {
            return this.forOf(nameOrPrefix, (0, code_1._)`Object.keys(${obj})`, forBody);
          }
          const name = this._scope.toName(nameOrPrefix);
          return this._for(new ForIter("in", varKind, name, obj), () => forBody(name));
        }
        // end `for` loop
        endFor() {
          return this._endBlockNode(For);
        }
        // `label` statement
        label(label) {
          return this._leafNode(new Label(label));
        }
        // `break` statement
        break(label) {
          return this._leafNode(new Break(label));
        }
        // `return` statement
        return(value) {
          const node = new Return();
          this._blockNode(node);
          this.code(value);
          if (node.nodes.length !== 1)
            throw new Error('CodeGen: "return" should have one node');
          return this._endBlockNode(Return);
        }
        // `try` statement
        try(tryBody, catchCode, finallyCode) {
          if (!catchCode && !finallyCode)
            throw new Error('CodeGen: "try" without "catch" and "finally"');
          const node = new Try();
          this._blockNode(node);
          this.code(tryBody);
          if (catchCode) {
            const error = this.name("e");
            this._currNode = node.catch = new Catch(error);
            catchCode(error);
          }
          if (finallyCode) {
            this._currNode = node.finally = new Finally();
            this.code(finallyCode);
          }
          return this._endBlockNode(Catch, Finally);
        }
        // `throw` statement
        throw(error) {
          return this._leafNode(new Throw(error));
        }
        // start self-balancing block
        block(body, nodeCount) {
          this._blockStarts.push(this._nodes.length);
          if (body)
            this.code(body).endBlock(nodeCount);
          return this;
        }
        // end the current self-balancing block
        endBlock(nodeCount) {
          const len = this._blockStarts.pop();
          if (len === void 0)
            throw new Error("CodeGen: not in self-balancing block");
          const toClose = this._nodes.length - len;
          if (toClose < 0 || nodeCount !== void 0 && toClose !== nodeCount) {
            throw new Error(`CodeGen: wrong number of nodes: ${toClose} vs ${nodeCount} expected`);
          }
          this._nodes.length = len;
          return this;
        }
        // `function` heading (or definition if funcBody is passed)
        func(name, args = code_1.nil, async, funcBody) {
          this._blockNode(new Func(name, args, async));
          if (funcBody)
            this.code(funcBody).endFunc();
          return this;
        }
        // end function definition
        endFunc() {
          return this._endBlockNode(Func);
        }
        optimize(n = 1) {
          while (n-- > 0) {
            this._root.optimizeNodes();
            this._root.optimizeNames(this._root.names, this._constants);
          }
        }
        _leafNode(node) {
          this._currNode.nodes.push(node);
          return this;
        }
        _blockNode(node) {
          this._currNode.nodes.push(node);
          this._nodes.push(node);
        }
        _endBlockNode(N1, N2) {
          const n = this._currNode;
          if (n instanceof N1 || N2 && n instanceof N2) {
            this._nodes.pop();
            return this;
          }
          throw new Error(`CodeGen: not in block "${N2 ? `${N1.kind}/${N2.kind}` : N1.kind}"`);
        }
        _elseNode(node) {
          const n = this._currNode;
          if (!(n instanceof If)) {
            throw new Error('CodeGen: "else" without "if"');
          }
          this._currNode = n.else = node;
          return this;
        }
        get _root() {
          return this._nodes[0];
        }
        get _currNode() {
          const ns = this._nodes;
          return ns[ns.length - 1];
        }
        set _currNode(node) {
          const ns = this._nodes;
          ns[ns.length - 1] = node;
        }
      };
      exports.CodeGen = CodeGen;
      function addNames(names, from) {
        for (const n in from)
          names[n] = (names[n] || 0) + (from[n] || 0);
        return names;
      }
      function addExprNames(names, from) {
        return from instanceof code_1._CodeOrName ? addNames(names, from.names) : names;
      }
      function optimizeExpr(expr, names, constants) {
        if (expr instanceof code_1.Name)
          return replaceName(expr);
        if (!canOptimize(expr))
          return expr;
        return new code_1._Code(expr._items.reduce((items, c) => {
          if (c instanceof code_1.Name)
            c = replaceName(c);
          if (c instanceof code_1._Code)
            items.push(...c._items);
          else
            items.push(c);
          return items;
        }, []));
        function replaceName(n) {
          const c = constants[n.str];
          if (c === void 0 || names[n.str] !== 1)
            return n;
          delete names[n.str];
          return c;
        }
        function canOptimize(e) {
          return e instanceof code_1._Code && e._items.some((c) => c instanceof code_1.Name && names[c.str] === 1 && constants[c.str] !== void 0);
        }
      }
      function subtractNames(names, from) {
        for (const n in from)
          names[n] = (names[n] || 0) - (from[n] || 0);
      }
      function not(x) {
        return typeof x == "boolean" || typeof x == "number" || x === null ? !x : (0, code_1._)`!${par(x)}`;
      }
      exports.not = not;
      var andCode = mappend(exports.operators.AND);
      function and(...args) {
        return args.reduce(andCode);
      }
      exports.and = and;
      var orCode = mappend(exports.operators.OR);
      function or(...args) {
        return args.reduce(orCode);
      }
      exports.or = or;
      function mappend(op) {
        return (x, y) => x === code_1.nil ? y : y === code_1.nil ? x : (0, code_1._)`${par(x)} ${op} ${par(y)}`;
      }
      function par(x) {
        return x instanceof code_1.Name ? x : (0, code_1._)`(${x})`;
      }
    }
  });

  // node_modules/ajv/dist/compile/util.js
  var require_util = __commonJS({
    "node_modules/ajv/dist/compile/util.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.checkStrictMode = exports.getErrorPath = exports.Type = exports.useFunc = exports.setEvaluated = exports.evaluatedPropsToName = exports.mergeEvaluated = exports.eachItem = exports.unescapeJsonPointer = exports.escapeJsonPointer = exports.escapeFragment = exports.unescapeFragment = exports.schemaRefOrVal = exports.schemaHasRulesButRef = exports.schemaHasRules = exports.checkUnknownRules = exports.alwaysValidSchema = exports.toHash = void 0;
      var codegen_1 = require_codegen();
      var code_1 = require_code();
      function toHash(arr) {
        const hash = {};
        for (const item of arr)
          hash[item] = true;
        return hash;
      }
      exports.toHash = toHash;
      function alwaysValidSchema(it, schema) {
        if (typeof schema == "boolean")
          return schema;
        if (Object.keys(schema).length === 0)
          return true;
        checkUnknownRules(it, schema);
        return !schemaHasRules(schema, it.self.RULES.all);
      }
      exports.alwaysValidSchema = alwaysValidSchema;
      function checkUnknownRules(it, schema = it.schema) {
        const { opts, self } = it;
        if (!opts.strictSchema)
          return;
        if (typeof schema === "boolean")
          return;
        const rules = self.RULES.keywords;
        for (const key in schema) {
          if (!rules[key])
            checkStrictMode(it, `unknown keyword: "${key}"`);
        }
      }
      exports.checkUnknownRules = checkUnknownRules;
      function schemaHasRules(schema, rules) {
        if (typeof schema == "boolean")
          return !schema;
        for (const key in schema)
          if (rules[key])
            return true;
        return false;
      }
      exports.schemaHasRules = schemaHasRules;
      function schemaHasRulesButRef(schema, RULES) {
        if (typeof schema == "boolean")
          return !schema;
        for (const key in schema)
          if (key !== "$ref" && RULES.all[key])
            return true;
        return false;
      }
      exports.schemaHasRulesButRef = schemaHasRulesButRef;
      function schemaRefOrVal({ topSchemaRef, schemaPath }, schema, keyword, $data) {
        if (!$data) {
          if (typeof schema == "number" || typeof schema == "boolean")
            return schema;
          if (typeof schema == "string")
            return (0, codegen_1._)`${schema}`;
        }
        return (0, codegen_1._)`${topSchemaRef}${schemaPath}${(0, codegen_1.getProperty)(keyword)}`;
      }
      exports.schemaRefOrVal = schemaRefOrVal;
      function unescapeFragment(str) {
        return unescapeJsonPointer(decodeURIComponent(str));
      }
      exports.unescapeFragment = unescapeFragment;
      function escapeFragment(str) {
        return encodeURIComponent(escapeJsonPointer(str));
      }
      exports.escapeFragment = escapeFragment;
      function escapeJsonPointer(str) {
        if (typeof str == "number")
          return `${str}`;
        return str.replace(/~/g, "~0").replace(/\//g, "~1");
      }
      exports.escapeJsonPointer = escapeJsonPointer;
      function unescapeJsonPointer(str) {
        return str.replace(/~1/g, "/").replace(/~0/g, "~");
      }
      exports.unescapeJsonPointer = unescapeJsonPointer;
      function eachItem(xs, f) {
        if (Array.isArray(xs)) {
          for (const x of xs)
            f(x);
        } else {
          f(xs);
        }
      }
      exports.eachItem = eachItem;
      function makeMergeEvaluated({ mergeNames, mergeToName, mergeValues, resultToName }) {
        return (gen, from, to, toName) => {
          const res = to === void 0 ? from : to instanceof codegen_1.Name ? (from instanceof codegen_1.Name ? mergeNames(gen, from, to) : mergeToName(gen, from, to), to) : from instanceof codegen_1.Name ? (mergeToName(gen, to, from), from) : mergeValues(from, to);
          return toName === codegen_1.Name && !(res instanceof codegen_1.Name) ? resultToName(gen, res) : res;
        };
      }
      exports.mergeEvaluated = {
        props: makeMergeEvaluated({
          mergeNames: (gen, from, to) => gen.if((0, codegen_1._)`${to} !== true && ${from} !== undefined`, () => {
            gen.if((0, codegen_1._)`${from} === true`, () => gen.assign(to, true), () => gen.assign(to, (0, codegen_1._)`${to} || {}`).code((0, codegen_1._)`Object.assign(${to}, ${from})`));
          }),
          mergeToName: (gen, from, to) => gen.if((0, codegen_1._)`${to} !== true`, () => {
            if (from === true) {
              gen.assign(to, true);
            } else {
              gen.assign(to, (0, codegen_1._)`${to} || {}`);
              setEvaluated(gen, to, from);
            }
          }),
          mergeValues: (from, to) => from === true ? true : { ...from, ...to },
          resultToName: evaluatedPropsToName
        }),
        items: makeMergeEvaluated({
          mergeNames: (gen, from, to) => gen.if((0, codegen_1._)`${to} !== true && ${from} !== undefined`, () => gen.assign(to, (0, codegen_1._)`${from} === true ? true : ${to} > ${from} ? ${to} : ${from}`)),
          mergeToName: (gen, from, to) => gen.if((0, codegen_1._)`${to} !== true`, () => gen.assign(to, from === true ? true : (0, codegen_1._)`${to} > ${from} ? ${to} : ${from}`)),
          mergeValues: (from, to) => from === true ? true : Math.max(from, to),
          resultToName: (gen, items) => gen.var("items", items)
        })
      };
      function evaluatedPropsToName(gen, ps) {
        if (ps === true)
          return gen.var("props", true);
        const props = gen.var("props", (0, codegen_1._)`{}`);
        if (ps !== void 0)
          setEvaluated(gen, props, ps);
        return props;
      }
      exports.evaluatedPropsToName = evaluatedPropsToName;
      function setEvaluated(gen, props, ps) {
        Object.keys(ps).forEach((p) => gen.assign((0, codegen_1._)`${props}${(0, codegen_1.getProperty)(p)}`, true));
      }
      exports.setEvaluated = setEvaluated;
      var snippets = {};
      function useFunc(gen, f) {
        return gen.scopeValue("func", {
          ref: f,
          code: snippets[f.code] || (snippets[f.code] = new code_1._Code(f.code))
        });
      }
      exports.useFunc = useFunc;
      var Type;
      (function(Type2) {
        Type2[Type2["Num"] = 0] = "Num";
        Type2[Type2["Str"] = 1] = "Str";
      })(Type || (exports.Type = Type = {}));
      function getErrorPath(dataProp, dataPropType, jsPropertySyntax) {
        if (dataProp instanceof codegen_1.Name) {
          const isNumber = dataPropType === Type.Num;
          return jsPropertySyntax ? isNumber ? (0, codegen_1._)`"[" + ${dataProp} + "]"` : (0, codegen_1._)`"['" + ${dataProp} + "']"` : isNumber ? (0, codegen_1._)`"/" + ${dataProp}` : (0, codegen_1._)`"/" + ${dataProp}.replace(/~/g, "~0").replace(/\\//g, "~1")`;
        }
        return jsPropertySyntax ? (0, codegen_1.getProperty)(dataProp).toString() : "/" + escapeJsonPointer(dataProp);
      }
      exports.getErrorPath = getErrorPath;
      function checkStrictMode(it, msg, mode = it.opts.strictSchema) {
        if (!mode)
          return;
        msg = `strict mode: ${msg}`;
        if (mode === true)
          throw new Error(msg);
        it.self.logger.warn(msg);
      }
      exports.checkStrictMode = checkStrictMode;
    }
  });

  // node_modules/ajv/dist/compile/names.js
  var require_names = __commonJS({
    "node_modules/ajv/dist/compile/names.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var names = {
        // validation function arguments
        data: new codegen_1.Name("data"),
        // data passed to validation function
        // args passed from referencing schema
        valCxt: new codegen_1.Name("valCxt"),
        // validation/data context - should not be used directly, it is destructured to the names below
        instancePath: new codegen_1.Name("instancePath"),
        parentData: new codegen_1.Name("parentData"),
        parentDataProperty: new codegen_1.Name("parentDataProperty"),
        rootData: new codegen_1.Name("rootData"),
        // root data - same as the data passed to the first/top validation function
        dynamicAnchors: new codegen_1.Name("dynamicAnchors"),
        // used to support recursiveRef and dynamicRef
        // function scoped variables
        vErrors: new codegen_1.Name("vErrors"),
        // null or array of validation errors
        errors: new codegen_1.Name("errors"),
        // counter of validation errors
        this: new codegen_1.Name("this"),
        // "globals"
        self: new codegen_1.Name("self"),
        scope: new codegen_1.Name("scope"),
        // JTD serialize/parse name for JSON string and position
        json: new codegen_1.Name("json"),
        jsonPos: new codegen_1.Name("jsonPos"),
        jsonLen: new codegen_1.Name("jsonLen"),
        jsonPart: new codegen_1.Name("jsonPart")
      };
      exports.default = names;
    }
  });

  // node_modules/ajv/dist/compile/errors.js
  var require_errors = __commonJS({
    "node_modules/ajv/dist/compile/errors.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.extendErrors = exports.resetErrorsCount = exports.reportExtraError = exports.reportError = exports.keyword$DataError = exports.keywordError = void 0;
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var names_1 = require_names();
      exports.keywordError = {
        message: ({ keyword }) => (0, codegen_1.str)`must pass "${keyword}" keyword validation`
      };
      exports.keyword$DataError = {
        message: ({ keyword, schemaType }) => schemaType ? (0, codegen_1.str)`"${keyword}" keyword must be ${schemaType} ($data)` : (0, codegen_1.str)`"${keyword}" keyword is invalid ($data)`
      };
      function reportError(cxt, error = exports.keywordError, errorPaths, overrideAllErrors) {
        const { it } = cxt;
        const { gen, compositeRule, allErrors } = it;
        const errObj = errorObjectCode(cxt, error, errorPaths);
        if (overrideAllErrors !== null && overrideAllErrors !== void 0 ? overrideAllErrors : compositeRule || allErrors) {
          addError(gen, errObj);
        } else {
          returnErrors(it, (0, codegen_1._)`[${errObj}]`);
        }
      }
      exports.reportError = reportError;
      function reportExtraError(cxt, error = exports.keywordError, errorPaths) {
        const { it } = cxt;
        const { gen, compositeRule, allErrors } = it;
        const errObj = errorObjectCode(cxt, error, errorPaths);
        addError(gen, errObj);
        if (!(compositeRule || allErrors)) {
          returnErrors(it, names_1.default.vErrors);
        }
      }
      exports.reportExtraError = reportExtraError;
      function resetErrorsCount(gen, errsCount) {
        gen.assign(names_1.default.errors, errsCount);
        gen.if((0, codegen_1._)`${names_1.default.vErrors} !== null`, () => gen.if(errsCount, () => gen.assign((0, codegen_1._)`${names_1.default.vErrors}.length`, errsCount), () => gen.assign(names_1.default.vErrors, null)));
      }
      exports.resetErrorsCount = resetErrorsCount;
      function extendErrors({ gen, keyword, schemaValue, data, errsCount, it }) {
        if (errsCount === void 0)
          throw new Error("ajv implementation error");
        const err = gen.name("err");
        gen.forRange("i", errsCount, names_1.default.errors, (i) => {
          gen.const(err, (0, codegen_1._)`${names_1.default.vErrors}[${i}]`);
          gen.if((0, codegen_1._)`${err}.instancePath === undefined`, () => gen.assign((0, codegen_1._)`${err}.instancePath`, (0, codegen_1.strConcat)(names_1.default.instancePath, it.errorPath)));
          gen.assign((0, codegen_1._)`${err}.schemaPath`, (0, codegen_1.str)`${it.errSchemaPath}/${keyword}`);
          if (it.opts.verbose) {
            gen.assign((0, codegen_1._)`${err}.schema`, schemaValue);
            gen.assign((0, codegen_1._)`${err}.data`, data);
          }
        });
      }
      exports.extendErrors = extendErrors;
      function addError(gen, errObj) {
        const err = gen.const("err", errObj);
        gen.if((0, codegen_1._)`${names_1.default.vErrors} === null`, () => gen.assign(names_1.default.vErrors, (0, codegen_1._)`[${err}]`), (0, codegen_1._)`${names_1.default.vErrors}.push(${err})`);
        gen.code((0, codegen_1._)`${names_1.default.errors}++`);
      }
      function returnErrors(it, errs) {
        const { gen, validateName, schemaEnv } = it;
        if (schemaEnv.$async) {
          gen.throw((0, codegen_1._)`new ${it.ValidationError}(${errs})`);
        } else {
          gen.assign((0, codegen_1._)`${validateName}.errors`, errs);
          gen.return(false);
        }
      }
      var E = {
        keyword: new codegen_1.Name("keyword"),
        schemaPath: new codegen_1.Name("schemaPath"),
        // also used in JTD errors
        params: new codegen_1.Name("params"),
        propertyName: new codegen_1.Name("propertyName"),
        message: new codegen_1.Name("message"),
        schema: new codegen_1.Name("schema"),
        parentSchema: new codegen_1.Name("parentSchema")
      };
      function errorObjectCode(cxt, error, errorPaths) {
        const { createErrors } = cxt.it;
        if (createErrors === false)
          return (0, codegen_1._)`{}`;
        return errorObject(cxt, error, errorPaths);
      }
      function errorObject(cxt, error, errorPaths = {}) {
        const { gen, it } = cxt;
        const keyValues = [
          errorInstancePath(it, errorPaths),
          errorSchemaPath(cxt, errorPaths)
        ];
        extraErrorProps(cxt, error, keyValues);
        return gen.object(...keyValues);
      }
      function errorInstancePath({ errorPath }, { instancePath }) {
        const instPath = instancePath ? (0, codegen_1.str)`${errorPath}${(0, util_1.getErrorPath)(instancePath, util_1.Type.Str)}` : errorPath;
        return [names_1.default.instancePath, (0, codegen_1.strConcat)(names_1.default.instancePath, instPath)];
      }
      function errorSchemaPath({ keyword, it: { errSchemaPath } }, { schemaPath, parentSchema }) {
        let schPath = parentSchema ? errSchemaPath : (0, codegen_1.str)`${errSchemaPath}/${keyword}`;
        if (schemaPath) {
          schPath = (0, codegen_1.str)`${schPath}${(0, util_1.getErrorPath)(schemaPath, util_1.Type.Str)}`;
        }
        return [E.schemaPath, schPath];
      }
      function extraErrorProps(cxt, { params, message }, keyValues) {
        const { keyword, data, schemaValue, it } = cxt;
        const { opts, propertyName, topSchemaRef, schemaPath } = it;
        keyValues.push([E.keyword, keyword], [E.params, typeof params == "function" ? params(cxt) : params || (0, codegen_1._)`{}`]);
        if (opts.messages) {
          keyValues.push([E.message, typeof message == "function" ? message(cxt) : message]);
        }
        if (opts.verbose) {
          keyValues.push([E.schema, schemaValue], [E.parentSchema, (0, codegen_1._)`${topSchemaRef}${schemaPath}`], [names_1.default.data, data]);
        }
        if (propertyName)
          keyValues.push([E.propertyName, propertyName]);
      }
    }
  });

  // node_modules/ajv/dist/compile/validate/boolSchema.js
  var require_boolSchema = __commonJS({
    "node_modules/ajv/dist/compile/validate/boolSchema.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.boolOrEmptySchema = exports.topBoolOrEmptySchema = void 0;
      var errors_1 = require_errors();
      var codegen_1 = require_codegen();
      var names_1 = require_names();
      var boolError = {
        message: "boolean schema is false"
      };
      function topBoolOrEmptySchema(it) {
        const { gen, schema, validateName } = it;
        if (schema === false) {
          falseSchemaError(it, false);
        } else if (typeof schema == "object" && schema.$async === true) {
          gen.return(names_1.default.data);
        } else {
          gen.assign((0, codegen_1._)`${validateName}.errors`, null);
          gen.return(true);
        }
      }
      exports.topBoolOrEmptySchema = topBoolOrEmptySchema;
      function boolOrEmptySchema(it, valid) {
        const { gen, schema } = it;
        if (schema === false) {
          gen.var(valid, false);
          falseSchemaError(it);
        } else {
          gen.var(valid, true);
        }
      }
      exports.boolOrEmptySchema = boolOrEmptySchema;
      function falseSchemaError(it, overrideAllErrors) {
        const { gen, data } = it;
        const cxt = {
          gen,
          keyword: "false schema",
          data,
          schema: false,
          schemaCode: false,
          schemaValue: false,
          params: {},
          it
        };
        (0, errors_1.reportError)(cxt, boolError, void 0, overrideAllErrors);
      }
    }
  });

  // node_modules/ajv/dist/compile/rules.js
  var require_rules = __commonJS({
    "node_modules/ajv/dist/compile/rules.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.getRules = exports.isJSONType = void 0;
      var _jsonTypes = ["string", "number", "integer", "boolean", "null", "object", "array"];
      var jsonTypes = new Set(_jsonTypes);
      function isJSONType(x) {
        return typeof x == "string" && jsonTypes.has(x);
      }
      exports.isJSONType = isJSONType;
      function getRules() {
        const groups = {
          number: { type: "number", rules: [] },
          string: { type: "string", rules: [] },
          array: { type: "array", rules: [] },
          object: { type: "object", rules: [] }
        };
        return {
          types: { ...groups, integer: true, boolean: true, null: true },
          rules: [{ rules: [] }, groups.number, groups.string, groups.array, groups.object],
          post: { rules: [] },
          all: {},
          keywords: {}
        };
      }
      exports.getRules = getRules;
    }
  });

  // node_modules/ajv/dist/compile/validate/applicability.js
  var require_applicability = __commonJS({
    "node_modules/ajv/dist/compile/validate/applicability.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.shouldUseRule = exports.shouldUseGroup = exports.schemaHasRulesForType = void 0;
      function schemaHasRulesForType({ schema, self }, type) {
        const group = self.RULES.types[type];
        return group && group !== true && shouldUseGroup(schema, group);
      }
      exports.schemaHasRulesForType = schemaHasRulesForType;
      function shouldUseGroup(schema, group) {
        return group.rules.some((rule) => shouldUseRule(schema, rule));
      }
      exports.shouldUseGroup = shouldUseGroup;
      function shouldUseRule(schema, rule) {
        var _a;
        return schema[rule.keyword] !== void 0 || ((_a = rule.definition.implements) === null || _a === void 0 ? void 0 : _a.some((kwd) => schema[kwd] !== void 0));
      }
      exports.shouldUseRule = shouldUseRule;
    }
  });

  // node_modules/ajv/dist/compile/validate/dataType.js
  var require_dataType = __commonJS({
    "node_modules/ajv/dist/compile/validate/dataType.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.reportTypeError = exports.checkDataTypes = exports.checkDataType = exports.coerceAndCheckDataType = exports.getJSONTypes = exports.getSchemaTypes = exports.DataType = void 0;
      var rules_1 = require_rules();
      var applicability_1 = require_applicability();
      var errors_1 = require_errors();
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var DataType;
      (function(DataType2) {
        DataType2[DataType2["Correct"] = 0] = "Correct";
        DataType2[DataType2["Wrong"] = 1] = "Wrong";
      })(DataType || (exports.DataType = DataType = {}));
      function getSchemaTypes(schema) {
        const types = getJSONTypes(schema.type);
        const hasNull = types.includes("null");
        if (hasNull) {
          if (schema.nullable === false)
            throw new Error("type: null contradicts nullable: false");
        } else {
          if (!types.length && schema.nullable !== void 0) {
            throw new Error('"nullable" cannot be used without "type"');
          }
          if (schema.nullable === true)
            types.push("null");
        }
        return types;
      }
      exports.getSchemaTypes = getSchemaTypes;
      function getJSONTypes(ts) {
        const types = Array.isArray(ts) ? ts : ts ? [ts] : [];
        if (types.every(rules_1.isJSONType))
          return types;
        throw new Error("type must be JSONType or JSONType[]: " + types.join(","));
      }
      exports.getJSONTypes = getJSONTypes;
      function coerceAndCheckDataType(it, types) {
        const { gen, data, opts } = it;
        const coerceTo = coerceToTypes(types, opts.coerceTypes);
        const checkTypes = types.length > 0 && !(coerceTo.length === 0 && types.length === 1 && (0, applicability_1.schemaHasRulesForType)(it, types[0]));
        if (checkTypes) {
          const wrongType = checkDataTypes(types, data, opts.strictNumbers, DataType.Wrong);
          gen.if(wrongType, () => {
            if (coerceTo.length)
              coerceData(it, types, coerceTo);
            else
              reportTypeError(it);
          });
        }
        return checkTypes;
      }
      exports.coerceAndCheckDataType = coerceAndCheckDataType;
      var COERCIBLE = /* @__PURE__ */ new Set(["string", "number", "integer", "boolean", "null"]);
      function coerceToTypes(types, coerceTypes) {
        return coerceTypes ? types.filter((t) => COERCIBLE.has(t) || coerceTypes === "array" && t === "array") : [];
      }
      function coerceData(it, types, coerceTo) {
        const { gen, data, opts } = it;
        const dataType = gen.let("dataType", (0, codegen_1._)`typeof ${data}`);
        const coerced = gen.let("coerced", (0, codegen_1._)`undefined`);
        if (opts.coerceTypes === "array") {
          gen.if((0, codegen_1._)`${dataType} == 'object' && Array.isArray(${data}) && ${data}.length == 1`, () => gen.assign(data, (0, codegen_1._)`${data}[0]`).assign(dataType, (0, codegen_1._)`typeof ${data}`).if(checkDataTypes(types, data, opts.strictNumbers), () => gen.assign(coerced, data)));
        }
        gen.if((0, codegen_1._)`${coerced} !== undefined`);
        for (const t of coerceTo) {
          if (COERCIBLE.has(t) || t === "array" && opts.coerceTypes === "array") {
            coerceSpecificType(t);
          }
        }
        gen.else();
        reportTypeError(it);
        gen.endIf();
        gen.if((0, codegen_1._)`${coerced} !== undefined`, () => {
          gen.assign(data, coerced);
          assignParentData(it, coerced);
        });
        function coerceSpecificType(t) {
          switch (t) {
            case "string":
              gen.elseIf((0, codegen_1._)`${dataType} == "number" || ${dataType} == "boolean"`).assign(coerced, (0, codegen_1._)`"" + ${data}`).elseIf((0, codegen_1._)`${data} === null`).assign(coerced, (0, codegen_1._)`""`);
              return;
            case "number":
              gen.elseIf((0, codegen_1._)`${dataType} == "boolean" || ${data} === null
              || (${dataType} == "string" && ${data} && ${data} == +${data})`).assign(coerced, (0, codegen_1._)`+${data}`);
              return;
            case "integer":
              gen.elseIf((0, codegen_1._)`${dataType} === "boolean" || ${data} === null
              || (${dataType} === "string" && ${data} && ${data} == +${data} && !(${data} % 1))`).assign(coerced, (0, codegen_1._)`+${data}`);
              return;
            case "boolean":
              gen.elseIf((0, codegen_1._)`${data} === "false" || ${data} === 0 || ${data} === null`).assign(coerced, false).elseIf((0, codegen_1._)`${data} === "true" || ${data} === 1`).assign(coerced, true);
              return;
            case "null":
              gen.elseIf((0, codegen_1._)`${data} === "" || ${data} === 0 || ${data} === false`);
              gen.assign(coerced, null);
              return;
            case "array":
              gen.elseIf((0, codegen_1._)`${dataType} === "string" || ${dataType} === "number"
              || ${dataType} === "boolean" || ${data} === null`).assign(coerced, (0, codegen_1._)`[${data}]`);
          }
        }
      }
      function assignParentData({ gen, parentData, parentDataProperty }, expr) {
        gen.if((0, codegen_1._)`${parentData} !== undefined`, () => gen.assign((0, codegen_1._)`${parentData}[${parentDataProperty}]`, expr));
      }
      function checkDataType(dataType, data, strictNums, correct = DataType.Correct) {
        const EQ = correct === DataType.Correct ? codegen_1.operators.EQ : codegen_1.operators.NEQ;
        let cond;
        switch (dataType) {
          case "null":
            return (0, codegen_1._)`${data} ${EQ} null`;
          case "array":
            cond = (0, codegen_1._)`Array.isArray(${data})`;
            break;
          case "object":
            cond = (0, codegen_1._)`${data} && typeof ${data} == "object" && !Array.isArray(${data})`;
            break;
          case "integer":
            cond = numCond((0, codegen_1._)`!(${data} % 1) && !isNaN(${data})`);
            break;
          case "number":
            cond = numCond();
            break;
          default:
            return (0, codegen_1._)`typeof ${data} ${EQ} ${dataType}`;
        }
        return correct === DataType.Correct ? cond : (0, codegen_1.not)(cond);
        function numCond(_cond = codegen_1.nil) {
          return (0, codegen_1.and)((0, codegen_1._)`typeof ${data} == "number"`, _cond, strictNums ? (0, codegen_1._)`isFinite(${data})` : codegen_1.nil);
        }
      }
      exports.checkDataType = checkDataType;
      function checkDataTypes(dataTypes, data, strictNums, correct) {
        if (dataTypes.length === 1) {
          return checkDataType(dataTypes[0], data, strictNums, correct);
        }
        let cond;
        const types = (0, util_1.toHash)(dataTypes);
        if (types.array && types.object) {
          const notObj = (0, codegen_1._)`typeof ${data} != "object"`;
          cond = types.null ? notObj : (0, codegen_1._)`!${data} || ${notObj}`;
          delete types.null;
          delete types.array;
          delete types.object;
        } else {
          cond = codegen_1.nil;
        }
        if (types.number)
          delete types.integer;
        for (const t in types)
          cond = (0, codegen_1.and)(cond, checkDataType(t, data, strictNums, correct));
        return cond;
      }
      exports.checkDataTypes = checkDataTypes;
      var typeError = {
        message: ({ schema }) => `must be ${schema}`,
        params: ({ schema, schemaValue }) => typeof schema == "string" ? (0, codegen_1._)`{type: ${schema}}` : (0, codegen_1._)`{type: ${schemaValue}}`
      };
      function reportTypeError(it) {
        const cxt = getTypeErrorContext(it);
        (0, errors_1.reportError)(cxt, typeError);
      }
      exports.reportTypeError = reportTypeError;
      function getTypeErrorContext(it) {
        const { gen, data, schema } = it;
        const schemaCode = (0, util_1.schemaRefOrVal)(it, schema, "type");
        return {
          gen,
          keyword: "type",
          data,
          schema: schema.type,
          schemaCode,
          schemaValue: schemaCode,
          parentSchema: schema,
          params: {},
          it
        };
      }
    }
  });

  // node_modules/ajv/dist/compile/validate/defaults.js
  var require_defaults = __commonJS({
    "node_modules/ajv/dist/compile/validate/defaults.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.assignDefaults = void 0;
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      function assignDefaults(it, ty) {
        const { properties, items } = it.schema;
        if (ty === "object" && properties) {
          for (const key in properties) {
            assignDefault(it, key, properties[key].default);
          }
        } else if (ty === "array" && Array.isArray(items)) {
          items.forEach((sch, i) => assignDefault(it, i, sch.default));
        }
      }
      exports.assignDefaults = assignDefaults;
      function assignDefault(it, prop, defaultValue) {
        const { gen, compositeRule, data, opts } = it;
        if (defaultValue === void 0)
          return;
        const childData = (0, codegen_1._)`${data}${(0, codegen_1.getProperty)(prop)}`;
        if (compositeRule) {
          (0, util_1.checkStrictMode)(it, `default is ignored for: ${childData}`);
          return;
        }
        let condition = (0, codegen_1._)`${childData} === undefined`;
        if (opts.useDefaults === "empty") {
          condition = (0, codegen_1._)`${condition} || ${childData} === null || ${childData} === ""`;
        }
        gen.if(condition, (0, codegen_1._)`${childData} = ${(0, codegen_1.stringify)(defaultValue)}`);
      }
    }
  });

  // node_modules/ajv/dist/vocabularies/code.js
  var require_code2 = __commonJS({
    "node_modules/ajv/dist/vocabularies/code.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.validateUnion = exports.validateArray = exports.usePattern = exports.callValidateCode = exports.schemaProperties = exports.allSchemaProperties = exports.noPropertyInData = exports.propertyInData = exports.isOwnProperty = exports.hasPropFunc = exports.reportMissingProp = exports.checkMissingProp = exports.checkReportMissingProp = void 0;
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var names_1 = require_names();
      var util_2 = require_util();
      function checkReportMissingProp(cxt, prop) {
        const { gen, data, it } = cxt;
        gen.if(noPropertyInData(gen, data, prop, it.opts.ownProperties), () => {
          cxt.setParams({ missingProperty: (0, codegen_1._)`${prop}` }, true);
          cxt.error();
        });
      }
      exports.checkReportMissingProp = checkReportMissingProp;
      function checkMissingProp({ gen, data, it: { opts } }, properties, missing) {
        return (0, codegen_1.or)(...properties.map((prop) => (0, codegen_1.and)(noPropertyInData(gen, data, prop, opts.ownProperties), (0, codegen_1._)`${missing} = ${prop}`)));
      }
      exports.checkMissingProp = checkMissingProp;
      function reportMissingProp(cxt, missing) {
        cxt.setParams({ missingProperty: missing }, true);
        cxt.error();
      }
      exports.reportMissingProp = reportMissingProp;
      function hasPropFunc(gen) {
        return gen.scopeValue("func", {
          // eslint-disable-next-line @typescript-eslint/unbound-method
          ref: Object.prototype.hasOwnProperty,
          code: (0, codegen_1._)`Object.prototype.hasOwnProperty`
        });
      }
      exports.hasPropFunc = hasPropFunc;
      function isOwnProperty(gen, data, property) {
        return (0, codegen_1._)`${hasPropFunc(gen)}.call(${data}, ${property})`;
      }
      exports.isOwnProperty = isOwnProperty;
      function propertyInData(gen, data, property, ownProperties) {
        const cond = (0, codegen_1._)`${data}${(0, codegen_1.getProperty)(property)} !== undefined`;
        return ownProperties ? (0, codegen_1._)`${cond} && ${isOwnProperty(gen, data, property)}` : cond;
      }
      exports.propertyInData = propertyInData;
      function noPropertyInData(gen, data, property, ownProperties) {
        const cond = (0, codegen_1._)`${data}${(0, codegen_1.getProperty)(property)} === undefined`;
        return ownProperties ? (0, codegen_1.or)(cond, (0, codegen_1.not)(isOwnProperty(gen, data, property))) : cond;
      }
      exports.noPropertyInData = noPropertyInData;
      function allSchemaProperties(schemaMap) {
        return schemaMap ? Object.keys(schemaMap).filter((p) => p !== "__proto__") : [];
      }
      exports.allSchemaProperties = allSchemaProperties;
      function schemaProperties(it, schemaMap) {
        return allSchemaProperties(schemaMap).filter((p) => !(0, util_1.alwaysValidSchema)(it, schemaMap[p]));
      }
      exports.schemaProperties = schemaProperties;
      function callValidateCode({ schemaCode, data, it: { gen, topSchemaRef, schemaPath, errorPath }, it }, func, context, passSchema) {
        const dataAndSchema = passSchema ? (0, codegen_1._)`${schemaCode}, ${data}, ${topSchemaRef}${schemaPath}` : data;
        const valCxt = [
          [names_1.default.instancePath, (0, codegen_1.strConcat)(names_1.default.instancePath, errorPath)],
          [names_1.default.parentData, it.parentData],
          [names_1.default.parentDataProperty, it.parentDataProperty],
          [names_1.default.rootData, names_1.default.rootData]
        ];
        if (it.opts.dynamicRef)
          valCxt.push([names_1.default.dynamicAnchors, names_1.default.dynamicAnchors]);
        const args = (0, codegen_1._)`${dataAndSchema}, ${gen.object(...valCxt)}`;
        return context !== codegen_1.nil ? (0, codegen_1._)`${func}.call(${context}, ${args})` : (0, codegen_1._)`${func}(${args})`;
      }
      exports.callValidateCode = callValidateCode;
      var newRegExp = (0, codegen_1._)`new RegExp`;
      function usePattern({ gen, it: { opts } }, pattern) {
        const u = opts.unicodeRegExp ? "u" : "";
        const { regExp } = opts.code;
        const rx = regExp(pattern, u);
        return gen.scopeValue("pattern", {
          key: rx.toString(),
          ref: rx,
          code: (0, codegen_1._)`${regExp.code === "new RegExp" ? newRegExp : (0, util_2.useFunc)(gen, regExp)}(${pattern}, ${u})`
        });
      }
      exports.usePattern = usePattern;
      function validateArray(cxt) {
        const { gen, data, keyword, it } = cxt;
        const valid = gen.name("valid");
        if (it.allErrors) {
          const validArr = gen.let("valid", true);
          validateItems(() => gen.assign(validArr, false));
          return validArr;
        }
        gen.var(valid, true);
        validateItems(() => gen.break());
        return valid;
        function validateItems(notValid) {
          const len = gen.const("len", (0, codegen_1._)`${data}.length`);
          gen.forRange("i", 0, len, (i) => {
            cxt.subschema({
              keyword,
              dataProp: i,
              dataPropType: util_1.Type.Num
            }, valid);
            gen.if((0, codegen_1.not)(valid), notValid);
          });
        }
      }
      exports.validateArray = validateArray;
      function validateUnion(cxt) {
        const { gen, schema, keyword, it } = cxt;
        if (!Array.isArray(schema))
          throw new Error("ajv implementation error");
        const alwaysValid = schema.some((sch) => (0, util_1.alwaysValidSchema)(it, sch));
        if (alwaysValid && !it.opts.unevaluated)
          return;
        const valid = gen.let("valid", false);
        const schValid = gen.name("_valid");
        gen.block(() => schema.forEach((_sch, i) => {
          const schCxt = cxt.subschema({
            keyword,
            schemaProp: i,
            compositeRule: true
          }, schValid);
          gen.assign(valid, (0, codegen_1._)`${valid} || ${schValid}`);
          const merged = cxt.mergeValidEvaluated(schCxt, schValid);
          if (!merged)
            gen.if((0, codegen_1.not)(valid));
        }));
        cxt.result(valid, () => cxt.reset(), () => cxt.error(true));
      }
      exports.validateUnion = validateUnion;
    }
  });

  // node_modules/ajv/dist/compile/validate/keyword.js
  var require_keyword = __commonJS({
    "node_modules/ajv/dist/compile/validate/keyword.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.validateKeywordUsage = exports.validSchemaType = exports.funcKeywordCode = exports.macroKeywordCode = void 0;
      var codegen_1 = require_codegen();
      var names_1 = require_names();
      var code_1 = require_code2();
      var errors_1 = require_errors();
      function macroKeywordCode(cxt, def) {
        const { gen, keyword, schema, parentSchema, it } = cxt;
        const macroSchema = def.macro.call(it.self, schema, parentSchema, it);
        const schemaRef = useKeyword(gen, keyword, macroSchema);
        if (it.opts.validateSchema !== false)
          it.self.validateSchema(macroSchema, true);
        const valid = gen.name("valid");
        cxt.subschema({
          schema: macroSchema,
          schemaPath: codegen_1.nil,
          errSchemaPath: `${it.errSchemaPath}/${keyword}`,
          topSchemaRef: schemaRef,
          compositeRule: true
        }, valid);
        cxt.pass(valid, () => cxt.error(true));
      }
      exports.macroKeywordCode = macroKeywordCode;
      function funcKeywordCode(cxt, def) {
        var _a;
        const { gen, keyword, schema, parentSchema, $data, it } = cxt;
        checkAsyncKeyword(it, def);
        const validate = !$data && def.compile ? def.compile.call(it.self, schema, parentSchema, it) : def.validate;
        const validateRef = useKeyword(gen, keyword, validate);
        const valid = gen.let("valid");
        cxt.block$data(valid, validateKeyword);
        cxt.ok((_a = def.valid) !== null && _a !== void 0 ? _a : valid);
        function validateKeyword() {
          if (def.errors === false) {
            assignValid();
            if (def.modifying)
              modifyData(cxt);
            reportErrs(() => cxt.error());
          } else {
            const ruleErrs = def.async ? validateAsync() : validateSync();
            if (def.modifying)
              modifyData(cxt);
            reportErrs(() => addErrs(cxt, ruleErrs));
          }
        }
        function validateAsync() {
          const ruleErrs = gen.let("ruleErrs", null);
          gen.try(() => assignValid((0, codegen_1._)`await `), (e) => gen.assign(valid, false).if((0, codegen_1._)`${e} instanceof ${it.ValidationError}`, () => gen.assign(ruleErrs, (0, codegen_1._)`${e}.errors`), () => gen.throw(e)));
          return ruleErrs;
        }
        function validateSync() {
          const validateErrs = (0, codegen_1._)`${validateRef}.errors`;
          gen.assign(validateErrs, null);
          assignValid(codegen_1.nil);
          return validateErrs;
        }
        function assignValid(_await = def.async ? (0, codegen_1._)`await ` : codegen_1.nil) {
          const passCxt = it.opts.passContext ? names_1.default.this : names_1.default.self;
          const passSchema = !("compile" in def && !$data || def.schema === false);
          gen.assign(valid, (0, codegen_1._)`${_await}${(0, code_1.callValidateCode)(cxt, validateRef, passCxt, passSchema)}`, def.modifying);
        }
        function reportErrs(errors2) {
          var _a2;
          gen.if((0, codegen_1.not)((_a2 = def.valid) !== null && _a2 !== void 0 ? _a2 : valid), errors2);
        }
      }
      exports.funcKeywordCode = funcKeywordCode;
      function modifyData(cxt) {
        const { gen, data, it } = cxt;
        gen.if(it.parentData, () => gen.assign(data, (0, codegen_1._)`${it.parentData}[${it.parentDataProperty}]`));
      }
      function addErrs(cxt, errs) {
        const { gen } = cxt;
        gen.if((0, codegen_1._)`Array.isArray(${errs})`, () => {
          gen.assign(names_1.default.vErrors, (0, codegen_1._)`${names_1.default.vErrors} === null ? ${errs} : ${names_1.default.vErrors}.concat(${errs})`).assign(names_1.default.errors, (0, codegen_1._)`${names_1.default.vErrors}.length`);
          (0, errors_1.extendErrors)(cxt);
        }, () => cxt.error());
      }
      function checkAsyncKeyword({ schemaEnv }, def) {
        if (def.async && !schemaEnv.$async)
          throw new Error("async keyword in sync schema");
      }
      function useKeyword(gen, keyword, result) {
        if (result === void 0)
          throw new Error(`keyword "${keyword}" failed to compile`);
        return gen.scopeValue("keyword", typeof result == "function" ? { ref: result } : { ref: result, code: (0, codegen_1.stringify)(result) });
      }
      function validSchemaType(schema, schemaType, allowUndefined = false) {
        return !schemaType.length || schemaType.some((st) => st === "array" ? Array.isArray(schema) : st === "object" ? schema && typeof schema == "object" && !Array.isArray(schema) : typeof schema == st || allowUndefined && typeof schema == "undefined");
      }
      exports.validSchemaType = validSchemaType;
      function validateKeywordUsage({ schema, opts, self, errSchemaPath }, def, keyword) {
        if (Array.isArray(def.keyword) ? !def.keyword.includes(keyword) : def.keyword !== keyword) {
          throw new Error("ajv implementation error");
        }
        const deps = def.dependencies;
        if (deps === null || deps === void 0 ? void 0 : deps.some((kwd) => !Object.prototype.hasOwnProperty.call(schema, kwd))) {
          throw new Error(`parent schema must have dependencies of ${keyword}: ${deps.join(",")}`);
        }
        if (def.validateSchema) {
          const valid = def.validateSchema(schema[keyword]);
          if (!valid) {
            const msg = `keyword "${keyword}" value is invalid at path "${errSchemaPath}": ` + self.errorsText(def.validateSchema.errors);
            if (opts.validateSchema === "log")
              self.logger.error(msg);
            else
              throw new Error(msg);
          }
        }
      }
      exports.validateKeywordUsage = validateKeywordUsage;
    }
  });

  // node_modules/ajv/dist/compile/validate/subschema.js
  var require_subschema = __commonJS({
    "node_modules/ajv/dist/compile/validate/subschema.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.extendSubschemaMode = exports.extendSubschemaData = exports.getSubschema = void 0;
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      function getSubschema(it, { keyword, schemaProp, schema, schemaPath, errSchemaPath, topSchemaRef }) {
        if (keyword !== void 0 && schema !== void 0) {
          throw new Error('both "keyword" and "schema" passed, only one allowed');
        }
        if (keyword !== void 0) {
          const sch = it.schema[keyword];
          return schemaProp === void 0 ? {
            schema: sch,
            schemaPath: (0, codegen_1._)`${it.schemaPath}${(0, codegen_1.getProperty)(keyword)}`,
            errSchemaPath: `${it.errSchemaPath}/${keyword}`
          } : {
            schema: sch[schemaProp],
            schemaPath: (0, codegen_1._)`${it.schemaPath}${(0, codegen_1.getProperty)(keyword)}${(0, codegen_1.getProperty)(schemaProp)}`,
            errSchemaPath: `${it.errSchemaPath}/${keyword}/${(0, util_1.escapeFragment)(schemaProp)}`
          };
        }
        if (schema !== void 0) {
          if (schemaPath === void 0 || errSchemaPath === void 0 || topSchemaRef === void 0) {
            throw new Error('"schemaPath", "errSchemaPath" and "topSchemaRef" are required with "schema"');
          }
          return {
            schema,
            schemaPath,
            topSchemaRef,
            errSchemaPath
          };
        }
        throw new Error('either "keyword" or "schema" must be passed');
      }
      exports.getSubschema = getSubschema;
      function extendSubschemaData(subschema, it, { dataProp, dataPropType: dpType, data, dataTypes, propertyName }) {
        if (data !== void 0 && dataProp !== void 0) {
          throw new Error('both "data" and "dataProp" passed, only one allowed');
        }
        const { gen } = it;
        if (dataProp !== void 0) {
          const { errorPath, dataPathArr, opts } = it;
          const nextData = gen.let("data", (0, codegen_1._)`${it.data}${(0, codegen_1.getProperty)(dataProp)}`, true);
          dataContextProps(nextData);
          subschema.errorPath = (0, codegen_1.str)`${errorPath}${(0, util_1.getErrorPath)(dataProp, dpType, opts.jsPropertySyntax)}`;
          subschema.parentDataProperty = (0, codegen_1._)`${dataProp}`;
          subschema.dataPathArr = [...dataPathArr, subschema.parentDataProperty];
        }
        if (data !== void 0) {
          const nextData = data instanceof codegen_1.Name ? data : gen.let("data", data, true);
          dataContextProps(nextData);
          if (propertyName !== void 0)
            subschema.propertyName = propertyName;
        }
        if (dataTypes)
          subschema.dataTypes = dataTypes;
        function dataContextProps(_nextData) {
          subschema.data = _nextData;
          subschema.dataLevel = it.dataLevel + 1;
          subschema.dataTypes = [];
          it.definedProperties = /* @__PURE__ */ new Set();
          subschema.parentData = it.data;
          subschema.dataNames = [...it.dataNames, _nextData];
        }
      }
      exports.extendSubschemaData = extendSubschemaData;
      function extendSubschemaMode(subschema, { jtdDiscriminator, jtdMetadata, compositeRule, createErrors, allErrors }) {
        if (compositeRule !== void 0)
          subschema.compositeRule = compositeRule;
        if (createErrors !== void 0)
          subschema.createErrors = createErrors;
        if (allErrors !== void 0)
          subschema.allErrors = allErrors;
        subschema.jtdDiscriminator = jtdDiscriminator;
        subschema.jtdMetadata = jtdMetadata;
      }
      exports.extendSubschemaMode = extendSubschemaMode;
    }
  });

  // node_modules/fast-deep-equal/index.js
  var require_fast_deep_equal = __commonJS({
    "node_modules/fast-deep-equal/index.js"(exports, module) {
      "use strict";
      module.exports = function equal(a, b) {
        if (a === b) return true;
        if (a && b && typeof a == "object" && typeof b == "object") {
          if (a.constructor !== b.constructor) return false;
          var length, i, keys2;
          if (Array.isArray(a)) {
            length = a.length;
            if (length != b.length) return false;
            for (i = length; i-- !== 0; )
              if (!equal(a[i], b[i])) return false;
            return true;
          }
          if (a.constructor === RegExp) return a.source === b.source && a.flags === b.flags;
          if (a.valueOf !== Object.prototype.valueOf) return a.valueOf() === b.valueOf();
          if (a.toString !== Object.prototype.toString) return a.toString() === b.toString();
          keys2 = Object.keys(a);
          length = keys2.length;
          if (length !== Object.keys(b).length) return false;
          for (i = length; i-- !== 0; )
            if (!Object.prototype.hasOwnProperty.call(b, keys2[i])) return false;
          for (i = length; i-- !== 0; ) {
            var key = keys2[i];
            if (!equal(a[key], b[key])) return false;
          }
          return true;
        }
        return a !== a && b !== b;
      };
    }
  });

  // node_modules/json-schema-traverse/index.js
  var require_json_schema_traverse = __commonJS({
    "node_modules/json-schema-traverse/index.js"(exports, module) {
      "use strict";
      var traverse = module.exports = function(schema, opts, cb) {
        if (typeof opts == "function") {
          cb = opts;
          opts = {};
        }
        cb = opts.cb || cb;
        var pre = typeof cb == "function" ? cb : cb.pre || function() {
        };
        var post = cb.post || function() {
        };
        _traverse(opts, pre, post, schema, "", schema);
      };
      traverse.keywords = {
        additionalItems: true,
        items: true,
        contains: true,
        additionalProperties: true,
        propertyNames: true,
        not: true,
        if: true,
        then: true,
        else: true
      };
      traverse.arrayKeywords = {
        items: true,
        allOf: true,
        anyOf: true,
        oneOf: true
      };
      traverse.propsKeywords = {
        $defs: true,
        definitions: true,
        properties: true,
        patternProperties: true,
        dependencies: true
      };
      traverse.skipKeywords = {
        default: true,
        enum: true,
        const: true,
        required: true,
        maximum: true,
        minimum: true,
        exclusiveMaximum: true,
        exclusiveMinimum: true,
        multipleOf: true,
        maxLength: true,
        minLength: true,
        pattern: true,
        format: true,
        maxItems: true,
        minItems: true,
        uniqueItems: true,
        maxProperties: true,
        minProperties: true
      };
      function _traverse(opts, pre, post, schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex) {
        if (schema && typeof schema == "object" && !Array.isArray(schema)) {
          pre(schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex);
          for (var key in schema) {
            var sch = schema[key];
            if (Array.isArray(sch)) {
              if (key in traverse.arrayKeywords) {
                for (var i = 0; i < sch.length; i++)
                  _traverse(opts, pre, post, sch[i], jsonPtr + "/" + key + "/" + i, rootSchema, jsonPtr, key, schema, i);
              }
            } else if (key in traverse.propsKeywords) {
              if (sch && typeof sch == "object") {
                for (var prop in sch)
                  _traverse(opts, pre, post, sch[prop], jsonPtr + "/" + key + "/" + escapeJsonPtr(prop), rootSchema, jsonPtr, key, schema, prop);
              }
            } else if (key in traverse.keywords || opts.allKeys && !(key in traverse.skipKeywords)) {
              _traverse(opts, pre, post, sch, jsonPtr + "/" + key, rootSchema, jsonPtr, key, schema);
            }
          }
          post(schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex);
        }
      }
      function escapeJsonPtr(str) {
        return str.replace(/~/g, "~0").replace(/\//g, "~1");
      }
    }
  });

  // node_modules/ajv/dist/compile/resolve.js
  var require_resolve = __commonJS({
    "node_modules/ajv/dist/compile/resolve.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.getSchemaRefs = exports.resolveUrl = exports.normalizeId = exports._getFullPath = exports.getFullPath = exports.inlineRef = void 0;
      var util_1 = require_util();
      var equal = require_fast_deep_equal();
      var traverse = require_json_schema_traverse();
      var SIMPLE_INLINED = /* @__PURE__ */ new Set([
        "type",
        "format",
        "pattern",
        "maxLength",
        "minLength",
        "maxProperties",
        "minProperties",
        "maxItems",
        "minItems",
        "maximum",
        "minimum",
        "uniqueItems",
        "multipleOf",
        "required",
        "enum",
        "const"
      ]);
      function inlineRef(schema, limit = true) {
        if (typeof schema == "boolean")
          return true;
        if (limit === true)
          return !hasRef(schema);
        if (!limit)
          return false;
        return countKeys(schema) <= limit;
      }
      exports.inlineRef = inlineRef;
      var REF_KEYWORDS = /* @__PURE__ */ new Set([
        "$ref",
        "$recursiveRef",
        "$recursiveAnchor",
        "$dynamicRef",
        "$dynamicAnchor"
      ]);
      function hasRef(schema) {
        for (const key in schema) {
          if (REF_KEYWORDS.has(key))
            return true;
          const sch = schema[key];
          if (Array.isArray(sch) && sch.some(hasRef))
            return true;
          if (typeof sch == "object" && hasRef(sch))
            return true;
        }
        return false;
      }
      function countKeys(schema) {
        let count = 0;
        for (const key in schema) {
          if (key === "$ref")
            return Infinity;
          count++;
          if (SIMPLE_INLINED.has(key))
            continue;
          if (typeof schema[key] == "object") {
            (0, util_1.eachItem)(schema[key], (sch) => count += countKeys(sch));
          }
          if (count === Infinity)
            return Infinity;
        }
        return count;
      }
      function getFullPath(resolver, id = "", normalize) {
        if (normalize !== false)
          id = normalizeId(id);
        const p = resolver.parse(id);
        return _getFullPath(resolver, p);
      }
      exports.getFullPath = getFullPath;
      function _getFullPath(resolver, p) {
        const serialized = resolver.serialize(p);
        return serialized.split("#")[0] + "#";
      }
      exports._getFullPath = _getFullPath;
      var TRAILING_SLASH_HASH = /#\/?$/;
      function normalizeId(id) {
        return id ? id.replace(TRAILING_SLASH_HASH, "") : "";
      }
      exports.normalizeId = normalizeId;
      function resolveUrl(resolver, baseId, id) {
        id = normalizeId(id);
        return resolver.resolve(baseId, id);
      }
      exports.resolveUrl = resolveUrl;
      var ANCHOR = /^[a-z_][-a-z0-9._]*$/i;
      function getSchemaRefs(schema, baseId) {
        if (typeof schema == "boolean")
          return {};
        const { schemaId, uriResolver } = this.opts;
        const schId = normalizeId(schema[schemaId] || baseId);
        const baseIds = { "": schId };
        const pathPrefix = getFullPath(uriResolver, schId, false);
        const localRefs = {};
        const schemaRefs = /* @__PURE__ */ new Set();
        traverse(schema, { allKeys: true }, (sch, jsonPtr, _, parentJsonPtr) => {
          if (parentJsonPtr === void 0)
            return;
          const fullPath = pathPrefix + jsonPtr;
          let innerBaseId = baseIds[parentJsonPtr];
          if (typeof sch[schemaId] == "string")
            innerBaseId = addRef.call(this, sch[schemaId]);
          addAnchor.call(this, sch.$anchor);
          addAnchor.call(this, sch.$dynamicAnchor);
          baseIds[jsonPtr] = innerBaseId;
          function addRef(ref) {
            const _resolve = this.opts.uriResolver.resolve;
            ref = normalizeId(innerBaseId ? _resolve(innerBaseId, ref) : ref);
            if (schemaRefs.has(ref))
              throw ambiguos(ref);
            schemaRefs.add(ref);
            let schOrRef = this.refs[ref];
            if (typeof schOrRef == "string")
              schOrRef = this.refs[schOrRef];
            if (typeof schOrRef == "object") {
              checkAmbiguosRef(sch, schOrRef.schema, ref);
            } else if (ref !== normalizeId(fullPath)) {
              if (ref[0] === "#") {
                checkAmbiguosRef(sch, localRefs[ref], ref);
                localRefs[ref] = sch;
              } else {
                this.refs[ref] = fullPath;
              }
            }
            return ref;
          }
          function addAnchor(anchor) {
            if (typeof anchor == "string") {
              if (!ANCHOR.test(anchor))
                throw new Error(`invalid anchor "${anchor}"`);
              addRef.call(this, `#${anchor}`);
            }
          }
        });
        return localRefs;
        function checkAmbiguosRef(sch1, sch2, ref) {
          if (sch2 !== void 0 && !equal(sch1, sch2))
            throw ambiguos(ref);
        }
        function ambiguos(ref) {
          return new Error(`reference "${ref}" resolves to more than one schema`);
        }
      }
      exports.getSchemaRefs = getSchemaRefs;
    }
  });

  // node_modules/ajv/dist/compile/validate/index.js
  var require_validate = __commonJS({
    "node_modules/ajv/dist/compile/validate/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.getData = exports.KeywordCxt = exports.validateFunctionCode = void 0;
      var boolSchema_1 = require_boolSchema();
      var dataType_1 = require_dataType();
      var applicability_1 = require_applicability();
      var dataType_2 = require_dataType();
      var defaults_1 = require_defaults();
      var keyword_1 = require_keyword();
      var subschema_1 = require_subschema();
      var codegen_1 = require_codegen();
      var names_1 = require_names();
      var resolve_1 = require_resolve();
      var util_1 = require_util();
      var errors_1 = require_errors();
      function validateFunctionCode(it) {
        if (isSchemaObj(it)) {
          checkKeywords(it);
          if (schemaCxtHasRules(it)) {
            topSchemaObjCode(it);
            return;
          }
        }
        validateFunction(it, () => (0, boolSchema_1.topBoolOrEmptySchema)(it));
      }
      exports.validateFunctionCode = validateFunctionCode;
      function validateFunction({ gen, validateName, schema, schemaEnv, opts }, body) {
        if (opts.code.es5) {
          gen.func(validateName, (0, codegen_1._)`${names_1.default.data}, ${names_1.default.valCxt}`, schemaEnv.$async, () => {
            gen.code((0, codegen_1._)`"use strict"; ${funcSourceUrl(schema, opts)}`);
            destructureValCxtES5(gen, opts);
            gen.code(body);
          });
        } else {
          gen.func(validateName, (0, codegen_1._)`${names_1.default.data}, ${destructureValCxt(opts)}`, schemaEnv.$async, () => gen.code(funcSourceUrl(schema, opts)).code(body));
        }
      }
      function destructureValCxt(opts) {
        return (0, codegen_1._)`{${names_1.default.instancePath}="", ${names_1.default.parentData}, ${names_1.default.parentDataProperty}, ${names_1.default.rootData}=${names_1.default.data}${opts.dynamicRef ? (0, codegen_1._)`, ${names_1.default.dynamicAnchors}={}` : codegen_1.nil}}={}`;
      }
      function destructureValCxtES5(gen, opts) {
        gen.if(names_1.default.valCxt, () => {
          gen.var(names_1.default.instancePath, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.instancePath}`);
          gen.var(names_1.default.parentData, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.parentData}`);
          gen.var(names_1.default.parentDataProperty, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.parentDataProperty}`);
          gen.var(names_1.default.rootData, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.rootData}`);
          if (opts.dynamicRef)
            gen.var(names_1.default.dynamicAnchors, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.dynamicAnchors}`);
        }, () => {
          gen.var(names_1.default.instancePath, (0, codegen_1._)`""`);
          gen.var(names_1.default.parentData, (0, codegen_1._)`undefined`);
          gen.var(names_1.default.parentDataProperty, (0, codegen_1._)`undefined`);
          gen.var(names_1.default.rootData, names_1.default.data);
          if (opts.dynamicRef)
            gen.var(names_1.default.dynamicAnchors, (0, codegen_1._)`{}`);
        });
      }
      function topSchemaObjCode(it) {
        const { schema, opts, gen } = it;
        validateFunction(it, () => {
          if (opts.$comment && schema.$comment)
            commentKeyword(it);
          checkNoDefault(it);
          gen.let(names_1.default.vErrors, null);
          gen.let(names_1.default.errors, 0);
          if (opts.unevaluated)
            resetEvaluated(it);
          typeAndKeywords(it);
          returnResults(it);
        });
        return;
      }
      function resetEvaluated(it) {
        const { gen, validateName } = it;
        it.evaluated = gen.const("evaluated", (0, codegen_1._)`${validateName}.evaluated`);
        gen.if((0, codegen_1._)`${it.evaluated}.dynamicProps`, () => gen.assign((0, codegen_1._)`${it.evaluated}.props`, (0, codegen_1._)`undefined`));
        gen.if((0, codegen_1._)`${it.evaluated}.dynamicItems`, () => gen.assign((0, codegen_1._)`${it.evaluated}.items`, (0, codegen_1._)`undefined`));
      }
      function funcSourceUrl(schema, opts) {
        const schId = typeof schema == "object" && schema[opts.schemaId];
        return schId && (opts.code.source || opts.code.process) ? (0, codegen_1._)`/*# sourceURL=${schId} */` : codegen_1.nil;
      }
      function subschemaCode(it, valid) {
        if (isSchemaObj(it)) {
          checkKeywords(it);
          if (schemaCxtHasRules(it)) {
            subSchemaObjCode(it, valid);
            return;
          }
        }
        (0, boolSchema_1.boolOrEmptySchema)(it, valid);
      }
      function schemaCxtHasRules({ schema, self }) {
        if (typeof schema == "boolean")
          return !schema;
        for (const key in schema)
          if (self.RULES.all[key])
            return true;
        return false;
      }
      function isSchemaObj(it) {
        return typeof it.schema != "boolean";
      }
      function subSchemaObjCode(it, valid) {
        const { schema, gen, opts } = it;
        if (opts.$comment && schema.$comment)
          commentKeyword(it);
        updateContext(it);
        checkAsyncSchema(it);
        const errsCount = gen.const("_errs", names_1.default.errors);
        typeAndKeywords(it, errsCount);
        gen.var(valid, (0, codegen_1._)`${errsCount} === ${names_1.default.errors}`);
      }
      function checkKeywords(it) {
        (0, util_1.checkUnknownRules)(it);
        checkRefsAndKeywords(it);
      }
      function typeAndKeywords(it, errsCount) {
        if (it.opts.jtd)
          return schemaKeywords(it, [], false, errsCount);
        const types = (0, dataType_1.getSchemaTypes)(it.schema);
        const checkedTypes = (0, dataType_1.coerceAndCheckDataType)(it, types);
        schemaKeywords(it, types, !checkedTypes, errsCount);
      }
      function checkRefsAndKeywords(it) {
        const { schema, errSchemaPath, opts, self } = it;
        if (schema.$ref && opts.ignoreKeywordsWithRef && (0, util_1.schemaHasRulesButRef)(schema, self.RULES)) {
          self.logger.warn(`$ref: keywords ignored in schema at path "${errSchemaPath}"`);
        }
      }
      function checkNoDefault(it) {
        const { schema, opts } = it;
        if (schema.default !== void 0 && opts.useDefaults && opts.strictSchema) {
          (0, util_1.checkStrictMode)(it, "default is ignored in the schema root");
        }
      }
      function updateContext(it) {
        const schId = it.schema[it.opts.schemaId];
        if (schId)
          it.baseId = (0, resolve_1.resolveUrl)(it.opts.uriResolver, it.baseId, schId);
      }
      function checkAsyncSchema(it) {
        if (it.schema.$async && !it.schemaEnv.$async)
          throw new Error("async schema in sync schema");
      }
      function commentKeyword({ gen, schemaEnv, schema, errSchemaPath, opts }) {
        const msg = schema.$comment;
        if (opts.$comment === true) {
          gen.code((0, codegen_1._)`${names_1.default.self}.logger.log(${msg})`);
        } else if (typeof opts.$comment == "function") {
          const schemaPath = (0, codegen_1.str)`${errSchemaPath}/$comment`;
          const rootName = gen.scopeValue("root", { ref: schemaEnv.root });
          gen.code((0, codegen_1._)`${names_1.default.self}.opts.$comment(${msg}, ${schemaPath}, ${rootName}.schema)`);
        }
      }
      function returnResults(it) {
        const { gen, schemaEnv, validateName, ValidationError, opts } = it;
        if (schemaEnv.$async) {
          gen.if((0, codegen_1._)`${names_1.default.errors} === 0`, () => gen.return(names_1.default.data), () => gen.throw((0, codegen_1._)`new ${ValidationError}(${names_1.default.vErrors})`));
        } else {
          gen.assign((0, codegen_1._)`${validateName}.errors`, names_1.default.vErrors);
          if (opts.unevaluated)
            assignEvaluated(it);
          gen.return((0, codegen_1._)`${names_1.default.errors} === 0`);
        }
      }
      function assignEvaluated({ gen, evaluated, props, items }) {
        if (props instanceof codegen_1.Name)
          gen.assign((0, codegen_1._)`${evaluated}.props`, props);
        if (items instanceof codegen_1.Name)
          gen.assign((0, codegen_1._)`${evaluated}.items`, items);
      }
      function schemaKeywords(it, types, typeErrors, errsCount) {
        const { gen, schema, data, allErrors, opts, self } = it;
        const { RULES } = self;
        if (schema.$ref && (opts.ignoreKeywordsWithRef || !(0, util_1.schemaHasRulesButRef)(schema, RULES))) {
          gen.block(() => keywordCode(it, "$ref", RULES.all.$ref.definition));
          return;
        }
        if (!opts.jtd)
          checkStrictTypes(it, types);
        gen.block(() => {
          for (const group of RULES.rules)
            groupKeywords(group);
          groupKeywords(RULES.post);
        });
        function groupKeywords(group) {
          if (!(0, applicability_1.shouldUseGroup)(schema, group))
            return;
          if (group.type) {
            gen.if((0, dataType_2.checkDataType)(group.type, data, opts.strictNumbers));
            iterateKeywords(it, group);
            if (types.length === 1 && types[0] === group.type && typeErrors) {
              gen.else();
              (0, dataType_2.reportTypeError)(it);
            }
            gen.endIf();
          } else {
            iterateKeywords(it, group);
          }
          if (!allErrors)
            gen.if((0, codegen_1._)`${names_1.default.errors} === ${errsCount || 0}`);
        }
      }
      function iterateKeywords(it, group) {
        const { gen, schema, opts: { useDefaults } } = it;
        if (useDefaults)
          (0, defaults_1.assignDefaults)(it, group.type);
        gen.block(() => {
          for (const rule of group.rules) {
            if ((0, applicability_1.shouldUseRule)(schema, rule)) {
              keywordCode(it, rule.keyword, rule.definition, group.type);
            }
          }
        });
      }
      function checkStrictTypes(it, types) {
        if (it.schemaEnv.meta || !it.opts.strictTypes)
          return;
        checkContextTypes(it, types);
        if (!it.opts.allowUnionTypes)
          checkMultipleTypes(it, types);
        checkKeywordTypes(it, it.dataTypes);
      }
      function checkContextTypes(it, types) {
        if (!types.length)
          return;
        if (!it.dataTypes.length) {
          it.dataTypes = types;
          return;
        }
        types.forEach((t) => {
          if (!includesType(it.dataTypes, t)) {
            strictTypesError(it, `type "${t}" not allowed by context "${it.dataTypes.join(",")}"`);
          }
        });
        narrowSchemaTypes(it, types);
      }
      function checkMultipleTypes(it, ts) {
        if (ts.length > 1 && !(ts.length === 2 && ts.includes("null"))) {
          strictTypesError(it, "use allowUnionTypes to allow union type keyword");
        }
      }
      function checkKeywordTypes(it, ts) {
        const rules = it.self.RULES.all;
        for (const keyword in rules) {
          const rule = rules[keyword];
          if (typeof rule == "object" && (0, applicability_1.shouldUseRule)(it.schema, rule)) {
            const { type } = rule.definition;
            if (type.length && !type.some((t) => hasApplicableType(ts, t))) {
              strictTypesError(it, `missing type "${type.join(",")}" for keyword "${keyword}"`);
            }
          }
        }
      }
      function hasApplicableType(schTs, kwdT) {
        return schTs.includes(kwdT) || kwdT === "number" && schTs.includes("integer");
      }
      function includesType(ts, t) {
        return ts.includes(t) || t === "integer" && ts.includes("number");
      }
      function narrowSchemaTypes(it, withTypes) {
        const ts = [];
        for (const t of it.dataTypes) {
          if (includesType(withTypes, t))
            ts.push(t);
          else if (withTypes.includes("integer") && t === "number")
            ts.push("integer");
        }
        it.dataTypes = ts;
      }
      function strictTypesError(it, msg) {
        const schemaPath = it.schemaEnv.baseId + it.errSchemaPath;
        msg += ` at "${schemaPath}" (strictTypes)`;
        (0, util_1.checkStrictMode)(it, msg, it.opts.strictTypes);
      }
      var KeywordCxt = class {
        constructor(it, def, keyword) {
          (0, keyword_1.validateKeywordUsage)(it, def, keyword);
          this.gen = it.gen;
          this.allErrors = it.allErrors;
          this.keyword = keyword;
          this.data = it.data;
          this.schema = it.schema[keyword];
          this.$data = def.$data && it.opts.$data && this.schema && this.schema.$data;
          this.schemaValue = (0, util_1.schemaRefOrVal)(it, this.schema, keyword, this.$data);
          this.schemaType = def.schemaType;
          this.parentSchema = it.schema;
          this.params = {};
          this.it = it;
          this.def = def;
          if (this.$data) {
            this.schemaCode = it.gen.const("vSchema", getData(this.$data, it));
          } else {
            this.schemaCode = this.schemaValue;
            if (!(0, keyword_1.validSchemaType)(this.schema, def.schemaType, def.allowUndefined)) {
              throw new Error(`${keyword} value must be ${JSON.stringify(def.schemaType)}`);
            }
          }
          if ("code" in def ? def.trackErrors : def.errors !== false) {
            this.errsCount = it.gen.const("_errs", names_1.default.errors);
          }
        }
        result(condition, successAction, failAction) {
          this.failResult((0, codegen_1.not)(condition), successAction, failAction);
        }
        failResult(condition, successAction, failAction) {
          this.gen.if(condition);
          if (failAction)
            failAction();
          else
            this.error();
          if (successAction) {
            this.gen.else();
            successAction();
            if (this.allErrors)
              this.gen.endIf();
          } else {
            if (this.allErrors)
              this.gen.endIf();
            else
              this.gen.else();
          }
        }
        pass(condition, failAction) {
          this.failResult((0, codegen_1.not)(condition), void 0, failAction);
        }
        fail(condition) {
          if (condition === void 0) {
            this.error();
            if (!this.allErrors)
              this.gen.if(false);
            return;
          }
          this.gen.if(condition);
          this.error();
          if (this.allErrors)
            this.gen.endIf();
          else
            this.gen.else();
        }
        fail$data(condition) {
          if (!this.$data)
            return this.fail(condition);
          const { schemaCode } = this;
          this.fail((0, codegen_1._)`${schemaCode} !== undefined && (${(0, codegen_1.or)(this.invalid$data(), condition)})`);
        }
        error(append, errorParams, errorPaths) {
          if (errorParams) {
            this.setParams(errorParams);
            this._error(append, errorPaths);
            this.setParams({});
            return;
          }
          this._error(append, errorPaths);
        }
        _error(append, errorPaths) {
          ;
          (append ? errors_1.reportExtraError : errors_1.reportError)(this, this.def.error, errorPaths);
        }
        $dataError() {
          (0, errors_1.reportError)(this, this.def.$dataError || errors_1.keyword$DataError);
        }
        reset() {
          if (this.errsCount === void 0)
            throw new Error('add "trackErrors" to keyword definition');
          (0, errors_1.resetErrorsCount)(this.gen, this.errsCount);
        }
        ok(cond) {
          if (!this.allErrors)
            this.gen.if(cond);
        }
        setParams(obj, assign) {
          if (assign)
            Object.assign(this.params, obj);
          else
            this.params = obj;
        }
        block$data(valid, codeBlock, $dataValid = codegen_1.nil) {
          this.gen.block(() => {
            this.check$data(valid, $dataValid);
            codeBlock();
          });
        }
        check$data(valid = codegen_1.nil, $dataValid = codegen_1.nil) {
          if (!this.$data)
            return;
          const { gen, schemaCode, schemaType, def } = this;
          gen.if((0, codegen_1.or)((0, codegen_1._)`${schemaCode} === undefined`, $dataValid));
          if (valid !== codegen_1.nil)
            gen.assign(valid, true);
          if (schemaType.length || def.validateSchema) {
            gen.elseIf(this.invalid$data());
            this.$dataError();
            if (valid !== codegen_1.nil)
              gen.assign(valid, false);
          }
          gen.else();
        }
        invalid$data() {
          const { gen, schemaCode, schemaType, def, it } = this;
          return (0, codegen_1.or)(wrong$DataType(), invalid$DataSchema());
          function wrong$DataType() {
            if (schemaType.length) {
              if (!(schemaCode instanceof codegen_1.Name))
                throw new Error("ajv implementation error");
              const st = Array.isArray(schemaType) ? schemaType : [schemaType];
              return (0, codegen_1._)`${(0, dataType_2.checkDataTypes)(st, schemaCode, it.opts.strictNumbers, dataType_2.DataType.Wrong)}`;
            }
            return codegen_1.nil;
          }
          function invalid$DataSchema() {
            if (def.validateSchema) {
              const validateSchemaRef = gen.scopeValue("validate$data", { ref: def.validateSchema });
              return (0, codegen_1._)`!${validateSchemaRef}(${schemaCode})`;
            }
            return codegen_1.nil;
          }
        }
        subschema(appl, valid) {
          const subschema = (0, subschema_1.getSubschema)(this.it, appl);
          (0, subschema_1.extendSubschemaData)(subschema, this.it, appl);
          (0, subschema_1.extendSubschemaMode)(subschema, appl);
          const nextContext = { ...this.it, ...subschema, items: void 0, props: void 0 };
          subschemaCode(nextContext, valid);
          return nextContext;
        }
        mergeEvaluated(schemaCxt, toName) {
          const { it, gen } = this;
          if (!it.opts.unevaluated)
            return;
          if (it.props !== true && schemaCxt.props !== void 0) {
            it.props = util_1.mergeEvaluated.props(gen, schemaCxt.props, it.props, toName);
          }
          if (it.items !== true && schemaCxt.items !== void 0) {
            it.items = util_1.mergeEvaluated.items(gen, schemaCxt.items, it.items, toName);
          }
        }
        mergeValidEvaluated(schemaCxt, valid) {
          const { it, gen } = this;
          if (it.opts.unevaluated && (it.props !== true || it.items !== true)) {
            gen.if(valid, () => this.mergeEvaluated(schemaCxt, codegen_1.Name));
            return true;
          }
        }
      };
      exports.KeywordCxt = KeywordCxt;
      function keywordCode(it, keyword, def, ruleType) {
        const cxt = new KeywordCxt(it, def, keyword);
        if ("code" in def) {
          def.code(cxt, ruleType);
        } else if (cxt.$data && def.validate) {
          (0, keyword_1.funcKeywordCode)(cxt, def);
        } else if ("macro" in def) {
          (0, keyword_1.macroKeywordCode)(cxt, def);
        } else if (def.compile || def.validate) {
          (0, keyword_1.funcKeywordCode)(cxt, def);
        }
      }
      var JSON_POINTER = /^\/(?:[^~]|~0|~1)*$/;
      var RELATIVE_JSON_POINTER = /^([0-9]+)(#|\/(?:[^~]|~0|~1)*)?$/;
      function getData($data, { dataLevel, dataNames, dataPathArr }) {
        let jsonPointer;
        let data;
        if ($data === "")
          return names_1.default.rootData;
        if ($data[0] === "/") {
          if (!JSON_POINTER.test($data))
            throw new Error(`Invalid JSON-pointer: ${$data}`);
          jsonPointer = $data;
          data = names_1.default.rootData;
        } else {
          const matches = RELATIVE_JSON_POINTER.exec($data);
          if (!matches)
            throw new Error(`Invalid JSON-pointer: ${$data}`);
          const up = +matches[1];
          jsonPointer = matches[2];
          if (jsonPointer === "#") {
            if (up >= dataLevel)
              throw new Error(errorMsg("property/index", up));
            return dataPathArr[dataLevel - up];
          }
          if (up > dataLevel)
            throw new Error(errorMsg("data", up));
          data = dataNames[dataLevel - up];
          if (!jsonPointer)
            return data;
        }
        let expr = data;
        const segments = jsonPointer.split("/");
        for (const segment of segments) {
          if (segment) {
            data = (0, codegen_1._)`${data}${(0, codegen_1.getProperty)((0, util_1.unescapeJsonPointer)(segment))}`;
            expr = (0, codegen_1._)`${expr} && ${data}`;
          }
        }
        return expr;
        function errorMsg(pointerType, up) {
          return `Cannot access ${pointerType} ${up} levels up, current level is ${dataLevel}`;
        }
      }
      exports.getData = getData;
    }
  });

  // node_modules/ajv/dist/runtime/validation_error.js
  var require_validation_error = __commonJS({
    "node_modules/ajv/dist/runtime/validation_error.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var ValidationError = class extends Error {
        constructor(errors2) {
          super("validation failed");
          this.errors = errors2;
          this.ajv = this.validation = true;
        }
      };
      exports.default = ValidationError;
    }
  });

  // node_modules/ajv/dist/compile/ref_error.js
  var require_ref_error = __commonJS({
    "node_modules/ajv/dist/compile/ref_error.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var resolve_1 = require_resolve();
      var MissingRefError = class extends Error {
        constructor(resolver, baseId, ref, msg) {
          super(msg || `can't resolve reference ${ref} from id ${baseId}`);
          this.missingRef = (0, resolve_1.resolveUrl)(resolver, baseId, ref);
          this.missingSchema = (0, resolve_1.normalizeId)((0, resolve_1.getFullPath)(resolver, this.missingRef));
        }
      };
      exports.default = MissingRefError;
    }
  });

  // node_modules/ajv/dist/compile/index.js
  var require_compile = __commonJS({
    "node_modules/ajv/dist/compile/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.resolveSchema = exports.getCompilingSchema = exports.resolveRef = exports.compileSchema = exports.SchemaEnv = void 0;
      var codegen_1 = require_codegen();
      var validation_error_1 = require_validation_error();
      var names_1 = require_names();
      var resolve_1 = require_resolve();
      var util_1 = require_util();
      var validate_1 = require_validate();
      var SchemaEnv = class {
        constructor(env) {
          var _a;
          this.refs = {};
          this.dynamicAnchors = {};
          let schema;
          if (typeof env.schema == "object")
            schema = env.schema;
          this.schema = env.schema;
          this.schemaId = env.schemaId;
          this.root = env.root || this;
          this.baseId = (_a = env.baseId) !== null && _a !== void 0 ? _a : (0, resolve_1.normalizeId)(schema === null || schema === void 0 ? void 0 : schema[env.schemaId || "$id"]);
          this.schemaPath = env.schemaPath;
          this.localRefs = env.localRefs;
          this.meta = env.meta;
          this.$async = schema === null || schema === void 0 ? void 0 : schema.$async;
          this.refs = {};
        }
      };
      exports.SchemaEnv = SchemaEnv;
      function compileSchema(sch) {
        const _sch = getCompilingSchema.call(this, sch);
        if (_sch)
          return _sch;
        const rootId = (0, resolve_1.getFullPath)(this.opts.uriResolver, sch.root.baseId);
        const { es5, lines } = this.opts.code;
        const { ownProperties } = this.opts;
        const gen = new codegen_1.CodeGen(this.scope, { es5, lines, ownProperties });
        let _ValidationError;
        if (sch.$async) {
          _ValidationError = gen.scopeValue("Error", {
            ref: validation_error_1.default,
            code: (0, codegen_1._)`require("ajv/dist/runtime/validation_error").default`
          });
        }
        const validateName = gen.scopeName("validate");
        sch.validateName = validateName;
        const schemaCxt = {
          gen,
          allErrors: this.opts.allErrors,
          data: names_1.default.data,
          parentData: names_1.default.parentData,
          parentDataProperty: names_1.default.parentDataProperty,
          dataNames: [names_1.default.data],
          dataPathArr: [codegen_1.nil],
          // TODO can its length be used as dataLevel if nil is removed?
          dataLevel: 0,
          dataTypes: [],
          definedProperties: /* @__PURE__ */ new Set(),
          topSchemaRef: gen.scopeValue("schema", this.opts.code.source === true ? { ref: sch.schema, code: (0, codegen_1.stringify)(sch.schema) } : { ref: sch.schema }),
          validateName,
          ValidationError: _ValidationError,
          schema: sch.schema,
          schemaEnv: sch,
          rootId,
          baseId: sch.baseId || rootId,
          schemaPath: codegen_1.nil,
          errSchemaPath: sch.schemaPath || (this.opts.jtd ? "" : "#"),
          errorPath: (0, codegen_1._)`""`,
          opts: this.opts,
          self: this
        };
        let sourceCode;
        try {
          this._compilations.add(sch);
          (0, validate_1.validateFunctionCode)(schemaCxt);
          gen.optimize(this.opts.code.optimize);
          const validateCode = gen.toString();
          sourceCode = `${gen.scopeRefs(names_1.default.scope)}return ${validateCode}`;
          if (this.opts.code.process)
            sourceCode = this.opts.code.process(sourceCode, sch);
          const makeValidate = new Function(`${names_1.default.self}`, `${names_1.default.scope}`, sourceCode);
          const validate = makeValidate(this, this.scope.get());
          this.scope.value(validateName, { ref: validate });
          validate.errors = null;
          validate.schema = sch.schema;
          validate.schemaEnv = sch;
          if (sch.$async)
            validate.$async = true;
          if (this.opts.code.source === true) {
            validate.source = { validateName, validateCode, scopeValues: gen._values };
          }
          if (this.opts.unevaluated) {
            const { props, items } = schemaCxt;
            validate.evaluated = {
              props: props instanceof codegen_1.Name ? void 0 : props,
              items: items instanceof codegen_1.Name ? void 0 : items,
              dynamicProps: props instanceof codegen_1.Name,
              dynamicItems: items instanceof codegen_1.Name
            };
            if (validate.source)
              validate.source.evaluated = (0, codegen_1.stringify)(validate.evaluated);
          }
          sch.validate = validate;
          return sch;
        } catch (e) {
          delete sch.validate;
          delete sch.validateName;
          if (sourceCode)
            this.logger.error("Error compiling schema, function code:", sourceCode);
          throw e;
        } finally {
          this._compilations.delete(sch);
        }
      }
      exports.compileSchema = compileSchema;
      function resolveRef(root2, baseId, ref) {
        var _a;
        ref = (0, resolve_1.resolveUrl)(this.opts.uriResolver, baseId, ref);
        const schOrFunc = root2.refs[ref];
        if (schOrFunc)
          return schOrFunc;
        let _sch = resolve.call(this, root2, ref);
        if (_sch === void 0) {
          const schema = (_a = root2.localRefs) === null || _a === void 0 ? void 0 : _a[ref];
          const { schemaId } = this.opts;
          if (schema)
            _sch = new SchemaEnv({ schema, schemaId, root: root2, baseId });
        }
        if (_sch === void 0)
          return;
        return root2.refs[ref] = inlineOrCompile.call(this, _sch);
      }
      exports.resolveRef = resolveRef;
      function inlineOrCompile(sch) {
        if ((0, resolve_1.inlineRef)(sch.schema, this.opts.inlineRefs))
          return sch.schema;
        return sch.validate ? sch : compileSchema.call(this, sch);
      }
      function getCompilingSchema(schEnv) {
        for (const sch of this._compilations) {
          if (sameSchemaEnv(sch, schEnv))
            return sch;
        }
      }
      exports.getCompilingSchema = getCompilingSchema;
      function sameSchemaEnv(s1, s2) {
        return s1.schema === s2.schema && s1.root === s2.root && s1.baseId === s2.baseId;
      }
      function resolve(root2, ref) {
        let sch;
        while (typeof (sch = this.refs[ref]) == "string")
          ref = sch;
        return sch || this.schemas[ref] || resolveSchema.call(this, root2, ref);
      }
      function resolveSchema(root2, ref) {
        const p = this.opts.uriResolver.parse(ref);
        const refPath = (0, resolve_1._getFullPath)(this.opts.uriResolver, p);
        let baseId = (0, resolve_1.getFullPath)(this.opts.uriResolver, root2.baseId, void 0);
        if (Object.keys(root2.schema).length > 0 && refPath === baseId) {
          return getJsonPointer.call(this, p, root2);
        }
        const id = (0, resolve_1.normalizeId)(refPath);
        const schOrRef = this.refs[id] || this.schemas[id];
        if (typeof schOrRef == "string") {
          const sch = resolveSchema.call(this, root2, schOrRef);
          if (typeof (sch === null || sch === void 0 ? void 0 : sch.schema) !== "object")
            return;
          return getJsonPointer.call(this, p, sch);
        }
        if (typeof (schOrRef === null || schOrRef === void 0 ? void 0 : schOrRef.schema) !== "object")
          return;
        if (!schOrRef.validate)
          compileSchema.call(this, schOrRef);
        if (id === (0, resolve_1.normalizeId)(ref)) {
          const { schema } = schOrRef;
          const { schemaId } = this.opts;
          const schId = schema[schemaId];
          if (schId)
            baseId = (0, resolve_1.resolveUrl)(this.opts.uriResolver, baseId, schId);
          return new SchemaEnv({ schema, schemaId, root: root2, baseId });
        }
        return getJsonPointer.call(this, p, schOrRef);
      }
      exports.resolveSchema = resolveSchema;
      var PREVENT_SCOPE_CHANGE = /* @__PURE__ */ new Set([
        "properties",
        "patternProperties",
        "enum",
        "dependencies",
        "definitions"
      ]);
      function getJsonPointer(parsedRef, { baseId, schema, root: root2 }) {
        var _a;
        if (((_a = parsedRef.fragment) === null || _a === void 0 ? void 0 : _a[0]) !== "/")
          return;
        for (const part of parsedRef.fragment.slice(1).split("/")) {
          if (typeof schema === "boolean")
            return;
          const partSchema = schema[(0, util_1.unescapeFragment)(part)];
          if (partSchema === void 0)
            return;
          schema = partSchema;
          const schId = typeof schema === "object" && schema[this.opts.schemaId];
          if (!PREVENT_SCOPE_CHANGE.has(part) && schId) {
            baseId = (0, resolve_1.resolveUrl)(this.opts.uriResolver, baseId, schId);
          }
        }
        let env;
        if (typeof schema != "boolean" && schema.$ref && !(0, util_1.schemaHasRulesButRef)(schema, this.RULES)) {
          const $ref = (0, resolve_1.resolveUrl)(this.opts.uriResolver, baseId, schema.$ref);
          env = resolveSchema.call(this, root2, $ref);
        }
        const { schemaId } = this.opts;
        env = env || new SchemaEnv({ schema, schemaId, root: root2, baseId });
        if (env.schema !== env.root.schema)
          return env;
        return void 0;
      }
    }
  });

  // node_modules/ajv/dist/refs/data.json
  var require_data = __commonJS({
    "node_modules/ajv/dist/refs/data.json"(exports, module) {
      module.exports = {
        $id: "https://raw.githubusercontent.com/ajv-validator/ajv/master/lib/refs/data.json#",
        description: "Meta-schema for $data reference (JSON AnySchema extension proposal)",
        type: "object",
        required: ["$data"],
        properties: {
          $data: {
            type: "string",
            anyOf: [{ format: "relative-json-pointer" }, { format: "json-pointer" }]
          }
        },
        additionalProperties: false
      };
    }
  });

  // node_modules/fast-uri/lib/utils.js
  var require_utils = __commonJS({
    "node_modules/fast-uri/lib/utils.js"(exports, module) {
      "use strict";
      var isUUID = RegExp.prototype.test.bind(/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/iu);
      var isIPv4 = RegExp.prototype.test.bind(/^(?:(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]\d|\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]\d|\d)$/u);
      var isPort = RegExp.prototype.test.bind(/^\d*$/u);
      var isHexPair = RegExp.prototype.test.bind(/^[\da-f]{2}$/iu);
      var isUnreserved = RegExp.prototype.test.bind(/^[\da-z\-._~]$/iu);
      var isPathCharacter = RegExp.prototype.test.bind(/^[A-Za-z0-9\-._~!$&'()*+,;=:@/]$/u);
      var isQueryFragmentCharacter = RegExp.prototype.test.bind(/^[A-Za-z0-9\-._~!$&'()*+,;=:@/?]$/u);
      var isUserinfoCharacter = RegExp.prototype.test.bind(/^[A-Za-z0-9\-._~!$&'()*+,;=:]$/u);
      var BYTE_HEX = new Array(256);
      {
        const HEX_DIGITS = "0123456789ABCDEF";
        for (let i = 0; i < 256; i++) {
          BYTE_HEX[i] = "%" + HEX_DIGITS[i >> 4] + HEX_DIGITS[i & 15];
        }
      }
      function percentEncodeNonAscii(cp) {
        if (cp < 2048) {
          return BYTE_HEX[192 | cp >> 6] + BYTE_HEX[128 | cp & 63];
        }
        if (cp < 65536) {
          return BYTE_HEX[224 | cp >> 12] + BYTE_HEX[128 | cp >> 6 & 63] + BYTE_HEX[128 | cp & 63];
        }
        return BYTE_HEX[240 | cp >> 18] + BYTE_HEX[128 | cp >> 12 & 63] + BYTE_HEX[128 | cp >> 6 & 63] + BYTE_HEX[128 | cp & 63];
      }
      function stringArrayToHexStripped(input) {
        let acc = "";
        let code = 0;
        let i = 0;
        for (i = 0; i < input.length; i++) {
          code = input[i].charCodeAt(0);
          if (code === 48) {
            continue;
          }
          if (!(code >= 48 && code <= 57 || code >= 65 && code <= 70 || code >= 97 && code <= 102)) {
            return "";
          }
          acc += input[i];
          break;
        }
        for (i += 1; i < input.length; i++) {
          code = input[i].charCodeAt(0);
          if (!(code >= 48 && code <= 57 || code >= 65 && code <= 70 || code >= 97 && code <= 102)) {
            return "";
          }
          acc += input[i];
        }
        return acc;
      }
      var isHextet = RegExp.prototype.test.bind(/^[\dA-Fa-f]{1,4}$/);
      var isIPvFuture = RegExp.prototype.test.bind(/^[vV][\dA-Fa-f]+\.[A-Za-z\d\-._~!$&'()*+,;=:]+$/);
      var isZoneCharacter = RegExp.prototype.test.bind(/^[A-Za-z\d\-._~]$/);
      var nonSimpleDomain = RegExp.prototype.test.bind(/[^!"$&'()*+,\-.;=_`a-z{}~]/u);
      function isZoneIdentifier(zone) {
        if (zone.length === 0) return false;
        for (let i = 0; i < zone.length; i++) {
          if (isZoneCharacter(zone[i])) continue;
          if (zone[i] === "%" && i + 2 < zone.length && isHexPair(zone.slice(i + 1, i + 3))) {
            i += 2;
            continue;
          }
          return false;
        }
        return true;
      }
      function compressIPv6ZeroRun(hextets) {
        let bestStart = -1;
        let bestLength = 0;
        let runStart = -1;
        let runLength = 0;
        for (let i = 0; i < hextets.length; i++) {
          if (hextets[i] === "0") {
            if (runStart === -1) runStart = i;
            runLength++;
            if (runLength > bestLength) {
              bestLength = runLength;
              bestStart = runStart;
            }
          } else {
            runStart = -1;
            runLength = 0;
          }
        }
        if (bestLength < 2) return hextets.join(":");
        const head = hextets.slice(0, bestStart).join(":");
        const tail = hextets.slice(bestStart + bestLength).join(":");
        return head + "::" + tail;
      }
      function normalizeIPv6Address(input) {
        const compression = input.indexOf("::");
        if (compression !== -1 && input.indexOf("::", compression + 1) !== -1) return void 0;
        const left = compression === -1 ? input.split(":") : input.slice(0, compression).split(":");
        const right = compression === -1 ? [] : input.slice(compression + 2).split(":");
        if (compression !== -1) {
          if (left.length === 1 && left[0] === "") left.length = 0;
          if (right.length === 1 && right[0] === "") right.length = 0;
        }
        const parts = left.concat(right);
        let hextetCount = 0;
        for (let i = 0; i < parts.length; i++) {
          const part = parts[i];
          if (part === "") return void 0;
          if (part.indexOf(".") !== -1) {
            if (i !== parts.length - 1 || compression !== -1 && right.length === 0 || !isIPv4(part)) return void 0;
            hextetCount += 2;
            continue;
          }
          if (!isHextet(part)) return void 0;
          parts[i] = parseInt(part, 16).toString(16);
          hextetCount++;
        }
        if (compression === -1) {
          if (hextetCount !== 8) return void 0;
          return compressIPv6ZeroRun(parts);
        }
        if (hextetCount >= 8) return void 0;
        const expanded = parts.slice(0, left.length);
        for (let i = hextetCount; i < 8; i++) expanded.push("0");
        for (let i = left.length; i < parts.length; i++) expanded.push(parts[i]);
        return compressIPv6ZeroRun(expanded);
      }
      function normalizeIPv6(host2) {
        const bracketed = host2[0] === "[" && host2[host2.length - 1] === "]";
        const hasBracket = host2[0] === "[" || host2[host2.length - 1] === "]";
        if (hasBracket && !bracketed) return { host: host2, isIPV6: false, error: true };
        let input = bracketed ? host2.slice(1, -1) : host2;
        if (bracketed && isIPvFuture(input)) {
          input = input.toLowerCase();
          return { host: `[${input}]`, escapedHost: input, isIPV6: false, isIPVFuture: true };
        }
        if (findToken(input, ":") < 2) {
          return { host: host2, isIPV6: false, error: bracketed };
        }
        let zoneIdentifier = "";
        const zoneSeparator = input.indexOf("%");
        if (zoneSeparator !== -1) {
          const separatorLength = input.slice(zoneSeparator, zoneSeparator + 3).toLowerCase() === "%25" ? 3 : 1;
          zoneIdentifier = input.slice(zoneSeparator + separatorLength);
          if (!isZoneIdentifier(zoneIdentifier)) return { host: host2, isIPV6: false, error: true };
          input = input.slice(0, zoneSeparator);
        }
        const address = normalizeIPv6Address(input);
        if (address === void 0) return { host: host2, isIPV6: false, error: true };
        return {
          host: address + (zoneIdentifier ? "%" + zoneIdentifier : ""),
          escapedHost: address + (zoneIdentifier ? "%25" + zoneIdentifier : ""),
          isIPV6: true
        };
      }
      function findToken(str, token) {
        let ind = 0;
        for (let i = 0; i < str.length; i++) {
          if (str[i] === token) ind++;
        }
        return ind;
      }
      function removeDotSegments(path) {
        let input = path;
        const output = [];
        let nextSlash = -1;
        let len = 0;
        while (len = input.length) {
          if (len === 1) {
            if (input === ".") {
              break;
            } else if (input === "/") {
              output.push("/");
              break;
            } else {
              output.push(input);
              break;
            }
          } else if (len === 2) {
            if (input[0] === ".") {
              if (input[1] === ".") {
                break;
              } else if (input[1] === "/") {
                input = input.slice(2);
                continue;
              }
            } else if (input[0] === "/") {
              if (input[1] === "." || input[1] === "/") {
                output.push("/");
                break;
              }
            }
          } else if (len === 3) {
            if (input === "/..") {
              if (output.length !== 0) {
                output.pop();
              }
              output.push("/");
              break;
            }
          }
          if (input[0] === ".") {
            if (input[1] === ".") {
              if (input[2] === "/") {
                input = input.slice(3);
                continue;
              }
            } else if (input[1] === "/") {
              input = input.slice(2);
              continue;
            }
          } else if (input[0] === "/") {
            if (input[1] === ".") {
              if (input[2] === "/") {
                input = input.slice(2);
                continue;
              } else if (input[2] === ".") {
                if (input[3] === "/") {
                  input = input.slice(3);
                  if (output.length !== 0) {
                    output.pop();
                  }
                  continue;
                }
              }
            }
          }
          if ((nextSlash = input.indexOf("/", 1)) === -1) {
            output.push(input);
            break;
          } else {
            output.push(input.slice(0, nextSlash));
            input = input.slice(nextSlash);
          }
        }
        return output.join("");
      }
      var HOST_DELIMS = { "@": "%40", "/": "%2F", "?": "%3F", "#": "%23", ":": "%3A" };
      var HOST_DELIM_RE = /[@/?#:]/g;
      var HOST_DELIM_NO_COLON_RE = /[@/?#]/g;
      function reescapeHostDelimiters(host2, isIP) {
        const re = isIP ? HOST_DELIM_NO_COLON_RE : HOST_DELIM_RE;
        re.lastIndex = 0;
        return host2.replace(re, (ch) => HOST_DELIMS[ch]);
      }
      function normalizePercentEncoding(input, decodeUnreserved = false) {
        if (input.indexOf("%") === -1) {
          return input;
        }
        let output = "";
        for (let i = 0; i < input.length; i++) {
          if (input[i] === "%" && i + 2 < input.length) {
            const hex = input.slice(i + 1, i + 3);
            if (isHexPair(hex)) {
              const normalizedHex = hex.toUpperCase();
              const decoded = String.fromCharCode(parseInt(normalizedHex, 16));
              if (decodeUnreserved && isUnreserved(decoded)) {
                output += decoded;
              } else {
                output += "%" + normalizedHex;
              }
              i += 2;
              continue;
            }
          }
          output += input[i];
        }
        return output;
      }
      function normalizePathEncoding(input) {
        let output = "";
        for (let i = 0; i < input.length; i++) {
          const ch = input[i];
          if (ch === "%" && i + 2 < input.length) {
            const hex = input.slice(i + 1, i + 3);
            if (isHexPair(hex)) {
              const normalizedHex = hex.toUpperCase();
              const decoded = String.fromCharCode(parseInt(normalizedHex, 16));
              if (decoded !== "." && isUnreserved(decoded)) {
                output += decoded;
              } else {
                output += "%" + normalizedHex;
              }
              i += 2;
              continue;
            }
          }
          if (isPathCharacter(ch)) {
            output += ch;
          } else {
            const code = input.charCodeAt(i);
            if (code < 128) {
              output += isEscapeSafe(code) ? ch : BYTE_HEX[code];
            } else if (code < 55296 || code > 57343) {
              output += percentEncodeNonAscii(code);
            } else if (code <= 56319 && i + 1 < input.length) {
              const low = input.charCodeAt(i + 1);
              if (low >= 56320 && low <= 57343) {
                output += percentEncodeNonAscii(65536 + (code - 55296 << 10) + (low - 56320));
                i++;
              } else {
                output += percentEncodeNonAscii(65533);
              }
            } else {
              output += percentEncodeNonAscii(65533);
            }
          }
        }
        return output;
      }
      function serializePathEncoding(input, pathNoScheme = false) {
        let output = "";
        let firstSegment = pathNoScheme && input[0] !== "/";
        for (let i = 0; i < input.length; i++) {
          const ch = input[i];
          if (ch === "%" && i + 2 < input.length) {
            const hex = input.slice(i + 1, i + 3);
            if (isHexPair(hex)) {
              output += "%" + hex.toUpperCase();
              i += 2;
              continue;
            }
          }
          if (ch === "/") {
            firstSegment = false;
          }
          if (isPathCharacter(ch) && (ch !== ":" || !firstSegment)) {
            output += ch;
          } else {
            const code = input.charCodeAt(i);
            if (code < 128) {
              output += BYTE_HEX[code];
            } else if (code < 55296 || code > 57343) {
              output += percentEncodeNonAscii(code);
            } else if (code <= 56319 && i + 1 < input.length) {
              const low = input.charCodeAt(i + 1);
              if (low >= 56320 && low <= 57343) {
                output += percentEncodeNonAscii(65536 + (code - 55296 << 10) + (low - 56320));
                i++;
              } else {
                output += percentEncodeNonAscii(65533);
              }
            } else {
              output += percentEncodeNonAscii(65533);
            }
          }
        }
        return output;
      }
      function encodeComponent(input, isAllowed) {
        let output = "";
        for (let i = 0; i < input.length; i++) {
          const ch = input[i];
          if (ch === "%" && i + 2 < input.length) {
            const hex = input.slice(i + 1, i + 3);
            if (isHexPair(hex)) {
              output += "%" + hex.toUpperCase();
              i += 2;
              continue;
            }
          }
          if (isAllowed(ch)) {
            output += ch;
          } else {
            const code = input.charCodeAt(i);
            if (code < 128) {
              output += BYTE_HEX[code];
            } else if (code < 55296 || code > 57343) {
              output += percentEncodeNonAscii(code);
            } else if (code <= 56319 && i + 1 < input.length) {
              const low = input.charCodeAt(i + 1);
              if (low >= 56320 && low <= 57343) {
                output += percentEncodeNonAscii(65536 + (code - 55296 << 10) + (low - 56320));
                i++;
              } else {
                output += percentEncodeNonAscii(65533);
              }
            } else {
              output += percentEncodeNonAscii(65533);
            }
          }
        }
        return output;
      }
      function encodeUserinfo(input) {
        return encodeComponent(input, isUserinfoCharacter);
      }
      function encodeQuery(input) {
        return encodeComponent(input, isQueryFragmentCharacter);
      }
      function encodeFragment(input) {
        return encodeComponent(input, isQueryFragmentCharacter);
      }
      function isEscapeSafe(cp) {
        return cp >= 48 && cp <= 57 || cp >= 65 && cp <= 90 || cp >= 97 && cp <= 122 || cp === 42 || cp === 43 || cp === 45 || cp === 46 || cp === 47 || cp === 64 || cp === 95;
      }
      function normalizeQueryFragmentEncoding(input) {
        let output = "";
        for (let i = 0; i < input.length; i++) {
          const ch = input[i];
          if (ch === "%" && i + 2 < input.length) {
            const hex = input.slice(i + 1, i + 3);
            if (isHexPair(hex)) {
              const normalizedHex = hex.toUpperCase();
              const decoded = String.fromCharCode(parseInt(normalizedHex, 16));
              if (isUnreserved(decoded)) {
                output += decoded;
              } else {
                output += "%" + normalizedHex;
              }
              i += 2;
              continue;
            }
          }
          if (isQueryFragmentCharacter(ch)) {
            output += ch;
          } else {
            const code = input.charCodeAt(i);
            if (code < 128) {
              output += isEscapeSafe(code) ? ch : BYTE_HEX[code];
            } else if (code < 55296 || code > 57343) {
              output += percentEncodeNonAscii(code);
            } else if (code <= 56319 && i + 1 < input.length) {
              const low = input.charCodeAt(i + 1);
              if (low >= 56320 && low <= 57343) {
                output += percentEncodeNonAscii(65536 + (code - 55296 << 10) + (low - 56320));
                i++;
              } else {
                output += percentEncodeNonAscii(65533);
              }
            } else {
              output += percentEncodeNonAscii(65533);
            }
          }
        }
        return output;
      }
      function escapePreservingEscapes(input) {
        let output = "";
        for (let i = 0; i < input.length; i++) {
          if (input[i] === "%" && i + 2 < input.length) {
            const hex = input.slice(i + 1, i + 3);
            if (isHexPair(hex)) {
              output += "%" + hex.toUpperCase();
              i += 2;
              continue;
            }
          }
          output += escape(input[i]);
        }
        return output;
      }
      function recomposeAuthority(component) {
        const uriTokens = [];
        if (component.userinfo !== void 0) {
          uriTokens.push(encodeUserinfo(component.userinfo));
          uriTokens.push("@");
        }
        if (component.host !== void 0) {
          let host2 = component.host;
          if (!isIPv4(host2)) {
            let ipV6res = normalizeIPv6(host2);
            if (ipV6res.isIPV6 !== true && ipV6res.isIPVFuture !== true) {
              host2 = normalizePercentEncoding(host2, true);
              ipV6res = normalizeIPv6(host2);
            }
            if (ipV6res.isIPV6 === true || ipV6res.isIPVFuture === true) {
              host2 = `[${ipV6res.escapedHost}]`;
            } else {
              host2 = reescapeHostDelimiters(host2, false);
            }
          }
          uriTokens.push(host2);
        }
        if (typeof component.port === "number" || typeof component.port === "string") {
          const port = String(component.port);
          if (!isPort(port)) {
            throw new TypeError("URI port is malformed.");
          }
          uriTokens.push(":");
          uriTokens.push(port);
        }
        return uriTokens.length ? uriTokens.join("") : void 0;
      }
      module.exports = {
        nonSimpleDomain,
        recomposeAuthority,
        reescapeHostDelimiters,
        normalizePercentEncoding,
        normalizePathEncoding,
        serializePathEncoding,
        normalizeQueryFragmentEncoding,
        encodeUserinfo,
        encodeQuery,
        encodeFragment,
        escapePreservingEscapes,
        removeDotSegments,
        isIPv4,
        isUUID,
        normalizeIPv6,
        stringArrayToHexStripped
      };
    }
  });

  // node_modules/fast-uri/lib/schemes.js
  var require_schemes = __commonJS({
    "node_modules/fast-uri/lib/schemes.js"(exports, module) {
      "use strict";
      var { isUUID } = require_utils();
      var URN_REG = /^([\da-z][\d\-a-z]{0,31}):((?:[\w!$'()*+,\-./:;=@]|%[\da-f]{2})+)$/iu;
      var supportedSchemeNames = (
        /** @type {const} */
        [
          "http",
          "https",
          "ws",
          "wss",
          "urn",
          "urn:uuid"
        ]
      );
      function isValidSchemeName(name) {
        return supportedSchemeNames.indexOf(
          /** @type {*} */
          name
        ) !== -1;
      }
      function wsIsSecure(wsComponent) {
        if (wsComponent.secure === true) {
          return true;
        } else if (wsComponent.secure === false) {
          return false;
        } else if (wsComponent.scheme) {
          return wsComponent.scheme.length === 3 && (wsComponent.scheme[0] === "w" || wsComponent.scheme[0] === "W") && (wsComponent.scheme[1] === "s" || wsComponent.scheme[1] === "S") && (wsComponent.scheme[2] === "s" || wsComponent.scheme[2] === "S");
        } else {
          return false;
        }
      }
      function httpParse(component) {
        if (!component.host) {
          component.error = component.error || "HTTP URIs must have a host.";
        }
        return component;
      }
      function httpSerialize(component) {
        const secure = String(component.scheme).toLowerCase() === "https";
        if (component.port === (secure ? 443 : 80) || component.port === "") {
          component.port = void 0;
        }
        if (!component.path) {
          component.path = "/";
        }
        return component;
      }
      function wsParse(wsComponent) {
        wsComponent.secure = wsIsSecure(wsComponent);
        wsComponent.resourceName = (wsComponent.path || "/") + (wsComponent.query ? "?" + wsComponent.query : "");
        wsComponent.path = void 0;
        wsComponent.query = void 0;
        return wsComponent;
      }
      function wsSerialize(wsComponent) {
        if (wsComponent.port === (wsIsSecure(wsComponent) ? 443 : 80) || wsComponent.port === "") {
          wsComponent.port = void 0;
        }
        if (typeof wsComponent.secure === "boolean") {
          wsComponent.scheme = wsComponent.secure ? "wss" : "ws";
          wsComponent.secure = void 0;
        }
        if (wsComponent.resourceName) {
          const queryIndex = wsComponent.resourceName.indexOf("?");
          const path = queryIndex === -1 ? wsComponent.resourceName : wsComponent.resourceName.slice(0, queryIndex);
          wsComponent.path = path && path !== "/" ? path : void 0;
          wsComponent.query = queryIndex === -1 ? void 0 : wsComponent.resourceName.slice(queryIndex + 1);
          wsComponent.resourceName = void 0;
        }
        wsComponent.fragment = void 0;
        return wsComponent;
      }
      function urnParse(urnComponent, options) {
        if (!urnComponent.path) {
          urnComponent.error = "URN can not be parsed";
          return urnComponent;
        }
        const matches = urnComponent.path.match(URN_REG);
        if (matches && matches[0] === urnComponent.path) {
          const scheme = options.scheme || urnComponent.scheme || "urn";
          urnComponent.nid = matches[1].toLowerCase();
          urnComponent.nss = matches[2];
          const urnScheme = `${scheme}:${options.nid || urnComponent.nid}`;
          const schemeHandler = getSchemeHandler(urnScheme);
          urnComponent.path = void 0;
          if (schemeHandler) {
            urnComponent = schemeHandler.parse(urnComponent, options);
          }
        } else {
          urnComponent.error = urnComponent.error || "URN can not be parsed.";
        }
        return urnComponent;
      }
      function urnSerialize(urnComponent, options) {
        if (urnComponent.nid === void 0) {
          throw new Error("URN without nid cannot be serialized");
        }
        const scheme = options.scheme || urnComponent.scheme || "urn";
        const nid = urnComponent.nid.toLowerCase();
        const urnScheme = `${scheme}:${options.nid || nid}`;
        const schemeHandler = getSchemeHandler(urnScheme);
        if (schemeHandler) {
          urnComponent = schemeHandler.serialize(urnComponent, options);
        }
        const uriComponent = urnComponent;
        const nss = urnComponent.nss;
        uriComponent.path = `${nid || options.nid}:${nss}`;
        options.skipEscape = true;
        return uriComponent;
      }
      function urnuuidParse(urnComponent, options) {
        const uuidComponent = urnComponent;
        uuidComponent.uuid = uuidComponent.nss;
        uuidComponent.nss = void 0;
        if (!options.tolerant && (!uuidComponent.uuid || !isUUID(uuidComponent.uuid))) {
          uuidComponent.error = uuidComponent.error || "UUID is not valid.";
        }
        return uuidComponent;
      }
      function urnuuidSerialize(uuidComponent) {
        const urnComponent = uuidComponent;
        urnComponent.nss = (uuidComponent.uuid || "").toLowerCase();
        return urnComponent;
      }
      var http = (
        /** @type {SchemeHandler} */
        {
          scheme: "http",
          domainHost: true,
          parse: httpParse,
          serialize: httpSerialize
        }
      );
      var https = (
        /** @type {SchemeHandler} */
        {
          scheme: "https",
          domainHost: http.domainHost,
          parse: httpParse,
          serialize: httpSerialize
        }
      );
      var ws = (
        /** @type {SchemeHandler} */
        {
          scheme: "ws",
          domainHost: true,
          parse: wsParse,
          serialize: wsSerialize
        }
      );
      var wss = (
        /** @type {SchemeHandler} */
        {
          scheme: "wss",
          domainHost: ws.domainHost,
          parse: ws.parse,
          serialize: ws.serialize
        }
      );
      var urn = (
        /** @type {SchemeHandler} */
        {
          scheme: "urn",
          parse: urnParse,
          serialize: urnSerialize,
          skipNormalize: true
        }
      );
      var urnuuid = (
        /** @type {SchemeHandler} */
        {
          scheme: "urn:uuid",
          parse: urnuuidParse,
          serialize: urnuuidSerialize,
          skipNormalize: true
        }
      );
      var SCHEMES = (
        /** @type {Record<SchemeName, SchemeHandler>} */
        {
          http,
          https,
          ws,
          wss,
          urn,
          "urn:uuid": urnuuid
        }
      );
      Object.setPrototypeOf(SCHEMES, null);
      function getSchemeHandler(scheme) {
        return scheme && (SCHEMES[
          /** @type {SchemeName} */
          scheme
        ] || SCHEMES[
          /** @type {SchemeName} */
          scheme.toLowerCase()
        ]) || void 0;
      }
      module.exports = {
        wsIsSecure,
        SCHEMES,
        isValidSchemeName,
        getSchemeHandler
      };
    }
  });

  // node_modules/fast-uri/index.js
  var require_fast_uri = __commonJS({
    "node_modules/fast-uri/index.js"(exports, module) {
      "use strict";
      var { normalizeIPv6, removeDotSegments, recomposeAuthority, normalizePercentEncoding, normalizePathEncoding, serializePathEncoding, normalizeQueryFragmentEncoding, encodeQuery, encodeFragment, reescapeHostDelimiters, isIPv4, nonSimpleDomain } = require_utils();
      var { SCHEMES, getSchemeHandler } = require_schemes();
      var VALID_SCHEME = /^[A-Za-z][A-Za-z0-9+.-]*$/u;
      var MALFORMED_SCHEME_ERROR = "URI scheme is malformed.";
      function decodeValidScheme(scheme) {
        const decodedScheme = unescape(String(scheme));
        if (!VALID_SCHEME.test(decodedScheme)) {
          throw new TypeError(MALFORMED_SCHEME_ERROR);
        }
        return decodedScheme;
      }
      function normalize(uri, options) {
        if (typeof uri === "string") {
          uri = /** @type {T} */
          normalizeString(uri, options);
        } else if (typeof uri === "object") {
          uri = /** @type {T} */
          parse(serialize(uri, options), options);
        }
        return uri;
      }
      function resolve(baseURI, relativeURI, options) {
        const schemelessOptions = options ? Object.assign({ scheme: "null" }, options) : { scheme: "null" };
        const {
          parsed: baseParsed,
          malformedAuthorityOrPort: baseMalformed,
          malformedPercentEncoding: baseMalformedPercentEncoding,
          malformedSchemeSpecific: baseMalformedSchemeSpecific,
          malformedHost: baseMalformedHost,
          malformedScheme: baseMalformedScheme
        } = parseWithStatus(baseURI, schemelessOptions);
        const {
          parsed: relativeParsed,
          malformedAuthorityOrPort: relativeMalformed,
          malformedPercentEncoding: relativeMalformedPercentEncoding,
          malformedSchemeSpecific: relativeMalformedSchemeSpecific,
          malformedHost: relativeMalformedHost,
          malformedScheme: relativeMalformedScheme
        } = parseWithStatus(relativeURI, schemelessOptions);
        if (baseMalformed || relativeMalformed || baseMalformedPercentEncoding || relativeMalformedPercentEncoding || baseMalformedSchemeSpecific || relativeMalformedSchemeSpecific || baseMalformedHost || relativeMalformedHost || baseMalformedScheme || relativeMalformedScheme) {
          throw new Error(baseParsed.error || relativeParsed.error || "URI is malformed.");
        }
        const resolved = resolveComponent(baseParsed, relativeParsed, schemelessOptions, true);
        const resolvedSchemeHandler = getSchemeHandler(options && options.scheme || resolved.scheme);
        const resolvedHost = resolved.host;
        const resolvedHostIsIP = resolvedHost !== void 0 && resolvedHost !== "" && (isIPv4(resolvedHost) || normalizeIPv6(resolvedHost).isIPV6);
        canonicalizeHost(resolved, options || {}, resolvedSchemeHandler, resolvedHostIsIP);
        const encodedASCIIHost = resolvedHost && resolvedHost.indexOf("%") !== -1 && !/\P{ASCII}/u.test(resolvedHost);
        if (resolved.error && !encodedASCIIHost) {
          throw new Error(resolved.error);
        }
        schemelessOptions.skipEscape = true;
        return serialize(resolved, schemelessOptions);
      }
      function resolveComponent(base, relative, options, skipNormalization) {
        const target2 = {};
        if (!skipNormalization) {
          base = parse(serialize(base, options), options);
          relative = parse(serialize(relative, options), options);
        }
        options = options || {};
        if (!options.tolerant && relative.scheme) {
          target2.scheme = relative.scheme;
          target2.userinfo = relative.userinfo;
          target2.host = relative.host;
          target2.port = relative.port;
          target2.path = removeDotSegments(relative.path || "");
          target2.query = relative.query;
        } else {
          if (relative.userinfo !== void 0 || relative.host !== void 0 || relative.port !== void 0) {
            target2.userinfo = relative.userinfo;
            target2.host = relative.host;
            target2.port = relative.port;
            target2.path = removeDotSegments(relative.path || "");
            target2.query = relative.query;
          } else {
            if (!relative.path) {
              target2.path = base.path;
              if (relative.query !== void 0) {
                target2.query = relative.query;
              } else {
                target2.query = base.query;
              }
            } else {
              if (relative.path[0] === "/") {
                target2.path = removeDotSegments(relative.path);
              } else {
                if ((base.userinfo !== void 0 || base.host !== void 0 || base.port !== void 0) && !base.path) {
                  target2.path = "/" + relative.path;
                } else if (!base.path) {
                  target2.path = relative.path;
                } else {
                  target2.path = base.path.slice(0, base.path.lastIndexOf("/") + 1) + relative.path;
                }
                target2.path = removeDotSegments(target2.path);
              }
              target2.query = relative.query;
            }
            target2.userinfo = base.userinfo;
            target2.host = base.host;
            target2.port = base.port;
          }
          target2.scheme = base.scheme;
        }
        target2.fragment = relative.fragment;
        return target2;
      }
      function equal(uriA, uriB, options) {
        const normalizedA = normalizeComparableURI(uriA, options);
        const normalizedB = normalizeComparableURI(uriB, options);
        return normalizedA !== void 0 && normalizedB !== void 0 && normalizedA === normalizedB;
      }
      function serialize(cmpts, opts) {
        const component = {
          host: cmpts.host,
          scheme: cmpts.scheme,
          userinfo: cmpts.userinfo,
          port: cmpts.port,
          path: cmpts.path,
          query: cmpts.query,
          nid: cmpts.nid,
          nss: cmpts.nss,
          uuid: cmpts.uuid,
          fragment: cmpts.fragment,
          reference: cmpts.reference,
          resourceName: cmpts.resourceName,
          secure: cmpts.secure,
          error: ""
        };
        const options = Object.assign({}, opts);
        const uriTokens = [];
        if (component.scheme) {
          component.scheme = decodeValidScheme(component.scheme);
        }
        const schemeHandler = getSchemeHandler(options.scheme || component.scheme);
        if (schemeHandler && schemeHandler.serialize) schemeHandler.serialize(component, options);
        const hasAuthority = component.userinfo !== void 0 || component.host !== void 0 || component.port !== void 0;
        const pathNoScheme = !options.skipEscape && component.scheme === void 0 && !hasAuthority;
        if (component.path !== void 0) {
          if (!options.skipEscape) {
            component.path = serializePathEncoding(component.path, pathNoScheme);
          } else {
            component.path = normalizePercentEncoding(component.path);
          }
        }
        if (options.reference !== "suffix" && component.scheme) {
          component.scheme = decodeValidScheme(component.scheme);
          uriTokens.push(component.scheme, ":");
        }
        const authority = recomposeAuthority(component);
        if (authority !== void 0) {
          if (options.reference !== "suffix") {
            uriTokens.push("//");
          }
          uriTokens.push(authority);
          if (component.path && component.path[0] !== "/") {
            uriTokens.push("/");
          }
        }
        if (component.path !== void 0) {
          let s = component.path;
          if (!options.absolutePath && (!schemeHandler || !schemeHandler.absolutePath)) {
            s = removeDotSegments(s);
          }
          if (pathNoScheme) {
            s = serializePathEncoding(s, true);
          }
          if (authority === void 0 && s[0] === "/" && s[1] === "/") {
            s = "/%2F" + s.slice(2);
          }
          uriTokens.push(s);
        }
        if (component.query !== void 0) {
          uriTokens.push("?", encodeQuery(component.query));
        }
        if (component.fragment !== void 0) {
          uriTokens.push("#", encodeFragment(component.fragment));
        }
        return uriTokens.join("");
      }
      var URI_PARSE = /^(?:([^#/:?]+):)?(?:\/\/((?:([^#/?@]*)@)?(\[[^#/?\]]+\]|[^#/:?]*)(?::(\d*))?))?([^#?]*)(?:\?([^#]*))?(?:#((?:.|[\n\r])*))?/u;
      var AUTHORITY_PREFIX = /^(?:[^#/:?]+:)?\/\/([^/?#]*)/;
      var AUTHORITY_INTRODUCER_REGION = /^(?:[^#/:?]+:)?([/\\\t\n\r]*)/;
      function getParseError(parsed, matches) {
        if (matches[2] !== void 0 && parsed.path && parsed.path[0] !== "/") {
          return 'URI path must start with "/" when authority is present.';
        }
        if (typeof parsed.port === "number" && (parsed.port < 0 || parsed.port > 65535)) {
          return "URI port is malformed.";
        }
        return void 0;
      }
      function hasMalformedPercentEncoding(component) {
        if (component === void 0) return false;
        let percent = component.indexOf("%");
        while (percent !== -1) {
          if (percent + 2 >= component.length || !/^[\da-f]{2}$/iu.test(component.slice(percent + 1, percent + 3))) {
            return true;
          }
          percent = component.indexOf("%", percent + 3);
        }
        return false;
      }
      function isIPLiteral(host2) {
        return host2[0] === "[" && host2[host2.length - 1] === "]";
      }
      function hasMalformedComponentPercentEncoding(matches) {
        const host2 = matches[4];
        return hasMalformedPercentEncoding(matches[3]) || host2 !== void 0 && !isIPLiteral(host2) && hasMalformedPercentEncoding(host2) || hasMalformedPercentEncoding(matches[6]) || hasMalformedPercentEncoding(matches[7]) || hasMalformedPercentEncoding(matches[8]);
      }
      function canonicalizeHost(parsed, options, schemeHandler, isIP) {
        if (!options.unicodeSupport && (!schemeHandler || !schemeHandler.unicodeSupport) && parsed.host && !isIPLiteral(parsed.host) && (options.domainHost || schemeHandler && schemeHandler.domainHost) && isIP === false && nonSimpleDomain(parsed.host)) {
          try {
            parsed.host = new URL("http://" + parsed.host).hostname;
          } catch (e) {
            parsed.error = parsed.error || "Host's domain name can not be converted to ASCII: " + e;
            return true;
          }
        }
        return false;
      }
      function parseWithStatus(uri, opts) {
        const options = Object.assign({}, opts);
        const parsed = {
          scheme: void 0,
          userinfo: void 0,
          host: "",
          port: void 0,
          path: "",
          query: void 0,
          fragment: void 0
        };
        let malformedAuthorityOrPort = false;
        let malformedPercentEncoding = false;
        let malformedSchemeSpecific = false;
        let malformedHost = false;
        let malformedIPLiteral = false;
        let malformedScheme = false;
        let isIP = false;
        if (options.reference === "suffix") {
          if (options.scheme) {
            uri = options.scheme + ":" + uri;
          } else {
            uri = "//" + uri;
          }
        }
        const authorityMatch = uri.match(AUTHORITY_PREFIX);
        if (authorityMatch !== null && authorityMatch[1].indexOf("\\") !== -1) {
          parsed.error = "URI authority must not contain a literal backslash.";
          malformedAuthorityOrPort = true;
        }
        const introducerMatch = uri.match(AUTHORITY_INTRODUCER_REGION);
        if (introducerMatch !== null) {
          const region = introducerMatch[1];
          const normalizedRegion = region.replace(/[\t\n\r]/g, "");
          if (normalizedRegion.length >= 2) {
            if (normalizedRegion.slice(0, 2) !== "//") {
              parsed.error = parsed.error || "URI authority must not contain a literal backslash.";
              malformedAuthorityOrPort = true;
            } else if (region.length !== normalizedRegion.length) {
              parsed.error = parsed.error || "URI authority introducer must not contain whitespace.";
              malformedAuthorityOrPort = true;
            }
          }
        }
        const matches = uri.match(URI_PARSE);
        if (matches) {
          parsed.scheme = matches[1];
          parsed.userinfo = matches[3];
          parsed.host = matches[4];
          parsed.port = parseInt(matches[5], 10);
          parsed.path = matches[6] || "";
          parsed.query = matches[7];
          parsed.fragment = matches[8];
          if (parsed.scheme !== void 0) {
            const decodedScheme = unescape(parsed.scheme);
            if (VALID_SCHEME.test(decodedScheme)) {
              parsed.scheme = decodedScheme.toLowerCase();
            } else {
              parsed.error = parsed.error || MALFORMED_SCHEME_ERROR;
              malformedScheme = true;
            }
          }
          malformedPercentEncoding = hasMalformedComponentPercentEncoding(matches);
          if (malformedPercentEncoding) {
            parsed.error = parsed.error || "URI contains malformed percent-encoding.";
          }
          if (isNaN(parsed.port)) {
            parsed.port = matches[5];
          }
          const parseError = getParseError(parsed, matches);
          if (parseError !== void 0) {
            parsed.error = parsed.error || parseError;
            malformedAuthorityOrPort = true;
          }
          if (parsed.host) {
            const ipv4result = isIPv4(parsed.host);
            if (ipv4result === false) {
              const bracketedIPLiteral = isIPLiteral(parsed.host);
              const hasIPLiteralBracket = parsed.host.indexOf("[") !== -1 || parsed.host.indexOf("]") !== -1;
              const ipv6result = normalizeIPv6(parsed.host);
              isIP = ipv6result.isIPV6 || ipv6result.isIPVFuture === true;
              malformedIPLiteral = hasIPLiteralBracket && (!bracketedIPLiteral || ipv6result.error === true);
              parsed.host = isIP ? ipv6result.host : ipv6result.host.toLowerCase();
              if (malformedIPLiteral) {
                parsed.error = parsed.error || "URI host is malformed.";
                malformedAuthorityOrPort = true;
              }
            } else {
              isIP = true;
            }
          }
          if (parsed.scheme === void 0 && parsed.userinfo === void 0 && parsed.host === void 0 && parsed.port === void 0 && parsed.query === void 0 && !parsed.path) {
            parsed.reference = "same-document";
          } else if (parsed.scheme === void 0) {
            parsed.reference = "relative";
          } else if (parsed.fragment === void 0) {
            parsed.reference = "absolute";
          } else {
            parsed.reference = "uri";
          }
          if (options.reference && options.reference !== "suffix" && options.reference !== parsed.reference) {
            parsed.error = parsed.error || "URI is not a " + options.reference + " reference.";
          }
          const schemeHandler = getSchemeHandler(options.scheme || parsed.scheme);
          if (!malformedIPLiteral) {
            malformedHost = canonicalizeHost(parsed, options, schemeHandler, isIP);
          }
          if (uri.indexOf("%") !== -1 && parsed.host !== void 0 && !malformedIPLiteral) {
            let host2 = isIP ? parsed.host : normalizePercentEncoding(parsed.host, true);
            if (!isIP) {
              host2 = normalizePercentEncoding(host2.toLowerCase());
            }
            parsed.host = reescapeHostDelimiters(host2, isIP);
          }
          if (!schemeHandler || schemeHandler && !schemeHandler.skipNormalize) {
            if (parsed.path) {
              parsed.path = normalizePathEncoding(parsed.path);
            }
            if (parsed.query) {
              parsed.query = normalizeQueryFragmentEncoding(parsed.query);
            }
            if (parsed.fragment) {
              parsed.fragment = normalizeQueryFragmentEncoding(parsed.fragment);
            }
          }
          if (schemeHandler && schemeHandler.parse) {
            schemeHandler.parse(parsed, options);
            if (schemeHandler === SCHEMES.urn && parsed.nid === void 0) {
              malformedSchemeSpecific = true;
            }
          }
        } else {
          parsed.error = parsed.error || "URI can not be parsed.";
        }
        return { parsed, malformedAuthorityOrPort, malformedPercentEncoding, malformedSchemeSpecific, malformedHost, malformedScheme };
      }
      function parse(uri, opts) {
        return parseWithStatus(uri, opts).parsed;
      }
      function normalizeString(uri, opts) {
        return normalizeStringWithStatus(uri, opts).normalized;
      }
      function normalizeStringWithStatus(uri, opts) {
        const { parsed, malformedAuthorityOrPort, malformedPercentEncoding, malformedSchemeSpecific, malformedHost, malformedScheme } = parseWithStatus(uri, opts);
        return {
          normalized: malformedAuthorityOrPort || malformedPercentEncoding || malformedSchemeSpecific || malformedHost || malformedScheme ? uri : serialize(parsed, opts),
          malformedAuthorityOrPort,
          malformedPercentEncoding,
          malformedSchemeSpecific,
          malformedHost,
          malformedScheme
        };
      }
      function normalizeComparableURI(uri, opts) {
        if (typeof uri !== "string" && typeof uri !== "object") {
          return void 0;
        }
        let value;
        try {
          value = typeof uri === "string" ? uri : serialize(uri, opts);
        } catch {
          return void 0;
        }
        const { normalized, malformedAuthorityOrPort, malformedPercentEncoding, malformedSchemeSpecific, malformedHost, malformedScheme } = normalizeStringWithStatus(value, opts);
        return malformedAuthorityOrPort || malformedPercentEncoding || malformedSchemeSpecific || malformedHost || malformedScheme ? void 0 : normalized;
      }
      var fastUri = {
        SCHEMES,
        normalize,
        resolve,
        resolveComponent,
        equal,
        serialize,
        parse
      };
      module.exports = fastUri;
      module.exports.default = fastUri;
      module.exports.fastUri = fastUri;
    }
  });

  // node_modules/ajv/dist/runtime/uri.js
  var require_uri = __commonJS({
    "node_modules/ajv/dist/runtime/uri.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var uri = require_fast_uri();
      uri.code = 'require("ajv/dist/runtime/uri").default';
      exports.default = uri;
    }
  });

  // node_modules/ajv/dist/core.js
  var require_core = __commonJS({
    "node_modules/ajv/dist/core.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.CodeGen = exports.Name = exports.nil = exports.stringify = exports.str = exports._ = exports.KeywordCxt = void 0;
      var validate_1 = require_validate();
      Object.defineProperty(exports, "KeywordCxt", { enumerable: true, get: function() {
        return validate_1.KeywordCxt;
      } });
      var codegen_1 = require_codegen();
      Object.defineProperty(exports, "_", { enumerable: true, get: function() {
        return codegen_1._;
      } });
      Object.defineProperty(exports, "str", { enumerable: true, get: function() {
        return codegen_1.str;
      } });
      Object.defineProperty(exports, "stringify", { enumerable: true, get: function() {
        return codegen_1.stringify;
      } });
      Object.defineProperty(exports, "nil", { enumerable: true, get: function() {
        return codegen_1.nil;
      } });
      Object.defineProperty(exports, "Name", { enumerable: true, get: function() {
        return codegen_1.Name;
      } });
      Object.defineProperty(exports, "CodeGen", { enumerable: true, get: function() {
        return codegen_1.CodeGen;
      } });
      var validation_error_1 = require_validation_error();
      var ref_error_1 = require_ref_error();
      var rules_1 = require_rules();
      var compile_1 = require_compile();
      var codegen_2 = require_codegen();
      var resolve_1 = require_resolve();
      var dataType_1 = require_dataType();
      var util_1 = require_util();
      var $dataRefSchema = require_data();
      var uri_1 = require_uri();
      var defaultRegExp = (str, flags) => new RegExp(str, flags);
      defaultRegExp.code = "new RegExp";
      var META_IGNORE_OPTIONS = ["removeAdditional", "useDefaults", "coerceTypes"];
      var EXT_SCOPE_NAMES = /* @__PURE__ */ new Set([
        "validate",
        "serialize",
        "parse",
        "wrapper",
        "root",
        "schema",
        "keyword",
        "pattern",
        "formats",
        "validate$data",
        "func",
        "obj",
        "Error"
      ]);
      var removedOptions = {
        errorDataPath: "",
        format: "`validateFormats: false` can be used instead.",
        nullable: '"nullable" keyword is supported by default.',
        jsonPointers: "Deprecated jsPropertySyntax can be used instead.",
        extendRefs: "Deprecated ignoreKeywordsWithRef can be used instead.",
        missingRefs: "Pass empty schema with $id that should be ignored to ajv.addSchema.",
        processCode: "Use option `code: {process: (code, schemaEnv: object) => string}`",
        sourceCode: "Use option `code: {source: true}`",
        strictDefaults: "It is default now, see option `strict`.",
        strictKeywords: "It is default now, see option `strict`.",
        uniqueItems: '"uniqueItems" keyword is always validated.',
        unknownFormats: "Disable strict mode or pass `true` to `ajv.addFormat` (or `formats` option).",
        cache: "Map is used as cache, schema object as key.",
        serialize: "Map is used as cache, schema object as key.",
        ajvErrors: "It is default now."
      };
      var deprecatedOptions = {
        ignoreKeywordsWithRef: "",
        jsPropertySyntax: "",
        unicode: '"minLength"/"maxLength" account for unicode characters by default.'
      };
      var MAX_EXPRESSION = 200;
      function requiredOptions(o) {
        var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q, _r, _s, _t, _u, _v, _w, _x, _y, _z, _0;
        const s = o.strict;
        const _optz = (_a = o.code) === null || _a === void 0 ? void 0 : _a.optimize;
        const optimize = _optz === true || _optz === void 0 ? 1 : _optz || 0;
        const regExp = (_c = (_b = o.code) === null || _b === void 0 ? void 0 : _b.regExp) !== null && _c !== void 0 ? _c : defaultRegExp;
        const uriResolver = (_d = o.uriResolver) !== null && _d !== void 0 ? _d : uri_1.default;
        return {
          strictSchema: (_f = (_e = o.strictSchema) !== null && _e !== void 0 ? _e : s) !== null && _f !== void 0 ? _f : true,
          strictNumbers: (_h = (_g = o.strictNumbers) !== null && _g !== void 0 ? _g : s) !== null && _h !== void 0 ? _h : true,
          strictTypes: (_k = (_j = o.strictTypes) !== null && _j !== void 0 ? _j : s) !== null && _k !== void 0 ? _k : "log",
          strictTuples: (_m = (_l = o.strictTuples) !== null && _l !== void 0 ? _l : s) !== null && _m !== void 0 ? _m : "log",
          strictRequired: (_p = (_o = o.strictRequired) !== null && _o !== void 0 ? _o : s) !== null && _p !== void 0 ? _p : false,
          code: o.code ? { ...o.code, optimize, regExp } : { optimize, regExp },
          loopRequired: (_q = o.loopRequired) !== null && _q !== void 0 ? _q : MAX_EXPRESSION,
          loopEnum: (_r = o.loopEnum) !== null && _r !== void 0 ? _r : MAX_EXPRESSION,
          meta: (_s = o.meta) !== null && _s !== void 0 ? _s : true,
          messages: (_t = o.messages) !== null && _t !== void 0 ? _t : true,
          inlineRefs: (_u = o.inlineRefs) !== null && _u !== void 0 ? _u : true,
          schemaId: (_v = o.schemaId) !== null && _v !== void 0 ? _v : "$id",
          addUsedSchema: (_w = o.addUsedSchema) !== null && _w !== void 0 ? _w : true,
          validateSchema: (_x = o.validateSchema) !== null && _x !== void 0 ? _x : true,
          validateFormats: (_y = o.validateFormats) !== null && _y !== void 0 ? _y : true,
          unicodeRegExp: (_z = o.unicodeRegExp) !== null && _z !== void 0 ? _z : true,
          int32range: (_0 = o.int32range) !== null && _0 !== void 0 ? _0 : true,
          uriResolver
        };
      }
      var Ajv2 = class {
        constructor(opts = {}) {
          this.schemas = {};
          this.refs = {};
          this.formats = /* @__PURE__ */ Object.create(null);
          this._compilations = /* @__PURE__ */ new Set();
          this._loading = {};
          this._cache = /* @__PURE__ */ new Map();
          opts = this.opts = { ...opts, ...requiredOptions(opts) };
          const { es5, lines } = this.opts.code;
          this.scope = new codegen_2.ValueScope({ scope: {}, prefixes: EXT_SCOPE_NAMES, es5, lines });
          this.logger = getLogger(opts.logger);
          const formatOpt = opts.validateFormats;
          opts.validateFormats = false;
          this.RULES = (0, rules_1.getRules)();
          checkOptions.call(this, removedOptions, opts, "NOT SUPPORTED");
          checkOptions.call(this, deprecatedOptions, opts, "DEPRECATED", "warn");
          this._metaOpts = getMetaSchemaOptions.call(this);
          if (opts.formats)
            addInitialFormats.call(this);
          this._addVocabularies();
          this._addDefaultMetaSchema();
          if (opts.keywords)
            addInitialKeywords.call(this, opts.keywords);
          if (typeof opts.meta == "object")
            this.addMetaSchema(opts.meta);
          addInitialSchemas.call(this);
          opts.validateFormats = formatOpt;
        }
        _addVocabularies() {
          this.addKeyword("$async");
        }
        _addDefaultMetaSchema() {
          const { $data, meta, schemaId } = this.opts;
          let _dataRefSchema = $dataRefSchema;
          if (schemaId === "id") {
            _dataRefSchema = { ...$dataRefSchema };
            _dataRefSchema.id = _dataRefSchema.$id;
            delete _dataRefSchema.$id;
          }
          if (meta && $data)
            this.addMetaSchema(_dataRefSchema, _dataRefSchema[schemaId], false);
        }
        defaultMeta() {
          const { meta, schemaId } = this.opts;
          return this.opts.defaultMeta = typeof meta == "object" ? meta[schemaId] || meta : void 0;
        }
        validate(schemaKeyRef, data) {
          let v;
          if (typeof schemaKeyRef == "string") {
            v = this.getSchema(schemaKeyRef);
            if (!v)
              throw new Error(`no schema with key or ref "${schemaKeyRef}"`);
          } else {
            v = this.compile(schemaKeyRef);
          }
          const valid = v(data);
          if (!("$async" in v))
            this.errors = v.errors;
          return valid;
        }
        compile(schema, _meta) {
          const sch = this._addSchema(schema, _meta);
          return sch.validate || this._compileSchemaEnv(sch);
        }
        compileAsync(schema, meta) {
          if (typeof this.opts.loadSchema != "function") {
            throw new Error("options.loadSchema should be a function");
          }
          const { loadSchema } = this.opts;
          return runCompileAsync.call(this, schema, meta);
          async function runCompileAsync(_schema, _meta) {
            await loadMetaSchema.call(this, _schema.$schema);
            const sch = this._addSchema(_schema, _meta);
            return sch.validate || _compileAsync.call(this, sch);
          }
          async function loadMetaSchema($ref) {
            if ($ref && !this.getSchema($ref)) {
              await runCompileAsync.call(this, { $ref }, true);
            }
          }
          async function _compileAsync(sch) {
            try {
              return this._compileSchemaEnv(sch);
            } catch (e) {
              if (!(e instanceof ref_error_1.default))
                throw e;
              checkLoaded.call(this, e);
              await loadMissingSchema.call(this, e.missingSchema);
              return _compileAsync.call(this, sch);
            }
          }
          function checkLoaded({ missingSchema: ref, missingRef }) {
            if (this.refs[ref]) {
              throw new Error(`AnySchema ${ref} is loaded but ${missingRef} cannot be resolved`);
            }
          }
          async function loadMissingSchema(ref) {
            const _schema = await _loadSchema.call(this, ref);
            if (!this.refs[ref])
              await loadMetaSchema.call(this, _schema.$schema);
            if (!this.refs[ref])
              this.addSchema(_schema, ref, meta);
          }
          async function _loadSchema(ref) {
            const p = this._loading[ref];
            if (p)
              return p;
            try {
              return await (this._loading[ref] = loadSchema(ref));
            } finally {
              delete this._loading[ref];
            }
          }
        }
        // Adds schema to the instance
        addSchema(schema, key, _meta, _validateSchema = this.opts.validateSchema) {
          if (Array.isArray(schema)) {
            for (const sch of schema)
              this.addSchema(sch, void 0, _meta, _validateSchema);
            return this;
          }
          let id;
          if (typeof schema === "object") {
            const { schemaId } = this.opts;
            id = schema[schemaId];
            if (id !== void 0 && typeof id != "string") {
              throw new Error(`schema ${schemaId} must be string`);
            }
          }
          key = (0, resolve_1.normalizeId)(key || id);
          this._checkUnique(key);
          this.schemas[key] = this._addSchema(schema, _meta, key, _validateSchema, true);
          return this;
        }
        // Add schema that will be used to validate other schemas
        // options in META_IGNORE_OPTIONS are alway set to false
        addMetaSchema(schema, key, _validateSchema = this.opts.validateSchema) {
          this.addSchema(schema, key, true, _validateSchema);
          return this;
        }
        //  Validate schema against its meta-schema
        validateSchema(schema, throwOrLogError) {
          if (typeof schema == "boolean")
            return true;
          let $schema;
          $schema = schema.$schema;
          if ($schema !== void 0 && typeof $schema != "string") {
            throw new Error("$schema must be a string");
          }
          $schema = $schema || this.opts.defaultMeta || this.defaultMeta();
          if (!$schema) {
            this.logger.warn("meta-schema not available");
            this.errors = null;
            return true;
          }
          const valid = this.validate($schema, schema);
          if (!valid && throwOrLogError) {
            const message = "schema is invalid: " + this.errorsText();
            if (this.opts.validateSchema === "log")
              this.logger.error(message);
            else
              throw new Error(message);
          }
          return valid;
        }
        // Get compiled schema by `key` or `ref`.
        // (`key` that was passed to `addSchema` or full schema reference - `schema.$id` or resolved id)
        getSchema(keyRef) {
          let sch;
          while (typeof (sch = getSchEnv.call(this, keyRef)) == "string")
            keyRef = sch;
          if (sch === void 0) {
            const { schemaId } = this.opts;
            const root2 = new compile_1.SchemaEnv({ schema: {}, schemaId });
            sch = compile_1.resolveSchema.call(this, root2, keyRef);
            if (!sch)
              return;
            this.refs[keyRef] = sch;
          }
          return sch.validate || this._compileSchemaEnv(sch);
        }
        // Remove cached schema(s).
        // If no parameter is passed all schemas but meta-schemas are removed.
        // If RegExp is passed all schemas with key/id matching pattern but meta-schemas are removed.
        // Even if schema is referenced by other schemas it still can be removed as other schemas have local references.
        removeSchema(schemaKeyRef) {
          if (schemaKeyRef instanceof RegExp) {
            this._removeAllSchemas(this.schemas, schemaKeyRef);
            this._removeAllSchemas(this.refs, schemaKeyRef);
            return this;
          }
          switch (typeof schemaKeyRef) {
            case "undefined":
              this._removeAllSchemas(this.schemas);
              this._removeAllSchemas(this.refs);
              this._cache.clear();
              return this;
            case "string": {
              const sch = getSchEnv.call(this, schemaKeyRef);
              if (typeof sch == "object")
                this._cache.delete(sch.schema);
              delete this.schemas[schemaKeyRef];
              delete this.refs[schemaKeyRef];
              return this;
            }
            case "object": {
              const cacheKey = schemaKeyRef;
              this._cache.delete(cacheKey);
              let id = schemaKeyRef[this.opts.schemaId];
              if (id) {
                id = (0, resolve_1.normalizeId)(id);
                delete this.schemas[id];
                delete this.refs[id];
              }
              return this;
            }
            default:
              throw new Error("ajv.removeSchema: invalid parameter");
          }
        }
        // add "vocabulary" - a collection of keywords
        addVocabulary(definitions) {
          for (const def of definitions)
            this.addKeyword(def);
          return this;
        }
        addKeyword(kwdOrDef, def) {
          let keyword;
          if (typeof kwdOrDef == "string") {
            keyword = kwdOrDef;
            if (typeof def == "object") {
              this.logger.warn("these parameters are deprecated, see docs for addKeyword");
              def.keyword = keyword;
            }
          } else if (typeof kwdOrDef == "object" && def === void 0) {
            def = kwdOrDef;
            keyword = def.keyword;
            if (Array.isArray(keyword) && !keyword.length) {
              throw new Error("addKeywords: keyword must be string or non-empty array");
            }
          } else {
            throw new Error("invalid addKeywords parameters");
          }
          checkKeyword.call(this, keyword, def);
          if (!def) {
            (0, util_1.eachItem)(keyword, (kwd) => addRule.call(this, kwd));
            return this;
          }
          keywordMetaschema.call(this, def);
          const definition = {
            ...def,
            type: (0, dataType_1.getJSONTypes)(def.type),
            schemaType: (0, dataType_1.getJSONTypes)(def.schemaType)
          };
          (0, util_1.eachItem)(keyword, definition.type.length === 0 ? (k) => addRule.call(this, k, definition) : (k) => definition.type.forEach((t) => addRule.call(this, k, definition, t)));
          return this;
        }
        getKeyword(keyword) {
          const rule = this.RULES.all[keyword];
          return typeof rule == "object" ? rule.definition : !!rule;
        }
        // Remove keyword
        removeKeyword(keyword) {
          const { RULES } = this;
          delete RULES.keywords[keyword];
          delete RULES.all[keyword];
          for (const group of RULES.rules) {
            const i = group.rules.findIndex((rule) => rule.keyword === keyword);
            if (i >= 0)
              group.rules.splice(i, 1);
          }
          return this;
        }
        // Add format
        addFormat(name, format) {
          if (typeof format == "string")
            format = new RegExp(format);
          this.formats[name] = format;
          return this;
        }
        errorsText(errors2 = this.errors, { separator = ", ", dataVar = "data" } = {}) {
          if (!errors2 || errors2.length === 0)
            return "No errors";
          return errors2.map((e) => `${dataVar}${e.instancePath} ${e.message}`).reduce((text, msg) => text + separator + msg);
        }
        $dataMetaSchema(metaSchema, keywordsJsonPointers) {
          const rules = this.RULES.all;
          metaSchema = JSON.parse(JSON.stringify(metaSchema));
          for (const jsonPointer of keywordsJsonPointers) {
            const segments = jsonPointer.split("/").slice(1);
            let keywords2 = metaSchema;
            for (const seg of segments)
              keywords2 = keywords2[seg];
            for (const key in rules) {
              const rule = rules[key];
              if (typeof rule != "object")
                continue;
              const { $data } = rule.definition;
              const schema = keywords2[key];
              if ($data && schema)
                keywords2[key] = schemaOrData(schema);
            }
          }
          return metaSchema;
        }
        _removeAllSchemas(schemas, regex) {
          for (const keyRef in schemas) {
            const sch = schemas[keyRef];
            if (!regex || regex.test(keyRef)) {
              if (typeof sch == "string") {
                delete schemas[keyRef];
              } else if (sch && !sch.meta) {
                this._cache.delete(sch.schema);
                delete schemas[keyRef];
              }
            }
          }
        }
        _addSchema(schema, meta, baseId, validateSchema = this.opts.validateSchema, addSchema = this.opts.addUsedSchema) {
          let id;
          const { schemaId } = this.opts;
          if (typeof schema == "object") {
            id = schema[schemaId];
          } else {
            if (this.opts.jtd)
              throw new Error("schema must be object");
            else if (typeof schema != "boolean")
              throw new Error("schema must be object or boolean");
          }
          let sch = this._cache.get(schema);
          if (sch !== void 0)
            return sch;
          baseId = (0, resolve_1.normalizeId)(id || baseId);
          const localRefs = resolve_1.getSchemaRefs.call(this, schema, baseId);
          sch = new compile_1.SchemaEnv({ schema, schemaId, meta, baseId, localRefs });
          this._cache.set(sch.schema, sch);
          if (addSchema && !baseId.startsWith("#")) {
            if (baseId)
              this._checkUnique(baseId);
            this.refs[baseId] = sch;
          }
          if (validateSchema)
            this.validateSchema(schema, true);
          return sch;
        }
        _checkUnique(id) {
          if (this.schemas[id] || this.refs[id]) {
            throw new Error(`schema with key or id "${id}" already exists`);
          }
        }
        _compileSchemaEnv(sch) {
          if (sch.meta)
            this._compileMetaSchema(sch);
          else
            compile_1.compileSchema.call(this, sch);
          if (!sch.validate)
            throw new Error("ajv implementation error");
          return sch.validate;
        }
        _compileMetaSchema(sch) {
          const currentOpts = this.opts;
          this.opts = this._metaOpts;
          try {
            compile_1.compileSchema.call(this, sch);
          } finally {
            this.opts = currentOpts;
          }
        }
      };
      Ajv2.ValidationError = validation_error_1.default;
      Ajv2.MissingRefError = ref_error_1.default;
      exports.default = Ajv2;
      function checkOptions(checkOpts, options, msg, log = "error") {
        for (const key in checkOpts) {
          const opt = key;
          if (opt in options)
            this.logger[log](`${msg}: option ${key}. ${checkOpts[opt]}`);
        }
      }
      function getSchEnv(keyRef) {
        keyRef = (0, resolve_1.normalizeId)(keyRef);
        return this.schemas[keyRef] || this.refs[keyRef];
      }
      function addInitialSchemas() {
        const optsSchemas = this.opts.schemas;
        if (!optsSchemas)
          return;
        if (Array.isArray(optsSchemas))
          this.addSchema(optsSchemas);
        else
          for (const key in optsSchemas)
            this.addSchema(optsSchemas[key], key);
      }
      function addInitialFormats() {
        for (const name in this.opts.formats) {
          const format = this.opts.formats[name];
          if (format)
            this.addFormat(name, format);
        }
      }
      function addInitialKeywords(defs) {
        if (Array.isArray(defs)) {
          this.addVocabulary(defs);
          return;
        }
        this.logger.warn("keywords option as map is deprecated, pass array");
        for (const keyword in defs) {
          const def = defs[keyword];
          if (!def.keyword)
            def.keyword = keyword;
          this.addKeyword(def);
        }
      }
      function getMetaSchemaOptions() {
        const metaOpts = { ...this.opts };
        for (const opt of META_IGNORE_OPTIONS)
          delete metaOpts[opt];
        return metaOpts;
      }
      var noLogs = { log() {
      }, warn() {
      }, error() {
      } };
      function getLogger(logger) {
        if (logger === false)
          return noLogs;
        if (logger === void 0)
          return console;
        if (logger.log && logger.warn && logger.error)
          return logger;
        throw new Error("logger must implement log, warn and error methods");
      }
      var KEYWORD_NAME = /^[a-z_$][a-z0-9_$:-]*$/i;
      function checkKeyword(keyword, def) {
        const { RULES } = this;
        (0, util_1.eachItem)(keyword, (kwd) => {
          if (RULES.keywords[kwd])
            throw new Error(`Keyword ${kwd} is already defined`);
          if (!KEYWORD_NAME.test(kwd))
            throw new Error(`Keyword ${kwd} has invalid name`);
        });
        if (!def)
          return;
        if (def.$data && !("code" in def || "validate" in def)) {
          throw new Error('$data keyword must have "code" or "validate" function');
        }
      }
      function addRule(keyword, definition, dataType) {
        var _a;
        const post = definition === null || definition === void 0 ? void 0 : definition.post;
        if (dataType && post)
          throw new Error('keyword with "post" flag cannot have "type"');
        const { RULES } = this;
        let ruleGroup = post ? RULES.post : RULES.rules.find(({ type: t }) => t === dataType);
        if (!ruleGroup) {
          ruleGroup = { type: dataType, rules: [] };
          RULES.rules.push(ruleGroup);
        }
        RULES.keywords[keyword] = true;
        if (!definition)
          return;
        const rule = {
          keyword,
          definition: {
            ...definition,
            type: (0, dataType_1.getJSONTypes)(definition.type),
            schemaType: (0, dataType_1.getJSONTypes)(definition.schemaType)
          }
        };
        if (definition.before)
          addBeforeRule.call(this, ruleGroup, rule, definition.before);
        else
          ruleGroup.rules.push(rule);
        RULES.all[keyword] = rule;
        (_a = definition.implements) === null || _a === void 0 ? void 0 : _a.forEach((kwd) => this.addKeyword(kwd));
      }
      function addBeforeRule(ruleGroup, rule, before) {
        const i = ruleGroup.rules.findIndex((_rule) => _rule.keyword === before);
        if (i >= 0) {
          ruleGroup.rules.splice(i, 0, rule);
        } else {
          ruleGroup.rules.push(rule);
          this.logger.warn(`rule ${before} is not defined`);
        }
      }
      function keywordMetaschema(def) {
        let { metaSchema } = def;
        if (metaSchema === void 0)
          return;
        if (def.$data && this.opts.$data)
          metaSchema = schemaOrData(metaSchema);
        def.validateSchema = this.compile(metaSchema, true);
      }
      var $dataRef = {
        $ref: "https://raw.githubusercontent.com/ajv-validator/ajv/master/lib/refs/data.json#"
      };
      function schemaOrData(schema) {
        return { anyOf: [schema, $dataRef] };
      }
    }
  });

  // node_modules/ajv/dist/vocabularies/core/id.js
  var require_id = __commonJS({
    "node_modules/ajv/dist/vocabularies/core/id.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var def = {
        keyword: "id",
        code() {
          throw new Error('NOT SUPPORTED: keyword "id", use "$id" for schema ID');
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/core/ref.js
  var require_ref = __commonJS({
    "node_modules/ajv/dist/vocabularies/core/ref.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.callRef = exports.getValidate = void 0;
      var ref_error_1 = require_ref_error();
      var code_1 = require_code2();
      var codegen_1 = require_codegen();
      var names_1 = require_names();
      var compile_1 = require_compile();
      var util_1 = require_util();
      var def = {
        keyword: "$ref",
        schemaType: "string",
        code(cxt) {
          const { gen, schema: $ref, it } = cxt;
          const { baseId, schemaEnv: env, validateName, opts, self } = it;
          const { root: root2 } = env;
          if (($ref === "#" || $ref === "#/") && baseId === root2.baseId)
            return callRootRef();
          const schOrEnv = compile_1.resolveRef.call(self, root2, baseId, $ref);
          if (schOrEnv === void 0)
            throw new ref_error_1.default(it.opts.uriResolver, baseId, $ref);
          if (schOrEnv instanceof compile_1.SchemaEnv)
            return callValidate(schOrEnv);
          return inlineRefSchema(schOrEnv);
          function callRootRef() {
            if (env === root2)
              return callRef(cxt, validateName, env, env.$async);
            const rootName = gen.scopeValue("root", { ref: root2 });
            return callRef(cxt, (0, codegen_1._)`${rootName}.validate`, root2, root2.$async);
          }
          function callValidate(sch) {
            const v = getValidate(cxt, sch);
            callRef(cxt, v, sch, sch.$async);
          }
          function inlineRefSchema(sch) {
            const schName = gen.scopeValue("schema", opts.code.source === true ? { ref: sch, code: (0, codegen_1.stringify)(sch) } : { ref: sch });
            const valid = gen.name("valid");
            const schCxt = cxt.subschema({
              schema: sch,
              dataTypes: [],
              schemaPath: codegen_1.nil,
              topSchemaRef: schName,
              errSchemaPath: $ref
            }, valid);
            cxt.mergeEvaluated(schCxt);
            cxt.ok(valid);
          }
        }
      };
      function getValidate(cxt, sch) {
        const { gen } = cxt;
        return sch.validate ? gen.scopeValue("validate", { ref: sch.validate }) : (0, codegen_1._)`${gen.scopeValue("wrapper", { ref: sch })}.validate`;
      }
      exports.getValidate = getValidate;
      function callRef(cxt, v, sch, $async) {
        const { gen, it } = cxt;
        const { allErrors, schemaEnv: env, opts } = it;
        const passCxt = opts.passContext ? names_1.default.this : codegen_1.nil;
        if ($async)
          callAsyncRef();
        else
          callSyncRef();
        function callAsyncRef() {
          if (!env.$async)
            throw new Error("async schema referenced by sync schema");
          const valid = gen.let("valid");
          gen.try(() => {
            gen.code((0, codegen_1._)`await ${(0, code_1.callValidateCode)(cxt, v, passCxt)}`);
            addEvaluatedFrom(v);
            if (!allErrors)
              gen.assign(valid, true);
          }, (e) => {
            gen.if((0, codegen_1._)`!(${e} instanceof ${it.ValidationError})`, () => gen.throw(e));
            addErrorsFrom(e);
            if (!allErrors)
              gen.assign(valid, false);
          });
          cxt.ok(valid);
        }
        function callSyncRef() {
          cxt.result((0, code_1.callValidateCode)(cxt, v, passCxt), () => addEvaluatedFrom(v), () => addErrorsFrom(v));
        }
        function addErrorsFrom(source) {
          const errs = (0, codegen_1._)`${source}.errors`;
          gen.assign(names_1.default.vErrors, (0, codegen_1._)`${names_1.default.vErrors} === null ? ${errs} : ${names_1.default.vErrors}.concat(${errs})`);
          gen.assign(names_1.default.errors, (0, codegen_1._)`${names_1.default.vErrors}.length`);
        }
        function addEvaluatedFrom(source) {
          var _a;
          if (!it.opts.unevaluated)
            return;
          const schEvaluated = (_a = sch === null || sch === void 0 ? void 0 : sch.validate) === null || _a === void 0 ? void 0 : _a.evaluated;
          if (it.props !== true) {
            if (schEvaluated && !schEvaluated.dynamicProps) {
              if (schEvaluated.props !== void 0) {
                it.props = util_1.mergeEvaluated.props(gen, schEvaluated.props, it.props);
              }
            } else {
              const props = gen.var("props", (0, codegen_1._)`${source}.evaluated.props`);
              it.props = util_1.mergeEvaluated.props(gen, props, it.props, codegen_1.Name);
            }
          }
          if (it.items !== true) {
            if (schEvaluated && !schEvaluated.dynamicItems) {
              if (schEvaluated.items !== void 0) {
                it.items = util_1.mergeEvaluated.items(gen, schEvaluated.items, it.items);
              }
            } else {
              const items = gen.var("items", (0, codegen_1._)`${source}.evaluated.items`);
              it.items = util_1.mergeEvaluated.items(gen, items, it.items, codegen_1.Name);
            }
          }
        }
      }
      exports.callRef = callRef;
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/core/index.js
  var require_core2 = __commonJS({
    "node_modules/ajv/dist/vocabularies/core/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var id_1 = require_id();
      var ref_1 = require_ref();
      var core = [
        "$schema",
        "$id",
        "$defs",
        "$vocabulary",
        { keyword: "$comment" },
        "definitions",
        id_1.default,
        ref_1.default
      ];
      exports.default = core;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/limitNumber.js
  var require_limitNumber = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/limitNumber.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var ops = codegen_1.operators;
      var KWDs = {
        maximum: { okStr: "<=", ok: ops.LTE, fail: ops.GT },
        minimum: { okStr: ">=", ok: ops.GTE, fail: ops.LT },
        exclusiveMaximum: { okStr: "<", ok: ops.LT, fail: ops.GTE },
        exclusiveMinimum: { okStr: ">", ok: ops.GT, fail: ops.LTE }
      };
      var error = {
        message: ({ keyword, schemaCode }) => (0, codegen_1.str)`must be ${KWDs[keyword].okStr} ${schemaCode}`,
        params: ({ keyword, schemaCode }) => (0, codegen_1._)`{comparison: ${KWDs[keyword].okStr}, limit: ${schemaCode}}`
      };
      var def = {
        keyword: Object.keys(KWDs),
        type: "number",
        schemaType: "number",
        $data: true,
        error,
        code(cxt) {
          const { keyword, data, schemaCode } = cxt;
          cxt.fail$data((0, codegen_1._)`${data} ${KWDs[keyword].fail} ${schemaCode} || isNaN(${data})`);
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/multipleOf.js
  var require_multipleOf = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/multipleOf.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var error = {
        message: ({ schemaCode }) => (0, codegen_1.str)`must be multiple of ${schemaCode}`,
        params: ({ schemaCode }) => (0, codegen_1._)`{multipleOf: ${schemaCode}}`
      };
      var def = {
        keyword: "multipleOf",
        type: "number",
        schemaType: "number",
        $data: true,
        error,
        code(cxt) {
          const { gen, data, schemaCode, it } = cxt;
          const prec = it.opts.multipleOfPrecision;
          const res = gen.let("res");
          const invalid = prec ? (0, codegen_1._)`Math.abs(Math.round(${res}) - ${res}) > 1e-${prec}` : (0, codegen_1._)`${res} !== parseInt(${res})`;
          cxt.fail$data((0, codegen_1._)`(${schemaCode} === 0 || (${res} = ${data}/${schemaCode}, ${invalid}))`);
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/runtime/ucs2length.js
  var require_ucs2length = __commonJS({
    "node_modules/ajv/dist/runtime/ucs2length.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      function ucs2length(str) {
        const len = str.length;
        let length = 0;
        let pos = 0;
        let value;
        while (pos < len) {
          length++;
          value = str.charCodeAt(pos++);
          if (value >= 55296 && value <= 56319 && pos < len) {
            value = str.charCodeAt(pos);
            if ((value & 64512) === 56320)
              pos++;
          }
        }
        return length;
      }
      exports.default = ucs2length;
      ucs2length.code = 'require("ajv/dist/runtime/ucs2length").default';
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/limitLength.js
  var require_limitLength = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/limitLength.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var ucs2length_1 = require_ucs2length();
      var error = {
        message({ keyword, schemaCode }) {
          const comp = keyword === "maxLength" ? "more" : "fewer";
          return (0, codegen_1.str)`must NOT have ${comp} than ${schemaCode} characters`;
        },
        params: ({ schemaCode }) => (0, codegen_1._)`{limit: ${schemaCode}}`
      };
      var def = {
        keyword: ["maxLength", "minLength"],
        type: "string",
        schemaType: "number",
        $data: true,
        error,
        code(cxt) {
          const { keyword, data, schemaCode, it } = cxt;
          const op = keyword === "maxLength" ? codegen_1.operators.GT : codegen_1.operators.LT;
          const len = it.opts.unicode === false ? (0, codegen_1._)`${data}.length` : (0, codegen_1._)`${(0, util_1.useFunc)(cxt.gen, ucs2length_1.default)}(${data})`;
          cxt.fail$data((0, codegen_1._)`${len} ${op} ${schemaCode}`);
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/pattern.js
  var require_pattern = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/pattern.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var code_1 = require_code2();
      var util_1 = require_util();
      var codegen_1 = require_codegen();
      var error = {
        message: ({ schemaCode }) => (0, codegen_1.str)`must match pattern "${schemaCode}"`,
        params: ({ schemaCode }) => (0, codegen_1._)`{pattern: ${schemaCode}}`
      };
      var def = {
        keyword: "pattern",
        type: "string",
        schemaType: "string",
        $data: true,
        error,
        code(cxt) {
          const { gen, data, $data, schema, schemaCode, it } = cxt;
          const u = it.opts.unicodeRegExp ? "u" : "";
          if ($data) {
            const { regExp } = it.opts.code;
            const regExpCode = regExp.code === "new RegExp" ? (0, codegen_1._)`new RegExp` : (0, util_1.useFunc)(gen, regExp);
            const valid = gen.let("valid");
            gen.try(() => gen.assign(valid, (0, codegen_1._)`${regExpCode}(${schemaCode}, ${u}).test(${data})`), () => gen.assign(valid, false));
            cxt.fail$data((0, codegen_1._)`!${valid}`);
          } else {
            const regExp = (0, code_1.usePattern)(cxt, schema);
            cxt.fail$data((0, codegen_1._)`!${regExp}.test(${data})`);
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/limitProperties.js
  var require_limitProperties = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/limitProperties.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var error = {
        message({ keyword, schemaCode }) {
          const comp = keyword === "maxProperties" ? "more" : "fewer";
          return (0, codegen_1.str)`must NOT have ${comp} than ${schemaCode} properties`;
        },
        params: ({ schemaCode }) => (0, codegen_1._)`{limit: ${schemaCode}}`
      };
      var def = {
        keyword: ["maxProperties", "minProperties"],
        type: "object",
        schemaType: "number",
        $data: true,
        error,
        code(cxt) {
          const { keyword, data, schemaCode } = cxt;
          const op = keyword === "maxProperties" ? codegen_1.operators.GT : codegen_1.operators.LT;
          cxt.fail$data((0, codegen_1._)`Object.keys(${data}).length ${op} ${schemaCode}`);
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/required.js
  var require_required = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/required.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var code_1 = require_code2();
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var error = {
        message: ({ params: { missingProperty } }) => (0, codegen_1.str)`must have required property '${missingProperty}'`,
        params: ({ params: { missingProperty } }) => (0, codegen_1._)`{missingProperty: ${missingProperty}}`
      };
      var def = {
        keyword: "required",
        type: "object",
        schemaType: "array",
        $data: true,
        error,
        code(cxt) {
          const { gen, schema, schemaCode, data, $data, it } = cxt;
          const { opts } = it;
          if (!$data && schema.length === 0)
            return;
          const useLoop = schema.length >= opts.loopRequired;
          if (it.allErrors)
            allErrorsMode();
          else
            exitOnErrorMode();
          if (opts.strictRequired) {
            const props = cxt.parentSchema.properties;
            const { definedProperties } = cxt.it;
            for (const requiredKey of schema) {
              if ((props === null || props === void 0 ? void 0 : props[requiredKey]) === void 0 && !definedProperties.has(requiredKey)) {
                const schemaPath = it.schemaEnv.baseId + it.errSchemaPath;
                const msg = `required property "${requiredKey}" is not defined at "${schemaPath}" (strictRequired)`;
                (0, util_1.checkStrictMode)(it, msg, it.opts.strictRequired);
              }
            }
          }
          function allErrorsMode() {
            if (useLoop || $data) {
              cxt.block$data(codegen_1.nil, loopAllRequired);
            } else {
              for (const prop of schema) {
                (0, code_1.checkReportMissingProp)(cxt, prop);
              }
            }
          }
          function exitOnErrorMode() {
            const missing = gen.let("missing");
            if (useLoop || $data) {
              const valid = gen.let("valid", true);
              cxt.block$data(valid, () => loopUntilMissing(missing, valid));
              cxt.ok(valid);
            } else {
              gen.if((0, code_1.checkMissingProp)(cxt, schema, missing));
              (0, code_1.reportMissingProp)(cxt, missing);
              gen.else();
            }
          }
          function loopAllRequired() {
            gen.forOf("prop", schemaCode, (prop) => {
              cxt.setParams({ missingProperty: prop });
              gen.if((0, code_1.noPropertyInData)(gen, data, prop, opts.ownProperties), () => cxt.error());
            });
          }
          function loopUntilMissing(missing, valid) {
            cxt.setParams({ missingProperty: missing });
            gen.forOf(missing, schemaCode, () => {
              gen.assign(valid, (0, code_1.propertyInData)(gen, data, missing, opts.ownProperties));
              gen.if((0, codegen_1.not)(valid), () => {
                cxt.error();
                gen.break();
              });
            }, codegen_1.nil);
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/limitItems.js
  var require_limitItems = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/limitItems.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var error = {
        message({ keyword, schemaCode }) {
          const comp = keyword === "maxItems" ? "more" : "fewer";
          return (0, codegen_1.str)`must NOT have ${comp} than ${schemaCode} items`;
        },
        params: ({ schemaCode }) => (0, codegen_1._)`{limit: ${schemaCode}}`
      };
      var def = {
        keyword: ["maxItems", "minItems"],
        type: "array",
        schemaType: "number",
        $data: true,
        error,
        code(cxt) {
          const { keyword, data, schemaCode } = cxt;
          const op = keyword === "maxItems" ? codegen_1.operators.GT : codegen_1.operators.LT;
          cxt.fail$data((0, codegen_1._)`${data}.length ${op} ${schemaCode}`);
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/runtime/equal.js
  var require_equal = __commonJS({
    "node_modules/ajv/dist/runtime/equal.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var equal = require_fast_deep_equal();
      equal.code = 'require("ajv/dist/runtime/equal").default';
      exports.default = equal;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/uniqueItems.js
  var require_uniqueItems = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/uniqueItems.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var dataType_1 = require_dataType();
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var equal_1 = require_equal();
      var error = {
        message: ({ params: { i, j } }) => (0, codegen_1.str)`must NOT have duplicate items (items ## ${j} and ${i} are identical)`,
        params: ({ params: { i, j } }) => (0, codegen_1._)`{i: ${i}, j: ${j}}`
      };
      var def = {
        keyword: "uniqueItems",
        type: "array",
        schemaType: "boolean",
        $data: true,
        error,
        code(cxt) {
          const { gen, data, $data, schema, parentSchema, schemaCode, it } = cxt;
          if (!$data && !schema)
            return;
          const valid = gen.let("valid");
          const itemTypes = parentSchema.items ? (0, dataType_1.getSchemaTypes)(parentSchema.items) : [];
          cxt.block$data(valid, validateUniqueItems, (0, codegen_1._)`${schemaCode} === false`);
          cxt.ok(valid);
          function validateUniqueItems() {
            const i = gen.let("i", (0, codegen_1._)`${data}.length`);
            const j = gen.let("j");
            cxt.setParams({ i, j });
            gen.assign(valid, true);
            gen.if((0, codegen_1._)`${i} > 1`, () => (canOptimize() ? loopN : loopN2)(i, j));
          }
          function canOptimize() {
            return itemTypes.length > 0 && !itemTypes.some((t) => t === "object" || t === "array");
          }
          function loopN(i, j) {
            const item = gen.name("item");
            const wrongType = (0, dataType_1.checkDataTypes)(itemTypes, item, it.opts.strictNumbers, dataType_1.DataType.Wrong);
            const indices = gen.const("indices", (0, codegen_1._)`{}`);
            gen.for((0, codegen_1._)`;${i}--;`, () => {
              gen.let(item, (0, codegen_1._)`${data}[${i}]`);
              gen.if(wrongType, (0, codegen_1._)`continue`);
              if (itemTypes.length > 1)
                gen.if((0, codegen_1._)`typeof ${item} == "string"`, (0, codegen_1._)`${item} += "_"`);
              gen.if((0, codegen_1._)`typeof ${indices}[${item}] == "number"`, () => {
                gen.assign(j, (0, codegen_1._)`${indices}[${item}]`);
                cxt.error();
                gen.assign(valid, false).break();
              }).code((0, codegen_1._)`${indices}[${item}] = ${i}`);
            });
          }
          function loopN2(i, j) {
            const eql = (0, util_1.useFunc)(gen, equal_1.default);
            const outer = gen.name("outer");
            gen.label(outer).for((0, codegen_1._)`;${i}--;`, () => gen.for((0, codegen_1._)`${j} = ${i}; ${j}--;`, () => gen.if((0, codegen_1._)`${eql}(${data}[${i}], ${data}[${j}])`, () => {
              cxt.error();
              gen.assign(valid, false).break(outer);
            })));
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/const.js
  var require_const = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/const.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var equal_1 = require_equal();
      var error = {
        message: "must be equal to constant",
        params: ({ schemaCode }) => (0, codegen_1._)`{allowedValue: ${schemaCode}}`
      };
      var def = {
        keyword: "const",
        $data: true,
        error,
        code(cxt) {
          const { gen, data, $data, schemaCode, schema } = cxt;
          if ($data || schema && typeof schema == "object") {
            cxt.fail$data((0, codegen_1._)`!${(0, util_1.useFunc)(gen, equal_1.default)}(${data}, ${schemaCode})`);
          } else {
            cxt.fail((0, codegen_1._)`${schema} !== ${data}`);
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/enum.js
  var require_enum = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/enum.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var equal_1 = require_equal();
      var error = {
        message: "must be equal to one of the allowed values",
        params: ({ schemaCode }) => (0, codegen_1._)`{allowedValues: ${schemaCode}}`
      };
      var def = {
        keyword: "enum",
        schemaType: "array",
        $data: true,
        error,
        code(cxt) {
          const { gen, data, $data, schema, schemaCode, it } = cxt;
          if (!$data && schema.length === 0)
            throw new Error("enum must have non-empty array");
          const useLoop = schema.length >= it.opts.loopEnum;
          let eql;
          const getEql = () => eql !== null && eql !== void 0 ? eql : eql = (0, util_1.useFunc)(gen, equal_1.default);
          let valid;
          if (useLoop || $data) {
            valid = gen.let("valid");
            cxt.block$data(valid, loopEnum);
          } else {
            if (!Array.isArray(schema))
              throw new Error("ajv implementation error");
            const vSchema = gen.const("vSchema", schemaCode);
            valid = (0, codegen_1.or)(...schema.map((_x, i) => equalCode(vSchema, i)));
          }
          cxt.pass(valid);
          function loopEnum() {
            gen.assign(valid, false);
            gen.forOf("v", schemaCode, (v) => gen.if((0, codegen_1._)`${getEql()}(${data}, ${v})`, () => gen.assign(valid, true).break()));
          }
          function equalCode(vSchema, i) {
            const sch = schema[i];
            return typeof sch === "object" && sch !== null ? (0, codegen_1._)`${getEql()}(${data}, ${vSchema}[${i}])` : (0, codegen_1._)`${data} === ${sch}`;
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/validation/index.js
  var require_validation = __commonJS({
    "node_modules/ajv/dist/vocabularies/validation/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var limitNumber_1 = require_limitNumber();
      var multipleOf_1 = require_multipleOf();
      var limitLength_1 = require_limitLength();
      var pattern_1 = require_pattern();
      var limitProperties_1 = require_limitProperties();
      var required_1 = require_required();
      var limitItems_1 = require_limitItems();
      var uniqueItems_1 = require_uniqueItems();
      var const_1 = require_const();
      var enum_1 = require_enum();
      var validation = [
        // number
        limitNumber_1.default,
        multipleOf_1.default,
        // string
        limitLength_1.default,
        pattern_1.default,
        // object
        limitProperties_1.default,
        required_1.default,
        // array
        limitItems_1.default,
        uniqueItems_1.default,
        // any
        { keyword: "type", schemaType: ["string", "array"] },
        { keyword: "nullable", schemaType: "boolean" },
        const_1.default,
        enum_1.default
      ];
      exports.default = validation;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/additionalItems.js
  var require_additionalItems = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/additionalItems.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.validateAdditionalItems = void 0;
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var error = {
        message: ({ params: { len } }) => (0, codegen_1.str)`must NOT have more than ${len} items`,
        params: ({ params: { len } }) => (0, codegen_1._)`{limit: ${len}}`
      };
      var def = {
        keyword: "additionalItems",
        type: "array",
        schemaType: ["boolean", "object"],
        before: "uniqueItems",
        error,
        code(cxt) {
          const { parentSchema, it } = cxt;
          const { items } = parentSchema;
          if (!Array.isArray(items)) {
            (0, util_1.checkStrictMode)(it, '"additionalItems" is ignored when "items" is not an array of schemas');
            return;
          }
          validateAdditionalItems(cxt, items);
        }
      };
      function validateAdditionalItems(cxt, items) {
        const { gen, schema, data, keyword, it } = cxt;
        it.items = true;
        const len = gen.const("len", (0, codegen_1._)`${data}.length`);
        if (schema === false) {
          cxt.setParams({ len: items.length });
          cxt.pass((0, codegen_1._)`${len} <= ${items.length}`);
        } else if (typeof schema == "object" && !(0, util_1.alwaysValidSchema)(it, schema)) {
          const valid = gen.var("valid", (0, codegen_1._)`${len} <= ${items.length}`);
          gen.if((0, codegen_1.not)(valid), () => validateItems(valid));
          cxt.ok(valid);
        }
        function validateItems(valid) {
          gen.forRange("i", items.length, len, (i) => {
            cxt.subschema({ keyword, dataProp: i, dataPropType: util_1.Type.Num }, valid);
            if (!it.allErrors)
              gen.if((0, codegen_1.not)(valid), () => gen.break());
          });
        }
      }
      exports.validateAdditionalItems = validateAdditionalItems;
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/items.js
  var require_items = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/items.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.validateTuple = void 0;
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var code_1 = require_code2();
      var def = {
        keyword: "items",
        type: "array",
        schemaType: ["object", "array", "boolean"],
        before: "uniqueItems",
        code(cxt) {
          const { schema, it } = cxt;
          if (Array.isArray(schema))
            return validateTuple(cxt, "additionalItems", schema);
          it.items = true;
          if ((0, util_1.alwaysValidSchema)(it, schema))
            return;
          cxt.ok((0, code_1.validateArray)(cxt));
        }
      };
      function validateTuple(cxt, extraItems, schArr = cxt.schema) {
        const { gen, parentSchema, data, keyword, it } = cxt;
        checkStrictTuple(parentSchema);
        if (it.opts.unevaluated && schArr.length && it.items !== true) {
          it.items = util_1.mergeEvaluated.items(gen, schArr.length, it.items);
        }
        const valid = gen.name("valid");
        const len = gen.const("len", (0, codegen_1._)`${data}.length`);
        schArr.forEach((sch, i) => {
          if ((0, util_1.alwaysValidSchema)(it, sch))
            return;
          gen.if((0, codegen_1._)`${len} > ${i}`, () => cxt.subschema({
            keyword,
            schemaProp: i,
            dataProp: i
          }, valid));
          cxt.ok(valid);
        });
        function checkStrictTuple(sch) {
          const { opts, errSchemaPath } = it;
          const l = schArr.length;
          const fullTuple = l === sch.minItems && (l === sch.maxItems || sch[extraItems] === false);
          if (opts.strictTuples && !fullTuple) {
            const msg = `"${keyword}" is ${l}-tuple, but minItems or maxItems/${extraItems} are not specified or different at path "${errSchemaPath}"`;
            (0, util_1.checkStrictMode)(it, msg, opts.strictTuples);
          }
        }
      }
      exports.validateTuple = validateTuple;
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/prefixItems.js
  var require_prefixItems = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/prefixItems.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var items_1 = require_items();
      var def = {
        keyword: "prefixItems",
        type: "array",
        schemaType: ["array"],
        before: "uniqueItems",
        code: (cxt) => (0, items_1.validateTuple)(cxt, "items")
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/items2020.js
  var require_items2020 = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/items2020.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var code_1 = require_code2();
      var additionalItems_1 = require_additionalItems();
      var error = {
        message: ({ params: { len } }) => (0, codegen_1.str)`must NOT have more than ${len} items`,
        params: ({ params: { len } }) => (0, codegen_1._)`{limit: ${len}}`
      };
      var def = {
        keyword: "items",
        type: "array",
        schemaType: ["object", "boolean"],
        before: "uniqueItems",
        error,
        code(cxt) {
          const { schema, parentSchema, it } = cxt;
          const { prefixItems } = parentSchema;
          it.items = true;
          if ((0, util_1.alwaysValidSchema)(it, schema))
            return;
          if (prefixItems)
            (0, additionalItems_1.validateAdditionalItems)(cxt, prefixItems);
          else
            cxt.ok((0, code_1.validateArray)(cxt));
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/contains.js
  var require_contains = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/contains.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var error = {
        message: ({ params: { min, max } }) => max === void 0 ? (0, codegen_1.str)`must contain at least ${min} valid item(s)` : (0, codegen_1.str)`must contain at least ${min} and no more than ${max} valid item(s)`,
        params: ({ params: { min, max } }) => max === void 0 ? (0, codegen_1._)`{minContains: ${min}}` : (0, codegen_1._)`{minContains: ${min}, maxContains: ${max}}`
      };
      var def = {
        keyword: "contains",
        type: "array",
        schemaType: ["object", "boolean"],
        before: "uniqueItems",
        trackErrors: true,
        error,
        code(cxt) {
          const { gen, schema, parentSchema, data, it } = cxt;
          let min;
          let max;
          const { minContains, maxContains } = parentSchema;
          if (it.opts.next) {
            min = minContains === void 0 ? 1 : minContains;
            max = maxContains;
          } else {
            min = 1;
          }
          const len = gen.const("len", (0, codegen_1._)`${data}.length`);
          cxt.setParams({ min, max });
          if (max === void 0 && min === 0) {
            (0, util_1.checkStrictMode)(it, `"minContains" == 0 without "maxContains": "contains" keyword ignored`);
            return;
          }
          if (max !== void 0 && min > max) {
            (0, util_1.checkStrictMode)(it, `"minContains" > "maxContains" is always invalid`);
            cxt.fail();
            return;
          }
          if ((0, util_1.alwaysValidSchema)(it, schema)) {
            let cond = (0, codegen_1._)`${len} >= ${min}`;
            if (max !== void 0)
              cond = (0, codegen_1._)`${cond} && ${len} <= ${max}`;
            cxt.pass(cond);
            return;
          }
          it.items = true;
          const valid = gen.name("valid");
          if (max === void 0 && min === 1) {
            validateItems(valid, () => gen.if(valid, () => gen.break()));
          } else if (min === 0) {
            gen.let(valid, true);
            if (max !== void 0)
              gen.if((0, codegen_1._)`${data}.length > 0`, validateItemsWithCount);
          } else {
            gen.let(valid, false);
            validateItemsWithCount();
          }
          cxt.result(valid, () => cxt.reset());
          function validateItemsWithCount() {
            const schValid = gen.name("_valid");
            const count = gen.let("count", 0);
            validateItems(schValid, () => gen.if(schValid, () => checkLimits(count)));
          }
          function validateItems(_valid, block) {
            gen.forRange("i", 0, len, (i) => {
              cxt.subschema({
                keyword: "contains",
                dataProp: i,
                dataPropType: util_1.Type.Num,
                compositeRule: true
              }, _valid);
              block();
            });
          }
          function checkLimits(count) {
            gen.code((0, codegen_1._)`${count}++`);
            if (max === void 0) {
              gen.if((0, codegen_1._)`${count} >= ${min}`, () => gen.assign(valid, true).break());
            } else {
              gen.if((0, codegen_1._)`${count} > ${max}`, () => gen.assign(valid, false).break());
              if (min === 1)
                gen.assign(valid, true);
              else
                gen.if((0, codegen_1._)`${count} >= ${min}`, () => gen.assign(valid, true));
            }
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/dependencies.js
  var require_dependencies = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/dependencies.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.validateSchemaDeps = exports.validatePropertyDeps = exports.error = void 0;
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var code_1 = require_code2();
      exports.error = {
        message: ({ params: { property, depsCount, deps } }) => {
          const property_ies = depsCount === 1 ? "property" : "properties";
          return (0, codegen_1.str)`must have ${property_ies} ${deps} when property ${property} is present`;
        },
        params: ({ params: { property, depsCount, deps, missingProperty } }) => (0, codegen_1._)`{property: ${property},
    missingProperty: ${missingProperty},
    depsCount: ${depsCount},
    deps: ${deps}}`
        // TODO change to reference
      };
      var def = {
        keyword: "dependencies",
        type: "object",
        schemaType: "object",
        error: exports.error,
        code(cxt) {
          const [propDeps, schDeps] = splitDependencies(cxt);
          validatePropertyDeps(cxt, propDeps);
          validateSchemaDeps(cxt, schDeps);
        }
      };
      function splitDependencies({ schema }) {
        const propertyDeps = {};
        const schemaDeps = {};
        for (const key in schema) {
          if (key === "__proto__")
            continue;
          const deps = Array.isArray(schema[key]) ? propertyDeps : schemaDeps;
          deps[key] = schema[key];
        }
        return [propertyDeps, schemaDeps];
      }
      function validatePropertyDeps(cxt, propertyDeps = cxt.schema) {
        const { gen, data, it } = cxt;
        if (Object.keys(propertyDeps).length === 0)
          return;
        const missing = gen.let("missing");
        for (const prop in propertyDeps) {
          const deps = propertyDeps[prop];
          if (deps.length === 0)
            continue;
          const hasProperty = (0, code_1.propertyInData)(gen, data, prop, it.opts.ownProperties);
          cxt.setParams({
            property: prop,
            depsCount: deps.length,
            deps: deps.join(", ")
          });
          if (it.allErrors) {
            gen.if(hasProperty, () => {
              for (const depProp of deps) {
                (0, code_1.checkReportMissingProp)(cxt, depProp);
              }
            });
          } else {
            gen.if((0, codegen_1._)`${hasProperty} && (${(0, code_1.checkMissingProp)(cxt, deps, missing)})`);
            (0, code_1.reportMissingProp)(cxt, missing);
            gen.else();
          }
        }
      }
      exports.validatePropertyDeps = validatePropertyDeps;
      function validateSchemaDeps(cxt, schemaDeps = cxt.schema) {
        const { gen, data, keyword, it } = cxt;
        const valid = gen.name("valid");
        for (const prop in schemaDeps) {
          if ((0, util_1.alwaysValidSchema)(it, schemaDeps[prop]))
            continue;
          gen.if(
            (0, code_1.propertyInData)(gen, data, prop, it.opts.ownProperties),
            () => {
              const schCxt = cxt.subschema({ keyword, schemaProp: prop }, valid);
              cxt.mergeValidEvaluated(schCxt, valid);
            },
            () => gen.var(valid, true)
            // TODO var
          );
          cxt.ok(valid);
        }
      }
      exports.validateSchemaDeps = validateSchemaDeps;
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/propertyNames.js
  var require_propertyNames = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/propertyNames.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var error = {
        message: "property name must be valid",
        params: ({ params }) => (0, codegen_1._)`{propertyName: ${params.propertyName}}`
      };
      var def = {
        keyword: "propertyNames",
        type: "object",
        schemaType: ["object", "boolean"],
        error,
        code(cxt) {
          const { gen, schema, data, it } = cxt;
          if ((0, util_1.alwaysValidSchema)(it, schema))
            return;
          const valid = gen.name("valid");
          gen.forIn("key", data, (key) => {
            cxt.setParams({ propertyName: key });
            cxt.subschema({
              keyword: "propertyNames",
              data: key,
              dataTypes: ["string"],
              propertyName: key,
              compositeRule: true
            }, valid);
            gen.if((0, codegen_1.not)(valid), () => {
              cxt.error(true);
              if (!it.allErrors)
                gen.break();
            });
          });
          cxt.ok(valid);
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/additionalProperties.js
  var require_additionalProperties = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/additionalProperties.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var code_1 = require_code2();
      var codegen_1 = require_codegen();
      var names_1 = require_names();
      var util_1 = require_util();
      var error = {
        message: "must NOT have additional properties",
        params: ({ params }) => (0, codegen_1._)`{additionalProperty: ${params.additionalProperty}}`
      };
      var def = {
        keyword: "additionalProperties",
        type: ["object"],
        schemaType: ["boolean", "object"],
        allowUndefined: true,
        trackErrors: true,
        error,
        code(cxt) {
          const { gen, schema, parentSchema, data, errsCount, it } = cxt;
          if (!errsCount)
            throw new Error("ajv implementation error");
          const { allErrors, opts } = it;
          it.props = true;
          if (opts.removeAdditional !== "all" && (0, util_1.alwaysValidSchema)(it, schema))
            return;
          const props = (0, code_1.allSchemaProperties)(parentSchema.properties);
          const patProps = (0, code_1.allSchemaProperties)(parentSchema.patternProperties);
          checkAdditionalProperties();
          cxt.ok((0, codegen_1._)`${errsCount} === ${names_1.default.errors}`);
          function checkAdditionalProperties() {
            gen.forIn("key", data, (key) => {
              if (!props.length && !patProps.length)
                additionalPropertyCode(key);
              else
                gen.if(isAdditional(key), () => additionalPropertyCode(key));
            });
          }
          function isAdditional(key) {
            let definedProp;
            if (props.length > 8) {
              const propsSchema = (0, util_1.schemaRefOrVal)(it, parentSchema.properties, "properties");
              definedProp = (0, code_1.isOwnProperty)(gen, propsSchema, key);
            } else if (props.length) {
              definedProp = (0, codegen_1.or)(...props.map((p) => (0, codegen_1._)`${key} === ${p}`));
            } else {
              definedProp = codegen_1.nil;
            }
            if (patProps.length) {
              definedProp = (0, codegen_1.or)(definedProp, ...patProps.map((p) => (0, codegen_1._)`${(0, code_1.usePattern)(cxt, p)}.test(${key})`));
            }
            return (0, codegen_1.not)(definedProp);
          }
          function deleteAdditional(key) {
            gen.code((0, codegen_1._)`delete ${data}[${key}]`);
          }
          function additionalPropertyCode(key) {
            if (opts.removeAdditional === "all" || opts.removeAdditional && schema === false) {
              deleteAdditional(key);
              return;
            }
            if (schema === false) {
              cxt.setParams({ additionalProperty: key });
              cxt.error();
              if (!allErrors)
                gen.break();
              return;
            }
            if (typeof schema == "object" && !(0, util_1.alwaysValidSchema)(it, schema)) {
              const valid = gen.name("valid");
              if (opts.removeAdditional === "failing") {
                applyAdditionalSchema(key, valid, false);
                gen.if((0, codegen_1.not)(valid), () => {
                  cxt.reset();
                  deleteAdditional(key);
                });
              } else {
                applyAdditionalSchema(key, valid);
                if (!allErrors)
                  gen.if((0, codegen_1.not)(valid), () => gen.break());
              }
            }
          }
          function applyAdditionalSchema(key, valid, errors2) {
            const subschema = {
              keyword: "additionalProperties",
              dataProp: key,
              dataPropType: util_1.Type.Str
            };
            if (errors2 === false) {
              Object.assign(subschema, {
                compositeRule: true,
                createErrors: false,
                allErrors: false
              });
            }
            cxt.subschema(subschema, valid);
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/properties.js
  var require_properties = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/properties.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var validate_1 = require_validate();
      var code_1 = require_code2();
      var util_1 = require_util();
      var additionalProperties_1 = require_additionalProperties();
      var def = {
        keyword: "properties",
        type: "object",
        schemaType: "object",
        code(cxt) {
          const { gen, schema, parentSchema, data, it } = cxt;
          if (it.opts.removeAdditional === "all" && parentSchema.additionalProperties === void 0) {
            additionalProperties_1.default.code(new validate_1.KeywordCxt(it, additionalProperties_1.default, "additionalProperties"));
          }
          const allProps = (0, code_1.allSchemaProperties)(schema);
          for (const prop of allProps) {
            it.definedProperties.add(prop);
          }
          if (it.opts.unevaluated && allProps.length && it.props !== true) {
            it.props = util_1.mergeEvaluated.props(gen, (0, util_1.toHash)(allProps), it.props);
          }
          const properties = allProps.filter((p) => !(0, util_1.alwaysValidSchema)(it, schema[p]));
          if (properties.length === 0)
            return;
          const valid = gen.name("valid");
          for (const prop of properties) {
            if (hasDefault(prop)) {
              applyPropertySchema(prop);
            } else {
              gen.if((0, code_1.propertyInData)(gen, data, prop, it.opts.ownProperties));
              applyPropertySchema(prop);
              if (!it.allErrors)
                gen.else().var(valid, true);
              gen.endIf();
            }
            cxt.it.definedProperties.add(prop);
            cxt.ok(valid);
          }
          function hasDefault(prop) {
            return it.opts.useDefaults && !it.compositeRule && schema[prop].default !== void 0;
          }
          function applyPropertySchema(prop) {
            cxt.subschema({
              keyword: "properties",
              schemaProp: prop,
              dataProp: prop
            }, valid);
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/patternProperties.js
  var require_patternProperties = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/patternProperties.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var code_1 = require_code2();
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var util_2 = require_util();
      var def = {
        keyword: "patternProperties",
        type: "object",
        schemaType: "object",
        code(cxt) {
          const { gen, schema, data, parentSchema, it } = cxt;
          const { opts } = it;
          const patterns = (0, code_1.allSchemaProperties)(schema);
          const alwaysValidPatterns = patterns.filter((p) => (0, util_1.alwaysValidSchema)(it, schema[p]));
          if (patterns.length === 0 || alwaysValidPatterns.length === patterns.length && (!it.opts.unevaluated || it.props === true)) {
            return;
          }
          const checkProperties = opts.strictSchema && !opts.allowMatchingProperties && parentSchema.properties;
          const valid = gen.name("valid");
          if (it.props !== true && !(it.props instanceof codegen_1.Name)) {
            it.props = (0, util_2.evaluatedPropsToName)(gen, it.props);
          }
          const { props } = it;
          validatePatternProperties();
          function validatePatternProperties() {
            for (const pat of patterns) {
              if (checkProperties)
                checkMatchingProperties(pat);
              if (it.allErrors) {
                validateProperties(pat);
              } else {
                gen.var(valid, true);
                validateProperties(pat);
                gen.if(valid);
              }
            }
          }
          function checkMatchingProperties(pat) {
            for (const prop in checkProperties) {
              if (new RegExp(pat).test(prop)) {
                (0, util_1.checkStrictMode)(it, `property ${prop} matches pattern ${pat} (use allowMatchingProperties)`);
              }
            }
          }
          function validateProperties(pat) {
            gen.forIn("key", data, (key) => {
              gen.if((0, codegen_1._)`${(0, code_1.usePattern)(cxt, pat)}.test(${key})`, () => {
                const alwaysValid = alwaysValidPatterns.includes(pat);
                if (!alwaysValid) {
                  cxt.subschema({
                    keyword: "patternProperties",
                    schemaProp: pat,
                    dataProp: key,
                    dataPropType: util_2.Type.Str
                  }, valid);
                }
                if (it.opts.unevaluated && props !== true) {
                  gen.assign((0, codegen_1._)`${props}[${key}]`, true);
                } else if (!alwaysValid && !it.allErrors) {
                  gen.if((0, codegen_1.not)(valid), () => gen.break());
                }
              });
            });
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/not.js
  var require_not = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/not.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var util_1 = require_util();
      var def = {
        keyword: "not",
        schemaType: ["object", "boolean"],
        trackErrors: true,
        code(cxt) {
          const { gen, schema, it } = cxt;
          if ((0, util_1.alwaysValidSchema)(it, schema)) {
            cxt.fail();
            return;
          }
          const valid = gen.name("valid");
          cxt.subschema({
            keyword: "not",
            compositeRule: true,
            createErrors: false,
            allErrors: false
          }, valid);
          cxt.failResult(valid, () => cxt.reset(), () => cxt.error());
        },
        error: { message: "must NOT be valid" }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/anyOf.js
  var require_anyOf = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/anyOf.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var code_1 = require_code2();
      var def = {
        keyword: "anyOf",
        schemaType: "array",
        trackErrors: true,
        code: code_1.validateUnion,
        error: { message: "must match a schema in anyOf" }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/oneOf.js
  var require_oneOf = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/oneOf.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var error = {
        message: "must match exactly one schema in oneOf",
        params: ({ params }) => (0, codegen_1._)`{passingSchemas: ${params.passing}}`
      };
      var def = {
        keyword: "oneOf",
        schemaType: "array",
        trackErrors: true,
        error,
        code(cxt) {
          const { gen, schema, parentSchema, it } = cxt;
          if (!Array.isArray(schema))
            throw new Error("ajv implementation error");
          if (it.opts.discriminator && parentSchema.discriminator)
            return;
          const schArr = schema;
          const valid = gen.let("valid", false);
          const passing = gen.let("passing", null);
          const schValid = gen.name("_valid");
          cxt.setParams({ passing });
          gen.block(validateOneOf);
          cxt.result(valid, () => cxt.reset(), () => cxt.error(true));
          function validateOneOf() {
            schArr.forEach((sch, i) => {
              let schCxt;
              if ((0, util_1.alwaysValidSchema)(it, sch)) {
                gen.var(schValid, true);
              } else {
                schCxt = cxt.subschema({
                  keyword: "oneOf",
                  schemaProp: i,
                  compositeRule: true
                }, schValid);
              }
              if (i > 0) {
                gen.if((0, codegen_1._)`${schValid} && ${valid}`).assign(valid, false).assign(passing, (0, codegen_1._)`[${passing}, ${i}]`).else();
              }
              gen.if(schValid, () => {
                gen.assign(valid, true);
                gen.assign(passing, i);
                if (schCxt)
                  cxt.mergeEvaluated(schCxt, codegen_1.Name);
              });
            });
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/allOf.js
  var require_allOf = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/allOf.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var util_1 = require_util();
      var def = {
        keyword: "allOf",
        schemaType: "array",
        code(cxt) {
          const { gen, schema, it } = cxt;
          if (!Array.isArray(schema))
            throw new Error("ajv implementation error");
          const valid = gen.name("valid");
          schema.forEach((sch, i) => {
            if ((0, util_1.alwaysValidSchema)(it, sch))
              return;
            const schCxt = cxt.subschema({ keyword: "allOf", schemaProp: i }, valid);
            cxt.ok(valid);
            cxt.mergeEvaluated(schCxt);
          });
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/if.js
  var require_if = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/if.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var util_1 = require_util();
      var error = {
        message: ({ params }) => (0, codegen_1.str)`must match "${params.ifClause}" schema`,
        params: ({ params }) => (0, codegen_1._)`{failingKeyword: ${params.ifClause}}`
      };
      var def = {
        keyword: "if",
        schemaType: ["object", "boolean"],
        trackErrors: true,
        error,
        code(cxt) {
          const { gen, parentSchema, it } = cxt;
          if (parentSchema.then === void 0 && parentSchema.else === void 0) {
            (0, util_1.checkStrictMode)(it, '"if" without "then" and "else" is ignored');
          }
          const hasThen = hasSchema(it, "then");
          const hasElse = hasSchema(it, "else");
          if (!hasThen && !hasElse)
            return;
          const valid = gen.let("valid", true);
          const schValid = gen.name("_valid");
          validateIf();
          cxt.reset();
          if (hasThen && hasElse) {
            const ifClause = gen.let("ifClause");
            cxt.setParams({ ifClause });
            gen.if(schValid, validateClause("then", ifClause), validateClause("else", ifClause));
          } else if (hasThen) {
            gen.if(schValid, validateClause("then"));
          } else {
            gen.if((0, codegen_1.not)(schValid), validateClause("else"));
          }
          cxt.pass(valid, () => cxt.error(true));
          function validateIf() {
            const schCxt = cxt.subschema({
              keyword: "if",
              compositeRule: true,
              createErrors: false,
              allErrors: false
            }, schValid);
            cxt.mergeEvaluated(schCxt);
          }
          function validateClause(keyword, ifClause) {
            return () => {
              const schCxt = cxt.subschema({ keyword }, schValid);
              gen.assign(valid, schValid);
              cxt.mergeValidEvaluated(schCxt, valid);
              if (ifClause)
                gen.assign(ifClause, (0, codegen_1._)`${keyword}`);
              else
                cxt.setParams({ ifClause: keyword });
            };
          }
        }
      };
      function hasSchema(it, keyword) {
        const schema = it.schema[keyword];
        return schema !== void 0 && !(0, util_1.alwaysValidSchema)(it, schema);
      }
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/thenElse.js
  var require_thenElse = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/thenElse.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var util_1 = require_util();
      var def = {
        keyword: ["then", "else"],
        schemaType: ["object", "boolean"],
        code({ keyword, parentSchema, it }) {
          if (parentSchema.if === void 0)
            (0, util_1.checkStrictMode)(it, `"${keyword}" without "if" is ignored`);
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/applicator/index.js
  var require_applicator = __commonJS({
    "node_modules/ajv/dist/vocabularies/applicator/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var additionalItems_1 = require_additionalItems();
      var prefixItems_1 = require_prefixItems();
      var items_1 = require_items();
      var items2020_1 = require_items2020();
      var contains_1 = require_contains();
      var dependencies_1 = require_dependencies();
      var propertyNames_1 = require_propertyNames();
      var additionalProperties_1 = require_additionalProperties();
      var properties_1 = require_properties();
      var patternProperties_1 = require_patternProperties();
      var not_1 = require_not();
      var anyOf_1 = require_anyOf();
      var oneOf_1 = require_oneOf();
      var allOf_1 = require_allOf();
      var if_1 = require_if();
      var thenElse_1 = require_thenElse();
      function getApplicator(draft2020 = false) {
        const applicator = [
          // any
          not_1.default,
          anyOf_1.default,
          oneOf_1.default,
          allOf_1.default,
          if_1.default,
          thenElse_1.default,
          // object
          propertyNames_1.default,
          additionalProperties_1.default,
          dependencies_1.default,
          properties_1.default,
          patternProperties_1.default
        ];
        if (draft2020)
          applicator.push(prefixItems_1.default, items2020_1.default);
        else
          applicator.push(additionalItems_1.default, items_1.default);
        applicator.push(contains_1.default);
        return applicator;
      }
      exports.default = getApplicator;
    }
  });

  // node_modules/ajv/dist/vocabularies/format/format.js
  var require_format = __commonJS({
    "node_modules/ajv/dist/vocabularies/format/format.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var error = {
        message: ({ schemaCode }) => (0, codegen_1.str)`must match format "${schemaCode}"`,
        params: ({ schemaCode }) => (0, codegen_1._)`{format: ${schemaCode}}`
      };
      var def = {
        keyword: "format",
        type: ["number", "string"],
        schemaType: "string",
        $data: true,
        error,
        code(cxt, ruleType) {
          const { gen, data, $data, schema, schemaCode, it } = cxt;
          const { opts, errSchemaPath, schemaEnv, self } = it;
          if (!opts.validateFormats)
            return;
          if ($data)
            validate$DataFormat();
          else
            validateFormat();
          function validate$DataFormat() {
            const fmts = gen.scopeValue("formats", {
              ref: self.formats,
              code: opts.code.formats
            });
            const fDef = gen.const("fDef", (0, codegen_1._)`${fmts}[${schemaCode}]`);
            const fType = gen.let("fType");
            const format = gen.let("format");
            gen.if((0, codegen_1._)`typeof ${fDef} == "object" && !(${fDef} instanceof RegExp)`, () => gen.assign(fType, (0, codegen_1._)`${fDef}.type || "string"`).assign(format, (0, codegen_1._)`${fDef}.validate`), () => gen.assign(fType, (0, codegen_1._)`"string"`).assign(format, fDef));
            cxt.fail$data((0, codegen_1.or)(unknownFmt(), invalidFmt()));
            function unknownFmt() {
              if (opts.strictSchema === false)
                return codegen_1.nil;
              return (0, codegen_1._)`${schemaCode} && !${format}`;
            }
            function invalidFmt() {
              const callFormat = schemaEnv.$async ? (0, codegen_1._)`(${fDef}.async ? await ${format}(${data}) : ${format}(${data}))` : (0, codegen_1._)`${format}(${data})`;
              const validData = (0, codegen_1._)`(typeof ${format} == "function" ? ${callFormat} : ${format}.test(${data}))`;
              return (0, codegen_1._)`${format} && ${format} !== true && ${fType} === ${ruleType} && !${validData}`;
            }
          }
          function validateFormat() {
            const formatDef = self.formats[schema];
            if (!formatDef) {
              unknownFormat();
              return;
            }
            if (formatDef === true)
              return;
            const [fmtType, format, fmtRef] = getFormat(formatDef);
            if (fmtType === ruleType)
              cxt.pass(validCondition());
            function unknownFormat() {
              if (opts.strictSchema === false) {
                self.logger.warn(unknownMsg());
                return;
              }
              throw new Error(unknownMsg());
              function unknownMsg() {
                return `unknown format "${schema}" ignored in schema at path "${errSchemaPath}"`;
              }
            }
            function getFormat(fmtDef) {
              const code = fmtDef instanceof RegExp ? (0, codegen_1.regexpCode)(fmtDef) : opts.code.formats ? (0, codegen_1._)`${opts.code.formats}${(0, codegen_1.getProperty)(schema)}` : void 0;
              const fmt = gen.scopeValue("formats", { key: schema, ref: fmtDef, code });
              if (typeof fmtDef == "object" && !(fmtDef instanceof RegExp)) {
                return [fmtDef.type || "string", fmtDef.validate, (0, codegen_1._)`${fmt}.validate`];
              }
              return ["string", fmtDef, fmt];
            }
            function validCondition() {
              if (typeof formatDef == "object" && !(formatDef instanceof RegExp) && formatDef.async) {
                if (!schemaEnv.$async)
                  throw new Error("async format in sync schema");
                return (0, codegen_1._)`await ${fmtRef}(${data})`;
              }
              return typeof format == "function" ? (0, codegen_1._)`${fmtRef}(${data})` : (0, codegen_1._)`${fmtRef}.test(${data})`;
            }
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/vocabularies/format/index.js
  var require_format2 = __commonJS({
    "node_modules/ajv/dist/vocabularies/format/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var format_1 = require_format();
      var format = [format_1.default];
      exports.default = format;
    }
  });

  // node_modules/ajv/dist/vocabularies/metadata.js
  var require_metadata = __commonJS({
    "node_modules/ajv/dist/vocabularies/metadata.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.contentVocabulary = exports.metadataVocabulary = void 0;
      exports.metadataVocabulary = [
        "title",
        "description",
        "default",
        "deprecated",
        "readOnly",
        "writeOnly",
        "examples"
      ];
      exports.contentVocabulary = [
        "contentMediaType",
        "contentEncoding",
        "contentSchema"
      ];
    }
  });

  // node_modules/ajv/dist/vocabularies/draft7.js
  var require_draft7 = __commonJS({
    "node_modules/ajv/dist/vocabularies/draft7.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var core_1 = require_core2();
      var validation_1 = require_validation();
      var applicator_1 = require_applicator();
      var format_1 = require_format2();
      var metadata_1 = require_metadata();
      var draft7Vocabularies = [
        core_1.default,
        validation_1.default,
        (0, applicator_1.default)(),
        format_1.default,
        metadata_1.metadataVocabulary,
        metadata_1.contentVocabulary
      ];
      exports.default = draft7Vocabularies;
    }
  });

  // node_modules/ajv/dist/vocabularies/discriminator/types.js
  var require_types = __commonJS({
    "node_modules/ajv/dist/vocabularies/discriminator/types.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.DiscrError = void 0;
      var DiscrError;
      (function(DiscrError2) {
        DiscrError2["Tag"] = "tag";
        DiscrError2["Mapping"] = "mapping";
      })(DiscrError || (exports.DiscrError = DiscrError = {}));
    }
  });

  // node_modules/ajv/dist/vocabularies/discriminator/index.js
  var require_discriminator = __commonJS({
    "node_modules/ajv/dist/vocabularies/discriminator/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var codegen_1 = require_codegen();
      var types_1 = require_types();
      var compile_1 = require_compile();
      var ref_error_1 = require_ref_error();
      var util_1 = require_util();
      var error = {
        message: ({ params: { discrError, tagName } }) => discrError === types_1.DiscrError.Tag ? `tag "${tagName}" must be string` : `value of tag "${tagName}" must be in oneOf`,
        params: ({ params: { discrError, tag, tagName } }) => (0, codegen_1._)`{error: ${discrError}, tag: ${tagName}, tagValue: ${tag}}`
      };
      var def = {
        keyword: "discriminator",
        type: "object",
        schemaType: "object",
        error,
        code(cxt) {
          const { gen, data, schema, parentSchema, it } = cxt;
          const { oneOf } = parentSchema;
          if (!it.opts.discriminator) {
            throw new Error("discriminator: requires discriminator option");
          }
          const tagName = schema.propertyName;
          if (typeof tagName != "string")
            throw new Error("discriminator: requires propertyName");
          if (schema.mapping)
            throw new Error("discriminator: mapping is not supported");
          if (!oneOf)
            throw new Error("discriminator: requires oneOf keyword");
          const valid = gen.let("valid", false);
          const tag = gen.const("tag", (0, codegen_1._)`${data}${(0, codegen_1.getProperty)(tagName)}`);
          gen.if((0, codegen_1._)`typeof ${tag} == "string"`, () => validateMapping(), () => cxt.error(false, { discrError: types_1.DiscrError.Tag, tag, tagName }));
          cxt.ok(valid);
          function validateMapping() {
            const mapping = getMapping();
            gen.if(false);
            for (const tagValue in mapping) {
              gen.elseIf((0, codegen_1._)`${tag} === ${tagValue}`);
              gen.assign(valid, applyTagSchema(mapping[tagValue]));
            }
            gen.else();
            cxt.error(false, { discrError: types_1.DiscrError.Mapping, tag, tagName });
            gen.endIf();
          }
          function applyTagSchema(schemaProp) {
            const _valid = gen.name("valid");
            const schCxt = cxt.subschema({ keyword: "oneOf", schemaProp }, _valid);
            cxt.mergeEvaluated(schCxt, codegen_1.Name);
            return _valid;
          }
          function getMapping() {
            var _a;
            const oneOfMapping = {};
            const topRequired = hasRequired(parentSchema);
            let tagRequired = true;
            for (let i = 0; i < oneOf.length; i++) {
              let sch = oneOf[i];
              if ((sch === null || sch === void 0 ? void 0 : sch.$ref) && !(0, util_1.schemaHasRulesButRef)(sch, it.self.RULES)) {
                const ref = sch.$ref;
                sch = compile_1.resolveRef.call(it.self, it.schemaEnv.root, it.baseId, ref);
                if (sch instanceof compile_1.SchemaEnv)
                  sch = sch.schema;
                if (sch === void 0)
                  throw new ref_error_1.default(it.opts.uriResolver, it.baseId, ref);
              }
              const propSch = (_a = sch === null || sch === void 0 ? void 0 : sch.properties) === null || _a === void 0 ? void 0 : _a[tagName];
              if (typeof propSch != "object") {
                throw new Error(`discriminator: oneOf subschemas (or referenced schemas) must have "properties/${tagName}"`);
              }
              tagRequired = tagRequired && (topRequired || hasRequired(sch));
              addMappings(propSch, i);
            }
            if (!tagRequired)
              throw new Error(`discriminator: "${tagName}" must be required`);
            return oneOfMapping;
            function hasRequired({ required }) {
              return Array.isArray(required) && required.includes(tagName);
            }
            function addMappings(sch, i) {
              if (sch.const) {
                addMapping(sch.const, i);
              } else if (sch.enum) {
                for (const tagValue of sch.enum) {
                  addMapping(tagValue, i);
                }
              } else {
                throw new Error(`discriminator: "properties/${tagName}" must have "const" or "enum"`);
              }
            }
            function addMapping(tagValue, i) {
              if (typeof tagValue != "string" || tagValue in oneOfMapping) {
                throw new Error(`discriminator: "${tagName}" values must be unique strings`);
              }
              oneOfMapping[tagValue] = i;
            }
          }
        }
      };
      exports.default = def;
    }
  });

  // node_modules/ajv/dist/refs/json-schema-draft-07.json
  var require_json_schema_draft_07 = __commonJS({
    "node_modules/ajv/dist/refs/json-schema-draft-07.json"(exports, module) {
      module.exports = {
        $schema: "http://json-schema.org/draft-07/schema#",
        $id: "http://json-schema.org/draft-07/schema#",
        title: "Core schema meta-schema",
        definitions: {
          schemaArray: {
            type: "array",
            minItems: 1,
            items: { $ref: "#" }
          },
          nonNegativeInteger: {
            type: "integer",
            minimum: 0
          },
          nonNegativeIntegerDefault0: {
            allOf: [{ $ref: "#/definitions/nonNegativeInteger" }, { default: 0 }]
          },
          simpleTypes: {
            enum: ["array", "boolean", "integer", "null", "number", "object", "string"]
          },
          stringArray: {
            type: "array",
            items: { type: "string" },
            uniqueItems: true,
            default: []
          }
        },
        type: ["object", "boolean"],
        properties: {
          $id: {
            type: "string",
            format: "uri-reference"
          },
          $schema: {
            type: "string",
            format: "uri"
          },
          $ref: {
            type: "string",
            format: "uri-reference"
          },
          $comment: {
            type: "string"
          },
          title: {
            type: "string"
          },
          description: {
            type: "string"
          },
          default: true,
          readOnly: {
            type: "boolean",
            default: false
          },
          examples: {
            type: "array",
            items: true
          },
          multipleOf: {
            type: "number",
            exclusiveMinimum: 0
          },
          maximum: {
            type: "number"
          },
          exclusiveMaximum: {
            type: "number"
          },
          minimum: {
            type: "number"
          },
          exclusiveMinimum: {
            type: "number"
          },
          maxLength: { $ref: "#/definitions/nonNegativeInteger" },
          minLength: { $ref: "#/definitions/nonNegativeIntegerDefault0" },
          pattern: {
            type: "string",
            format: "regex"
          },
          additionalItems: { $ref: "#" },
          items: {
            anyOf: [{ $ref: "#" }, { $ref: "#/definitions/schemaArray" }],
            default: true
          },
          maxItems: { $ref: "#/definitions/nonNegativeInteger" },
          minItems: { $ref: "#/definitions/nonNegativeIntegerDefault0" },
          uniqueItems: {
            type: "boolean",
            default: false
          },
          contains: { $ref: "#" },
          maxProperties: { $ref: "#/definitions/nonNegativeInteger" },
          minProperties: { $ref: "#/definitions/nonNegativeIntegerDefault0" },
          required: { $ref: "#/definitions/stringArray" },
          additionalProperties: { $ref: "#" },
          definitions: {
            type: "object",
            additionalProperties: { $ref: "#" },
            default: {}
          },
          properties: {
            type: "object",
            additionalProperties: { $ref: "#" },
            default: {}
          },
          patternProperties: {
            type: "object",
            additionalProperties: { $ref: "#" },
            propertyNames: { format: "regex" },
            default: {}
          },
          dependencies: {
            type: "object",
            additionalProperties: {
              anyOf: [{ $ref: "#" }, { $ref: "#/definitions/stringArray" }]
            }
          },
          propertyNames: { $ref: "#" },
          const: true,
          enum: {
            type: "array",
            items: true,
            minItems: 1,
            uniqueItems: true
          },
          type: {
            anyOf: [
              { $ref: "#/definitions/simpleTypes" },
              {
                type: "array",
                items: { $ref: "#/definitions/simpleTypes" },
                minItems: 1,
                uniqueItems: true
              }
            ]
          },
          format: { type: "string" },
          contentMediaType: { type: "string" },
          contentEncoding: { type: "string" },
          if: { $ref: "#" },
          then: { $ref: "#" },
          else: { $ref: "#" },
          allOf: { $ref: "#/definitions/schemaArray" },
          anyOf: { $ref: "#/definitions/schemaArray" },
          oneOf: { $ref: "#/definitions/schemaArray" },
          not: { $ref: "#" }
        },
        default: true
      };
    }
  });

  // node_modules/ajv/dist/ajv.js
  var require_ajv = __commonJS({
    "node_modules/ajv/dist/ajv.js"(exports, module) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.MissingRefError = exports.ValidationError = exports.CodeGen = exports.Name = exports.nil = exports.stringify = exports.str = exports._ = exports.KeywordCxt = exports.Ajv = void 0;
      var core_1 = require_core();
      var draft7_1 = require_draft7();
      var discriminator_1 = require_discriminator();
      var draft7MetaSchema = require_json_schema_draft_07();
      var META_SUPPORT_DATA = ["/properties"];
      var META_SCHEMA_ID = "http://json-schema.org/draft-07/schema";
      var Ajv2 = class extends core_1.default {
        _addVocabularies() {
          super._addVocabularies();
          draft7_1.default.forEach((v) => this.addVocabulary(v));
          if (this.opts.discriminator)
            this.addKeyword(discriminator_1.default);
        }
        _addDefaultMetaSchema() {
          super._addDefaultMetaSchema();
          if (!this.opts.meta)
            return;
          const metaSchema = this.opts.$data ? this.$dataMetaSchema(draft7MetaSchema, META_SUPPORT_DATA) : draft7MetaSchema;
          this.addMetaSchema(metaSchema, META_SCHEMA_ID, false);
          this.refs["http://json-schema.org/schema"] = META_SCHEMA_ID;
        }
        defaultMeta() {
          return this.opts.defaultMeta = super.defaultMeta() || (this.getSchema(META_SCHEMA_ID) ? META_SCHEMA_ID : void 0);
        }
      };
      exports.Ajv = Ajv2;
      module.exports = exports = Ajv2;
      module.exports.Ajv = Ajv2;
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.default = Ajv2;
      var validate_1 = require_validate();
      Object.defineProperty(exports, "KeywordCxt", { enumerable: true, get: function() {
        return validate_1.KeywordCxt;
      } });
      var codegen_1 = require_codegen();
      Object.defineProperty(exports, "_", { enumerable: true, get: function() {
        return codegen_1._;
      } });
      Object.defineProperty(exports, "str", { enumerable: true, get: function() {
        return codegen_1.str;
      } });
      Object.defineProperty(exports, "stringify", { enumerable: true, get: function() {
        return codegen_1.stringify;
      } });
      Object.defineProperty(exports, "nil", { enumerable: true, get: function() {
        return codegen_1.nil;
      } });
      Object.defineProperty(exports, "Name", { enumerable: true, get: function() {
        return codegen_1.Name;
      } });
      Object.defineProperty(exports, "CodeGen", { enumerable: true, get: function() {
        return codegen_1.CodeGen;
      } });
      var validation_error_1 = require_validation_error();
      Object.defineProperty(exports, "ValidationError", { enumerable: true, get: function() {
        return validation_error_1.default;
      } });
      var ref_error_1 = require_ref_error();
      Object.defineProperty(exports, "MissingRefError", { enumerable: true, get: function() {
        return ref_error_1.default;
      } });
    }
  });

  // src/native-transport.js
  var CHUNK_CHARACTERS = 1024 * 1024;
  var MAX_MESSAGE_CHARACTERS = 128 * 1024 * 1024;
  var MAX_PARTS = MAX_MESSAGE_CHARACTERS / CHUNK_CHARACTERS;
  var LIFETIME_MS = 3e4;
  function createMessageAssembler(now = () => Date.now()) {
    let pending = null;
    function reset() {
      pending = null;
    }
    function accept(packet) {
      if (pending && now() - pending.started > LIFETIME_MS) reset();
      if (packet?.kind === "native-error") reset();
      if (packet?.kind !== "qf-transport-chunk") return packet;
      const { id, index, total, length, data } = packet;
      const invalid = typeof id !== "string" || !/^[a-zA-Z0-9-]{1,80}$/.test(id) || !Number.isSafeInteger(index) || !Number.isSafeInteger(total) || total < 1 || total > MAX_PARTS || index < 0 || index >= total || !Number.isSafeInteger(length) || length < 1 || length > MAX_MESSAGE_CHARACTERS || Math.ceil(length / CHUNK_CHARACTERS) !== total || typeof data !== "string" || data.length !== Math.min(CHUNK_CHARACTERS, length - index * CHUNK_CHARACTERS);
      if (invalid) {
        reset();
        throw new Error("Invalid browser transport chunk");
      }
      if (index === 0) {
        if (pending) {
          reset();
          throw new Error("Overlapping browser transport messages");
        }
        pending = { id, total, length, index: 0, parts: [], started: now() };
      }
      if (!pending || pending.id !== id || pending.index !== index || pending.total !== total || pending.length !== length) {
        reset();
        throw new Error("Out-of-order browser transport chunk");
      }
      pending.parts.push(data);
      pending.index++;
      if (pending.index !== total) return null;
      const encoded = pending.parts.join("");
      reset();
      const result = JSON.parse(encoded);
      if (!result || typeof result !== "object" || result.kind === "qf-transport-chunk") throw new Error("Invalid browser transport payload");
      return result;
    }
    return { accept, reset };
  }
  function sendMessage(transport2, message, id) {
    const encoded = JSON.stringify(message);
    if (typeof encoded !== "string" || encoded.length > MAX_MESSAGE_CHARACTERS) throw new Error("Browser message exceeds 128 Mi characters");
    if (encoded.length <= CHUNK_CHARACTERS) {
      transport2.postMessage(message);
      return;
    }
    const total = Math.ceil(encoded.length / CHUNK_CHARACTERS);
    for (let index = 0; index < total; index++) transport2.postMessage({ kind: "qf-transport-chunk", id, index, total, length: encoded.length, data: encoded.slice(index * CHUNK_CHARACTERS, (index + 1) * CHUNK_CHARACTERS) });
  }
  function createNativeTransport(raw) {
    const assembler = createMessageAssembler();
    let sequence3 = 0;
    const listeners = /* @__PURE__ */ new Set();
    const cleanup = setInterval(() => assembler.accept(null), 1e3);
    cleanup.unref?.();
    raw.addEventListener("message", (event) => {
      try {
        const message = assembler.accept(event.data);
        if (message) for (const listener of listeners) listener({ data: message });
      } catch (error) {
        for (const listener of listeners) listener({ data: { kind: "native-error", code: "INVALID_TRANSPORT", message: error.message } });
      }
    });
    globalThis.window?.addEventListener("pagehide", () => {
      clearInterval(cleanup);
      assembler.reset();
      listeners.clear();
    }, { once: true });
    return Object.freeze({
      postMessage(message) {
        sendMessage(raw, message, "page-" + ++sequence3);
      },
      addEventListener(type, listener) {
        if (type !== "message") throw new Error("Unsupported native transport event");
        listeners.add(listener);
      }
    });
  }

  // src/webview2-editor-bridge.js
  var transport = createNativeTransport(window.chrome.webview);
  var send = (message) => transport.postMessage(message);
  var sequence = 0;
  var initialized = false;
  var closing = false;
  var calls = /* @__PURE__ */ new Map();
  var permissions = [];
  var questionId = () => window.qfEditorShell?.getState()?.question?.id ?? "";
  function call(method, encoded) {
    const id = String(++sequence);
    return new Promise((resolve, reject2) => {
      const timer = setTimeout(() => {
        calls.delete(id);
        reject2(new Error("\u7F16\u8F91\u63A5\u53E3\u7B49\u5F85\u8D85\u65F6"));
      }, 15e3);
      calls.set(id, { resolve, reject: reject2, timer });
      send({ kind: "editor-call", id, method, questionId: questionId(), encoded });
    });
  }
  var host = Object.freeze({
    viewportHeight: () => innerHeight,
    changed: (encoded) => call("changed", encoded),
    editContent: (encoded) => call("editContent", encoded),
    resolveContent: (encoded) => call("resolveContent", encoded),
    configureUi: (id, encoded) => send({ kind: "configure-ui", questionId: id, encoded }),
    flushed: (id, draft) => send({ kind: "flushed", id, draft }),
    flushFailed: (id, message) => send({ kind: "flush-failed", id, message }),
    reloadFailed: (message) => send({ kind: "reload-failed", message }),
    contentHeight: () => {
    },
    request: (requestId, encoded) => send({ kind: "bank-request", requestId, encoded })
  });
  window.editorHost = host;
  window.extensionEditorHost = host;
  window.bankEditorHost = host;
  var changes = Promise.resolve();
  transport.addEventListener("message", (event) => {
    const message = event.data;
    if (message.kind === "editor-reply") {
      const pending = calls.get(message.id);
      if (!pending) return;
      clearTimeout(pending.timer);
      calls.delete(message.id);
      if (message.reply.ok) pending.resolve(message.reply.data);
      else {
        const error = new Error(message.reply.error.message);
        Object.assign(error, message.reply.error);
        pending.reject(error);
      }
      return;
    }
    if (message.kind === "permissions") {
      if (initialized) window.questionExtensions.updatePermissions(message.value);
      else permissions.push(message.value);
      return;
    }
    if (message.kind === "shell" && message.method === "replyFromJson") {
      window.qfEditorShell.replyFromJson(JSON.stringify(message.value));
      return;
    }
    changes = changes.then(async () => {
      switch (message.kind) {
        case "bootstrap":
          for (const bundle of message.packages) await window.questionExtensions.install(bundle);
          initialized = true;
          for (const value of permissions) window.questionExtensions.updatePermissions(value);
          permissions.length = 0;
          await window.qfEditorShell.updateFromJson(JSON.stringify(message.state));
          if (message.state.editable) {
            await window.extensionEditorFlush();
          }
          send({ kind: "editor-ready" });
          break;
        case "shell":
          if (!["updateFromJson", "errorFromJson"].includes(message.method)) throw new Error("\u672A\u77E5\u7F16\u8F91\u9875\u9762\u6307\u4EE4");
          await window.qfEditorShell[message.method](JSON.stringify(message.value));
          break;
        case "flush":
          window.extensionEditorFlushToHost(message.id);
          break;
        case "development":
          await window.questionExtensions.replaceDevelopment(message.bundle);
          break;
        case "closing":
          closing = message.value;
          document.querySelector("#qf-editor-shell").inert = closing;
          break;
        default:
          throw new Error("\u672A\u77E5\u7F16\u8F91\u5BBF\u4E3B\u6307\u4EE4");
      }
    }).catch((error) => send({ kind: "page-error", message: error.message }));
  });
  window.addEventListener("pagehide", () => {
    for (const pending of calls.values()) {
      clearTimeout(pending.timer);
      pending.reject(new Error("\u7F16\u8F91\u9875\u9762\u5DF2\u5173\u95ED"));
    }
    calls.clear();
  });
  send({ kind: "boot" });

  // src/extensions/rules-runtime.js
  (function installQuestionRules(global) {
    if (global.QuestionRules?.apiMajor === 2) return;
    const clone3 = (value) => JSON.parse(JSON.stringify(value));
    const operations = ["createDraft", "validate", "duplicate", "snapshot", "targets", "validateAnswer", "grade"];
    function createRegistry() {
      const definitions = /* @__PURE__ */ Object.create(null), templates = /* @__PURE__ */ Object.create(null);
      function allocate(source, ids) {
        const question = clone3(source), replacements = /* @__PURE__ */ new Map([[question.id, ids.question]]);
        question.id = ids.question;
        let counter = 0;
        function rekey(value) {
          if (Array.isArray(value)) {
            value.forEach(rekey);
            return;
          }
          if (!value || typeof value !== "object") return;
          if (typeof value.id === "string" && value.id.startsWith("opt_")) {
            const previous = value.id, next = (ids.opts || ids.options || [])[counter] || `opt_${ids.question}_${counter + 1}`;
            counter++;
            replacements.set(previous, next);
            value.id = next;
          }
          Object.values(value).forEach(rekey);
        }
        rekey(question.payload);
        function references(value) {
          if (typeof value === "string") return replacements.get(value) || value;
          if (Array.isArray(value)) return value.map(references);
          if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, references(v)]));
          return value;
        }
        question.answerSpec = references(question.answerSpec);
        return question;
      }
      const registry = {
        apiMajor: 2,
        runtimeSource: installQuestionRules.toString(),
        createRegistry,
        installDefaultQuestion(type, template) {
          const q = typeof template === "string" ? JSON.parse(template) : template;
          if (!q || q.type !== type || !q.id || !q.payload || !q.answerSpec || !q.scoreSpec) throw new TypeError("Invalid full Question default.json");
          templates[type] = clone3(q);
        },
        register(type, definition) {
          if (!type || definitions[type]) throw new TypeError("Duplicate or invalid rules type");
          for (const operation of operations) if (typeof definition[operation] !== "function") throw new TypeError(`Missing rules operation: ${operation}`);
          definitions[type] = Object.freeze(definition);
        },
        defineQuestionType(definition) {
          const type = definition.type;
          if (typeof type !== "string" || typeof definition.grade !== "function") throw new TypeError("Type and grade are required");
          const targets2 = (q) => definition.targets ? definition.targets(q) : [{ id: q.id, number: 1, label: "", locked: false, gradable: true }];
          const maximum2 = (q) => q.scoreSpec?.defaultMaxScore ?? q.maxScore ?? null;
          registry.register(type, {
            createDraft({ ids }) {
              if (!templates[type]) throw new TypeError(`Missing default.json for ${type}`);
              return allocate(templates[type], ids);
            },
            duplicate({ question, ids }) {
              return allocate(question, ids);
            },
            validate({ question }) {
              return { errors: definition.validate?.(clone3(question)) || [] };
            },
            validateAnswer({ question, answer }) {
              const result = definition.validateAnswer?.(clone3(question), clone3(answer || {}));
              return result || { errors: [], empty: !Object.keys(answer || {}).length };
            },
            targets({ question }) {
              return { targets: targets2(question) };
            },
            snapshot({ question }) {
              return { targets: targets2(question), maxScore: maximum2(question) };
            },
            grade(input) {
              const maxScore = input.maxScore ?? maximum2(input.question);
              let reported = null, active2 = true;
              const ctx = Object.freeze({
                question: clone3(input.question),
                answer: clone3(input.answer),
                maxScore,
                reportResult(result) {
                  if (!active2 || reported) throw new TypeError("Result already reported or grading finished");
                  const score = result.score;
                  if (score !== null && (!Number.isFinite(score) || score < 0 || maxScore == null || score > maxScore)) throw new TypeError("Invalid reported score");
                  reported = {
                    status: score == null ? "UNSCORED" : score === maxScore ? "CORRECT" : "INCORRECT",
                    score,
                    maxScore,
                    ...result.feedback ? { feedback: clone3(result.feedback) } : {}
                  };
                  return { ok: true, data: { staged: true } };
                }
              });
              try {
                const reply = definition.grade(ctx);
                if (reply?.then) throw new TypeError("Async grading is not implemented in SDK 2.0");
                if (reply?.ok === false) throw new TypeError(reply.error?.message || "Grading failed");
                if (!reported) throw new TypeError("Grading result missing");
                return reported;
              } finally {
                active2 = false;
              }
            }
          });
        },
        has(type) {
          return Boolean(definitions[type]);
        },
        invoke(type, operation, input) {
          if (!definitions[type] || !operations.includes(operation)) throw new TypeError("Unsupported rules operation");
          const result = definitions[type][operation](typeof input === "string" ? JSON.parse(input) : input);
          if (!result || Array.isArray(result) || typeof result !== "object" || result.then) throw new TypeError("Rules must return a synchronous JSON object");
          return JSON.stringify(result);
        }
      };
      return Object.freeze(registry);
    }
    global.QuestionRules = createRegistry();
    global.QF = Object.freeze({
      defineQuestionType: (definition) => global.QuestionRules.defineQuestionType(definition),
      installDefaultQuestion: (type, template) => global.QuestionRules.installDefaultQuestion(type, template)
    });
  })(typeof window === "undefined" ? globalThis : window);

  // src/extensions/version.js
  function compareVersions(left, right) {
    const split = (value) => {
      const at = value.indexOf("-");
      return at < 0 ? [value, null] : [value.slice(0, at), value.slice(at + 1)];
    };
    const numeric = (a2, b2) => {
      a2 = a2.replace(/^0+(?=\d)/, "");
      b2 = b2.replace(/^0+(?=\d)/, "");
      return a2.length - b2.length || (a2 < b2 ? -1 : a2 > b2 ? 1 : 0);
    };
    const [a, ap] = split(left), [b, bp] = split(right), av = a.split("."), bv = b.split(".");
    for (let i = 0; i < 3; i++) {
      const order = numeric(av[i], bv[i]);
      if (order) return order;
    }
    if (ap === null || bp === null) return ap === bp ? 0 : ap === null ? 1 : -1;
    const ai = ap.split("."), bi = bp.split(".");
    for (let i = 0; i < Math.min(ai.length, bi.length); i++) {
      const an = /^\d+$/.test(ai[i]), bn = /^\d+$/.test(bi[i]);
      const order = an && bn ? numeric(ai[i], bi[i]) : an !== bn ? an ? -1 : 1 : ai[i] < bi[i] ? -1 : ai[i] > bi[i] ? 1 : 0;
      if (order) return order;
    }
    return ai.length - bi.length;
  }

  // src/extensions/compatibility.js
  var hostSdk = Object.freeze({ apiMajor: 2, apiMinor: 1, packageFormatVersion: 2 });
  function requireCompatibleManifest(manifest) {
    if (!manifest || typeof manifest !== "object") throw new TypeError("Missing extension manifest");
    const format = manifest.packageFormatVersion, major = manifest.sdkApiMajor, minor = manifest.minSdkApiMinor ?? 0;
    if (!Number.isInteger(format) || !Number.isInteger(major) || !Number.isInteger(minor) || minor < 0)
      throw new TypeError("packageFormatVersion/sdkApiMajor/minSdkApiMinor must be integers; minSdkApiMinor must be nonnegative");
    if (format !== hostSdk.packageFormatVersion) throw new TypeError(`\u6269\u5C55\u9700\u8981\u683C\u5F0F ${format}\uFF0C\u5F53\u524D\u652F\u6301\u683C\u5F0F 2\uFF1B${format > 2 ? "\u8BF7\u66F4\u65B0\u5E94\u7528" : "\u8BF7\u66F4\u65B0\u6269\u5C55\u4E3A HTML/JSON \u683C\u5F0F 2"}`);
    if (major !== hostSdk.apiMajor) throw new TypeError(`\u6269\u5C55\u9700\u8981 SDK ${major}\uFF0C\u5F53\u524D SDK 2.1\uFF1B${major > 2 ? "\u8BF7\u66F4\u65B0\u5E94\u7528" : "HTML SDK 2 required\uFF0C\u8BF7\u66F4\u65B0\u6269\u5C55"}`);
    if (minor > hostSdk.apiMinor) throw new TypeError(`\u6269\u5C55\u9700\u8981 SDK 2.${minor}\uFF0C\u5F53\u524D SDK 2.1\uFF1B\u8BF7\u66F4\u65B0\u5E94\u7528`);
  }

  // src/shared/renderer/registry.js
  var renderers = /* @__PURE__ */ new Map();
  var extensions = /* @__PURE__ */ new Map();
  var editors = /* @__PURE__ */ new Map();
  var owners = /* @__PURE__ */ new Map();
  var active = /* @__PURE__ */ new Map();
  function registerQuestionExtension(manifest, definitions, editorDefinitions = [], { replace = false } = {}) {
    requireCompatibleManifest(manifest);
    if (typeof manifest.id !== "string" || typeof manifest.version !== "string" || !Array.isArray(manifest.types) || !manifest.types.length)
      throw new TypeError("Unsupported question extension manifest: HTML SDK 2 required");
    const key = `${manifest.id}@${manifest.version}`;
    if (extensions.has(key) && !replace) return false;
    if (replace && !extensions.has(key)) throw new TypeError("Development preview requires an installed extension version");
    if (replace && JSON.stringify(extensions.get(key)) !== JSON.stringify(manifest))
      throw new TypeError("Development preview cannot change the installed manifest");
    const ids = /* @__PURE__ */ new Set();
    for (const type of manifest.types) {
      if (typeof type.id !== "string" || !type.id.trim() || ids.has(type.id) || owners.has(type.id) && owners.get(type.id) !== manifest.id)
        throw new TypeError(`Question type is already registered: ${type.id}`);
      ids.add(type.id);
      const renderer = definitions.find((d) => d.questionType === type.id);
      if (!renderer || typeof renderer.parse !== "function" || typeof renderer.mount !== "function")
        throw new TypeError(`Missing renderer for ${type.id}`);
    }
    if (definitions.some((d) => !ids.has(d.questionType))) throw new TypeError("Undeclared renderer");
    for (const editor of editorDefinitions)
      if (!ids.has(editor.questionType) || typeof editor.mount !== "function") throw new TypeError("Invalid extension editor");
    for (const type of manifest.types) {
      const definition = definitions.find((d) => d.questionType === type.id);
      renderers.set(`${type.id}@${manifest.version}`, Object.freeze({ ...definition, label: type.label, extensionId: manifest.id, extensionVersion: manifest.version }));
      owners.set(type.id, manifest.id);
      if (!active.has(type.id) || compareVersions(manifest.version, active.get(type.id)) > 0) active.set(type.id, manifest.version);
    }
    for (const editor of editorDefinitions) editors.set(`${editor.questionType}@${manifest.version}`, Object.freeze(editor));
    extensions.set(key, JSON.parse(JSON.stringify(manifest)));
    return true;
  }
  var QuestionRendererRegistry = Object.freeze({
    get types() {
      return Object.freeze([...owners.keys()]);
    },
    require(type, version) {
      const renderer = renderers.get(`${type}@${version || active.get(type)}`);
      if (!renderer) throw new TypeError(`\u7F3A\u5C11\u5BF9\u5E94\u9898\u578B\u62D3\u5C55\uFF1A${type}${version ? `\uFF08${version}\uFF09` : ""}\u3002\u8BF7\u5B89\u88C5\u65B0\u7248\u9898\u578B\u62D3\u5C55\u3002`);
      return renderer;
    },
    editor(type, version) {
      this.require(type, version);
      return editors.get(`${type}@${version || active.get(type)}`) ?? null;
    },
    extensions() {
      return [...extensions.values()].map((m) => JSON.parse(JSON.stringify(m)));
    }
  });

  // src/shared/renderer/contract.js
  var RendererMode = Object.freeze({ ACTIVE: "ACTIVE", READ_ONLY_HISTORY: "READ_ONLY_HISTORY" });
  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== void 0) node.textContent = text;
    return node;
  }

  // src/shared/renderer/content.js
  function readContent(value, name) {
    const fail = () => {
      throw new TypeError(`Unsupported content: ${name} is missing structured data`);
    };
    if (value?.kind === "RICH" && Array.isArray(value.document?.blocks)) {
      const plain = (node) => typeof node.text === "string" ? node.text : (node.children || node.blocks || node.items || []).map(plain).join("");
      return { ...value, text: typeof value.text === "string" ? value.text : value.document.blocks.map(plain).join("\n") };
    }
    if (!value || typeof value.text !== "string") fail();
    if (value.kind === "TEXT") return { kind: "TEXT", text: value.text };
    if (value.kind === "DOCUMENT" && Array.isArray(value.document?.data?.main)) return value;
    if (value.kind === "RICH" && Array.isArray(value.document?.blocks)) return value;
    fail();
  }
  function image(src, alt = "") {
    const node = element("img");
    node.alt = alt;
    if (/^data:image\/(png|jpeg|jpg|gif|webp|bmp|svg\+xml);base64,/i.test(src || "")) node.src = src;
    return node;
  }
  function style(node, item, defaults = {}) {
    if (item.font || defaults.defaultFont) node.style.fontFamily = item.font || defaults.defaultFont;
    if (Number.isFinite(item.size || defaults.defaultSize)) node.style.fontSize = `${item.size || defaults.defaultSize}px`;
    if (item.bold) node.style.fontWeight = "bold";
    if (item.italic) node.style.fontStyle = "italic";
    if (item.color) node.style.color = item.color;
    if (item.highlight) node.style.backgroundColor = item.highlight;
    const decoration = [item.underline ? "underline" : "", item.strikeout ? "line-through" : ""].filter(Boolean);
    if (decoration.length) node.style.textDecoration = decoration.join(" ");
  }
  function nativeElements(root2, items, defaults, inline = false) {
    let paragraph = null;
    function line(item = {}) {
      if (inline && paragraph) root2.append(element("br"));
      paragraph = element(inline ? "span" : "div", "document-paragraph");
      root2.append(paragraph);
      const flex = item.rowFlex;
      paragraph.style.textAlign = { alignment: "justify", justify: "justify", center: "center", right: "right", left: "left" }[flex] || "left";
      if (Number.isFinite(item.rowMargin)) paragraph.style.lineHeight = String(1.2 + item.rowMargin * 0.2);
      return paragraph;
    }
    function block(node) {
      root2.append(node);
      paragraph = null;
    }
    for (const item of items) {
      if (item.type === "table") {
        const table = element("table");
        table.style.width = "100%";
        if (item.borderType === "none") table.classList.add("document-borderless");
        const cols = element("colgroup");
        const total = (item.colgroup || []).reduce((n, c) => n + c.width, 0);
        for (const c of item.colgroup || []) {
          const col = element("col");
          if (total) col.style.width = `${c.width / total * 100}%`;
          cols.append(col);
        }
        table.append(cols);
        for (const row of item.trList || []) {
          const tr = element("tr");
          for (const cell of row.tdList || []) {
            const td = element("td");
            td.colSpan = cell.colspan || 1;
            td.rowSpan = cell.rowspan || 1;
            td.style.verticalAlign = cell.verticalAlign || "top";
            if (cell.backgroundColor) td.style.backgroundColor = cell.backgroundColor;
            nativeElements(td, cell.value || [], defaults);
            tr.append(td);
          }
          table.append(tr);
        }
        block(table);
        continue;
      }
      if (item.type === "title") {
        const level = { first: 1, second: 2, third: 3, fourth: 4, fifth: 5, sixth: 6 }[item.level] || 3;
        const heading = element(`h${level}`);
        nativeElements(heading, item.valueList || [], defaults);
        block(heading);
        continue;
      }
      if (item.type === "list") {
        const list = element(item.listType === "ol" ? "ol" : "ul");
        let parts2 = [];
        for (const part of item.valueList || []) {
          if ((part.value === "\n" || part.listWrap) && parts2.length) {
            const li = element("li");
            nativeElements(li, parts2, defaults);
            list.append(li);
            parts2 = [];
          }
          parts2.push(part);
        }
        if (parts2.length) {
          const li = element("li");
          nativeElements(li, parts2, defaults);
          list.append(li);
        }
        block(list);
        continue;
      }
      if (item.type === "separator") {
        block(element("hr"));
        continue;
      }
      if (item.type === "pageBreak") continue;
      if (item.type === "image") {
        const img = image(item.value, item.alt);
        if (item.width) img.style.width = `${item.width}px`;
        (paragraph || line(item)).append(img);
        continue;
      }
      if (item.type === "hyperlink" || item.type === "control" || item.type === "date") {
        const span = element("span");
        style(span, item, defaults);
        nativeElements(span, item.valueList || item.control?.value || [], defaults, true);
        (paragraph || line(item)).append(span);
        continue;
      }
      const parts = String(item.value || "").replace(/\u200b/g, "").split("\n");
      parts.forEach((part, n) => {
        if (n) line(item);
        if (!part) return;
        const container = paragraph || line(item);
        if (item.rowFlex) container.style.textAlign = { alignment: "justify", justify: "justify", center: "center", right: "right", left: "left" }[item.rowFlex] || "left";
        const span = element(item.type === "superscript" ? "sup" : item.type === "subscript" ? "sub" : "span", "", part);
        style(span, item, defaults);
        container.append(span);
      });
    }
  }
  function richBlocks(root2, blocks, images) {
    for (const block of blocks) {
      let node;
      if (block.type === "IMAGE") {
        node = element("figure");
        const img = image(images[block.resourceId], block.alt);
        if (block.widthPercent) img.style.width = `${block.widthPercent}%`;
        node.append(img);
        if (block.caption) node.append(element("figcaption", "", block.caption));
      } else if (block.type === "MATH") node = element("p", "document-math", block.tex);
      else if (block.type === "BLOCK_QUOTE") {
        node = element("blockquote");
        richBlocks(node, block.blocks, images);
      } else if (block.type === "BULLET_LIST" || block.type === "ORDERED_LIST") {
        node = element(block.type === "BULLET_LIST" ? "ul" : "ol");
        if (block.start) node.start = block.start;
        for (const item of block.items) {
          const li = element("li");
          richBlocks(li, item.blocks, images);
          node.append(li);
        }
      } else {
        node = element(block.type === "HEADING" ? `h${block.level}` : "p");
        for (const item of block.children || []) richInline(node, item, images);
      }
      if (block.alignment) node.style.textAlign = { JUSTIFY: "justify", CENTER: "center", RIGHT: "right", LEFT: "left" }[block.alignment] || "left";
      root2.append(node);
    }
  }
  function richInline(root2, item, images) {
    if (item.type === "IMAGE") {
      root2.append(image(images[item.resourceId], item.alt));
      return;
    }
    if (item.type === "LINE_BREAK") {
      root2.append(element("br"));
      return;
    }
    if (item.type === "LINK") {
      for (const child of item.children) richInline(root2, child, images);
      return;
    }
    const span = element("span", "", item.type === "MATH" ? item.tex : item.text);
    style(span, { bold: item.marks?.includes("BOLD"), italic: item.marks?.includes("ITALIC"), underline: item.marks?.includes("UNDERLINE"), strikeout: item.marks?.includes("STRIKE") });
    root2.append(span);
  }
  function renderContent(root2, content) {
    root2.classList.add("shared-content");
    if (content.kind === "TEXT") {
      root2.textContent = content.text;
      return root2;
    }
    root2.classList.add("document-content");
    if (content.kind === "DOCUMENT") nativeElements(root2, content.document.data.main, content.document.options || {});
    else richBlocks(root2, content.document.blocks, content.images || {});
    return root2;
  }
  function isolatedContentSource() {
    return [element, image, style, nativeElements, richBlocks, richInline, renderContent].map((fn) => fn.toString()).join("\n");
  }

  // src/shared/ui/preferences.js
  var fields = Object.freeze({
    EDITOR: ["title", "save", "position", "typeLabel", "add", "duplicate", "delete", "sources", "outline", "errors"],
    PRACTICE: ["card", "typeLabel", "position", "score", "state", "submit", "retry", "confirmation", "note", "sources", "outline", "draftToggle", "draftToolbar", "draftZoom", "errors"]
  });
  function defaultUi(mode) {
    return Object.freeze(Object.fromEntries(fields[mode === "EDITOR" ? "EDITOR" : "PRACTICE"].map((name) => [name, true])));
  }
  function configureUi(current, patch, mode) {
    if (!patch || typeof patch !== "object" || Array.isArray(patch)) throw new TypeError("\u516C\u5171 UI \u914D\u7F6E\u5FC5\u987B\u662F\u5BF9\u8C61");
    const allowed = fields[mode === "EDITOR" ? "EDITOR" : "PRACTICE"];
    for (const [name, value] of Object.entries(patch)) {
      if (!allowed.includes(name)) throw new TypeError(name === "navigation" ? "\u4E0A\u4E00\u9898\u3001\u4E0B\u4E00\u9898\u7531\u5BBF\u4E3B\u4FDD\u7559\uFF0C\u4E0D\u80FD\u5173\u95ED" : `\u672A\u77E5\u516C\u5171 UI \u9009\u9879\uFF1A${name}`);
      if (typeof value !== "boolean") throw new TypeError(`\u516C\u5171 UI \u9009\u9879 ${name} \u5FC5\u987B\u4E3A\u5E03\u5C14\u503C`);
    }
    return Object.freeze({ ...defaultUi(mode), ...current, ...patch });
  }
  function isolatedUiSource() {
    return `const fields=${JSON.stringify(fields)};
${defaultUi.toString()}
${configureUi.toString()}`;
  }

  // src/shared/ui/layout.js
  var keys = ["cardWidth", "maxCardWidth", "horizontalAlign", "verticalAlign", "padding"];
  function defaultLayout(mode = "PRACTICE") {
    return Object.freeze({ cardWidth: 720, maxCardWidth: "100%", horizontalAlign: "center", verticalAlign: mode === "EDITOR" ? "top" : "center", padding: 20 });
  }
  function configureLayout(current, patch) {
    if (!patch || typeof patch !== "object" || Array.isArray(patch)) throw new TypeError("\u5E03\u5C40\u914D\u7F6E\u5FC5\u987B\u4E3A\u5BF9\u8C61");
    if (Object.keys(patch).some((key) => !keys.includes(key))) throw new TypeError("\u672A\u77E5\u5E03\u5C40\u5B57\u6BB5");
    const next = { ...current, ...patch };
    if (!Number.isFinite(next.cardWidth) || next.cardWidth < 64 || next.cardWidth > 8192) throw new TypeError("cardWidth \u5FC5\u987B\u4E3A 64\u20138192 \u7684\u6570\u503C");
    if (typeof next.maxCardWidth === "number") {
      if (!Number.isFinite(next.maxCardWidth) || next.maxCardWidth < 64 || next.maxCardWidth > 8192) throw new TypeError("maxCardWidth \u6570\u503C\u5FC5\u987B\u4E3A 64\u20138192");
    } else if (typeof next.maxCardWidth !== "string" || !/^(?:\d+(?:\.\d+)?)%$/.test(next.maxCardWidth) || parseFloat(next.maxCardWidth) <= 0 || parseFloat(next.maxCardWidth) > 100) {
      throw new TypeError("maxCardWidth \u5FC5\u987B\u4E3A\u6570\u503C\u6216\u5927\u4E8E 0\u3001\u4E0D\u8D85\u8FC7 100% \u7684\u767E\u5206\u6BD4");
    }
    if (!["left", "center", "right"].includes(next.horizontalAlign) || !["top", "center", "bottom"].includes(next.verticalAlign)) throw new TypeError("\u4E0D\u652F\u6301\u7684\u5BF9\u9F50\u65B9\u5F0F");
    if (!Number.isFinite(next.padding) || next.padding < 0 || next.padding > 256) throw new TypeError("padding \u5FC5\u987B\u4E3A 0\u2013256 \u7684\u6570\u503C");
    return Object.freeze(next);
  }
  function isolatedLayoutSource() {
    return `const keys=${JSON.stringify(keys)};
${configureLayout.toString()}`;
  }
  function availableCardWidth(layout, width) {
    const available = Math.max(1, width - 2 * layout.padding);
    return Math.min(available, typeof layout.maxCardWidth === "number" ? layout.maxCardWidth : available * parseFloat(layout.maxCardWidth) / 100);
  }
  function initialCardWidth(layout, width) {
    return Math.max(1, Math.min(layout.cardWidth, availableCardWidth(layout, width)));
  }
  function alignedOffset(align, available, content, padding) {
    return Math.max(padding, align === "right" || align === "bottom" ? available - padding - content : align === "center" ? (available - content) / 2 : padding);
  }
  function createDomLayout(target2, { mode = "PRACTICE", getHeight = () => window.innerHeight } = {}) {
    let layout = defaultLayout(mode), destroyed = false;
    const properties = ["width", "max-width", "box-sizing", "margin-left", "margin-right", "margin-top", "margin-bottom"];
    const original = new Map(properties.map((key) => [key, [target2.style.getPropertyValue(key), target2.style.getPropertyPriority(key)]]));
    const set = (key, value) => {
      if (target2.style.getPropertyValue(key) !== value) target2.style.setProperty(key, value);
    };
    function render() {
      if (destroyed) return;
      const width = target2.parentElement?.clientWidth || window.innerWidth || layout.cardWidth;
      const cardWidth = initialCardWidth(layout, width);
      set("box-sizing", "border-box");
      set("width", cardWidth + "px");
      set("max-width", "100%");
      const left = alignedOffset(layout.horizontalAlign, width, cardWidth, layout.padding);
      set("margin-left", left + "px");
      set("margin-right", "0px");
      set("margin-top", alignedOffset(layout.verticalAlign, getHeight(), target2.offsetHeight, layout.padding) + "px");
      set("margin-bottom", layout.padding + "px");
    }
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(render);
    if (target2.parentElement) observer?.observe(target2.parentElement);
    observer?.observe(target2);
    window.addEventListener("resize", render);
    return Object.freeze({
      configure(next) {
        layout = next;
        render();
      },
      getState() {
        return { configuration: { ...layout }, cardWidth: target2.getBoundingClientRect().width, hasSavedGeometry: false };
      },
      destroy() {
        destroyed = true;
        observer?.disconnect();
        window.removeEventListener("resize", render);
        original.forEach(([value, priority], key) => value ? target2.style.setProperty(key, value, priority) : target2.style.removeProperty(key));
      }
    });
  }

  // src/extensions/frame-client.js
  function startFrameClient(boot, renderContent2, configureLayout2, configureUi2, validateArguments2) {
    const root2 = document.querySelector(".qf-extension-page");
    const pending = /* @__PURE__ */ new Set(), writes = /* @__PURE__ */ new Set(), requests2 = /* @__PURE__ */ new Map(), singleFlights = /* @__PURE__ */ new Map(), subscriptions = /* @__PURE__ */ new Set(), controls = /* @__PURE__ */ new Set(), disposers = /* @__PURE__ */ new Set();
    let sequence3 = 0, lastError = null, closed = false, ready, layout = boot.layout, ui = boot.ui, layoutState = boot.layoutState, interaction = "INTERACT";
    const clone3 = (value) => value == null ? value : JSON.parse(JSON.stringify(value));
    const ok2 = (data) => ({ ok: true, data: clone3(data) });
    const fail = (code, message) => ({ ok: false, error: { code, message, retryable: false } });
    const send2 = (value) => parent.postMessage({ ...value, channel: "qf-type-frame", session: boot.session }, "*");
    if (boot.nativePage) {
      let popup, selected, padding;
      const close = () => {
        if (popup) {
          root2.style.paddingBottom = padding;
          popup.remove();
        }
        popup = null;
        selected = null;
      };
      const open = (select) => {
        if (select === selected) {
          close();
          return;
        }
        close();
        selected = select;
        const r = select.getBoundingClientRect();
        popup = document.createElement("div");
        popup.setAttribute("role", "listbox");
        popup.style.cssText = `position:absolute;z-index:2147483647;left:${r.left}px;top:${r.bottom}px;min-width:${r.width}px;max-height:240px;overflow:auto;background:white;color:#282432;border:1px solid #ded8e8;border-radius:6px;box-shadow:0 4px 16px #0002;padding:4px;`;
        for (const option of select.options) {
          const button = document.createElement("button");
          button.type = "button";
          button.textContent = option.textContent;
          button.disabled = option.disabled;
          button.setAttribute("role", "option");
          button.setAttribute("aria-selected", String(option.selected));
          button.style.cssText = "display:block;width:100%;text-align:left;border:0;padding:6px 10px;background:" + (option.selected ? "#eee8f8" : "transparent");
          button.addEventListener("click", () => {
            select.value = option.value;
            close();
            select.dispatchEvent(new Event("input", { bubbles: true }));
            select.dispatchEvent(new Event("change", { bubbles: true }));
          });
          popup.append(button);
        }
        padding = root2.style.paddingBottom;
        root2.style.paddingBottom = (parseFloat(getComputedStyle(root2).paddingBottom) || 0) + 240 + "px";
        root2.append(popup);
      };
      document.addEventListener("pointerdown", (event) => {
        const select = event.target.closest("select");
        if (select && !select.disabled && !select.multiple && select.size <= 1) {
          event.preventDefault();
          select.focus();
          open(select);
        } else if (popup && !popup.contains(event.target)) close();
      }, true);
      document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") close();
        if (event.key === "Enter" && event.target.matches("select:not([multiple])")) {
          event.preventDefault();
          open(event.target);
        }
      }, true);
    }
    function notify(message) {
      let node = root2.querySelector("[data-qf-error]");
      if (!node) {
        node = document.createElement("p");
        node.dataset.qfError = "";
        node.setAttribute("role", "alert");
        root2.append(node);
      }
      node.textContent = String(message || "");
      node.hidden = !message;
    }
    function track(value) {
      const promise = Promise.resolve(value);
      pending.add(promise);
      promise.catch((error) => {
        lastError = error;
        notify(error.message);
      }).finally(() => pending.delete(promise));
      return promise;
    }
    function request(method, args = []) {
      if (closed) return Promise.resolve(fail("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED"));
      try {
        args = validateArguments2(args);
      } catch (error) {
        return Promise.resolve(fail("INVALID_ARGUMENT", error.message));
      }
      const id = String(++sequence3);
      return new Promise((resolve) => {
        const timeout = setTimeout(() => {
          requests2.delete(id);
          resolve(fail("SDK_TIMEOUT", "\u9898\u578B\u63A5\u53E3\u8BF7\u6C42\u8D85\u65F6\uFF1A" + method));
        }, boot.nativePage ? 6e4 : 3e4);
        requests2.set(id, { resolve, timeout });
        try {
          send2({ kind: "request", id, method, args });
        } catch (error) {
          clearTimeout(timeout);
          requests2.delete(id);
          resolve(fail("INVALID_ARGUMENT", error.message));
        }
      });
    }
    const barriers = /* @__PURE__ */ new Set(["editor.save", "bank.save", "bank.addQuestion", "bank.duplicateQuestion", "bank.deleteQuestion", "navigation.goTo", "navigation.previous", "navigation.next", "practice.submit", "practice.retry", "answer.flush"]);
    const reads = /* @__PURE__ */ new Set(["editor.getData", "answer.get", "practice.getState", "practice.getResult"]);
    const remote = (method, tracked = false) => (...args) => {
      let key;
      try {
        args = validateArguments2(args);
        key = method + JSON.stringify(args);
      } catch (error) {
        return Promise.resolve(fail("INVALID_ARGUMENT", error.message));
      }
      if (barriers.has(method) && singleFlights.has(key)) return singleFlights.get(key);
      const value = barriers.has(method) || reads.has(method) && writes.size ? Promise.all([...writes]).then((replies) => barriers.has(method) && replies.find((reply) => !reply.ok) || request(method, args)) : request(method, args);
      if (tracked) {
        writes.add(value);
        value.finally(() => writes.delete(value));
        track(value);
      }
      if (barriers.has(method)) {
        singleFlights.set(key, value);
        value.finally(() => singleFlights.delete(key));
      }
      return value;
    };
    function own2(node) {
      if (!root2.contains(node)) throw new TypeError("\u8282\u70B9\u5FC5\u987B\u5C5E\u4E8E\u5F53\u524D\u9898\u578B\u9875\u9762");
      return node;
    }
    function on(node, event, listener) {
      own2(node).addEventListener(event, listener);
      return () => node.removeEventListener(event, listener);
    }
    async function flush() {
      await ready;
      while (pending.size) await Promise.all([...pending]);
      if (lastError) throw lastError;
    }
    function configure(kind, patch) {
      if (closed) return fail("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
      try {
        const next = kind === "layout" ? configureLayout2(layout, patch) : configureUi2(ui, patch, boot.mode);
        if (kind === "layout") layout = next;
        else ui = next;
        track(request(kind + ".configure", [patch]).then((reply) => {
          if (!reply.ok) throw new Error(reply.error.message);
          if (kind === "layout") {
            layout = reply.data;
            layoutState.configuration = clone3(layout);
          } else ui = reply.data;
        }));
        return ok2(next);
      } catch (error) {
        return fail(kind === "layout" ? "INVALID_LAYOUT" : "INVALID_UI", error.message);
      }
    }
    const QF = Object.freeze({
      dom: Object.freeze({ root: root2, $: (selector) => root2.querySelector(selector), on }),
      host: Object.freeze({ getContext: remote("host.getContext"), subscribe(listener) {
        subscriptions.add(listener);
        return () => subscriptions.delete(listener);
      } }),
      ids: Object.freeze({ create(prefix = "opt_") {
        const bytes = new Uint32Array(4);
        crypto.getRandomValues(bytes);
        return prefix + Array.from(bytes, (n) => n.toString(16).padStart(8, "0")).join("");
      } }),
      editor: Object.freeze({ getData: remote("editor.getData"), update: remote("editor.update", true), save: remote("editor.save") }),
      bank: Object.freeze({ getState: remote("bank.getState"), save: remote("bank.save"), addQuestion: remote("bank.addQuestion"), duplicateQuestion: remote("bank.duplicateQuestion"), deleteQuestion: remote("bank.deleteQuestion") }),
      navigation: Object.freeze({ getState: remote("navigation.getState"), goTo: remote("navigation.goTo"), previous: remote("navigation.previous"), next: remote("navigation.next") }),
      sources: Object.freeze({ list: remote("sources.list"), add: remote("sources.add"), remove: remote("sources.remove"), open: remote("sources.open") }),
      learning: Object.freeze({ getMode: remote("learning.getMode"), setMode: remote("learning.setMode"), toggleMode: remote("learning.toggleMode") }),
      whiteboard: Object.freeze({ getState: remote("whiteboard.getState"), setTool: remote("whiteboard.setTool"), undo: remote("whiteboard.undo"), redo: remote("whiteboard.redo"), clear: remote("whiteboard.clear"), setAppearance: remote("whiteboard.setAppearance"), setZoom: remote("whiteboard.setZoom"), zoomBy: remote("whiteboard.zoomBy") }),
      practice: Object.freeze({ getState: remote("practice.getState"), getQuestion: remote("practice.getQuestion"), getResult: remote("practice.getResult"), submit: remote("practice.submit"), retry: remote("practice.retry") }),
      answer: Object.freeze({ get: remote("answer.get"), update: remote("answer.update", true), flush: remote("answer.flush") }),
      content: Object.freeze({
        render(node, content) {
          own2(node);
          const revision = String(++sequence3);
          node.dataset.qfContentRevision = revision;
          track(request("content.resolve", [content]).then((reply) => {
            if (!reply.ok) throw new Error(reply.error.message);
            if (node.dataset.qfContentRevision === revision) {
              node.replaceChildren();
              renderContent2(node, reply.data);
            }
          }));
          return node;
        },
        mountEditor(node, { value, onChange, formatting = true }) {
          own2(node);
          if (boot.mode !== "EDITOR") throw new TypeError("\u5BCC\u6587\u672C\u7F16\u8F91\u5668\u53EA\u80FD\u7528\u4E8E\u7F16\u8F91\u6A21\u5F0F");
          let content = clone3(value || { kind: "TEXT", text: "" }), disposed = false, canEdit = false, permissionRevision = 0, removeInput, button, removeButton;
          const body = document.createElement("div");
          body.className = "qf-rich-editor-body";
          node.append(body);
          function repaint() {
            removeInput?.();
            body.replaceChildren();
            if (content.kind === "TEXT") {
              const input = document.createElement("textarea");
              input.rows = 4;
              input.value = content.text || "";
              input.disabled = !canEdit;
              body.append(input);
              removeInput = on(input, "input", () => {
                if (disposed || !canEdit) return;
                content = { kind: "TEXT", text: input.value };
                track(onChange(clone3(content)));
              });
            } else QF.content.render(body, content);
          }
          async function refreshPermission() {
            const revision = ++permissionRevision, reply = await QF.host.getContext();
            if (disposed || closed || revision !== permissionRevision) return;
            canEdit = reply.ok && reply.data.capabilities.editQuestion;
            const input = body.querySelector("textarea");
            if (input) input.disabled = !canEdit;
            if (button) button.disabled = !canEdit;
          }
          if (formatting) {
            button = document.createElement("button");
            button.type = "button";
            button.className = "extension-editor-action";
            button.textContent = "\u7F16\u8F91\u683C\u5F0F";
            button.disabled = true;
            node.append(button);
            removeButton = on(button, "click", () => {
              if (disposed || !canEdit) return;
              button.disabled = true;
              track(request("content.edit", [content]).then((reply) => {
                if (disposed || closed) return;
                if (!reply.ok) {
                  notify(reply.error.message);
                  return;
                }
                if (reply.data) {
                  content = clone3(reply.data);
                  repaint();
                  return onChange(clone3(content));
                }
              }).finally(() => {
                if (!disposed && !closed) button.disabled = !canEdit;
              }));
            });
          }
          const unsubscribe = QF.host.subscribe(refreshPermission);
          function destroy() {
            if (disposed) return;
            disposed = true;
            unsubscribe();
            removeInput?.();
            removeButton?.();
            body.dataset.qfContentRevision = "disposed";
            body.remove();
            button?.remove();
            disposers.delete(destroy);
          }
          disposers.add(destroy);
          repaint();
          track(refreshPermission());
          return { getValue: () => clone3(content), destroy };
        }
      }),
      ui: Object.freeze({ configure: (patch) => configure("ui", patch), getConfiguration: () => ok2(ui), notify, mountControls(node) {
        own2(node);
        node.dataset.qfHostControls = "";
        controls.add(node);
        measure();
      }, mountActions(node) {
        own2(node).dataset.qfActions = "";
      } }),
      layout: Object.freeze({ configure: (patch) => configure("layout", patch), getConfiguration: () => ok2(layout), getState: () => ok2(layoutState) })
    });
    document.addEventListener("submit", (event) => event.preventDefault());
    document.addEventListener("keydown", (event) => {
      if (boot.mode === "EDITOR" && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        QF.bank.save();
      }
    });
    let measuredHeight = -1, lastControls = "";
    function measure() {
      if (closed) return;
      const height = Math.ceil(root2.getBoundingClientRect().height);
      const regions = Array.from(controls).filter((node) => node.isConnected && !node.hidden).map((node) => {
        const r = node.getBoundingClientRect();
        return { x: r.left, y: r.top, width: r.width, height: r.height };
      });
      const encoded = JSON.stringify(regions);
      if (height !== measuredHeight || encoded !== lastControls) {
        measuredHeight = height;
        lastControls = encoded;
        send2({ kind: "geometry", height, controls: regions });
      }
    }
    const resize2 = new ResizeObserver(measure);
    resize2.observe(root2);
    const mutations2 = new MutationObserver(measure);
    mutations2.observe(root2, { subtree: true, childList: true, attributes: true, characterData: true });
    if (boot.relayWheel) document.addEventListener("wheel", (event) => {
      event.preventDefault();
      send2({ kind: "wheel", x: event.clientX, y: event.clientY, deltaX: event.deltaX, deltaY: event.deltaY, deltaMode: event.deltaMode, ctrlKey: event.ctrlKey });
    }, { passive: false });
    window.addEventListener("message", (event) => {
      const message = event.data;
      if (event.source !== parent || message?.channel !== "qf-type-frame" || message.session !== boot.session) return;
      if (message.kind === "reply") {
        const entry = requests2.get(message.id);
        if (entry) {
          clearTimeout(entry.timeout);
          requests2.delete(message.id);
          if (message.layoutState) layoutState = message.layoutState;
          const reply = message.reply;
          entry.resolve(reply && typeof reply.ok === "boolean" && (reply.ok || typeof reply.error?.code === "string") ? reply : fail("INVALID_REPLY", "\u5BBF\u4E3B\u8FD4\u56DE\u683C\u5F0F\u65E0\u6548"));
        }
        setTimeout(() => send2({ kind: "reply-ack", id: message.id }), 0);
        return;
      }
      if (message.kind === "closing") {
        closed = true;
        for (const entry of requests2.values()) {
          clearTimeout(entry.timeout);
          entry.resolve(fail("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED"));
        }
        requests2.clear();
        setTimeout(() => send2({ kind: "close-ack" }), 0);
        return;
      }
      if (message.kind === "state") {
        layoutState = message.layoutState || layoutState;
        interaction = message.interaction;
        if (interaction !== "INTERACT") document.activeElement?.blur();
        for (const listener of subscriptions) track(Promise.resolve().then(listener));
        return;
      }
      if (message.kind === "blur") {
        document.activeElement?.blur();
        return;
      }
      if (message.kind === "flush") {
        flush().then(() => {
          measure();
          send2({ kind: "flushed", id: message.id });
        }, (error) => send2({ kind: "flushed", id: message.id, error: error.message }));
        return;
      }
      if (message.kind === "control-click" && interaction !== "INTERACT") {
        const target2 = document.elementFromPoint(message.x, message.y);
        if (target2 && Array.from(controls).some((node) => node.contains(target2))) target2.closest("button,input,select")?.click();
        return;
      }
      if (message.kind === "focus") {
        const target2 = message.id === boot.questionId ? root2 : root2.querySelector('[data-target-id="' + CSS.escape(message.id) + '"]');
        target2?.scrollIntoView({ block: "nearest" });
        return;
      }
    });
    window.addEventListener("pagehide", () => {
      closed = true;
      resize2.disconnect();
      mutations2.disconnect();
      for (const dispose of disposers) dispose();
      subscriptions.clear();
      for (const entry of requests2.values()) {
        clearTimeout(entry.timeout);
        entry.resolve(fail("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED"));
      }
      requests2.clear();
    });
    Object.defineProperty(window, "QF", { value: QF, writable: false, configurable: false });
    let reportedFailure = false;
    const reportFailure = (error) => {
      if (closed || reportedFailure) return;
      reportedFailure = true;
      send2({ kind: "failed", error: String(error?.message || error || "\u9898\u578B\u9875\u9762\u6267\u884C\u5931\u8D25").slice(0, 1024) });
    };
    window.addEventListener("error", (event) => reportFailure(event.error || event.message));
    window.addEventListener("unhandledrejection", (event) => {
      event.preventDefault();
      reportFailure(event.reason);
    });
    window.__qfStart = (promise) => {
      ready = track(promise);
      ready.then(async () => {
        try {
          await flush();
          measure();
          root2.dataset.qfReady = "true";
          send2({ kind: "ready" });
        } catch (error) {
          send2({ kind: "failed", error: error.message });
        }
      }, (error) => send2({ kind: "failed", error: error.message }));
    };
    measure();
  }

  // src/extensions/protocol.js
  function validateArguments(args) {
    if (!Array.isArray(args) || args.length > 4) throw new TypeError("\u63A5\u53E3\u53C2\u6570\u5FC5\u987B\u662F\u6700\u591A\u56DB\u9879\u7684\u6570\u7EC4");
    const ancestors = /* @__PURE__ */ new Set();
    function visit(value, depth) {
      if (depth > 32) throw new TypeError("\u63A5\u53E3\u53C2\u6570\u5D4C\u5957\u8FC7\u6DF1");
      if (value === null || typeof value === "string" || typeof value === "boolean") return;
      if (typeof value === "number" && Number.isFinite(value)) return;
      if (typeof value !== "object" || ancestors.has(value)) throw new TypeError("\u63A5\u53E3\u53EA\u63A5\u53D7\u6709\u9650\u6570\u503C\u53CA JSON \u6570\u636E");
      if (!Array.isArray(value) && Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null)
        throw new TypeError("\u63A5\u53E3\u53EA\u63A5\u53D7\u666E\u901A JSON \u5BF9\u8C61");
      ancestors.add(value);
      for (const item of Object.values(value)) visit(item, depth + 1);
      ancestors.delete(value);
    }
    visit(args, 0);
    const encoded = JSON.stringify(args);
    if (encoded.length > 128 * 1024 * 1024) throw new TypeError("\u63A5\u53E3\u53C2\u6570\u8FC7\u5927");
    return JSON.parse(encoded);
  }
  function validateMethodArguments(method, args) {
    const objects = /* @__PURE__ */ new Set(["editor.update", "answer.update", "content.resolve", "content.edit", "ui.configure", "layout.configure", "whiteboard.setAppearance"]);
    const strings = /* @__PURE__ */ new Set(["bank.addQuestion", "sources.add", "learning.setMode", "whiteboard.setTool"]);
    const indices = /* @__PURE__ */ new Set(["navigation.goTo", "sources.remove", "sources.open"]);
    const numbers = /* @__PURE__ */ new Set(["whiteboard.setZoom", "whiteboard.zoomBy"]);
    const single = objects.has(method) || strings.has(method) || indices.has(method) || numbers.has(method);
    if (args.length !== (single ? 1 : 0)) throw new TypeError("\u63A5\u53E3\u53C2\u6570\u6570\u91CF\u4E0D\u5339\u914D\uFF1A" + method);
    const value = args[0];
    if (objects.has(method) && (!value || typeof value !== "object" || Array.isArray(value))) throw new TypeError("\u63A5\u53E3\u9700\u8981 JSON \u5BF9\u8C61\uFF1A" + method);
    if (strings.has(method) && (typeof value !== "string" || !value.trim())) throw new TypeError("\u63A5\u53E3\u9700\u8981\u975E\u7A7A\u6587\u672C\uFF1A" + method);
    if (indices.has(method) && (!Number.isSafeInteger(value) || value < 0)) throw new TypeError("\u7D22\u5F15\u5FC5\u987B\u4E3A\u975E\u8D1F\u6574\u6570");
    if (numbers.has(method) && (!Number.isFinite(value) || value <= 0)) throw new TypeError("\u7F29\u653E\u53C2\u6570\u5FC5\u987B\u4E3A\u6B63\u6570");
  }
  function replaceChildrenRetainingFrames(root2, ...nodes) {
    for (const child of [...root2.childNodes]) {
      if (child.nodeType === 1 && (child.matches(".qf-frame-retiring") || child.querySelector(".qf-frame-retiring"))) {
        child.dataset.qfRetiredRoot = "";
        child.style.cssText = "position:absolute!important;visibility:hidden!important;pointer-events:none!important;";
        child.removeAttribute("id");
        child.querySelectorAll("[id]").forEach((node) => node.removeAttribute("id"));
      } else child.remove();
    }
    root2.append(...nodes);
  }

  // src/extensions/native-page.js
  var sessions = /* @__PURE__ */ new Map();
  function endpoint() {
    if (!window.qfNativePages) window.qfNativePages = Object.freeze({ receiveFromJson(encoded) {
      const { session, event } = JSON.parse(encoded);
      sessions.get(session)?.receive(event);
    } });
  }
  function nativePage(boot, documentSource, onMessage, onFailure) {
    const host2 = window.nativePagesHost;
    endpoint();
    const frame2 = document.createElement("div"), image2 = document.createElement("img"), input = document.createElement("textarea");
    frame2.dataset.qfRemotePage = boot.session;
    frame2.setAttribute("role", "group");
    frame2.style.cssText = "position:relative;overflow:hidden;width:100%;height:1px;";
    image2.draggable = false;
    image2.alt = "";
    image2.style.cssText = "position:absolute;left:0;top:0;width:100%;pointer-events:none;user-select:none;";
    input.setAttribute("aria-label", "\u9898\u5361\u6587\u5B57\u8F93\u5165");
    input.setAttribute("autocomplete", "off");
    input.setAttribute("autocapitalize", "off");
    input.spellcheck = false;
    input.style.cssText = "position:absolute;left:0;top:0;width:1px;height:1px;opacity:0;padding:0;border:0;resize:none;";
    frame2.append(image2, input);
    let closed = false, started = false, lastViewport = "", composing = false, interaction = "INTERACT";
    const port = { postMessage(message) {
      if (!closed) host2.send(boot.session, JSON.stringify(message));
    } };
    frame2.contentWindow = port;
    frame2.srcdoc = documentSource;
    const sendInput = (event) => {
      if (!closed && started) host2.input(boot.session, JSON.stringify(event));
    };
    function viewport() {
      if (closed || !started || !frame2.isConnected) return;
      const r = frame2.getBoundingClientRect(), scale = r.width / frame2.clientWidth || 1;
      const bounds = JSON.parse(host2.visibleBounds()), top = Math.max(0, bounds.top - r.top);
      const width = Math.max(1, Math.min(2048, Math.round(frame2.clientWidth)));
      const offset = Math.max(0, Math.min(2e5, Math.floor(top / scale)));
      const height = Math.max(1, Math.min(1536, Math.ceil(Math.min(frame2.clientHeight - offset, (bounds.height + 200) / scale))));
      const value = [width, height, offset].join(":");
      if (value !== lastViewport) {
        lastViewport = value;
        host2.viewport(boot.session, width, height, offset);
      }
    }
    const modifiers = (e) => ({ shiftKey: e.shiftKey, ctrlKey: e.ctrlKey, altKey: e.altKey, metaKey: e.metaKey });
    for (const type of ["pointerdown", "pointermove", "pointerup", "pointercancel"]) frame2.addEventListener(type, (e) => {
      if (interaction !== "INTERACT" || e.target === input) return;
      if (type === "pointerdown") {
        viewport();
        input.focus({ preventScroll: true });
        frame2.setPointerCapture?.(e.pointerId);
      }
      const r = frame2.getBoundingClientRect();
      sendInput({ type: type === "pointermove" && e.buttons ? "pointerdrag" : type === "pointercancel" ? "pointerup" : type, x: (e.clientX - r.left) * frame2.clientWidth / r.width, y: (e.clientY - r.top) * frame2.clientHeight / r.height, button: e.button, buttons: e.buttons, clickCount: e.detail || 1, ...modifiers(e) });
      if (type === "pointerup" || type === "pointercancel") frame2.releasePointerCapture?.(e.pointerId);
      e.preventDefault();
      e.stopPropagation();
    });
    for (const type of ["keydown", "keyup"]) input.addEventListener(type, (e) => {
      if (e.isComposing || composing) return;
      if ((e.ctrlKey || e.metaKey) && e.code === "KeyV") return;
      sendInput({ type, key: e.key, code: e.code, ...modifiers(e) });
      if (e.key.length !== 1 && !["Shift", "Control", "Alt", "Meta"].includes(e.key) || (e.ctrlKey || e.metaKey) && ["KeyA", "KeyC", "KeyX"].includes(e.code)) e.preventDefault();
      e.stopPropagation();
    });
    input.addEventListener("compositionstart", () => {
      composing = true;
    });
    input.addEventListener("compositionend", () => {
      composing = false;
      if (input.value) {
        sendInput({ type: "text", text: input.value });
        input.value = "";
      }
    });
    input.addEventListener("input", () => {
      if (!composing && input.value) {
        sendInput({ type: "text", text: input.value });
        input.value = "";
      }
    });
    input.addEventListener("blur", () => sendInput({ type: "blur", session: boot.session }));
    frame2.addEventListener("wheel", (e) => {
      if (!boot.relayWheel) return;
      e.preventDefault();
      const r = frame2.getBoundingClientRect();
      onMessage({ source: port, data: { kind: "wheel", channel: "qf-type-frame", session: boot.session, x: (e.clientX - r.left) * frame2.clientWidth / r.width, y: (e.clientY - r.top) * frame2.clientHeight / r.height, deltaX: e.deltaX, deltaY: e.deltaY, deltaMode: e.deltaMode, ctrlKey: e.ctrlKey } });
    }, { passive: false });
    const entry = { receive(event) {
      if (closed) return;
      if (event.kind === "failure") {
        onFailure(Object.assign(new Error(event.message), { code: event.code }));
        return;
      }
      if (event.kind === "image") {
        if (typeof event.png !== "string" || !Number.isFinite(event.offset) || !Number.isFinite(event.height)) return;
        image2.style.top = event.offset + "px";
        image2.style.height = event.height + "px";
        image2.src = "data:image/png;base64," + event.png;
        return;
      }
      if (event.kind === "page") {
        const data = event.message;
        if (data?.session !== boot.session || data?.channel !== "qf-type-frame") return;
        onMessage({ source: port, data });
        window.dispatchEvent(new MessageEvent("message", { source: null, data }));
      }
    } };
    sessions.set(boot.session, entry);
    const resize2 = new ResizeObserver(viewport);
    resize2.observe(frame2);
    const timer = setInterval(viewport, 250);
    return {
      frame: frame2,
      start() {
        started = true;
        host2.open(boot.session, documentSource, Math.max(1, Math.min(2048, Math.round(frame2.clientWidth || 720))));
        viewport();
      },
      state(value) {
        interaction = value;
        frame2.style.cursor = value === "INTERACT" ? "auto" : "";
        if (value !== "INTERACT") input.blur();
      },
      destroy() {
        if (closed) return;
        closed = true;
        clearInterval(timer);
        resize2.disconnect();
        sessions.delete(boot.session);
        host2.closePage(boot.session);
      }
    };
  }

  // src/extensions/frame-host.js
  var nonce = "qf-isolated-page-v1";
  var escapeScript = (source) => source.replace(/<\/script/gi, "<\\/script");
  function frameDocument(html, styles, source, boot) {
    const policy = `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; font-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'`;
    const helpers = isolatedContentSource() + "\n" + isolatedUiSource() + "\n" + isolatedLayoutSource() + "\n" + validateArguments.toString();
    const init = helpers + "\n(" + startFrameClient.toString() + ")(" + JSON.stringify(boot).replace(/</g, "\\u003c") + "," + renderContent.name + "," + configureLayout.name + "," + configureUi.name + "," + validateArguments.name + ");";
    return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${policy}"><meta http-equiv="Content-Security-Policy" content="script-src 'unsafe-inline'"><style>html,body{margin:0;padding:0;overflow:hidden}body,.qf-extension-page{display:flow-root}*{box-sizing:border-box}[hidden]{display:none!important}.shared-content{white-space:pre-wrap;overflow-wrap:anywhere}.document-content{white-space:normal}.document-content img{max-width:100%;height:auto}.document-content table{border-collapse:collapse}.document-content td{border:1px solid #ded8e8;padding:6px}${styles.replace(/<\/style/gi, "<\\/style")}</style><body><section class="qf-extension-page">${html}</section><script nonce="${nonce}">${escapeScript(init)}<\/script><script nonce="${nonce}">window.__qfStart((async(QF)=>{
${escapeScript(source)}
})(window.QF));<\/script></body></html>`;
  }
  function connectFrame(page, source, { html, styles, boot, invoke, onReady, onFailure, onGeometry, getState, interaction }) {
    const documentSource = frameDocument(html, styles, source, { ...boot, nativePage: Boolean(window.nativePagesHost) });
    let native;
    const frame2 = window.nativePagesHost ? (native = nativePage(boot, documentSource, (event) => receive(event), (error) => failFrame(error))).frame : document.createElement("iframe");
    frame2.className = "qf-type-frame";
    frame2.title = boot.mode === "EDITOR" ? "\u9898\u578B\u7F16\u8F91\u9875\u9762" : "\u9898\u578B\u7EC3\u4E60\u9875\u9762";
    frame2.setAttribute("sandbox", "allow-scripts");
    frame2.setAttribute("referrerpolicy", "no-referrer");
    frame2.style.cssText = "display:block;position:relative;overflow:hidden;width:100%;height:1px;border:0;";
    page.style.cssText = "position:relative;min-width:0;";
    page.append(frame2);
    let closed = false, retiring = false, loaded = false, seq = 0, lastRequest = 0, writeQueue = Promise.resolve(), retirement, finishRetirement, retirementTimer;
    const waiting = /* @__PURE__ */ new Map(), inFlight = /* @__PURE__ */ new Map(), regions = [];
    const send2 = (message) => {
      if (!closed) frame2.contentWindow?.postMessage({ ...message, channel: "qf-type-frame", session: boot.session }, "*");
    };
    const state = () => {
      if (retiring || closed) return;
      const value = getState();
      send2({ kind: "state", ...value });
      controls();
    };
    function controls() {
      native?.state(interaction());
      for (const region of regions) region.style.display = interaction() === "INTERACT" ? "none" : "block";
    }
    function setGeometry(message) {
      if (!Number.isFinite(message.height) || message.height < 0 || message.height > 2e5) return;
      const height = Math.max(1, message.height);
      frame2.style.height = height + "px";
      regions.splice(0).forEach((node) => node.remove());
      for (const rect of Array.isArray(message.controls) ? message.controls.slice(0, 32) : []) {
        if (![rect.x, rect.y, rect.width, rect.height].every(Number.isFinite) || rect.width <= 0 || rect.height <= 0) continue;
        const x = Math.max(0, rect.x), y = Math.max(0, rect.y), w = Math.min(rect.width, frame2.clientWidth - x), h = Math.min(rect.height, height - y);
        if (w <= 0 || h <= 0) continue;
        const overlay = document.createElement("div");
        overlay.dataset.qfHostControls = "";
        overlay.style.cssText = `position:absolute;left:${x}px;top:${y}px;width:${w}px;height:${h}px;pointer-events:auto;`;
        overlay.addEventListener("pointerdown", (event) => event.stopPropagation());
        overlay.addEventListener("click", (event) => {
          event.stopPropagation();
          const r = overlay.getBoundingClientRect();
          send2({ kind: "control-click", x: x + (event.clientX - r.left) * w / r.width, y: y + (event.clientY - r.top) * h / r.height });
        });
        page.append(overlay);
        regions.push(overlay);
      }
      controls();
      onGeometry?.();
    }
    async function receive(event) {
      const m = event.data;
      if (closed || event.source !== frame2.contentWindow || m?.channel !== "qf-type-frame" || m.session !== boot.session) return;
      if (m.kind === "request") {
        if (typeof m.id !== "string" || !/^\d+$/.test(m.id) || !Number.isSafeInteger(Number(m.id)) || Number(m.id) <= lastRequest) return;
        lastRequest = Number(m.id);
        if (retiring) {
          send2({ kind: "reply", id: m.id, reply: failure3("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED") });
          return;
        }
        let args;
        try {
          if (typeof m.method !== "string") throw new TypeError("\u63A5\u53E3\u540D\u79F0\u65E0\u6548");
          args = validateArguments(m.args);
        } catch (error) {
          send2({ kind: "reply", id: m.id, reply: failure3("INVALID_ARGUMENT", error.message) });
          return;
        }
        inFlight.set(m.id, false);
        const execute = () => retiring || closed ? failure3("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED") : invoke(m.method, args);
        let reply;
        try {
          if (["editor.update", "answer.update"].includes(m.method)) {
            const operation = writeQueue.then(execute);
            writeQueue = operation.catch(() => {
            });
            reply = await operation;
          } else reply = await execute();
        } catch (error) {
          reply = failure3("SDK_FAILED", error.message);
        }
        inFlight.set(m.id, true);
        send2({ kind: "reply", id: m.id, reply, layoutState: getState().layoutState });
        return;
      }
      if (m.kind === "reply-ack") {
        if (inFlight.get(m.id) === true) inFlight.delete(m.id);
        if (retiring && !inFlight.size) closeFrame();
        return;
      }
      if (m.kind === "close-ack" && retiring) {
        closeFrame();
        return;
      }
      if (retiring) return;
      if (m.kind === "geometry") {
        setGeometry(m);
        return;
      }
      if (m.kind === "ready") {
        if (!loaded) {
          loaded = true;
          clearTimeout(startup);
          onReady();
          state();
        }
        return;
      }
      if (m.kind === "failed") {
        failFrame(new Error(String(m.error || "\u9898\u578B\u9875\u9762\u52A0\u8F7D\u5931\u8D25").slice(0, 1024)));
        return;
      }
      if (m.kind === "flushed") {
        const entry = waiting.get(m.id);
        if (entry) {
          clearTimeout(entry.timeout);
          waiting.delete(m.id);
          m.error ? entry.reject(new Error(m.error)) : entry.resolve();
        }
        return;
      }
      if (m.kind === "wheel" && boot.relayWheel && [m.x, m.y, m.deltaX, m.deltaY].every(Number.isFinite)) {
        const r = frame2.getBoundingClientRect(), scale = frame2.clientWidth ? r.width / frame2.clientWidth : 1;
        page.dispatchEvent(new WheelEvent("wheel", { bubbles: true, cancelable: true, clientX: r.left + m.x * scale, clientY: r.top + m.y * scale, deltaX: m.deltaX, deltaY: m.deltaY, deltaMode: [0, 1, 2].includes(m.deltaMode) ? m.deltaMode : 0, ctrlKey: m.ctrlKey === true }));
      }
    }
    function failure3(code, message) {
      return { ok: false, error: { code, message, retryable: false } };
    }
    function failFrame(error) {
      if (closed) return;
      closeFrame();
      onFailure(error);
    }
    function closeFrame() {
      if (closed) return;
      closed = true;
      clearTimeout(startup);
      clearTimeout(retirementTimer);
      window.removeEventListener("message", receive);
      for (const entry of waiting.values()) {
        clearTimeout(entry.timeout);
        entry.reject(new Error("\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED"));
      }
      waiting.clear();
      regions.splice(0).forEach((node) => node.remove());
      native?.destroy();
      frame2.remove();
      finishRetirement?.();
    }
    window.addEventListener("message", receive);
    const startup = setTimeout(() => {
      if (!loaded && !closed) failFrame(new Error("\u9694\u79BB\u9898\u578B\u9875\u9762\u52A0\u8F7D\u8D85\u65F6"));
    }, native ? 6e4 : 15e3);
    if (native) native.start();
    else frame2.srcdoc = documentSource;
    return {
      state,
      flush() {
        if (closed) return Promise.reject(new Error("\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED"));
        const id = String(++seq);
        return new Promise((resolve, reject2) => {
          const timeout = setTimeout(() => {
            waiting.delete(id);
            reject2(new Error("\u9898\u578B\u9875\u9762\u5237\u65B0\u8D85\u65F6"));
          }, 15e3);
          waiting.set(id, { resolve, reject: reject2, timeout });
          send2({ kind: "flush", id });
        });
      },
      focus(id) {
        send2({ kind: "focus", id });
        return page;
      },
      destroy() {
        if (retirement) return retirement;
        if (closed) return Promise.resolve();
        retiring = true;
        page.classList.add("qf-frame-retiring");
        retirement = new Promise((resolve) => {
          finishRetirement = resolve;
        });
        clearTimeout(startup);
        if (!inFlight.size) {
          closeFrame();
          return retirement;
        }
        retirementTimer = setTimeout(() => {
          send2({ kind: "closing" });
          retirementTimer = setTimeout(closeFrame, 1e3);
        }, 15e3);
        return retirement;
      }
    };
  }

  // src/extensions/permissions.js
  var permissionNames = Object.freeze([
    "question.edit",
    "bank.save",
    "bank.add",
    "bank.duplicate",
    "bank.delete",
    "answer.write",
    "practice.submit",
    "practice.retry",
    "navigation",
    "sources.open",
    "sources.manage",
    "learning.mode",
    "whiteboard.tools",
    "whiteboard.history",
    "whiteboard.clear",
    "whiteboard.appearance",
    "whiteboard.zoom"
  ]);
  function readPermissions(value) {
    if (value == null) return [];
    if (!Array.isArray(value) || value.some((name) => !permissionNames.includes(name))) throw new TypeError("\u672A\u77E5\u6216\u65E0\u6548\u7684\u62D3\u5C55\u6743\u9650");
    return [...new Set(value)];
  }
  var requirements = Object.freeze({
    "editor.update": "question.edit",
    "editor.save": "question.edit",
    "content.edit": "question.edit",
    "bank.save": "bank.save",
    "bank.addQuestion": "bank.add",
    "bank.duplicateQuestion": "bank.duplicate",
    "bank.deleteQuestion": "bank.delete",
    "answer.update": "answer.write",
    "answer.flush": "answer.write",
    "practice.submit": "practice.submit",
    "practice.retry": "practice.retry",
    "navigation.goTo": "navigation",
    "navigation.previous": "navigation",
    "navigation.next": "navigation",
    "sources.open": "sources.open",
    "sources.add": "sources.manage",
    "sources.remove": "sources.manage",
    "learning.setMode": "learning.mode",
    "learning.toggleMode": "learning.mode",
    "whiteboard.setTool": "whiteboard.tools",
    "whiteboard.undo": "whiteboard.history",
    "whiteboard.redo": "whiteboard.history",
    "whiteboard.clear": "whiteboard.clear",
    "whiteboard.setAppearance": "whiteboard.appearance",
    "whiteboard.setZoom": "whiteboard.zoom",
    "whiteboard.zoomBy": "whiteboard.zoom"
  });
  var publicMethods = /* @__PURE__ */ new Set([
    "host.getContext",
    "editor.getData",
    "bank.getState",
    "navigation.getState",
    "sources.list",
    "learning.getMode",
    "whiteboard.getState",
    "practice.getState",
    "practice.getQuestion",
    "practice.getResult",
    "answer.get",
    "content.resolve",
    "ui.configure",
    "ui.getConfiguration",
    "layout.configure",
    "layout.getConfiguration",
    "layout.getState"
  ]);
  function createPermissionPolicy(declared, approved) {
    const requested = Object.freeze(readPermissions(declared)), listeners = /* @__PURE__ */ new Set();
    let granted = Object.freeze(readPermissions(approved).filter((name) => requested.includes(name))), allowed = new Set(granted);
    return Object.freeze({
      declared: requested,
      get granted() {
        return granted;
      },
      can: (method) => publicMethods.has(method) || Boolean(requirements[method] && allowed.has(requirements[method])),
      update(values) {
        const next = readPermissions(values);
        if (next.some((name) => !requested.includes(name))) throw new TypeError("\u4E0D\u80FD\u6388\u4E88\u672A\u58F0\u660E\u7684\u62D3\u5C55\u6743\u9650");
        granted = Object.freeze(next);
        allowed = new Set(next);
        for (const listener of listeners) {
          try {
            listener();
          } catch {
          }
        }
      },
      subscribe(listener) {
        listeners.add(listener);
        return () => listeners.delete(listener);
      }
    });
  }

  // src/extensions/data-validation-core.js
  var import_ajv = __toESM(require_ajv(), 1);
  var clone = (value) => JSON.parse(JSON.stringify(value));
  var object = (value) => Boolean(value) && typeof value === "object" && !Array.isArray(value);
  var own = (value, key) => Object.prototype.hasOwnProperty.call(value, key);
  var escape2 = (value) => value.replace(/~/g, "~0").replace(/\//g, "~1");
  var keywords = new Set("$schema $ref $comment title description default examples readOnly writeOnly type enum const multipleOf maximum exclusiveMaximum minimum exclusiveMinimum maxLength minLength pattern format items additionalItems maxItems minItems uniqueItems contains maxProperties minProperties required properties patternProperties additionalProperties dependencies propertyNames allOf anyOf oneOf not if then else definitions contentEncoding contentMediaType".split(" "));
  var maps = /* @__PURE__ */ new Set(["properties", "patternProperties", "definitions", "dependencies"]);
  var singles = /* @__PURE__ */ new Set(["additionalItems", "additionalProperties", "contains", "propertyNames", "not", "if", "then", "else"]);
  var arrays = /* @__PURE__ */ new Set(["allOf", "anyOf", "oneOf"]);
  var annotations = new Set("$schema $ref $comment title description default examples readOnly writeOnly".split(" "));
  var DataValidationError = class extends TypeError {
    constructor(issues) {
      super(issues.map((issue) => `${issue.path}: ${issue.message}`).join("; "));
      this.code = "DATA_VALIDATION_FAILED";
      this.issues = clone(issues);
    }
  };
  var reject = (path, message) => {
    throw new DataValidationError([{ path, message }]);
  };
  function validationFailure(error) {
    return { ok: false, error: { code: error.code || "DATA_VALIDATION_FAILED", message: error.message, retryable: false, ...error.issues ? { issues: clone(error.issues) } : {} } };
  }
  function safePattern(pattern, path) {
    if (typeof pattern !== "string" || pattern.length > 256) reject(path, "pattern must be text of at most 256 characters");
    let characterClass = false, quantifiers = 0;
    for (let index = 0; index < pattern.length; index++) {
      const token = pattern[index];
      if (token === "\\") {
        const next = pattern[++index];
        if (!next || !"dDsSwW\\.^$[]{}()*+?|-/".includes(next)) reject(path, "unsupported pattern escape");
        continue;
      }
      if (token === "[" && !characterClass) {
        characterClass = true;
        continue;
      }
      if (token === "]" && characterClass) {
        characterClass = false;
        continue;
      }
      if (characterClass) {
        if (token === "[" || token === "&") reject(path, "nested or intersected character classes are not supported");
        continue;
      }
      if ("()|".includes(token)) reject(path, "pattern groups and alternation are not supported");
      if ("*+?".includes(token)) quantifiers++;
      if (token === "{") {
        const end = pattern.indexOf("}", index), repeat = pattern.slice(index + 1, end);
        if (end < 0 || !/^\d+(,\d*)?$/.test(repeat) || repeat.split(",").filter(Boolean).some((n) => Number(n) > 1024)) reject(path, "pattern repeat bounds must be at most 1024");
        quantifiers++;
        index = end;
      }
      if (quantifiers > 1) reject(path, "patterns may contain at most one quantifier");
    }
    if (quantifiers && (!pattern.startsWith("^") || !pattern.endsWith("$") || /\\\$$/.test(pattern))) reject(path, "patterns with quantifiers must be anchored with ^ and $");
  }
  function schemaProfile(root2, path) {
    if (!object(root2)) reject(path, "schema must be a JSON object");
    const nodes = /* @__PURE__ */ new Map(), edges = /* @__PURE__ */ new Map();
    let patterns = 0;
    function collect(node, pointer, depth) {
      if (depth > 32 || nodes.size >= 4096) reject(path + pointer, "schema is too large or deeply nested");
      if (!object(node) && typeof node !== "boolean") reject(path + pointer, "must be a schema object or boolean");
      nodes.set(pointer, node);
      edges.set(pointer, []);
      if (typeof node === "boolean") return;
      const child = (value, key) => {
        collect(value, key, depth + 1);
        edges.get(pointer).push(key);
      };
      for (const [key, value] of Object.entries(node)) {
        if (!keywords.has(key)) reject(path + pointer + "/" + escape2(key), "unsupported schema keyword");
        if (key === "$schema" && !["http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft-07/schema#"].includes(value)) reject(path + pointer + "/$schema", "only Draft-07 is supported; omit $schema to use Draft-07");
        if (own(node, "$ref") && !annotations.has(key)) reject(path + pointer, "$ref cannot have assertion siblings");
        if (key === "pattern") {
          if (++patterns > 64) reject(path, "schema has too many patterns");
          safePattern(value, path + pointer + "/pattern");
        }
        if (key === "patternProperties" && object(value)) for (const pattern of Object.keys(value)) {
          if (++patterns > 64) reject(path, "schema has too many patterns");
          safePattern(pattern, path + pointer + "/patternProperties/" + escape2(pattern));
        }
        if (maps.has(key) && object(value)) for (const [name, sub] of Object.entries(value)) {
          if (key !== "dependencies" || !Array.isArray(sub)) child(sub, `${pointer}/${key}/${escape2(name)}`);
        }
        else if ((arrays.has(key) || key === "items") && Array.isArray(value)) value.forEach((sub, index) => child(sub, `${pointer}/${key}/${index}`));
        else if (singles.has(key) || key === "items") child(value, `${pointer}/${key}`);
      }
    }
    collect(root2, "", 0);
    for (const [pointer, node] of nodes) if (object(node) && own(node, "$ref")) {
      if (typeof node.$ref !== "string" || !node.$ref.startsWith("#/")) reject(path + pointer + "/$ref", "only local JSON pointer references are supported");
      const target2 = node.$ref.slice(1);
      if (!nodes.has(target2)) reject(path + pointer + "/$ref", "reference must point to a declared schema");
      edges.get(pointer).push(target2);
    }
    const active2 = /* @__PURE__ */ new Set(), done = /* @__PURE__ */ new Set();
    function visit(pointer, depth) {
      if (active2.has(pointer) || depth > 32) reject(path + pointer, "cyclic or overly deep schema references are not supported");
      if (done.has(pointer)) return;
      done.add(pointer);
      active2.add(pointer);
      edges.get(pointer).forEach((target2) => visit(target2, depth + 1));
      active2.delete(pointer);
    }
    visit("", 0);
    let steps = 0;
    function expanded(pointer) {
      if (++steps > 16384) reject(path, "expanded schema exceeds its complexity budget");
      for (const target2 of edges.get(pointer)) expanded(target2);
    }
    expanded("");
    for (const node of nodes.values()) if (object(node) && own(node, "$schema")) node.$schema = "http://json-schema.org/draft-07/schema#";
  }
  var target = { type: "object", required: ["id"], properties: { id: { type: "string", minLength: 1 }, number: { type: "integer", minimum: 1 }, locked: { type: "boolean" }, gradable: { type: "boolean" }, label: { type: "string" } } };
  var targets = { type: "array", items: target };
  var errors = { type: "array", items: { type: "string" } };
  var maximum = { type: "number", exclusiveMinimum: 0 };
  var outputSchemas = {
    validate: { type: "object", required: ["errors"], properties: { errors } },
    validateAnswer: { type: "object", required: ["errors", "empty"], properties: { errors, empty: { type: "boolean" } } },
    targets: { type: "object", required: ["targets"], properties: { targets } },
    snapshot: { type: "object", properties: { targets, maxScore: maximum } },
    grade: { type: "object", required: ["status", "score"], properties: { status: { enum: ["CORRECT", "INCORRECT", "UNSCORED"] }, score: { type: ["number", "null"] }, maxScore: maximum } }
  };
  var ajv = new import_ajv.default({ allErrors: true, strict: false, validateFormats: false, coerceTypes: false, useDefaults: false, removeAdditional: false, ownProperties: true });
  function requireValid(validate, value, path) {
    if (validate(value)) return;
    throw new DataValidationError(validate.errors.slice(0, 32).map((error) => ({ path: path + error.instancePath + (error.keyword === "required" ? "/" + escape2(error.params.missingProperty) : error.keyword === "additionalProperties" ? "/" + escape2(error.params.additionalProperty) : ""), message: error.message })));
  }
  var outputs = Object.fromEntries(Object.entries(outputSchemas).map(([key, schema]) => [key, ajv.compile(schema)]));
  function compileDataValidation(type, asset) {
    function compile(source, path) {
      try {
        if ((typeof source === "string" ? source : JSON.stringify(source)).length > 256 * 1024) reject(path, "schema exceeds 256 KiB character limit");
        const schema = typeof source === "string" ? JSON.parse(source) : clone(source);
        schemaProfile(schema, path);
        return ajv.compile(schema);
      } catch (error) {
        if (error instanceof DataValidationError) throw error;
        reject(path, `invalid schema: ${error.message}`);
      }
    }
    const questionSchema = compile(asset.questionSchemaSource, "/schemas/question"), answerSchema = compile(asset.answerSchemaSource, "/schemas/answer");
    function question(value) {
      if (!object(value)) reject("/question", "must be an object");
      validateArguments([value]);
      for (const key of ["id", "type"]) if (typeof value[key] !== "string" || !value[key].trim()) reject("/question/" + key, "must be nonempty text");
      if (value.type !== type.id) reject("/question/type", "does not match the registered type");
      for (const key of ["prompt", "payload", "answerSpec", "scoreSpec"]) if (!object(value[key])) reject("/question/" + key, "must be an object");
      if (!Number.isFinite(value.scoreSpec.defaultMaxScore) || value.scoreSpec.defaultMaxScore <= 0) reject("/question/scoreSpec/defaultMaxScore", "must be positive and finite");
      requireValid(questionSchema, value, "/question");
    }
    function answer(value) {
      if (!object(value)) reject("/answer", "must be an object");
      validateArguments([value]);
      if (Object.keys(value).length) requireValid(answerSchema, value, "/answer");
    }
    function checkTargets(value, path) {
      const ids = /* @__PURE__ */ new Set(), numbers = /* @__PURE__ */ new Set();
      value.forEach((item, index) => {
        if (!item.id.trim() || ids.has(item.id)) reject(`${path}/${index}/id`, "must be nonempty and unique");
        ids.add(item.id);
        const number = item.number ?? index + 1;
        if (numbers.has(number)) reject(`${path}/${index}/number`, "must be unique");
        numbers.add(number);
      });
    }
    function input(operation, value) {
      validateArguments([value]);
      if (value.question !== void 0) question(value.question);
      if (["validateAnswer", "grade"].includes(operation)) answer(value.answer);
      if (operation === "grade" && !Object.keys(value.answer).length) reject("/answer", "write an answer first");
    }
    function output(operation, value, input2) {
      validateArguments([value]);
      if (["createDraft", "duplicate"].includes(operation)) {
        question(value);
        return;
      }
      if (!outputs[operation]) reject("/rules", "unsupported operation");
      requireValid(outputs[operation], value, "/rules/" + operation);
      if (value.targets) checkTargets(value.targets, "/rules/" + operation + "/targets");
      if (operation === "grade") {
        const max = input2.maxScore;
        if (!Number.isFinite(max) || max <= 0) reject("/rules/grade/maxScore", "requires a frozen positive maximum");
        if (own(value, "maxScore") && value.maxScore !== max) reject("/rules/grade/maxScore", "cannot change the frozen maximum score");
        if (value.status === "UNSCORED") {
          if (value.score !== null) reject("/rules/grade/score", "an unscored result requires null");
        } else if (!Number.isFinite(value.score) || value.score < 0 || value.score > max) reject("/rules/grade/score", "is outside the allowed range");
        else if (value.status === "CORRECT" !== (value.score === max)) reject("/rules/grade/score", "full credit requires CORRECT status");
      }
    }
    return Object.freeze({ question, answer, input, output });
  }

  // src/extensions/schema-worker-client.js
  var validators = /* @__PURE__ */ new Set();
  var failure = (message) => new DataValidationError([{ path: "/schemas", message }]);
  function createBrowserWorker() {
    if (typeof Worker !== "function" || false)
      throw failure("isolated schema validation is unavailable; synchronous fallback is disabled");
    const url = URL.createObjectURL(new Blob(['(() => {\n  var __create = Object.create;\n  var __defProp = Object.defineProperty;\n  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;\n  var __getOwnPropNames = Object.getOwnPropertyNames;\n  var __getProtoOf = Object.getPrototypeOf;\n  var __hasOwnProp = Object.prototype.hasOwnProperty;\n  var __commonJS = (cb, mod) => function __require() {\n    try {\n      return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;\n    } catch (e) {\n      throw mod = 0, e;\n    }\n  };\n  var __copyProps = (to, from, except, desc) => {\n    if (from && typeof from === "object" || typeof from === "function") {\n      for (let key of __getOwnPropNames(from))\n        if (!__hasOwnProp.call(to, key) && key !== except)\n          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });\n    }\n    return to;\n  };\n  var __toESM = (mod, isNodeMode, target2) => (target2 = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(\n    // If the importer is in node compatibility mode or this is not an ESM\n    // file that has been converted to a CommonJS file using a Babel-\n    // compatible transform (i.e. "__esModule" has not been set), then set\n    // "default" to the CommonJS "module.exports" for node compatibility.\n    isNodeMode || !mod || !mod.__esModule ? __defProp(target2, "default", { value: mod, enumerable: true }) : target2,\n    mod\n  ));\n\n  // node_modules/ajv/dist/compile/codegen/code.js\n  var require_code = __commonJS({\n    "node_modules/ajv/dist/compile/codegen/code.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.regexpCode = exports.getEsmExportName = exports.getProperty = exports.safeStringify = exports.stringify = exports.strConcat = exports.addCodeArg = exports.str = exports._ = exports.nil = exports._Code = exports.Name = exports.IDENTIFIER = exports._CodeOrName = void 0;\n      var _CodeOrName = class {\n      };\n      exports._CodeOrName = _CodeOrName;\n      exports.IDENTIFIER = /^[a-z$_][a-z$_0-9]*$/i;\n      var Name = class extends _CodeOrName {\n        constructor(s) {\n          super();\n          if (!exports.IDENTIFIER.test(s))\n            throw new Error("CodeGen: name must be a valid identifier");\n          this.str = s;\n        }\n        toString() {\n          return this.str;\n        }\n        emptyStr() {\n          return false;\n        }\n        get names() {\n          return { [this.str]: 1 };\n        }\n      };\n      exports.Name = Name;\n      var _Code = class extends _CodeOrName {\n        constructor(code) {\n          super();\n          this._items = typeof code === "string" ? [code] : code;\n        }\n        toString() {\n          return this.str;\n        }\n        emptyStr() {\n          if (this._items.length > 1)\n            return false;\n          const item = this._items[0];\n          return item === "" || item === \'""\';\n        }\n        get str() {\n          var _a;\n          return (_a = this._str) !== null && _a !== void 0 ? _a : this._str = this._items.reduce((s, c) => `${s}${c}`, "");\n        }\n        get names() {\n          var _a;\n          return (_a = this._names) !== null && _a !== void 0 ? _a : this._names = this._items.reduce((names, c) => {\n            if (c instanceof Name)\n              names[c.str] = (names[c.str] || 0) + 1;\n            return names;\n          }, {});\n        }\n      };\n      exports._Code = _Code;\n      exports.nil = new _Code("");\n      function _(strs, ...args) {\n        const code = [strs[0]];\n        let i = 0;\n        while (i < args.length) {\n          addCodeArg(code, args[i]);\n          code.push(strs[++i]);\n        }\n        return new _Code(code);\n      }\n      exports._ = _;\n      var plus = new _Code("+");\n      function str(strs, ...args) {\n        const expr = [safeStringify(strs[0])];\n        let i = 0;\n        while (i < args.length) {\n          expr.push(plus);\n          addCodeArg(expr, args[i]);\n          expr.push(plus, safeStringify(strs[++i]));\n        }\n        optimize(expr);\n        return new _Code(expr);\n      }\n      exports.str = str;\n      function addCodeArg(code, arg) {\n        if (arg instanceof _Code)\n          code.push(...arg._items);\n        else if (arg instanceof Name)\n          code.push(arg);\n        else\n          code.push(interpolate(arg));\n      }\n      exports.addCodeArg = addCodeArg;\n      function optimize(expr) {\n        let i = 1;\n        while (i < expr.length - 1) {\n          if (expr[i] === plus) {\n            const res = mergeExprItems(expr[i - 1], expr[i + 1]);\n            if (res !== void 0) {\n              expr.splice(i - 1, 3, res);\n              continue;\n            }\n            expr[i++] = "+";\n          }\n          i++;\n        }\n      }\n      function mergeExprItems(a, b) {\n        if (b === \'""\')\n          return a;\n        if (a === \'""\')\n          return b;\n        if (typeof a == "string") {\n          if (b instanceof Name || a[a.length - 1] !== \'"\')\n            return;\n          if (typeof b != "string")\n            return `${a.slice(0, -1)}${b}"`;\n          if (b[0] === \'"\')\n            return a.slice(0, -1) + b.slice(1);\n          return;\n        }\n        if (typeof b == "string" && b[0] === \'"\' && !(a instanceof Name))\n          return `"${a}${b.slice(1)}`;\n        return;\n      }\n      function strConcat(c1, c2) {\n        return c2.emptyStr() ? c1 : c1.emptyStr() ? c2 : str`${c1}${c2}`;\n      }\n      exports.strConcat = strConcat;\n      function interpolate(x) {\n        return typeof x == "number" || typeof x == "boolean" || x === null ? x : safeStringify(Array.isArray(x) ? x.join(",") : x);\n      }\n      function stringify(x) {\n        return new _Code(safeStringify(x));\n      }\n      exports.stringify = stringify;\n      function safeStringify(x) {\n        return JSON.stringify(x).replace(/\\u2028/g, "\\\\u2028").replace(/\\u2029/g, "\\\\u2029");\n      }\n      exports.safeStringify = safeStringify;\n      function getProperty(key) {\n        return typeof key == "string" && exports.IDENTIFIER.test(key) ? new _Code(`.${key}`) : _`[${key}]`;\n      }\n      exports.getProperty = getProperty;\n      function getEsmExportName(key) {\n        if (typeof key == "string" && exports.IDENTIFIER.test(key)) {\n          return new _Code(`${key}`);\n        }\n        throw new Error(`CodeGen: invalid export name: ${key}, use explicit $id name mapping`);\n      }\n      exports.getEsmExportName = getEsmExportName;\n      function regexpCode(rx) {\n        return new _Code(rx.toString());\n      }\n      exports.regexpCode = regexpCode;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/codegen/scope.js\n  var require_scope = __commonJS({\n    "node_modules/ajv/dist/compile/codegen/scope.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.ValueScope = exports.ValueScopeName = exports.Scope = exports.varKinds = exports.UsedValueState = void 0;\n      var code_1 = require_code();\n      var ValueError = class extends Error {\n        constructor(name) {\n          super(`CodeGen: "code" for ${name} not defined`);\n          this.value = name.value;\n        }\n      };\n      var UsedValueState;\n      (function(UsedValueState2) {\n        UsedValueState2[UsedValueState2["Started"] = 0] = "Started";\n        UsedValueState2[UsedValueState2["Completed"] = 1] = "Completed";\n      })(UsedValueState || (exports.UsedValueState = UsedValueState = {}));\n      exports.varKinds = {\n        const: new code_1.Name("const"),\n        let: new code_1.Name("let"),\n        var: new code_1.Name("var")\n      };\n      var Scope = class {\n        constructor({ prefixes, parent } = {}) {\n          this._names = {};\n          this._prefixes = prefixes;\n          this._parent = parent;\n        }\n        toName(nameOrPrefix) {\n          return nameOrPrefix instanceof code_1.Name ? nameOrPrefix : this.name(nameOrPrefix);\n        }\n        name(prefix) {\n          return new code_1.Name(this._newName(prefix));\n        }\n        _newName(prefix) {\n          const ng = this._names[prefix] || this._nameGroup(prefix);\n          return `${prefix}${ng.index++}`;\n        }\n        _nameGroup(prefix) {\n          var _a, _b;\n          if (((_b = (_a = this._parent) === null || _a === void 0 ? void 0 : _a._prefixes) === null || _b === void 0 ? void 0 : _b.has(prefix)) || this._prefixes && !this._prefixes.has(prefix)) {\n            throw new Error(`CodeGen: prefix "${prefix}" is not allowed in this scope`);\n          }\n          return this._names[prefix] = { prefix, index: 0 };\n        }\n      };\n      exports.Scope = Scope;\n      var ValueScopeName = class extends code_1.Name {\n        constructor(prefix, nameStr) {\n          super(nameStr);\n          this.prefix = prefix;\n        }\n        setValue(value, { property, itemIndex }) {\n          this.value = value;\n          this.scopePath = (0, code_1._)`.${new code_1.Name(property)}[${itemIndex}]`;\n        }\n      };\n      exports.ValueScopeName = ValueScopeName;\n      var line = (0, code_1._)`\\n`;\n      var ValueScope = class extends Scope {\n        constructor(opts) {\n          super(opts);\n          this._values = {};\n          this._scope = opts.scope;\n          this.opts = { ...opts, _n: opts.lines ? line : code_1.nil };\n        }\n        get() {\n          return this._scope;\n        }\n        name(prefix) {\n          return new ValueScopeName(prefix, this._newName(prefix));\n        }\n        value(nameOrPrefix, value) {\n          var _a;\n          if (value.ref === void 0)\n            throw new Error("CodeGen: ref must be passed in value");\n          const name = this.toName(nameOrPrefix);\n          const { prefix } = name;\n          const valueKey = (_a = value.key) !== null && _a !== void 0 ? _a : value.ref;\n          let vs = this._values[prefix];\n          if (vs) {\n            const _name = vs.get(valueKey);\n            if (_name)\n              return _name;\n          } else {\n            vs = this._values[prefix] = /* @__PURE__ */ new Map();\n          }\n          vs.set(valueKey, name);\n          const s = this._scope[prefix] || (this._scope[prefix] = []);\n          const itemIndex = s.length;\n          s[itemIndex] = value.ref;\n          name.setValue(value, { property: prefix, itemIndex });\n          return name;\n        }\n        getValue(prefix, keyOrRef) {\n          const vs = this._values[prefix];\n          if (!vs)\n            return;\n          return vs.get(keyOrRef);\n        }\n        scopeRefs(scopeName, values = this._values) {\n          return this._reduceValues(values, (name) => {\n            if (name.scopePath === void 0)\n              throw new Error(`CodeGen: name "${name}" has no value`);\n            return (0, code_1._)`${scopeName}${name.scopePath}`;\n          });\n        }\n        scopeCode(values = this._values, usedValues, getCode) {\n          return this._reduceValues(values, (name) => {\n            if (name.value === void 0)\n              throw new Error(`CodeGen: name "${name}" has no value`);\n            return name.value.code;\n          }, usedValues, getCode);\n        }\n        _reduceValues(values, valueCode, usedValues = {}, getCode) {\n          let code = code_1.nil;\n          for (const prefix in values) {\n            const vs = values[prefix];\n            if (!vs)\n              continue;\n            const nameSet = usedValues[prefix] = usedValues[prefix] || /* @__PURE__ */ new Map();\n            vs.forEach((name) => {\n              if (nameSet.has(name))\n                return;\n              nameSet.set(name, UsedValueState.Started);\n              let c = valueCode(name);\n              if (c) {\n                const def = this.opts.es5 ? exports.varKinds.var : exports.varKinds.const;\n                code = (0, code_1._)`${code}${def} ${name} = ${c};${this.opts._n}`;\n              } else if (c = getCode === null || getCode === void 0 ? void 0 : getCode(name)) {\n                code = (0, code_1._)`${code}${c}${this.opts._n}`;\n              } else {\n                throw new ValueError(name);\n              }\n              nameSet.set(name, UsedValueState.Completed);\n            });\n          }\n          return code;\n        }\n      };\n      exports.ValueScope = ValueScope;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/codegen/index.js\n  var require_codegen = __commonJS({\n    "node_modules/ajv/dist/compile/codegen/index.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.or = exports.and = exports.not = exports.CodeGen = exports.operators = exports.varKinds = exports.ValueScopeName = exports.ValueScope = exports.Scope = exports.Name = exports.regexpCode = exports.stringify = exports.getProperty = exports.nil = exports.strConcat = exports.str = exports._ = void 0;\n      var code_1 = require_code();\n      var scope_1 = require_scope();\n      var code_2 = require_code();\n      Object.defineProperty(exports, "_", { enumerable: true, get: function() {\n        return code_2._;\n      } });\n      Object.defineProperty(exports, "str", { enumerable: true, get: function() {\n        return code_2.str;\n      } });\n      Object.defineProperty(exports, "strConcat", { enumerable: true, get: function() {\n        return code_2.strConcat;\n      } });\n      Object.defineProperty(exports, "nil", { enumerable: true, get: function() {\n        return code_2.nil;\n      } });\n      Object.defineProperty(exports, "getProperty", { enumerable: true, get: function() {\n        return code_2.getProperty;\n      } });\n      Object.defineProperty(exports, "stringify", { enumerable: true, get: function() {\n        return code_2.stringify;\n      } });\n      Object.defineProperty(exports, "regexpCode", { enumerable: true, get: function() {\n        return code_2.regexpCode;\n      } });\n      Object.defineProperty(exports, "Name", { enumerable: true, get: function() {\n        return code_2.Name;\n      } });\n      var scope_2 = require_scope();\n      Object.defineProperty(exports, "Scope", { enumerable: true, get: function() {\n        return scope_2.Scope;\n      } });\n      Object.defineProperty(exports, "ValueScope", { enumerable: true, get: function() {\n        return scope_2.ValueScope;\n      } });\n      Object.defineProperty(exports, "ValueScopeName", { enumerable: true, get: function() {\n        return scope_2.ValueScopeName;\n      } });\n      Object.defineProperty(exports, "varKinds", { enumerable: true, get: function() {\n        return scope_2.varKinds;\n      } });\n      exports.operators = {\n        GT: new code_1._Code(">"),\n        GTE: new code_1._Code(">="),\n        LT: new code_1._Code("<"),\n        LTE: new code_1._Code("<="),\n        EQ: new code_1._Code("==="),\n        NEQ: new code_1._Code("!=="),\n        NOT: new code_1._Code("!"),\n        OR: new code_1._Code("||"),\n        AND: new code_1._Code("&&"),\n        ADD: new code_1._Code("+")\n      };\n      var Node = class {\n        optimizeNodes() {\n          return this;\n        }\n        optimizeNames(_names, _constants) {\n          return this;\n        }\n      };\n      var Def = class extends Node {\n        constructor(varKind, name, rhs) {\n          super();\n          this.varKind = varKind;\n          this.name = name;\n          this.rhs = rhs;\n        }\n        render({ es5, _n }) {\n          const varKind = es5 ? scope_1.varKinds.var : this.varKind;\n          const rhs = this.rhs === void 0 ? "" : ` = ${this.rhs}`;\n          return `${varKind} ${this.name}${rhs};` + _n;\n        }\n        optimizeNames(names, constants) {\n          if (!names[this.name.str])\n            return;\n          if (this.rhs)\n            this.rhs = optimizeExpr(this.rhs, names, constants);\n          return this;\n        }\n        get names() {\n          return this.rhs instanceof code_1._CodeOrName ? this.rhs.names : {};\n        }\n      };\n      var Assign = class extends Node {\n        constructor(lhs, rhs, sideEffects) {\n          super();\n          this.lhs = lhs;\n          this.rhs = rhs;\n          this.sideEffects = sideEffects;\n        }\n        render({ _n }) {\n          return `${this.lhs} = ${this.rhs};` + _n;\n        }\n        optimizeNames(names, constants) {\n          if (this.lhs instanceof code_1.Name && !names[this.lhs.str] && !this.sideEffects)\n            return;\n          this.rhs = optimizeExpr(this.rhs, names, constants);\n          return this;\n        }\n        get names() {\n          const names = this.lhs instanceof code_1.Name ? {} : { ...this.lhs.names };\n          return addExprNames(names, this.rhs);\n        }\n      };\n      var AssignOp = class extends Assign {\n        constructor(lhs, op, rhs, sideEffects) {\n          super(lhs, rhs, sideEffects);\n          this.op = op;\n        }\n        render({ _n }) {\n          return `${this.lhs} ${this.op}= ${this.rhs};` + _n;\n        }\n      };\n      var Label = class extends Node {\n        constructor(label) {\n          super();\n          this.label = label;\n          this.names = {};\n        }\n        render({ _n }) {\n          return `${this.label}:` + _n;\n        }\n      };\n      var Break = class extends Node {\n        constructor(label) {\n          super();\n          this.label = label;\n          this.names = {};\n        }\n        render({ _n }) {\n          const label = this.label ? ` ${this.label}` : "";\n          return `break${label};` + _n;\n        }\n      };\n      var Throw = class extends Node {\n        constructor(error) {\n          super();\n          this.error = error;\n        }\n        render({ _n }) {\n          return `throw ${this.error};` + _n;\n        }\n        get names() {\n          return this.error.names;\n        }\n      };\n      var AnyCode = class extends Node {\n        constructor(code) {\n          super();\n          this.code = code;\n        }\n        render({ _n }) {\n          return `${this.code};` + _n;\n        }\n        optimizeNodes() {\n          return `${this.code}` ? this : void 0;\n        }\n        optimizeNames(names, constants) {\n          this.code = optimizeExpr(this.code, names, constants);\n          return this;\n        }\n        get names() {\n          return this.code instanceof code_1._CodeOrName ? this.code.names : {};\n        }\n      };\n      var ParentNode = class extends Node {\n        constructor(nodes = []) {\n          super();\n          this.nodes = nodes;\n        }\n        render(opts) {\n          return this.nodes.reduce((code, n) => code + n.render(opts), "");\n        }\n        optimizeNodes() {\n          const { nodes } = this;\n          let i = nodes.length;\n          while (i--) {\n            const n = nodes[i].optimizeNodes();\n            if (Array.isArray(n))\n              nodes.splice(i, 1, ...n);\n            else if (n)\n              nodes[i] = n;\n            else\n              nodes.splice(i, 1);\n          }\n          return nodes.length > 0 ? this : void 0;\n        }\n        optimizeNames(names, constants) {\n          const { nodes } = this;\n          let i = nodes.length;\n          while (i--) {\n            const n = nodes[i];\n            if (n.optimizeNames(names, constants))\n              continue;\n            subtractNames(names, n.names);\n            nodes.splice(i, 1);\n          }\n          return nodes.length > 0 ? this : void 0;\n        }\n        get names() {\n          return this.nodes.reduce((names, n) => addNames(names, n.names), {});\n        }\n      };\n      var BlockNode = class extends ParentNode {\n        render(opts) {\n          return "{" + opts._n + super.render(opts) + "}" + opts._n;\n        }\n      };\n      var Root = class extends ParentNode {\n      };\n      var Else = class extends BlockNode {\n      };\n      Else.kind = "else";\n      var If = class _If extends BlockNode {\n        constructor(condition, nodes) {\n          super(nodes);\n          this.condition = condition;\n        }\n        render(opts) {\n          let code = `if(${this.condition})` + super.render(opts);\n          if (this.else)\n            code += "else " + this.else.render(opts);\n          return code;\n        }\n        optimizeNodes() {\n          super.optimizeNodes();\n          const cond = this.condition;\n          if (cond === true)\n            return this.nodes;\n          let e = this.else;\n          if (e) {\n            const ns = e.optimizeNodes();\n            e = this.else = Array.isArray(ns) ? new Else(ns) : ns;\n          }\n          if (e) {\n            if (cond === false)\n              return e instanceof _If ? e : e.nodes;\n            if (this.nodes.length)\n              return this;\n            return new _If(not(cond), e instanceof _If ? [e] : e.nodes);\n          }\n          if (cond === false || !this.nodes.length)\n            return void 0;\n          return this;\n        }\n        optimizeNames(names, constants) {\n          var _a;\n          this.else = (_a = this.else) === null || _a === void 0 ? void 0 : _a.optimizeNames(names, constants);\n          if (!(super.optimizeNames(names, constants) || this.else))\n            return;\n          this.condition = optimizeExpr(this.condition, names, constants);\n          return this;\n        }\n        get names() {\n          const names = super.names;\n          addExprNames(names, this.condition);\n          if (this.else)\n            addNames(names, this.else.names);\n          return names;\n        }\n      };\n      If.kind = "if";\n      var For = class extends BlockNode {\n      };\n      For.kind = "for";\n      var ForLoop = class extends For {\n        constructor(iteration) {\n          super();\n          this.iteration = iteration;\n        }\n        render(opts) {\n          return `for(${this.iteration})` + super.render(opts);\n        }\n        optimizeNames(names, constants) {\n          if (!super.optimizeNames(names, constants))\n            return;\n          this.iteration = optimizeExpr(this.iteration, names, constants);\n          return this;\n        }\n        get names() {\n          return addNames(super.names, this.iteration.names);\n        }\n      };\n      var ForRange = class extends For {\n        constructor(varKind, name, from, to) {\n          super();\n          this.varKind = varKind;\n          this.name = name;\n          this.from = from;\n          this.to = to;\n        }\n        render(opts) {\n          const varKind = opts.es5 ? scope_1.varKinds.var : this.varKind;\n          const { name, from, to } = this;\n          return `for(${varKind} ${name}=${from}; ${name}<${to}; ${name}++)` + super.render(opts);\n        }\n        get names() {\n          const names = addExprNames(super.names, this.from);\n          return addExprNames(names, this.to);\n        }\n      };\n      var ForIter = class extends For {\n        constructor(loop, varKind, name, iterable) {\n          super();\n          this.loop = loop;\n          this.varKind = varKind;\n          this.name = name;\n          this.iterable = iterable;\n        }\n        render(opts) {\n          return `for(${this.varKind} ${this.name} ${this.loop} ${this.iterable})` + super.render(opts);\n        }\n        optimizeNames(names, constants) {\n          if (!super.optimizeNames(names, constants))\n            return;\n          this.iterable = optimizeExpr(this.iterable, names, constants);\n          return this;\n        }\n        get names() {\n          return addNames(super.names, this.iterable.names);\n        }\n      };\n      var Func = class extends BlockNode {\n        constructor(name, args, async) {\n          super();\n          this.name = name;\n          this.args = args;\n          this.async = async;\n        }\n        render(opts) {\n          const _async = this.async ? "async " : "";\n          return `${_async}function ${this.name}(${this.args})` + super.render(opts);\n        }\n      };\n      Func.kind = "func";\n      var Return = class extends ParentNode {\n        render(opts) {\n          return "return " + super.render(opts);\n        }\n      };\n      Return.kind = "return";\n      var Try = class extends BlockNode {\n        render(opts) {\n          let code = "try" + super.render(opts);\n          if (this.catch)\n            code += this.catch.render(opts);\n          if (this.finally)\n            code += this.finally.render(opts);\n          return code;\n        }\n        optimizeNodes() {\n          var _a, _b;\n          super.optimizeNodes();\n          (_a = this.catch) === null || _a === void 0 ? void 0 : _a.optimizeNodes();\n          (_b = this.finally) === null || _b === void 0 ? void 0 : _b.optimizeNodes();\n          return this;\n        }\n        optimizeNames(names, constants) {\n          var _a, _b;\n          super.optimizeNames(names, constants);\n          (_a = this.catch) === null || _a === void 0 ? void 0 : _a.optimizeNames(names, constants);\n          (_b = this.finally) === null || _b === void 0 ? void 0 : _b.optimizeNames(names, constants);\n          return this;\n        }\n        get names() {\n          const names = super.names;\n          if (this.catch)\n            addNames(names, this.catch.names);\n          if (this.finally)\n            addNames(names, this.finally.names);\n          return names;\n        }\n      };\n      var Catch = class extends BlockNode {\n        constructor(error) {\n          super();\n          this.error = error;\n        }\n        render(opts) {\n          return `catch(${this.error})` + super.render(opts);\n        }\n      };\n      Catch.kind = "catch";\n      var Finally = class extends BlockNode {\n        render(opts) {\n          return "finally" + super.render(opts);\n        }\n      };\n      Finally.kind = "finally";\n      var CodeGen = class {\n        constructor(extScope, opts = {}) {\n          this._values = {};\n          this._blockStarts = [];\n          this._constants = {};\n          this.opts = { ...opts, _n: opts.lines ? "\\n" : "" };\n          this._extScope = extScope;\n          this._scope = new scope_1.Scope({ parent: extScope });\n          this._nodes = [new Root()];\n        }\n        toString() {\n          return this._root.render(this.opts);\n        }\n        // returns unique name in the internal scope\n        name(prefix) {\n          return this._scope.name(prefix);\n        }\n        // reserves unique name in the external scope\n        scopeName(prefix) {\n          return this._extScope.name(prefix);\n        }\n        // reserves unique name in the external scope and assigns value to it\n        scopeValue(prefixOrName, value) {\n          const name = this._extScope.value(prefixOrName, value);\n          const vs = this._values[name.prefix] || (this._values[name.prefix] = /* @__PURE__ */ new Set());\n          vs.add(name);\n          return name;\n        }\n        getScopeValue(prefix, keyOrRef) {\n          return this._extScope.getValue(prefix, keyOrRef);\n        }\n        // return code that assigns values in the external scope to the names that are used internally\n        // (same names that were returned by gen.scopeName or gen.scopeValue)\n        scopeRefs(scopeName) {\n          return this._extScope.scopeRefs(scopeName, this._values);\n        }\n        scopeCode() {\n          return this._extScope.scopeCode(this._values);\n        }\n        _def(varKind, nameOrPrefix, rhs, constant) {\n          const name = this._scope.toName(nameOrPrefix);\n          if (rhs !== void 0 && constant)\n            this._constants[name.str] = rhs;\n          this._leafNode(new Def(varKind, name, rhs));\n          return name;\n        }\n        // `const` declaration (`var` in es5 mode)\n        const(nameOrPrefix, rhs, _constant) {\n          return this._def(scope_1.varKinds.const, nameOrPrefix, rhs, _constant);\n        }\n        // `let` declaration with optional assignment (`var` in es5 mode)\n        let(nameOrPrefix, rhs, _constant) {\n          return this._def(scope_1.varKinds.let, nameOrPrefix, rhs, _constant);\n        }\n        // `var` declaration with optional assignment\n        var(nameOrPrefix, rhs, _constant) {\n          return this._def(scope_1.varKinds.var, nameOrPrefix, rhs, _constant);\n        }\n        // assignment code\n        assign(lhs, rhs, sideEffects) {\n          return this._leafNode(new Assign(lhs, rhs, sideEffects));\n        }\n        // `+=` code\n        add(lhs, rhs) {\n          return this._leafNode(new AssignOp(lhs, exports.operators.ADD, rhs));\n        }\n        // appends passed SafeExpr to code or executes Block\n        code(c) {\n          if (typeof c == "function")\n            c();\n          else if (c !== code_1.nil)\n            this._leafNode(new AnyCode(c));\n          return this;\n        }\n        // returns code for object literal for the passed argument list of key-value pairs\n        object(...keyValues) {\n          const code = ["{"];\n          for (const [key, value] of keyValues) {\n            if (code.length > 1)\n              code.push(",");\n            code.push(key);\n            if (key !== value || this.opts.es5) {\n              code.push(":");\n              (0, code_1.addCodeArg)(code, value);\n            }\n          }\n          code.push("}");\n          return new code_1._Code(code);\n        }\n        // `if` clause (or statement if `thenBody` and, optionally, `elseBody` are passed)\n        if(condition, thenBody, elseBody) {\n          this._blockNode(new If(condition));\n          if (thenBody && elseBody) {\n            this.code(thenBody).else().code(elseBody).endIf();\n          } else if (thenBody) {\n            this.code(thenBody).endIf();\n          } else if (elseBody) {\n            throw new Error(\'CodeGen: "else" body without "then" body\');\n          }\n          return this;\n        }\n        // `else if` clause - invalid without `if` or after `else` clauses\n        elseIf(condition) {\n          return this._elseNode(new If(condition));\n        }\n        // `else` clause - only valid after `if` or `else if` clauses\n        else() {\n          return this._elseNode(new Else());\n        }\n        // end `if` statement (needed if gen.if was used only with condition)\n        endIf() {\n          return this._endBlockNode(If, Else);\n        }\n        _for(node, forBody) {\n          this._blockNode(node);\n          if (forBody)\n            this.code(forBody).endFor();\n          return this;\n        }\n        // a generic `for` clause (or statement if `forBody` is passed)\n        for(iteration, forBody) {\n          return this._for(new ForLoop(iteration), forBody);\n        }\n        // `for` statement for a range of values\n        forRange(nameOrPrefix, from, to, forBody, varKind = this.opts.es5 ? scope_1.varKinds.var : scope_1.varKinds.let) {\n          const name = this._scope.toName(nameOrPrefix);\n          return this._for(new ForRange(varKind, name, from, to), () => forBody(name));\n        }\n        // `for-of` statement (in es5 mode replace with a normal for loop)\n        forOf(nameOrPrefix, iterable, forBody, varKind = scope_1.varKinds.const) {\n          const name = this._scope.toName(nameOrPrefix);\n          if (this.opts.es5) {\n            const arr = iterable instanceof code_1.Name ? iterable : this.var("_arr", iterable);\n            return this.forRange("_i", 0, (0, code_1._)`${arr}.length`, (i) => {\n              this.var(name, (0, code_1._)`${arr}[${i}]`);\n              forBody(name);\n            });\n          }\n          return this._for(new ForIter("of", varKind, name, iterable), () => forBody(name));\n        }\n        // `for-in` statement.\n        // With option `ownProperties` replaced with a `for-of` loop for object keys\n        forIn(nameOrPrefix, obj, forBody, varKind = this.opts.es5 ? scope_1.varKinds.var : scope_1.varKinds.const) {\n          if (this.opts.ownProperties) {\n            return this.forOf(nameOrPrefix, (0, code_1._)`Object.keys(${obj})`, forBody);\n          }\n          const name = this._scope.toName(nameOrPrefix);\n          return this._for(new ForIter("in", varKind, name, obj), () => forBody(name));\n        }\n        // end `for` loop\n        endFor() {\n          return this._endBlockNode(For);\n        }\n        // `label` statement\n        label(label) {\n          return this._leafNode(new Label(label));\n        }\n        // `break` statement\n        break(label) {\n          return this._leafNode(new Break(label));\n        }\n        // `return` statement\n        return(value) {\n          const node = new Return();\n          this._blockNode(node);\n          this.code(value);\n          if (node.nodes.length !== 1)\n            throw new Error(\'CodeGen: "return" should have one node\');\n          return this._endBlockNode(Return);\n        }\n        // `try` statement\n        try(tryBody, catchCode, finallyCode) {\n          if (!catchCode && !finallyCode)\n            throw new Error(\'CodeGen: "try" without "catch" and "finally"\');\n          const node = new Try();\n          this._blockNode(node);\n          this.code(tryBody);\n          if (catchCode) {\n            const error = this.name("e");\n            this._currNode = node.catch = new Catch(error);\n            catchCode(error);\n          }\n          if (finallyCode) {\n            this._currNode = node.finally = new Finally();\n            this.code(finallyCode);\n          }\n          return this._endBlockNode(Catch, Finally);\n        }\n        // `throw` statement\n        throw(error) {\n          return this._leafNode(new Throw(error));\n        }\n        // start self-balancing block\n        block(body, nodeCount) {\n          this._blockStarts.push(this._nodes.length);\n          if (body)\n            this.code(body).endBlock(nodeCount);\n          return this;\n        }\n        // end the current self-balancing block\n        endBlock(nodeCount) {\n          const len = this._blockStarts.pop();\n          if (len === void 0)\n            throw new Error("CodeGen: not in self-balancing block");\n          const toClose = this._nodes.length - len;\n          if (toClose < 0 || nodeCount !== void 0 && toClose !== nodeCount) {\n            throw new Error(`CodeGen: wrong number of nodes: ${toClose} vs ${nodeCount} expected`);\n          }\n          this._nodes.length = len;\n          return this;\n        }\n        // `function` heading (or definition if funcBody is passed)\n        func(name, args = code_1.nil, async, funcBody) {\n          this._blockNode(new Func(name, args, async));\n          if (funcBody)\n            this.code(funcBody).endFunc();\n          return this;\n        }\n        // end function definition\n        endFunc() {\n          return this._endBlockNode(Func);\n        }\n        optimize(n = 1) {\n          while (n-- > 0) {\n            this._root.optimizeNodes();\n            this._root.optimizeNames(this._root.names, this._constants);\n          }\n        }\n        _leafNode(node) {\n          this._currNode.nodes.push(node);\n          return this;\n        }\n        _blockNode(node) {\n          this._currNode.nodes.push(node);\n          this._nodes.push(node);\n        }\n        _endBlockNode(N1, N2) {\n          const n = this._currNode;\n          if (n instanceof N1 || N2 && n instanceof N2) {\n            this._nodes.pop();\n            return this;\n          }\n          throw new Error(`CodeGen: not in block "${N2 ? `${N1.kind}/${N2.kind}` : N1.kind}"`);\n        }\n        _elseNode(node) {\n          const n = this._currNode;\n          if (!(n instanceof If)) {\n            throw new Error(\'CodeGen: "else" without "if"\');\n          }\n          this._currNode = n.else = node;\n          return this;\n        }\n        get _root() {\n          return this._nodes[0];\n        }\n        get _currNode() {\n          const ns = this._nodes;\n          return ns[ns.length - 1];\n        }\n        set _currNode(node) {\n          const ns = this._nodes;\n          ns[ns.length - 1] = node;\n        }\n      };\n      exports.CodeGen = CodeGen;\n      function addNames(names, from) {\n        for (const n in from)\n          names[n] = (names[n] || 0) + (from[n] || 0);\n        return names;\n      }\n      function addExprNames(names, from) {\n        return from instanceof code_1._CodeOrName ? addNames(names, from.names) : names;\n      }\n      function optimizeExpr(expr, names, constants) {\n        if (expr instanceof code_1.Name)\n          return replaceName(expr);\n        if (!canOptimize(expr))\n          return expr;\n        return new code_1._Code(expr._items.reduce((items, c) => {\n          if (c instanceof code_1.Name)\n            c = replaceName(c);\n          if (c instanceof code_1._Code)\n            items.push(...c._items);\n          else\n            items.push(c);\n          return items;\n        }, []));\n        function replaceName(n) {\n          const c = constants[n.str];\n          if (c === void 0 || names[n.str] !== 1)\n            return n;\n          delete names[n.str];\n          return c;\n        }\n        function canOptimize(e) {\n          return e instanceof code_1._Code && e._items.some((c) => c instanceof code_1.Name && names[c.str] === 1 && constants[c.str] !== void 0);\n        }\n      }\n      function subtractNames(names, from) {\n        for (const n in from)\n          names[n] = (names[n] || 0) - (from[n] || 0);\n      }\n      function not(x) {\n        return typeof x == "boolean" || typeof x == "number" || x === null ? !x : (0, code_1._)`!${par(x)}`;\n      }\n      exports.not = not;\n      var andCode = mappend(exports.operators.AND);\n      function and(...args) {\n        return args.reduce(andCode);\n      }\n      exports.and = and;\n      var orCode = mappend(exports.operators.OR);\n      function or(...args) {\n        return args.reduce(orCode);\n      }\n      exports.or = or;\n      function mappend(op) {\n        return (x, y) => x === code_1.nil ? y : y === code_1.nil ? x : (0, code_1._)`${par(x)} ${op} ${par(y)}`;\n      }\n      function par(x) {\n        return x instanceof code_1.Name ? x : (0, code_1._)`(${x})`;\n      }\n    }\n  });\n\n  // node_modules/ajv/dist/compile/util.js\n  var require_util = __commonJS({\n    "node_modules/ajv/dist/compile/util.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.checkStrictMode = exports.getErrorPath = exports.Type = exports.useFunc = exports.setEvaluated = exports.evaluatedPropsToName = exports.mergeEvaluated = exports.eachItem = exports.unescapeJsonPointer = exports.escapeJsonPointer = exports.escapeFragment = exports.unescapeFragment = exports.schemaRefOrVal = exports.schemaHasRulesButRef = exports.schemaHasRules = exports.checkUnknownRules = exports.alwaysValidSchema = exports.toHash = void 0;\n      var codegen_1 = require_codegen();\n      var code_1 = require_code();\n      function toHash(arr) {\n        const hash = {};\n        for (const item of arr)\n          hash[item] = true;\n        return hash;\n      }\n      exports.toHash = toHash;\n      function alwaysValidSchema(it, schema) {\n        if (typeof schema == "boolean")\n          return schema;\n        if (Object.keys(schema).length === 0)\n          return true;\n        checkUnknownRules(it, schema);\n        return !schemaHasRules(schema, it.self.RULES.all);\n      }\n      exports.alwaysValidSchema = alwaysValidSchema;\n      function checkUnknownRules(it, schema = it.schema) {\n        const { opts, self: self2 } = it;\n        if (!opts.strictSchema)\n          return;\n        if (typeof schema === "boolean")\n          return;\n        const rules = self2.RULES.keywords;\n        for (const key in schema) {\n          if (!rules[key])\n            checkStrictMode(it, `unknown keyword: "${key}"`);\n        }\n      }\n      exports.checkUnknownRules = checkUnknownRules;\n      function schemaHasRules(schema, rules) {\n        if (typeof schema == "boolean")\n          return !schema;\n        for (const key in schema)\n          if (rules[key])\n            return true;\n        return false;\n      }\n      exports.schemaHasRules = schemaHasRules;\n      function schemaHasRulesButRef(schema, RULES) {\n        if (typeof schema == "boolean")\n          return !schema;\n        for (const key in schema)\n          if (key !== "$ref" && RULES.all[key])\n            return true;\n        return false;\n      }\n      exports.schemaHasRulesButRef = schemaHasRulesButRef;\n      function schemaRefOrVal({ topSchemaRef, schemaPath }, schema, keyword, $data) {\n        if (!$data) {\n          if (typeof schema == "number" || typeof schema == "boolean")\n            return schema;\n          if (typeof schema == "string")\n            return (0, codegen_1._)`${schema}`;\n        }\n        return (0, codegen_1._)`${topSchemaRef}${schemaPath}${(0, codegen_1.getProperty)(keyword)}`;\n      }\n      exports.schemaRefOrVal = schemaRefOrVal;\n      function unescapeFragment(str) {\n        return unescapeJsonPointer(decodeURIComponent(str));\n      }\n      exports.unescapeFragment = unescapeFragment;\n      function escapeFragment(str) {\n        return encodeURIComponent(escapeJsonPointer(str));\n      }\n      exports.escapeFragment = escapeFragment;\n      function escapeJsonPointer(str) {\n        if (typeof str == "number")\n          return `${str}`;\n        return str.replace(/~/g, "~0").replace(/\\//g, "~1");\n      }\n      exports.escapeJsonPointer = escapeJsonPointer;\n      function unescapeJsonPointer(str) {\n        return str.replace(/~1/g, "/").replace(/~0/g, "~");\n      }\n      exports.unescapeJsonPointer = unescapeJsonPointer;\n      function eachItem(xs, f) {\n        if (Array.isArray(xs)) {\n          for (const x of xs)\n            f(x);\n        } else {\n          f(xs);\n        }\n      }\n      exports.eachItem = eachItem;\n      function makeMergeEvaluated({ mergeNames, mergeToName, mergeValues, resultToName }) {\n        return (gen, from, to, toName) => {\n          const res = to === void 0 ? from : to instanceof codegen_1.Name ? (from instanceof codegen_1.Name ? mergeNames(gen, from, to) : mergeToName(gen, from, to), to) : from instanceof codegen_1.Name ? (mergeToName(gen, to, from), from) : mergeValues(from, to);\n          return toName === codegen_1.Name && !(res instanceof codegen_1.Name) ? resultToName(gen, res) : res;\n        };\n      }\n      exports.mergeEvaluated = {\n        props: makeMergeEvaluated({\n          mergeNames: (gen, from, to) => gen.if((0, codegen_1._)`${to} !== true && ${from} !== undefined`, () => {\n            gen.if((0, codegen_1._)`${from} === true`, () => gen.assign(to, true), () => gen.assign(to, (0, codegen_1._)`${to} || {}`).code((0, codegen_1._)`Object.assign(${to}, ${from})`));\n          }),\n          mergeToName: (gen, from, to) => gen.if((0, codegen_1._)`${to} !== true`, () => {\n            if (from === true) {\n              gen.assign(to, true);\n            } else {\n              gen.assign(to, (0, codegen_1._)`${to} || {}`);\n              setEvaluated(gen, to, from);\n            }\n          }),\n          mergeValues: (from, to) => from === true ? true : { ...from, ...to },\n          resultToName: evaluatedPropsToName\n        }),\n        items: makeMergeEvaluated({\n          mergeNames: (gen, from, to) => gen.if((0, codegen_1._)`${to} !== true && ${from} !== undefined`, () => gen.assign(to, (0, codegen_1._)`${from} === true ? true : ${to} > ${from} ? ${to} : ${from}`)),\n          mergeToName: (gen, from, to) => gen.if((0, codegen_1._)`${to} !== true`, () => gen.assign(to, from === true ? true : (0, codegen_1._)`${to} > ${from} ? ${to} : ${from}`)),\n          mergeValues: (from, to) => from === true ? true : Math.max(from, to),\n          resultToName: (gen, items) => gen.var("items", items)\n        })\n      };\n      function evaluatedPropsToName(gen, ps) {\n        if (ps === true)\n          return gen.var("props", true);\n        const props = gen.var("props", (0, codegen_1._)`{}`);\n        if (ps !== void 0)\n          setEvaluated(gen, props, ps);\n        return props;\n      }\n      exports.evaluatedPropsToName = evaluatedPropsToName;\n      function setEvaluated(gen, props, ps) {\n        Object.keys(ps).forEach((p) => gen.assign((0, codegen_1._)`${props}${(0, codegen_1.getProperty)(p)}`, true));\n      }\n      exports.setEvaluated = setEvaluated;\n      var snippets = {};\n      function useFunc(gen, f) {\n        return gen.scopeValue("func", {\n          ref: f,\n          code: snippets[f.code] || (snippets[f.code] = new code_1._Code(f.code))\n        });\n      }\n      exports.useFunc = useFunc;\n      var Type;\n      (function(Type2) {\n        Type2[Type2["Num"] = 0] = "Num";\n        Type2[Type2["Str"] = 1] = "Str";\n      })(Type || (exports.Type = Type = {}));\n      function getErrorPath(dataProp, dataPropType, jsPropertySyntax) {\n        if (dataProp instanceof codegen_1.Name) {\n          const isNumber = dataPropType === Type.Num;\n          return jsPropertySyntax ? isNumber ? (0, codegen_1._)`"[" + ${dataProp} + "]"` : (0, codegen_1._)`"[\'" + ${dataProp} + "\']"` : isNumber ? (0, codegen_1._)`"/" + ${dataProp}` : (0, codegen_1._)`"/" + ${dataProp}.replace(/~/g, "~0").replace(/\\\\//g, "~1")`;\n        }\n        return jsPropertySyntax ? (0, codegen_1.getProperty)(dataProp).toString() : "/" + escapeJsonPointer(dataProp);\n      }\n      exports.getErrorPath = getErrorPath;\n      function checkStrictMode(it, msg, mode = it.opts.strictSchema) {\n        if (!mode)\n          return;\n        msg = `strict mode: ${msg}`;\n        if (mode === true)\n          throw new Error(msg);\n        it.self.logger.warn(msg);\n      }\n      exports.checkStrictMode = checkStrictMode;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/names.js\n  var require_names = __commonJS({\n    "node_modules/ajv/dist/compile/names.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var names = {\n        // validation function arguments\n        data: new codegen_1.Name("data"),\n        // data passed to validation function\n        // args passed from referencing schema\n        valCxt: new codegen_1.Name("valCxt"),\n        // validation/data context - should not be used directly, it is destructured to the names below\n        instancePath: new codegen_1.Name("instancePath"),\n        parentData: new codegen_1.Name("parentData"),\n        parentDataProperty: new codegen_1.Name("parentDataProperty"),\n        rootData: new codegen_1.Name("rootData"),\n        // root data - same as the data passed to the first/top validation function\n        dynamicAnchors: new codegen_1.Name("dynamicAnchors"),\n        // used to support recursiveRef and dynamicRef\n        // function scoped variables\n        vErrors: new codegen_1.Name("vErrors"),\n        // null or array of validation errors\n        errors: new codegen_1.Name("errors"),\n        // counter of validation errors\n        this: new codegen_1.Name("this"),\n        // "globals"\n        self: new codegen_1.Name("self"),\n        scope: new codegen_1.Name("scope"),\n        // JTD serialize/parse name for JSON string and position\n        json: new codegen_1.Name("json"),\n        jsonPos: new codegen_1.Name("jsonPos"),\n        jsonLen: new codegen_1.Name("jsonLen"),\n        jsonPart: new codegen_1.Name("jsonPart")\n      };\n      exports.default = names;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/errors.js\n  var require_errors = __commonJS({\n    "node_modules/ajv/dist/compile/errors.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.extendErrors = exports.resetErrorsCount = exports.reportExtraError = exports.reportError = exports.keyword$DataError = exports.keywordError = void 0;\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var names_1 = require_names();\n      exports.keywordError = {\n        message: ({ keyword }) => (0, codegen_1.str)`must pass "${keyword}" keyword validation`\n      };\n      exports.keyword$DataError = {\n        message: ({ keyword, schemaType }) => schemaType ? (0, codegen_1.str)`"${keyword}" keyword must be ${schemaType} ($data)` : (0, codegen_1.str)`"${keyword}" keyword is invalid ($data)`\n      };\n      function reportError(cxt, error = exports.keywordError, errorPaths, overrideAllErrors) {\n        const { it } = cxt;\n        const { gen, compositeRule, allErrors } = it;\n        const errObj = errorObjectCode(cxt, error, errorPaths);\n        if (overrideAllErrors !== null && overrideAllErrors !== void 0 ? overrideAllErrors : compositeRule || allErrors) {\n          addError(gen, errObj);\n        } else {\n          returnErrors(it, (0, codegen_1._)`[${errObj}]`);\n        }\n      }\n      exports.reportError = reportError;\n      function reportExtraError(cxt, error = exports.keywordError, errorPaths) {\n        const { it } = cxt;\n        const { gen, compositeRule, allErrors } = it;\n        const errObj = errorObjectCode(cxt, error, errorPaths);\n        addError(gen, errObj);\n        if (!(compositeRule || allErrors)) {\n          returnErrors(it, names_1.default.vErrors);\n        }\n      }\n      exports.reportExtraError = reportExtraError;\n      function resetErrorsCount(gen, errsCount) {\n        gen.assign(names_1.default.errors, errsCount);\n        gen.if((0, codegen_1._)`${names_1.default.vErrors} !== null`, () => gen.if(errsCount, () => gen.assign((0, codegen_1._)`${names_1.default.vErrors}.length`, errsCount), () => gen.assign(names_1.default.vErrors, null)));\n      }\n      exports.resetErrorsCount = resetErrorsCount;\n      function extendErrors({ gen, keyword, schemaValue, data, errsCount, it }) {\n        if (errsCount === void 0)\n          throw new Error("ajv implementation error");\n        const err = gen.name("err");\n        gen.forRange("i", errsCount, names_1.default.errors, (i) => {\n          gen.const(err, (0, codegen_1._)`${names_1.default.vErrors}[${i}]`);\n          gen.if((0, codegen_1._)`${err}.instancePath === undefined`, () => gen.assign((0, codegen_1._)`${err}.instancePath`, (0, codegen_1.strConcat)(names_1.default.instancePath, it.errorPath)));\n          gen.assign((0, codegen_1._)`${err}.schemaPath`, (0, codegen_1.str)`${it.errSchemaPath}/${keyword}`);\n          if (it.opts.verbose) {\n            gen.assign((0, codegen_1._)`${err}.schema`, schemaValue);\n            gen.assign((0, codegen_1._)`${err}.data`, data);\n          }\n        });\n      }\n      exports.extendErrors = extendErrors;\n      function addError(gen, errObj) {\n        const err = gen.const("err", errObj);\n        gen.if((0, codegen_1._)`${names_1.default.vErrors} === null`, () => gen.assign(names_1.default.vErrors, (0, codegen_1._)`[${err}]`), (0, codegen_1._)`${names_1.default.vErrors}.push(${err})`);\n        gen.code((0, codegen_1._)`${names_1.default.errors}++`);\n      }\n      function returnErrors(it, errs) {\n        const { gen, validateName, schemaEnv } = it;\n        if (schemaEnv.$async) {\n          gen.throw((0, codegen_1._)`new ${it.ValidationError}(${errs})`);\n        } else {\n          gen.assign((0, codegen_1._)`${validateName}.errors`, errs);\n          gen.return(false);\n        }\n      }\n      var E = {\n        keyword: new codegen_1.Name("keyword"),\n        schemaPath: new codegen_1.Name("schemaPath"),\n        // also used in JTD errors\n        params: new codegen_1.Name("params"),\n        propertyName: new codegen_1.Name("propertyName"),\n        message: new codegen_1.Name("message"),\n        schema: new codegen_1.Name("schema"),\n        parentSchema: new codegen_1.Name("parentSchema")\n      };\n      function errorObjectCode(cxt, error, errorPaths) {\n        const { createErrors } = cxt.it;\n        if (createErrors === false)\n          return (0, codegen_1._)`{}`;\n        return errorObject(cxt, error, errorPaths);\n      }\n      function errorObject(cxt, error, errorPaths = {}) {\n        const { gen, it } = cxt;\n        const keyValues = [\n          errorInstancePath(it, errorPaths),\n          errorSchemaPath(cxt, errorPaths)\n        ];\n        extraErrorProps(cxt, error, keyValues);\n        return gen.object(...keyValues);\n      }\n      function errorInstancePath({ errorPath }, { instancePath }) {\n        const instPath = instancePath ? (0, codegen_1.str)`${errorPath}${(0, util_1.getErrorPath)(instancePath, util_1.Type.Str)}` : errorPath;\n        return [names_1.default.instancePath, (0, codegen_1.strConcat)(names_1.default.instancePath, instPath)];\n      }\n      function errorSchemaPath({ keyword, it: { errSchemaPath } }, { schemaPath, parentSchema }) {\n        let schPath = parentSchema ? errSchemaPath : (0, codegen_1.str)`${errSchemaPath}/${keyword}`;\n        if (schemaPath) {\n          schPath = (0, codegen_1.str)`${schPath}${(0, util_1.getErrorPath)(schemaPath, util_1.Type.Str)}`;\n        }\n        return [E.schemaPath, schPath];\n      }\n      function extraErrorProps(cxt, { params, message }, keyValues) {\n        const { keyword, data, schemaValue, it } = cxt;\n        const { opts, propertyName, topSchemaRef, schemaPath } = it;\n        keyValues.push([E.keyword, keyword], [E.params, typeof params == "function" ? params(cxt) : params || (0, codegen_1._)`{}`]);\n        if (opts.messages) {\n          keyValues.push([E.message, typeof message == "function" ? message(cxt) : message]);\n        }\n        if (opts.verbose) {\n          keyValues.push([E.schema, schemaValue], [E.parentSchema, (0, codegen_1._)`${topSchemaRef}${schemaPath}`], [names_1.default.data, data]);\n        }\n        if (propertyName)\n          keyValues.push([E.propertyName, propertyName]);\n      }\n    }\n  });\n\n  // node_modules/ajv/dist/compile/validate/boolSchema.js\n  var require_boolSchema = __commonJS({\n    "node_modules/ajv/dist/compile/validate/boolSchema.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.boolOrEmptySchema = exports.topBoolOrEmptySchema = void 0;\n      var errors_1 = require_errors();\n      var codegen_1 = require_codegen();\n      var names_1 = require_names();\n      var boolError = {\n        message: "boolean schema is false"\n      };\n      function topBoolOrEmptySchema(it) {\n        const { gen, schema, validateName } = it;\n        if (schema === false) {\n          falseSchemaError(it, false);\n        } else if (typeof schema == "object" && schema.$async === true) {\n          gen.return(names_1.default.data);\n        } else {\n          gen.assign((0, codegen_1._)`${validateName}.errors`, null);\n          gen.return(true);\n        }\n      }\n      exports.topBoolOrEmptySchema = topBoolOrEmptySchema;\n      function boolOrEmptySchema(it, valid) {\n        const { gen, schema } = it;\n        if (schema === false) {\n          gen.var(valid, false);\n          falseSchemaError(it);\n        } else {\n          gen.var(valid, true);\n        }\n      }\n      exports.boolOrEmptySchema = boolOrEmptySchema;\n      function falseSchemaError(it, overrideAllErrors) {\n        const { gen, data } = it;\n        const cxt = {\n          gen,\n          keyword: "false schema",\n          data,\n          schema: false,\n          schemaCode: false,\n          schemaValue: false,\n          params: {},\n          it\n        };\n        (0, errors_1.reportError)(cxt, boolError, void 0, overrideAllErrors);\n      }\n    }\n  });\n\n  // node_modules/ajv/dist/compile/rules.js\n  var require_rules = __commonJS({\n    "node_modules/ajv/dist/compile/rules.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.getRules = exports.isJSONType = void 0;\n      var _jsonTypes = ["string", "number", "integer", "boolean", "null", "object", "array"];\n      var jsonTypes = new Set(_jsonTypes);\n      function isJSONType(x) {\n        return typeof x == "string" && jsonTypes.has(x);\n      }\n      exports.isJSONType = isJSONType;\n      function getRules() {\n        const groups = {\n          number: { type: "number", rules: [] },\n          string: { type: "string", rules: [] },\n          array: { type: "array", rules: [] },\n          object: { type: "object", rules: [] }\n        };\n        return {\n          types: { ...groups, integer: true, boolean: true, null: true },\n          rules: [{ rules: [] }, groups.number, groups.string, groups.array, groups.object],\n          post: { rules: [] },\n          all: {},\n          keywords: {}\n        };\n      }\n      exports.getRules = getRules;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/validate/applicability.js\n  var require_applicability = __commonJS({\n    "node_modules/ajv/dist/compile/validate/applicability.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.shouldUseRule = exports.shouldUseGroup = exports.schemaHasRulesForType = void 0;\n      function schemaHasRulesForType({ schema, self: self2 }, type) {\n        const group = self2.RULES.types[type];\n        return group && group !== true && shouldUseGroup(schema, group);\n      }\n      exports.schemaHasRulesForType = schemaHasRulesForType;\n      function shouldUseGroup(schema, group) {\n        return group.rules.some((rule) => shouldUseRule(schema, rule));\n      }\n      exports.shouldUseGroup = shouldUseGroup;\n      function shouldUseRule(schema, rule) {\n        var _a;\n        return schema[rule.keyword] !== void 0 || ((_a = rule.definition.implements) === null || _a === void 0 ? void 0 : _a.some((kwd) => schema[kwd] !== void 0));\n      }\n      exports.shouldUseRule = shouldUseRule;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/validate/dataType.js\n  var require_dataType = __commonJS({\n    "node_modules/ajv/dist/compile/validate/dataType.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.reportTypeError = exports.checkDataTypes = exports.checkDataType = exports.coerceAndCheckDataType = exports.getJSONTypes = exports.getSchemaTypes = exports.DataType = void 0;\n      var rules_1 = require_rules();\n      var applicability_1 = require_applicability();\n      var errors_1 = require_errors();\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var DataType;\n      (function(DataType2) {\n        DataType2[DataType2["Correct"] = 0] = "Correct";\n        DataType2[DataType2["Wrong"] = 1] = "Wrong";\n      })(DataType || (exports.DataType = DataType = {}));\n      function getSchemaTypes(schema) {\n        const types = getJSONTypes(schema.type);\n        const hasNull = types.includes("null");\n        if (hasNull) {\n          if (schema.nullable === false)\n            throw new Error("type: null contradicts nullable: false");\n        } else {\n          if (!types.length && schema.nullable !== void 0) {\n            throw new Error(\'"nullable" cannot be used without "type"\');\n          }\n          if (schema.nullable === true)\n            types.push("null");\n        }\n        return types;\n      }\n      exports.getSchemaTypes = getSchemaTypes;\n      function getJSONTypes(ts) {\n        const types = Array.isArray(ts) ? ts : ts ? [ts] : [];\n        if (types.every(rules_1.isJSONType))\n          return types;\n        throw new Error("type must be JSONType or JSONType[]: " + types.join(","));\n      }\n      exports.getJSONTypes = getJSONTypes;\n      function coerceAndCheckDataType(it, types) {\n        const { gen, data, opts } = it;\n        const coerceTo = coerceToTypes(types, opts.coerceTypes);\n        const checkTypes = types.length > 0 && !(coerceTo.length === 0 && types.length === 1 && (0, applicability_1.schemaHasRulesForType)(it, types[0]));\n        if (checkTypes) {\n          const wrongType = checkDataTypes(types, data, opts.strictNumbers, DataType.Wrong);\n          gen.if(wrongType, () => {\n            if (coerceTo.length)\n              coerceData(it, types, coerceTo);\n            else\n              reportTypeError(it);\n          });\n        }\n        return checkTypes;\n      }\n      exports.coerceAndCheckDataType = coerceAndCheckDataType;\n      var COERCIBLE = /* @__PURE__ */ new Set(["string", "number", "integer", "boolean", "null"]);\n      function coerceToTypes(types, coerceTypes) {\n        return coerceTypes ? types.filter((t) => COERCIBLE.has(t) || coerceTypes === "array" && t === "array") : [];\n      }\n      function coerceData(it, types, coerceTo) {\n        const { gen, data, opts } = it;\n        const dataType = gen.let("dataType", (0, codegen_1._)`typeof ${data}`);\n        const coerced = gen.let("coerced", (0, codegen_1._)`undefined`);\n        if (opts.coerceTypes === "array") {\n          gen.if((0, codegen_1._)`${dataType} == \'object\' && Array.isArray(${data}) && ${data}.length == 1`, () => gen.assign(data, (0, codegen_1._)`${data}[0]`).assign(dataType, (0, codegen_1._)`typeof ${data}`).if(checkDataTypes(types, data, opts.strictNumbers), () => gen.assign(coerced, data)));\n        }\n        gen.if((0, codegen_1._)`${coerced} !== undefined`);\n        for (const t of coerceTo) {\n          if (COERCIBLE.has(t) || t === "array" && opts.coerceTypes === "array") {\n            coerceSpecificType(t);\n          }\n        }\n        gen.else();\n        reportTypeError(it);\n        gen.endIf();\n        gen.if((0, codegen_1._)`${coerced} !== undefined`, () => {\n          gen.assign(data, coerced);\n          assignParentData(it, coerced);\n        });\n        function coerceSpecificType(t) {\n          switch (t) {\n            case "string":\n              gen.elseIf((0, codegen_1._)`${dataType} == "number" || ${dataType} == "boolean"`).assign(coerced, (0, codegen_1._)`"" + ${data}`).elseIf((0, codegen_1._)`${data} === null`).assign(coerced, (0, codegen_1._)`""`);\n              return;\n            case "number":\n              gen.elseIf((0, codegen_1._)`${dataType} == "boolean" || ${data} === null\n              || (${dataType} == "string" && ${data} && ${data} == +${data})`).assign(coerced, (0, codegen_1._)`+${data}`);\n              return;\n            case "integer":\n              gen.elseIf((0, codegen_1._)`${dataType} === "boolean" || ${data} === null\n              || (${dataType} === "string" && ${data} && ${data} == +${data} && !(${data} % 1))`).assign(coerced, (0, codegen_1._)`+${data}`);\n              return;\n            case "boolean":\n              gen.elseIf((0, codegen_1._)`${data} === "false" || ${data} === 0 || ${data} === null`).assign(coerced, false).elseIf((0, codegen_1._)`${data} === "true" || ${data} === 1`).assign(coerced, true);\n              return;\n            case "null":\n              gen.elseIf((0, codegen_1._)`${data} === "" || ${data} === 0 || ${data} === false`);\n              gen.assign(coerced, null);\n              return;\n            case "array":\n              gen.elseIf((0, codegen_1._)`${dataType} === "string" || ${dataType} === "number"\n              || ${dataType} === "boolean" || ${data} === null`).assign(coerced, (0, codegen_1._)`[${data}]`);\n          }\n        }\n      }\n      function assignParentData({ gen, parentData, parentDataProperty }, expr) {\n        gen.if((0, codegen_1._)`${parentData} !== undefined`, () => gen.assign((0, codegen_1._)`${parentData}[${parentDataProperty}]`, expr));\n      }\n      function checkDataType(dataType, data, strictNums, correct = DataType.Correct) {\n        const EQ = correct === DataType.Correct ? codegen_1.operators.EQ : codegen_1.operators.NEQ;\n        let cond;\n        switch (dataType) {\n          case "null":\n            return (0, codegen_1._)`${data} ${EQ} null`;\n          case "array":\n            cond = (0, codegen_1._)`Array.isArray(${data})`;\n            break;\n          case "object":\n            cond = (0, codegen_1._)`${data} && typeof ${data} == "object" && !Array.isArray(${data})`;\n            break;\n          case "integer":\n            cond = numCond((0, codegen_1._)`!(${data} % 1) && !isNaN(${data})`);\n            break;\n          case "number":\n            cond = numCond();\n            break;\n          default:\n            return (0, codegen_1._)`typeof ${data} ${EQ} ${dataType}`;\n        }\n        return correct === DataType.Correct ? cond : (0, codegen_1.not)(cond);\n        function numCond(_cond = codegen_1.nil) {\n          return (0, codegen_1.and)((0, codegen_1._)`typeof ${data} == "number"`, _cond, strictNums ? (0, codegen_1._)`isFinite(${data})` : codegen_1.nil);\n        }\n      }\n      exports.checkDataType = checkDataType;\n      function checkDataTypes(dataTypes, data, strictNums, correct) {\n        if (dataTypes.length === 1) {\n          return checkDataType(dataTypes[0], data, strictNums, correct);\n        }\n        let cond;\n        const types = (0, util_1.toHash)(dataTypes);\n        if (types.array && types.object) {\n          const notObj = (0, codegen_1._)`typeof ${data} != "object"`;\n          cond = types.null ? notObj : (0, codegen_1._)`!${data} || ${notObj}`;\n          delete types.null;\n          delete types.array;\n          delete types.object;\n        } else {\n          cond = codegen_1.nil;\n        }\n        if (types.number)\n          delete types.integer;\n        for (const t in types)\n          cond = (0, codegen_1.and)(cond, checkDataType(t, data, strictNums, correct));\n        return cond;\n      }\n      exports.checkDataTypes = checkDataTypes;\n      var typeError = {\n        message: ({ schema }) => `must be ${schema}`,\n        params: ({ schema, schemaValue }) => typeof schema == "string" ? (0, codegen_1._)`{type: ${schema}}` : (0, codegen_1._)`{type: ${schemaValue}}`\n      };\n      function reportTypeError(it) {\n        const cxt = getTypeErrorContext(it);\n        (0, errors_1.reportError)(cxt, typeError);\n      }\n      exports.reportTypeError = reportTypeError;\n      function getTypeErrorContext(it) {\n        const { gen, data, schema } = it;\n        const schemaCode = (0, util_1.schemaRefOrVal)(it, schema, "type");\n        return {\n          gen,\n          keyword: "type",\n          data,\n          schema: schema.type,\n          schemaCode,\n          schemaValue: schemaCode,\n          parentSchema: schema,\n          params: {},\n          it\n        };\n      }\n    }\n  });\n\n  // node_modules/ajv/dist/compile/validate/defaults.js\n  var require_defaults = __commonJS({\n    "node_modules/ajv/dist/compile/validate/defaults.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.assignDefaults = void 0;\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      function assignDefaults(it, ty) {\n        const { properties, items } = it.schema;\n        if (ty === "object" && properties) {\n          for (const key in properties) {\n            assignDefault(it, key, properties[key].default);\n          }\n        } else if (ty === "array" && Array.isArray(items)) {\n          items.forEach((sch, i) => assignDefault(it, i, sch.default));\n        }\n      }\n      exports.assignDefaults = assignDefaults;\n      function assignDefault(it, prop, defaultValue) {\n        const { gen, compositeRule, data, opts } = it;\n        if (defaultValue === void 0)\n          return;\n        const childData = (0, codegen_1._)`${data}${(0, codegen_1.getProperty)(prop)}`;\n        if (compositeRule) {\n          (0, util_1.checkStrictMode)(it, `default is ignored for: ${childData}`);\n          return;\n        }\n        let condition = (0, codegen_1._)`${childData} === undefined`;\n        if (opts.useDefaults === "empty") {\n          condition = (0, codegen_1._)`${condition} || ${childData} === null || ${childData} === ""`;\n        }\n        gen.if(condition, (0, codegen_1._)`${childData} = ${(0, codegen_1.stringify)(defaultValue)}`);\n      }\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/code.js\n  var require_code2 = __commonJS({\n    "node_modules/ajv/dist/vocabularies/code.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.validateUnion = exports.validateArray = exports.usePattern = exports.callValidateCode = exports.schemaProperties = exports.allSchemaProperties = exports.noPropertyInData = exports.propertyInData = exports.isOwnProperty = exports.hasPropFunc = exports.reportMissingProp = exports.checkMissingProp = exports.checkReportMissingProp = void 0;\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var names_1 = require_names();\n      var util_2 = require_util();\n      function checkReportMissingProp(cxt, prop) {\n        const { gen, data, it } = cxt;\n        gen.if(noPropertyInData(gen, data, prop, it.opts.ownProperties), () => {\n          cxt.setParams({ missingProperty: (0, codegen_1._)`${prop}` }, true);\n          cxt.error();\n        });\n      }\n      exports.checkReportMissingProp = checkReportMissingProp;\n      function checkMissingProp({ gen, data, it: { opts } }, properties, missing) {\n        return (0, codegen_1.or)(...properties.map((prop) => (0, codegen_1.and)(noPropertyInData(gen, data, prop, opts.ownProperties), (0, codegen_1._)`${missing} = ${prop}`)));\n      }\n      exports.checkMissingProp = checkMissingProp;\n      function reportMissingProp(cxt, missing) {\n        cxt.setParams({ missingProperty: missing }, true);\n        cxt.error();\n      }\n      exports.reportMissingProp = reportMissingProp;\n      function hasPropFunc(gen) {\n        return gen.scopeValue("func", {\n          // eslint-disable-next-line @typescript-eslint/unbound-method\n          ref: Object.prototype.hasOwnProperty,\n          code: (0, codegen_1._)`Object.prototype.hasOwnProperty`\n        });\n      }\n      exports.hasPropFunc = hasPropFunc;\n      function isOwnProperty(gen, data, property) {\n        return (0, codegen_1._)`${hasPropFunc(gen)}.call(${data}, ${property})`;\n      }\n      exports.isOwnProperty = isOwnProperty;\n      function propertyInData(gen, data, property, ownProperties) {\n        const cond = (0, codegen_1._)`${data}${(0, codegen_1.getProperty)(property)} !== undefined`;\n        return ownProperties ? (0, codegen_1._)`${cond} && ${isOwnProperty(gen, data, property)}` : cond;\n      }\n      exports.propertyInData = propertyInData;\n      function noPropertyInData(gen, data, property, ownProperties) {\n        const cond = (0, codegen_1._)`${data}${(0, codegen_1.getProperty)(property)} === undefined`;\n        return ownProperties ? (0, codegen_1.or)(cond, (0, codegen_1.not)(isOwnProperty(gen, data, property))) : cond;\n      }\n      exports.noPropertyInData = noPropertyInData;\n      function allSchemaProperties(schemaMap) {\n        return schemaMap ? Object.keys(schemaMap).filter((p) => p !== "__proto__") : [];\n      }\n      exports.allSchemaProperties = allSchemaProperties;\n      function schemaProperties(it, schemaMap) {\n        return allSchemaProperties(schemaMap).filter((p) => !(0, util_1.alwaysValidSchema)(it, schemaMap[p]));\n      }\n      exports.schemaProperties = schemaProperties;\n      function callValidateCode({ schemaCode, data, it: { gen, topSchemaRef, schemaPath, errorPath }, it }, func, context, passSchema) {\n        const dataAndSchema = passSchema ? (0, codegen_1._)`${schemaCode}, ${data}, ${topSchemaRef}${schemaPath}` : data;\n        const valCxt = [\n          [names_1.default.instancePath, (0, codegen_1.strConcat)(names_1.default.instancePath, errorPath)],\n          [names_1.default.parentData, it.parentData],\n          [names_1.default.parentDataProperty, it.parentDataProperty],\n          [names_1.default.rootData, names_1.default.rootData]\n        ];\n        if (it.opts.dynamicRef)\n          valCxt.push([names_1.default.dynamicAnchors, names_1.default.dynamicAnchors]);\n        const args = (0, codegen_1._)`${dataAndSchema}, ${gen.object(...valCxt)}`;\n        return context !== codegen_1.nil ? (0, codegen_1._)`${func}.call(${context}, ${args})` : (0, codegen_1._)`${func}(${args})`;\n      }\n      exports.callValidateCode = callValidateCode;\n      var newRegExp = (0, codegen_1._)`new RegExp`;\n      function usePattern({ gen, it: { opts } }, pattern) {\n        const u = opts.unicodeRegExp ? "u" : "";\n        const { regExp } = opts.code;\n        const rx = regExp(pattern, u);\n        return gen.scopeValue("pattern", {\n          key: rx.toString(),\n          ref: rx,\n          code: (0, codegen_1._)`${regExp.code === "new RegExp" ? newRegExp : (0, util_2.useFunc)(gen, regExp)}(${pattern}, ${u})`\n        });\n      }\n      exports.usePattern = usePattern;\n      function validateArray(cxt) {\n        const { gen, data, keyword, it } = cxt;\n        const valid = gen.name("valid");\n        if (it.allErrors) {\n          const validArr = gen.let("valid", true);\n          validateItems(() => gen.assign(validArr, false));\n          return validArr;\n        }\n        gen.var(valid, true);\n        validateItems(() => gen.break());\n        return valid;\n        function validateItems(notValid) {\n          const len = gen.const("len", (0, codegen_1._)`${data}.length`);\n          gen.forRange("i", 0, len, (i) => {\n            cxt.subschema({\n              keyword,\n              dataProp: i,\n              dataPropType: util_1.Type.Num\n            }, valid);\n            gen.if((0, codegen_1.not)(valid), notValid);\n          });\n        }\n      }\n      exports.validateArray = validateArray;\n      function validateUnion(cxt) {\n        const { gen, schema, keyword, it } = cxt;\n        if (!Array.isArray(schema))\n          throw new Error("ajv implementation error");\n        const alwaysValid = schema.some((sch) => (0, util_1.alwaysValidSchema)(it, sch));\n        if (alwaysValid && !it.opts.unevaluated)\n          return;\n        const valid = gen.let("valid", false);\n        const schValid = gen.name("_valid");\n        gen.block(() => schema.forEach((_sch, i) => {\n          const schCxt = cxt.subschema({\n            keyword,\n            schemaProp: i,\n            compositeRule: true\n          }, schValid);\n          gen.assign(valid, (0, codegen_1._)`${valid} || ${schValid}`);\n          const merged = cxt.mergeValidEvaluated(schCxt, schValid);\n          if (!merged)\n            gen.if((0, codegen_1.not)(valid));\n        }));\n        cxt.result(valid, () => cxt.reset(), () => cxt.error(true));\n      }\n      exports.validateUnion = validateUnion;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/validate/keyword.js\n  var require_keyword = __commonJS({\n    "node_modules/ajv/dist/compile/validate/keyword.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.validateKeywordUsage = exports.validSchemaType = exports.funcKeywordCode = exports.macroKeywordCode = void 0;\n      var codegen_1 = require_codegen();\n      var names_1 = require_names();\n      var code_1 = require_code2();\n      var errors_1 = require_errors();\n      function macroKeywordCode(cxt, def) {\n        const { gen, keyword, schema, parentSchema, it } = cxt;\n        const macroSchema = def.macro.call(it.self, schema, parentSchema, it);\n        const schemaRef = useKeyword(gen, keyword, macroSchema);\n        if (it.opts.validateSchema !== false)\n          it.self.validateSchema(macroSchema, true);\n        const valid = gen.name("valid");\n        cxt.subschema({\n          schema: macroSchema,\n          schemaPath: codegen_1.nil,\n          errSchemaPath: `${it.errSchemaPath}/${keyword}`,\n          topSchemaRef: schemaRef,\n          compositeRule: true\n        }, valid);\n        cxt.pass(valid, () => cxt.error(true));\n      }\n      exports.macroKeywordCode = macroKeywordCode;\n      function funcKeywordCode(cxt, def) {\n        var _a;\n        const { gen, keyword, schema, parentSchema, $data, it } = cxt;\n        checkAsyncKeyword(it, def);\n        const validate = !$data && def.compile ? def.compile.call(it.self, schema, parentSchema, it) : def.validate;\n        const validateRef = useKeyword(gen, keyword, validate);\n        const valid = gen.let("valid");\n        cxt.block$data(valid, validateKeyword);\n        cxt.ok((_a = def.valid) !== null && _a !== void 0 ? _a : valid);\n        function validateKeyword() {\n          if (def.errors === false) {\n            assignValid();\n            if (def.modifying)\n              modifyData(cxt);\n            reportErrs(() => cxt.error());\n          } else {\n            const ruleErrs = def.async ? validateAsync() : validateSync();\n            if (def.modifying)\n              modifyData(cxt);\n            reportErrs(() => addErrs(cxt, ruleErrs));\n          }\n        }\n        function validateAsync() {\n          const ruleErrs = gen.let("ruleErrs", null);\n          gen.try(() => assignValid((0, codegen_1._)`await `), (e) => gen.assign(valid, false).if((0, codegen_1._)`${e} instanceof ${it.ValidationError}`, () => gen.assign(ruleErrs, (0, codegen_1._)`${e}.errors`), () => gen.throw(e)));\n          return ruleErrs;\n        }\n        function validateSync() {\n          const validateErrs = (0, codegen_1._)`${validateRef}.errors`;\n          gen.assign(validateErrs, null);\n          assignValid(codegen_1.nil);\n          return validateErrs;\n        }\n        function assignValid(_await = def.async ? (0, codegen_1._)`await ` : codegen_1.nil) {\n          const passCxt = it.opts.passContext ? names_1.default.this : names_1.default.self;\n          const passSchema = !("compile" in def && !$data || def.schema === false);\n          gen.assign(valid, (0, codegen_1._)`${_await}${(0, code_1.callValidateCode)(cxt, validateRef, passCxt, passSchema)}`, def.modifying);\n        }\n        function reportErrs(errors2) {\n          var _a2;\n          gen.if((0, codegen_1.not)((_a2 = def.valid) !== null && _a2 !== void 0 ? _a2 : valid), errors2);\n        }\n      }\n      exports.funcKeywordCode = funcKeywordCode;\n      function modifyData(cxt) {\n        const { gen, data, it } = cxt;\n        gen.if(it.parentData, () => gen.assign(data, (0, codegen_1._)`${it.parentData}[${it.parentDataProperty}]`));\n      }\n      function addErrs(cxt, errs) {\n        const { gen } = cxt;\n        gen.if((0, codegen_1._)`Array.isArray(${errs})`, () => {\n          gen.assign(names_1.default.vErrors, (0, codegen_1._)`${names_1.default.vErrors} === null ? ${errs} : ${names_1.default.vErrors}.concat(${errs})`).assign(names_1.default.errors, (0, codegen_1._)`${names_1.default.vErrors}.length`);\n          (0, errors_1.extendErrors)(cxt);\n        }, () => cxt.error());\n      }\n      function checkAsyncKeyword({ schemaEnv }, def) {\n        if (def.async && !schemaEnv.$async)\n          throw new Error("async keyword in sync schema");\n      }\n      function useKeyword(gen, keyword, result) {\n        if (result === void 0)\n          throw new Error(`keyword "${keyword}" failed to compile`);\n        return gen.scopeValue("keyword", typeof result == "function" ? { ref: result } : { ref: result, code: (0, codegen_1.stringify)(result) });\n      }\n      function validSchemaType(schema, schemaType, allowUndefined = false) {\n        return !schemaType.length || schemaType.some((st) => st === "array" ? Array.isArray(schema) : st === "object" ? schema && typeof schema == "object" && !Array.isArray(schema) : typeof schema == st || allowUndefined && typeof schema == "undefined");\n      }\n      exports.validSchemaType = validSchemaType;\n      function validateKeywordUsage({ schema, opts, self: self2, errSchemaPath }, def, keyword) {\n        if (Array.isArray(def.keyword) ? !def.keyword.includes(keyword) : def.keyword !== keyword) {\n          throw new Error("ajv implementation error");\n        }\n        const deps = def.dependencies;\n        if (deps === null || deps === void 0 ? void 0 : deps.some((kwd) => !Object.prototype.hasOwnProperty.call(schema, kwd))) {\n          throw new Error(`parent schema must have dependencies of ${keyword}: ${deps.join(",")}`);\n        }\n        if (def.validateSchema) {\n          const valid = def.validateSchema(schema[keyword]);\n          if (!valid) {\n            const msg = `keyword "${keyword}" value is invalid at path "${errSchemaPath}": ` + self2.errorsText(def.validateSchema.errors);\n            if (opts.validateSchema === "log")\n              self2.logger.error(msg);\n            else\n              throw new Error(msg);\n          }\n        }\n      }\n      exports.validateKeywordUsage = validateKeywordUsage;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/validate/subschema.js\n  var require_subschema = __commonJS({\n    "node_modules/ajv/dist/compile/validate/subschema.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.extendSubschemaMode = exports.extendSubschemaData = exports.getSubschema = void 0;\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      function getSubschema(it, { keyword, schemaProp, schema, schemaPath, errSchemaPath, topSchemaRef }) {\n        if (keyword !== void 0 && schema !== void 0) {\n          throw new Error(\'both "keyword" and "schema" passed, only one allowed\');\n        }\n        if (keyword !== void 0) {\n          const sch = it.schema[keyword];\n          return schemaProp === void 0 ? {\n            schema: sch,\n            schemaPath: (0, codegen_1._)`${it.schemaPath}${(0, codegen_1.getProperty)(keyword)}`,\n            errSchemaPath: `${it.errSchemaPath}/${keyword}`\n          } : {\n            schema: sch[schemaProp],\n            schemaPath: (0, codegen_1._)`${it.schemaPath}${(0, codegen_1.getProperty)(keyword)}${(0, codegen_1.getProperty)(schemaProp)}`,\n            errSchemaPath: `${it.errSchemaPath}/${keyword}/${(0, util_1.escapeFragment)(schemaProp)}`\n          };\n        }\n        if (schema !== void 0) {\n          if (schemaPath === void 0 || errSchemaPath === void 0 || topSchemaRef === void 0) {\n            throw new Error(\'"schemaPath", "errSchemaPath" and "topSchemaRef" are required with "schema"\');\n          }\n          return {\n            schema,\n            schemaPath,\n            topSchemaRef,\n            errSchemaPath\n          };\n        }\n        throw new Error(\'either "keyword" or "schema" must be passed\');\n      }\n      exports.getSubschema = getSubschema;\n      function extendSubschemaData(subschema, it, { dataProp, dataPropType: dpType, data, dataTypes, propertyName }) {\n        if (data !== void 0 && dataProp !== void 0) {\n          throw new Error(\'both "data" and "dataProp" passed, only one allowed\');\n        }\n        const { gen } = it;\n        if (dataProp !== void 0) {\n          const { errorPath, dataPathArr, opts } = it;\n          const nextData = gen.let("data", (0, codegen_1._)`${it.data}${(0, codegen_1.getProperty)(dataProp)}`, true);\n          dataContextProps(nextData);\n          subschema.errorPath = (0, codegen_1.str)`${errorPath}${(0, util_1.getErrorPath)(dataProp, dpType, opts.jsPropertySyntax)}`;\n          subschema.parentDataProperty = (0, codegen_1._)`${dataProp}`;\n          subschema.dataPathArr = [...dataPathArr, subschema.parentDataProperty];\n        }\n        if (data !== void 0) {\n          const nextData = data instanceof codegen_1.Name ? data : gen.let("data", data, true);\n          dataContextProps(nextData);\n          if (propertyName !== void 0)\n            subschema.propertyName = propertyName;\n        }\n        if (dataTypes)\n          subschema.dataTypes = dataTypes;\n        function dataContextProps(_nextData) {\n          subschema.data = _nextData;\n          subschema.dataLevel = it.dataLevel + 1;\n          subschema.dataTypes = [];\n          it.definedProperties = /* @__PURE__ */ new Set();\n          subschema.parentData = it.data;\n          subschema.dataNames = [...it.dataNames, _nextData];\n        }\n      }\n      exports.extendSubschemaData = extendSubschemaData;\n      function extendSubschemaMode(subschema, { jtdDiscriminator, jtdMetadata, compositeRule, createErrors, allErrors }) {\n        if (compositeRule !== void 0)\n          subschema.compositeRule = compositeRule;\n        if (createErrors !== void 0)\n          subschema.createErrors = createErrors;\n        if (allErrors !== void 0)\n          subschema.allErrors = allErrors;\n        subschema.jtdDiscriminator = jtdDiscriminator;\n        subschema.jtdMetadata = jtdMetadata;\n      }\n      exports.extendSubschemaMode = extendSubschemaMode;\n    }\n  });\n\n  // node_modules/fast-deep-equal/index.js\n  var require_fast_deep_equal = __commonJS({\n    "node_modules/fast-deep-equal/index.js"(exports, module) {\n      "use strict";\n      module.exports = function equal(a, b) {\n        if (a === b) return true;\n        if (a && b && typeof a == "object" && typeof b == "object") {\n          if (a.constructor !== b.constructor) return false;\n          var length, i, keys;\n          if (Array.isArray(a)) {\n            length = a.length;\n            if (length != b.length) return false;\n            for (i = length; i-- !== 0; )\n              if (!equal(a[i], b[i])) return false;\n            return true;\n          }\n          if (a.constructor === RegExp) return a.source === b.source && a.flags === b.flags;\n          if (a.valueOf !== Object.prototype.valueOf) return a.valueOf() === b.valueOf();\n          if (a.toString !== Object.prototype.toString) return a.toString() === b.toString();\n          keys = Object.keys(a);\n          length = keys.length;\n          if (length !== Object.keys(b).length) return false;\n          for (i = length; i-- !== 0; )\n            if (!Object.prototype.hasOwnProperty.call(b, keys[i])) return false;\n          for (i = length; i-- !== 0; ) {\n            var key = keys[i];\n            if (!equal(a[key], b[key])) return false;\n          }\n          return true;\n        }\n        return a !== a && b !== b;\n      };\n    }\n  });\n\n  // node_modules/json-schema-traverse/index.js\n  var require_json_schema_traverse = __commonJS({\n    "node_modules/json-schema-traverse/index.js"(exports, module) {\n      "use strict";\n      var traverse = module.exports = function(schema, opts, cb) {\n        if (typeof opts == "function") {\n          cb = opts;\n          opts = {};\n        }\n        cb = opts.cb || cb;\n        var pre = typeof cb == "function" ? cb : cb.pre || function() {\n        };\n        var post = cb.post || function() {\n        };\n        _traverse(opts, pre, post, schema, "", schema);\n      };\n      traverse.keywords = {\n        additionalItems: true,\n        items: true,\n        contains: true,\n        additionalProperties: true,\n        propertyNames: true,\n        not: true,\n        if: true,\n        then: true,\n        else: true\n      };\n      traverse.arrayKeywords = {\n        items: true,\n        allOf: true,\n        anyOf: true,\n        oneOf: true\n      };\n      traverse.propsKeywords = {\n        $defs: true,\n        definitions: true,\n        properties: true,\n        patternProperties: true,\n        dependencies: true\n      };\n      traverse.skipKeywords = {\n        default: true,\n        enum: true,\n        const: true,\n        required: true,\n        maximum: true,\n        minimum: true,\n        exclusiveMaximum: true,\n        exclusiveMinimum: true,\n        multipleOf: true,\n        maxLength: true,\n        minLength: true,\n        pattern: true,\n        format: true,\n        maxItems: true,\n        minItems: true,\n        uniqueItems: true,\n        maxProperties: true,\n        minProperties: true\n      };\n      function _traverse(opts, pre, post, schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex) {\n        if (schema && typeof schema == "object" && !Array.isArray(schema)) {\n          pre(schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex);\n          for (var key in schema) {\n            var sch = schema[key];\n            if (Array.isArray(sch)) {\n              if (key in traverse.arrayKeywords) {\n                for (var i = 0; i < sch.length; i++)\n                  _traverse(opts, pre, post, sch[i], jsonPtr + "/" + key + "/" + i, rootSchema, jsonPtr, key, schema, i);\n              }\n            } else if (key in traverse.propsKeywords) {\n              if (sch && typeof sch == "object") {\n                for (var prop in sch)\n                  _traverse(opts, pre, post, sch[prop], jsonPtr + "/" + key + "/" + escapeJsonPtr(prop), rootSchema, jsonPtr, key, schema, prop);\n              }\n            } else if (key in traverse.keywords || opts.allKeys && !(key in traverse.skipKeywords)) {\n              _traverse(opts, pre, post, sch, jsonPtr + "/" + key, rootSchema, jsonPtr, key, schema);\n            }\n          }\n          post(schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex);\n        }\n      }\n      function escapeJsonPtr(str) {\n        return str.replace(/~/g, "~0").replace(/\\//g, "~1");\n      }\n    }\n  });\n\n  // node_modules/ajv/dist/compile/resolve.js\n  var require_resolve = __commonJS({\n    "node_modules/ajv/dist/compile/resolve.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.getSchemaRefs = exports.resolveUrl = exports.normalizeId = exports._getFullPath = exports.getFullPath = exports.inlineRef = void 0;\n      var util_1 = require_util();\n      var equal = require_fast_deep_equal();\n      var traverse = require_json_schema_traverse();\n      var SIMPLE_INLINED = /* @__PURE__ */ new Set([\n        "type",\n        "format",\n        "pattern",\n        "maxLength",\n        "minLength",\n        "maxProperties",\n        "minProperties",\n        "maxItems",\n        "minItems",\n        "maximum",\n        "minimum",\n        "uniqueItems",\n        "multipleOf",\n        "required",\n        "enum",\n        "const"\n      ]);\n      function inlineRef(schema, limit = true) {\n        if (typeof schema == "boolean")\n          return true;\n        if (limit === true)\n          return !hasRef(schema);\n        if (!limit)\n          return false;\n        return countKeys(schema) <= limit;\n      }\n      exports.inlineRef = inlineRef;\n      var REF_KEYWORDS = /* @__PURE__ */ new Set([\n        "$ref",\n        "$recursiveRef",\n        "$recursiveAnchor",\n        "$dynamicRef",\n        "$dynamicAnchor"\n      ]);\n      function hasRef(schema) {\n        for (const key in schema) {\n          if (REF_KEYWORDS.has(key))\n            return true;\n          const sch = schema[key];\n          if (Array.isArray(sch) && sch.some(hasRef))\n            return true;\n          if (typeof sch == "object" && hasRef(sch))\n            return true;\n        }\n        return false;\n      }\n      function countKeys(schema) {\n        let count = 0;\n        for (const key in schema) {\n          if (key === "$ref")\n            return Infinity;\n          count++;\n          if (SIMPLE_INLINED.has(key))\n            continue;\n          if (typeof schema[key] == "object") {\n            (0, util_1.eachItem)(schema[key], (sch) => count += countKeys(sch));\n          }\n          if (count === Infinity)\n            return Infinity;\n        }\n        return count;\n      }\n      function getFullPath(resolver, id = "", normalize) {\n        if (normalize !== false)\n          id = normalizeId(id);\n        const p = resolver.parse(id);\n        return _getFullPath(resolver, p);\n      }\n      exports.getFullPath = getFullPath;\n      function _getFullPath(resolver, p) {\n        const serialized = resolver.serialize(p);\n        return serialized.split("#")[0] + "#";\n      }\n      exports._getFullPath = _getFullPath;\n      var TRAILING_SLASH_HASH = /#\\/?$/;\n      function normalizeId(id) {\n        return id ? id.replace(TRAILING_SLASH_HASH, "") : "";\n      }\n      exports.normalizeId = normalizeId;\n      function resolveUrl(resolver, baseId, id) {\n        id = normalizeId(id);\n        return resolver.resolve(baseId, id);\n      }\n      exports.resolveUrl = resolveUrl;\n      var ANCHOR = /^[a-z_][-a-z0-9._]*$/i;\n      function getSchemaRefs(schema, baseId) {\n        if (typeof schema == "boolean")\n          return {};\n        const { schemaId, uriResolver } = this.opts;\n        const schId = normalizeId(schema[schemaId] || baseId);\n        const baseIds = { "": schId };\n        const pathPrefix = getFullPath(uriResolver, schId, false);\n        const localRefs = {};\n        const schemaRefs = /* @__PURE__ */ new Set();\n        traverse(schema, { allKeys: true }, (sch, jsonPtr, _, parentJsonPtr) => {\n          if (parentJsonPtr === void 0)\n            return;\n          const fullPath = pathPrefix + jsonPtr;\n          let innerBaseId = baseIds[parentJsonPtr];\n          if (typeof sch[schemaId] == "string")\n            innerBaseId = addRef.call(this, sch[schemaId]);\n          addAnchor.call(this, sch.$anchor);\n          addAnchor.call(this, sch.$dynamicAnchor);\n          baseIds[jsonPtr] = innerBaseId;\n          function addRef(ref) {\n            const _resolve = this.opts.uriResolver.resolve;\n            ref = normalizeId(innerBaseId ? _resolve(innerBaseId, ref) : ref);\n            if (schemaRefs.has(ref))\n              throw ambiguos(ref);\n            schemaRefs.add(ref);\n            let schOrRef = this.refs[ref];\n            if (typeof schOrRef == "string")\n              schOrRef = this.refs[schOrRef];\n            if (typeof schOrRef == "object") {\n              checkAmbiguosRef(sch, schOrRef.schema, ref);\n            } else if (ref !== normalizeId(fullPath)) {\n              if (ref[0] === "#") {\n                checkAmbiguosRef(sch, localRefs[ref], ref);\n                localRefs[ref] = sch;\n              } else {\n                this.refs[ref] = fullPath;\n              }\n            }\n            return ref;\n          }\n          function addAnchor(anchor) {\n            if (typeof anchor == "string") {\n              if (!ANCHOR.test(anchor))\n                throw new Error(`invalid anchor "${anchor}"`);\n              addRef.call(this, `#${anchor}`);\n            }\n          }\n        });\n        return localRefs;\n        function checkAmbiguosRef(sch1, sch2, ref) {\n          if (sch2 !== void 0 && !equal(sch1, sch2))\n            throw ambiguos(ref);\n        }\n        function ambiguos(ref) {\n          return new Error(`reference "${ref}" resolves to more than one schema`);\n        }\n      }\n      exports.getSchemaRefs = getSchemaRefs;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/validate/index.js\n  var require_validate = __commonJS({\n    "node_modules/ajv/dist/compile/validate/index.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.getData = exports.KeywordCxt = exports.validateFunctionCode = void 0;\n      var boolSchema_1 = require_boolSchema();\n      var dataType_1 = require_dataType();\n      var applicability_1 = require_applicability();\n      var dataType_2 = require_dataType();\n      var defaults_1 = require_defaults();\n      var keyword_1 = require_keyword();\n      var subschema_1 = require_subschema();\n      var codegen_1 = require_codegen();\n      var names_1 = require_names();\n      var resolve_1 = require_resolve();\n      var util_1 = require_util();\n      var errors_1 = require_errors();\n      function validateFunctionCode(it) {\n        if (isSchemaObj(it)) {\n          checkKeywords(it);\n          if (schemaCxtHasRules(it)) {\n            topSchemaObjCode(it);\n            return;\n          }\n        }\n        validateFunction(it, () => (0, boolSchema_1.topBoolOrEmptySchema)(it));\n      }\n      exports.validateFunctionCode = validateFunctionCode;\n      function validateFunction({ gen, validateName, schema, schemaEnv, opts }, body) {\n        if (opts.code.es5) {\n          gen.func(validateName, (0, codegen_1._)`${names_1.default.data}, ${names_1.default.valCxt}`, schemaEnv.$async, () => {\n            gen.code((0, codegen_1._)`"use strict"; ${funcSourceUrl(schema, opts)}`);\n            destructureValCxtES5(gen, opts);\n            gen.code(body);\n          });\n        } else {\n          gen.func(validateName, (0, codegen_1._)`${names_1.default.data}, ${destructureValCxt(opts)}`, schemaEnv.$async, () => gen.code(funcSourceUrl(schema, opts)).code(body));\n        }\n      }\n      function destructureValCxt(opts) {\n        return (0, codegen_1._)`{${names_1.default.instancePath}="", ${names_1.default.parentData}, ${names_1.default.parentDataProperty}, ${names_1.default.rootData}=${names_1.default.data}${opts.dynamicRef ? (0, codegen_1._)`, ${names_1.default.dynamicAnchors}={}` : codegen_1.nil}}={}`;\n      }\n      function destructureValCxtES5(gen, opts) {\n        gen.if(names_1.default.valCxt, () => {\n          gen.var(names_1.default.instancePath, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.instancePath}`);\n          gen.var(names_1.default.parentData, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.parentData}`);\n          gen.var(names_1.default.parentDataProperty, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.parentDataProperty}`);\n          gen.var(names_1.default.rootData, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.rootData}`);\n          if (opts.dynamicRef)\n            gen.var(names_1.default.dynamicAnchors, (0, codegen_1._)`${names_1.default.valCxt}.${names_1.default.dynamicAnchors}`);\n        }, () => {\n          gen.var(names_1.default.instancePath, (0, codegen_1._)`""`);\n          gen.var(names_1.default.parentData, (0, codegen_1._)`undefined`);\n          gen.var(names_1.default.parentDataProperty, (0, codegen_1._)`undefined`);\n          gen.var(names_1.default.rootData, names_1.default.data);\n          if (opts.dynamicRef)\n            gen.var(names_1.default.dynamicAnchors, (0, codegen_1._)`{}`);\n        });\n      }\n      function topSchemaObjCode(it) {\n        const { schema, opts, gen } = it;\n        validateFunction(it, () => {\n          if (opts.$comment && schema.$comment)\n            commentKeyword(it);\n          checkNoDefault(it);\n          gen.let(names_1.default.vErrors, null);\n          gen.let(names_1.default.errors, 0);\n          if (opts.unevaluated)\n            resetEvaluated(it);\n          typeAndKeywords(it);\n          returnResults(it);\n        });\n        return;\n      }\n      function resetEvaluated(it) {\n        const { gen, validateName } = it;\n        it.evaluated = gen.const("evaluated", (0, codegen_1._)`${validateName}.evaluated`);\n        gen.if((0, codegen_1._)`${it.evaluated}.dynamicProps`, () => gen.assign((0, codegen_1._)`${it.evaluated}.props`, (0, codegen_1._)`undefined`));\n        gen.if((0, codegen_1._)`${it.evaluated}.dynamicItems`, () => gen.assign((0, codegen_1._)`${it.evaluated}.items`, (0, codegen_1._)`undefined`));\n      }\n      function funcSourceUrl(schema, opts) {\n        const schId = typeof schema == "object" && schema[opts.schemaId];\n        return schId && (opts.code.source || opts.code.process) ? (0, codegen_1._)`/*# sourceURL=${schId} */` : codegen_1.nil;\n      }\n      function subschemaCode(it, valid) {\n        if (isSchemaObj(it)) {\n          checkKeywords(it);\n          if (schemaCxtHasRules(it)) {\n            subSchemaObjCode(it, valid);\n            return;\n          }\n        }\n        (0, boolSchema_1.boolOrEmptySchema)(it, valid);\n      }\n      function schemaCxtHasRules({ schema, self: self2 }) {\n        if (typeof schema == "boolean")\n          return !schema;\n        for (const key in schema)\n          if (self2.RULES.all[key])\n            return true;\n        return false;\n      }\n      function isSchemaObj(it) {\n        return typeof it.schema != "boolean";\n      }\n      function subSchemaObjCode(it, valid) {\n        const { schema, gen, opts } = it;\n        if (opts.$comment && schema.$comment)\n          commentKeyword(it);\n        updateContext(it);\n        checkAsyncSchema(it);\n        const errsCount = gen.const("_errs", names_1.default.errors);\n        typeAndKeywords(it, errsCount);\n        gen.var(valid, (0, codegen_1._)`${errsCount} === ${names_1.default.errors}`);\n      }\n      function checkKeywords(it) {\n        (0, util_1.checkUnknownRules)(it);\n        checkRefsAndKeywords(it);\n      }\n      function typeAndKeywords(it, errsCount) {\n        if (it.opts.jtd)\n          return schemaKeywords(it, [], false, errsCount);\n        const types = (0, dataType_1.getSchemaTypes)(it.schema);\n        const checkedTypes = (0, dataType_1.coerceAndCheckDataType)(it, types);\n        schemaKeywords(it, types, !checkedTypes, errsCount);\n      }\n      function checkRefsAndKeywords(it) {\n        const { schema, errSchemaPath, opts, self: self2 } = it;\n        if (schema.$ref && opts.ignoreKeywordsWithRef && (0, util_1.schemaHasRulesButRef)(schema, self2.RULES)) {\n          self2.logger.warn(`$ref: keywords ignored in schema at path "${errSchemaPath}"`);\n        }\n      }\n      function checkNoDefault(it) {\n        const { schema, opts } = it;\n        if (schema.default !== void 0 && opts.useDefaults && opts.strictSchema) {\n          (0, util_1.checkStrictMode)(it, "default is ignored in the schema root");\n        }\n      }\n      function updateContext(it) {\n        const schId = it.schema[it.opts.schemaId];\n        if (schId)\n          it.baseId = (0, resolve_1.resolveUrl)(it.opts.uriResolver, it.baseId, schId);\n      }\n      function checkAsyncSchema(it) {\n        if (it.schema.$async && !it.schemaEnv.$async)\n          throw new Error("async schema in sync schema");\n      }\n      function commentKeyword({ gen, schemaEnv, schema, errSchemaPath, opts }) {\n        const msg = schema.$comment;\n        if (opts.$comment === true) {\n          gen.code((0, codegen_1._)`${names_1.default.self}.logger.log(${msg})`);\n        } else if (typeof opts.$comment == "function") {\n          const schemaPath = (0, codegen_1.str)`${errSchemaPath}/$comment`;\n          const rootName = gen.scopeValue("root", { ref: schemaEnv.root });\n          gen.code((0, codegen_1._)`${names_1.default.self}.opts.$comment(${msg}, ${schemaPath}, ${rootName}.schema)`);\n        }\n      }\n      function returnResults(it) {\n        const { gen, schemaEnv, validateName, ValidationError, opts } = it;\n        if (schemaEnv.$async) {\n          gen.if((0, codegen_1._)`${names_1.default.errors} === 0`, () => gen.return(names_1.default.data), () => gen.throw((0, codegen_1._)`new ${ValidationError}(${names_1.default.vErrors})`));\n        } else {\n          gen.assign((0, codegen_1._)`${validateName}.errors`, names_1.default.vErrors);\n          if (opts.unevaluated)\n            assignEvaluated(it);\n          gen.return((0, codegen_1._)`${names_1.default.errors} === 0`);\n        }\n      }\n      function assignEvaluated({ gen, evaluated, props, items }) {\n        if (props instanceof codegen_1.Name)\n          gen.assign((0, codegen_1._)`${evaluated}.props`, props);\n        if (items instanceof codegen_1.Name)\n          gen.assign((0, codegen_1._)`${evaluated}.items`, items);\n      }\n      function schemaKeywords(it, types, typeErrors, errsCount) {\n        const { gen, schema, data, allErrors, opts, self: self2 } = it;\n        const { RULES } = self2;\n        if (schema.$ref && (opts.ignoreKeywordsWithRef || !(0, util_1.schemaHasRulesButRef)(schema, RULES))) {\n          gen.block(() => keywordCode(it, "$ref", RULES.all.$ref.definition));\n          return;\n        }\n        if (!opts.jtd)\n          checkStrictTypes(it, types);\n        gen.block(() => {\n          for (const group of RULES.rules)\n            groupKeywords(group);\n          groupKeywords(RULES.post);\n        });\n        function groupKeywords(group) {\n          if (!(0, applicability_1.shouldUseGroup)(schema, group))\n            return;\n          if (group.type) {\n            gen.if((0, dataType_2.checkDataType)(group.type, data, opts.strictNumbers));\n            iterateKeywords(it, group);\n            if (types.length === 1 && types[0] === group.type && typeErrors) {\n              gen.else();\n              (0, dataType_2.reportTypeError)(it);\n            }\n            gen.endIf();\n          } else {\n            iterateKeywords(it, group);\n          }\n          if (!allErrors)\n            gen.if((0, codegen_1._)`${names_1.default.errors} === ${errsCount || 0}`);\n        }\n      }\n      function iterateKeywords(it, group) {\n        const { gen, schema, opts: { useDefaults } } = it;\n        if (useDefaults)\n          (0, defaults_1.assignDefaults)(it, group.type);\n        gen.block(() => {\n          for (const rule of group.rules) {\n            if ((0, applicability_1.shouldUseRule)(schema, rule)) {\n              keywordCode(it, rule.keyword, rule.definition, group.type);\n            }\n          }\n        });\n      }\n      function checkStrictTypes(it, types) {\n        if (it.schemaEnv.meta || !it.opts.strictTypes)\n          return;\n        checkContextTypes(it, types);\n        if (!it.opts.allowUnionTypes)\n          checkMultipleTypes(it, types);\n        checkKeywordTypes(it, it.dataTypes);\n      }\n      function checkContextTypes(it, types) {\n        if (!types.length)\n          return;\n        if (!it.dataTypes.length) {\n          it.dataTypes = types;\n          return;\n        }\n        types.forEach((t) => {\n          if (!includesType(it.dataTypes, t)) {\n            strictTypesError(it, `type "${t}" not allowed by context "${it.dataTypes.join(",")}"`);\n          }\n        });\n        narrowSchemaTypes(it, types);\n      }\n      function checkMultipleTypes(it, ts) {\n        if (ts.length > 1 && !(ts.length === 2 && ts.includes("null"))) {\n          strictTypesError(it, "use allowUnionTypes to allow union type keyword");\n        }\n      }\n      function checkKeywordTypes(it, ts) {\n        const rules = it.self.RULES.all;\n        for (const keyword in rules) {\n          const rule = rules[keyword];\n          if (typeof rule == "object" && (0, applicability_1.shouldUseRule)(it.schema, rule)) {\n            const { type } = rule.definition;\n            if (type.length && !type.some((t) => hasApplicableType(ts, t))) {\n              strictTypesError(it, `missing type "${type.join(",")}" for keyword "${keyword}"`);\n            }\n          }\n        }\n      }\n      function hasApplicableType(schTs, kwdT) {\n        return schTs.includes(kwdT) || kwdT === "number" && schTs.includes("integer");\n      }\n      function includesType(ts, t) {\n        return ts.includes(t) || t === "integer" && ts.includes("number");\n      }\n      function narrowSchemaTypes(it, withTypes) {\n        const ts = [];\n        for (const t of it.dataTypes) {\n          if (includesType(withTypes, t))\n            ts.push(t);\n          else if (withTypes.includes("integer") && t === "number")\n            ts.push("integer");\n        }\n        it.dataTypes = ts;\n      }\n      function strictTypesError(it, msg) {\n        const schemaPath = it.schemaEnv.baseId + it.errSchemaPath;\n        msg += ` at "${schemaPath}" (strictTypes)`;\n        (0, util_1.checkStrictMode)(it, msg, it.opts.strictTypes);\n      }\n      var KeywordCxt = class {\n        constructor(it, def, keyword) {\n          (0, keyword_1.validateKeywordUsage)(it, def, keyword);\n          this.gen = it.gen;\n          this.allErrors = it.allErrors;\n          this.keyword = keyword;\n          this.data = it.data;\n          this.schema = it.schema[keyword];\n          this.$data = def.$data && it.opts.$data && this.schema && this.schema.$data;\n          this.schemaValue = (0, util_1.schemaRefOrVal)(it, this.schema, keyword, this.$data);\n          this.schemaType = def.schemaType;\n          this.parentSchema = it.schema;\n          this.params = {};\n          this.it = it;\n          this.def = def;\n          if (this.$data) {\n            this.schemaCode = it.gen.const("vSchema", getData(this.$data, it));\n          } else {\n            this.schemaCode = this.schemaValue;\n            if (!(0, keyword_1.validSchemaType)(this.schema, def.schemaType, def.allowUndefined)) {\n              throw new Error(`${keyword} value must be ${JSON.stringify(def.schemaType)}`);\n            }\n          }\n          if ("code" in def ? def.trackErrors : def.errors !== false) {\n            this.errsCount = it.gen.const("_errs", names_1.default.errors);\n          }\n        }\n        result(condition, successAction, failAction) {\n          this.failResult((0, codegen_1.not)(condition), successAction, failAction);\n        }\n        failResult(condition, successAction, failAction) {\n          this.gen.if(condition);\n          if (failAction)\n            failAction();\n          else\n            this.error();\n          if (successAction) {\n            this.gen.else();\n            successAction();\n            if (this.allErrors)\n              this.gen.endIf();\n          } else {\n            if (this.allErrors)\n              this.gen.endIf();\n            else\n              this.gen.else();\n          }\n        }\n        pass(condition, failAction) {\n          this.failResult((0, codegen_1.not)(condition), void 0, failAction);\n        }\n        fail(condition) {\n          if (condition === void 0) {\n            this.error();\n            if (!this.allErrors)\n              this.gen.if(false);\n            return;\n          }\n          this.gen.if(condition);\n          this.error();\n          if (this.allErrors)\n            this.gen.endIf();\n          else\n            this.gen.else();\n        }\n        fail$data(condition) {\n          if (!this.$data)\n            return this.fail(condition);\n          const { schemaCode } = this;\n          this.fail((0, codegen_1._)`${schemaCode} !== undefined && (${(0, codegen_1.or)(this.invalid$data(), condition)})`);\n        }\n        error(append, errorParams, errorPaths) {\n          if (errorParams) {\n            this.setParams(errorParams);\n            this._error(append, errorPaths);\n            this.setParams({});\n            return;\n          }\n          this._error(append, errorPaths);\n        }\n        _error(append, errorPaths) {\n          ;\n          (append ? errors_1.reportExtraError : errors_1.reportError)(this, this.def.error, errorPaths);\n        }\n        $dataError() {\n          (0, errors_1.reportError)(this, this.def.$dataError || errors_1.keyword$DataError);\n        }\n        reset() {\n          if (this.errsCount === void 0)\n            throw new Error(\'add "trackErrors" to keyword definition\');\n          (0, errors_1.resetErrorsCount)(this.gen, this.errsCount);\n        }\n        ok(cond) {\n          if (!this.allErrors)\n            this.gen.if(cond);\n        }\n        setParams(obj, assign) {\n          if (assign)\n            Object.assign(this.params, obj);\n          else\n            this.params = obj;\n        }\n        block$data(valid, codeBlock, $dataValid = codegen_1.nil) {\n          this.gen.block(() => {\n            this.check$data(valid, $dataValid);\n            codeBlock();\n          });\n        }\n        check$data(valid = codegen_1.nil, $dataValid = codegen_1.nil) {\n          if (!this.$data)\n            return;\n          const { gen, schemaCode, schemaType, def } = this;\n          gen.if((0, codegen_1.or)((0, codegen_1._)`${schemaCode} === undefined`, $dataValid));\n          if (valid !== codegen_1.nil)\n            gen.assign(valid, true);\n          if (schemaType.length || def.validateSchema) {\n            gen.elseIf(this.invalid$data());\n            this.$dataError();\n            if (valid !== codegen_1.nil)\n              gen.assign(valid, false);\n          }\n          gen.else();\n        }\n        invalid$data() {\n          const { gen, schemaCode, schemaType, def, it } = this;\n          return (0, codegen_1.or)(wrong$DataType(), invalid$DataSchema());\n          function wrong$DataType() {\n            if (schemaType.length) {\n              if (!(schemaCode instanceof codegen_1.Name))\n                throw new Error("ajv implementation error");\n              const st = Array.isArray(schemaType) ? schemaType : [schemaType];\n              return (0, codegen_1._)`${(0, dataType_2.checkDataTypes)(st, schemaCode, it.opts.strictNumbers, dataType_2.DataType.Wrong)}`;\n            }\n            return codegen_1.nil;\n          }\n          function invalid$DataSchema() {\n            if (def.validateSchema) {\n              const validateSchemaRef = gen.scopeValue("validate$data", { ref: def.validateSchema });\n              return (0, codegen_1._)`!${validateSchemaRef}(${schemaCode})`;\n            }\n            return codegen_1.nil;\n          }\n        }\n        subschema(appl, valid) {\n          const subschema = (0, subschema_1.getSubschema)(this.it, appl);\n          (0, subschema_1.extendSubschemaData)(subschema, this.it, appl);\n          (0, subschema_1.extendSubschemaMode)(subschema, appl);\n          const nextContext = { ...this.it, ...subschema, items: void 0, props: void 0 };\n          subschemaCode(nextContext, valid);\n          return nextContext;\n        }\n        mergeEvaluated(schemaCxt, toName) {\n          const { it, gen } = this;\n          if (!it.opts.unevaluated)\n            return;\n          if (it.props !== true && schemaCxt.props !== void 0) {\n            it.props = util_1.mergeEvaluated.props(gen, schemaCxt.props, it.props, toName);\n          }\n          if (it.items !== true && schemaCxt.items !== void 0) {\n            it.items = util_1.mergeEvaluated.items(gen, schemaCxt.items, it.items, toName);\n          }\n        }\n        mergeValidEvaluated(schemaCxt, valid) {\n          const { it, gen } = this;\n          if (it.opts.unevaluated && (it.props !== true || it.items !== true)) {\n            gen.if(valid, () => this.mergeEvaluated(schemaCxt, codegen_1.Name));\n            return true;\n          }\n        }\n      };\n      exports.KeywordCxt = KeywordCxt;\n      function keywordCode(it, keyword, def, ruleType) {\n        const cxt = new KeywordCxt(it, def, keyword);\n        if ("code" in def) {\n          def.code(cxt, ruleType);\n        } else if (cxt.$data && def.validate) {\n          (0, keyword_1.funcKeywordCode)(cxt, def);\n        } else if ("macro" in def) {\n          (0, keyword_1.macroKeywordCode)(cxt, def);\n        } else if (def.compile || def.validate) {\n          (0, keyword_1.funcKeywordCode)(cxt, def);\n        }\n      }\n      var JSON_POINTER = /^\\/(?:[^~]|~0|~1)*$/;\n      var RELATIVE_JSON_POINTER = /^([0-9]+)(#|\\/(?:[^~]|~0|~1)*)?$/;\n      function getData($data, { dataLevel, dataNames, dataPathArr }) {\n        let jsonPointer;\n        let data;\n        if ($data === "")\n          return names_1.default.rootData;\n        if ($data[0] === "/") {\n          if (!JSON_POINTER.test($data))\n            throw new Error(`Invalid JSON-pointer: ${$data}`);\n          jsonPointer = $data;\n          data = names_1.default.rootData;\n        } else {\n          const matches = RELATIVE_JSON_POINTER.exec($data);\n          if (!matches)\n            throw new Error(`Invalid JSON-pointer: ${$data}`);\n          const up = +matches[1];\n          jsonPointer = matches[2];\n          if (jsonPointer === "#") {\n            if (up >= dataLevel)\n              throw new Error(errorMsg("property/index", up));\n            return dataPathArr[dataLevel - up];\n          }\n          if (up > dataLevel)\n            throw new Error(errorMsg("data", up));\n          data = dataNames[dataLevel - up];\n          if (!jsonPointer)\n            return data;\n        }\n        let expr = data;\n        const segments = jsonPointer.split("/");\n        for (const segment of segments) {\n          if (segment) {\n            data = (0, codegen_1._)`${data}${(0, codegen_1.getProperty)((0, util_1.unescapeJsonPointer)(segment))}`;\n            expr = (0, codegen_1._)`${expr} && ${data}`;\n          }\n        }\n        return expr;\n        function errorMsg(pointerType, up) {\n          return `Cannot access ${pointerType} ${up} levels up, current level is ${dataLevel}`;\n        }\n      }\n      exports.getData = getData;\n    }\n  });\n\n  // node_modules/ajv/dist/runtime/validation_error.js\n  var require_validation_error = __commonJS({\n    "node_modules/ajv/dist/runtime/validation_error.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var ValidationError = class extends Error {\n        constructor(errors2) {\n          super("validation failed");\n          this.errors = errors2;\n          this.ajv = this.validation = true;\n        }\n      };\n      exports.default = ValidationError;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/ref_error.js\n  var require_ref_error = __commonJS({\n    "node_modules/ajv/dist/compile/ref_error.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var resolve_1 = require_resolve();\n      var MissingRefError = class extends Error {\n        constructor(resolver, baseId, ref, msg) {\n          super(msg || `can\'t resolve reference ${ref} from id ${baseId}`);\n          this.missingRef = (0, resolve_1.resolveUrl)(resolver, baseId, ref);\n          this.missingSchema = (0, resolve_1.normalizeId)((0, resolve_1.getFullPath)(resolver, this.missingRef));\n        }\n      };\n      exports.default = MissingRefError;\n    }\n  });\n\n  // node_modules/ajv/dist/compile/index.js\n  var require_compile = __commonJS({\n    "node_modules/ajv/dist/compile/index.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.resolveSchema = exports.getCompilingSchema = exports.resolveRef = exports.compileSchema = exports.SchemaEnv = void 0;\n      var codegen_1 = require_codegen();\n      var validation_error_1 = require_validation_error();\n      var names_1 = require_names();\n      var resolve_1 = require_resolve();\n      var util_1 = require_util();\n      var validate_1 = require_validate();\n      var SchemaEnv = class {\n        constructor(env) {\n          var _a;\n          this.refs = {};\n          this.dynamicAnchors = {};\n          let schema;\n          if (typeof env.schema == "object")\n            schema = env.schema;\n          this.schema = env.schema;\n          this.schemaId = env.schemaId;\n          this.root = env.root || this;\n          this.baseId = (_a = env.baseId) !== null && _a !== void 0 ? _a : (0, resolve_1.normalizeId)(schema === null || schema === void 0 ? void 0 : schema[env.schemaId || "$id"]);\n          this.schemaPath = env.schemaPath;\n          this.localRefs = env.localRefs;\n          this.meta = env.meta;\n          this.$async = schema === null || schema === void 0 ? void 0 : schema.$async;\n          this.refs = {};\n        }\n      };\n      exports.SchemaEnv = SchemaEnv;\n      function compileSchema(sch) {\n        const _sch = getCompilingSchema.call(this, sch);\n        if (_sch)\n          return _sch;\n        const rootId = (0, resolve_1.getFullPath)(this.opts.uriResolver, sch.root.baseId);\n        const { es5, lines } = this.opts.code;\n        const { ownProperties } = this.opts;\n        const gen = new codegen_1.CodeGen(this.scope, { es5, lines, ownProperties });\n        let _ValidationError;\n        if (sch.$async) {\n          _ValidationError = gen.scopeValue("Error", {\n            ref: validation_error_1.default,\n            code: (0, codegen_1._)`require("ajv/dist/runtime/validation_error").default`\n          });\n        }\n        const validateName = gen.scopeName("validate");\n        sch.validateName = validateName;\n        const schemaCxt = {\n          gen,\n          allErrors: this.opts.allErrors,\n          data: names_1.default.data,\n          parentData: names_1.default.parentData,\n          parentDataProperty: names_1.default.parentDataProperty,\n          dataNames: [names_1.default.data],\n          dataPathArr: [codegen_1.nil],\n          // TODO can its length be used as dataLevel if nil is removed?\n          dataLevel: 0,\n          dataTypes: [],\n          definedProperties: /* @__PURE__ */ new Set(),\n          topSchemaRef: gen.scopeValue("schema", this.opts.code.source === true ? { ref: sch.schema, code: (0, codegen_1.stringify)(sch.schema) } : { ref: sch.schema }),\n          validateName,\n          ValidationError: _ValidationError,\n          schema: sch.schema,\n          schemaEnv: sch,\n          rootId,\n          baseId: sch.baseId || rootId,\n          schemaPath: codegen_1.nil,\n          errSchemaPath: sch.schemaPath || (this.opts.jtd ? "" : "#"),\n          errorPath: (0, codegen_1._)`""`,\n          opts: this.opts,\n          self: this\n        };\n        let sourceCode;\n        try {\n          this._compilations.add(sch);\n          (0, validate_1.validateFunctionCode)(schemaCxt);\n          gen.optimize(this.opts.code.optimize);\n          const validateCode = gen.toString();\n          sourceCode = `${gen.scopeRefs(names_1.default.scope)}return ${validateCode}`;\n          if (this.opts.code.process)\n            sourceCode = this.opts.code.process(sourceCode, sch);\n          const makeValidate = new Function(`${names_1.default.self}`, `${names_1.default.scope}`, sourceCode);\n          const validate = makeValidate(this, this.scope.get());\n          this.scope.value(validateName, { ref: validate });\n          validate.errors = null;\n          validate.schema = sch.schema;\n          validate.schemaEnv = sch;\n          if (sch.$async)\n            validate.$async = true;\n          if (this.opts.code.source === true) {\n            validate.source = { validateName, validateCode, scopeValues: gen._values };\n          }\n          if (this.opts.unevaluated) {\n            const { props, items } = schemaCxt;\n            validate.evaluated = {\n              props: props instanceof codegen_1.Name ? void 0 : props,\n              items: items instanceof codegen_1.Name ? void 0 : items,\n              dynamicProps: props instanceof codegen_1.Name,\n              dynamicItems: items instanceof codegen_1.Name\n            };\n            if (validate.source)\n              validate.source.evaluated = (0, codegen_1.stringify)(validate.evaluated);\n          }\n          sch.validate = validate;\n          return sch;\n        } catch (e) {\n          delete sch.validate;\n          delete sch.validateName;\n          if (sourceCode)\n            this.logger.error("Error compiling schema, function code:", sourceCode);\n          throw e;\n        } finally {\n          this._compilations.delete(sch);\n        }\n      }\n      exports.compileSchema = compileSchema;\n      function resolveRef(root, baseId, ref) {\n        var _a;\n        ref = (0, resolve_1.resolveUrl)(this.opts.uriResolver, baseId, ref);\n        const schOrFunc = root.refs[ref];\n        if (schOrFunc)\n          return schOrFunc;\n        let _sch = resolve.call(this, root, ref);\n        if (_sch === void 0) {\n          const schema = (_a = root.localRefs) === null || _a === void 0 ? void 0 : _a[ref];\n          const { schemaId } = this.opts;\n          if (schema)\n            _sch = new SchemaEnv({ schema, schemaId, root, baseId });\n        }\n        if (_sch === void 0)\n          return;\n        return root.refs[ref] = inlineOrCompile.call(this, _sch);\n      }\n      exports.resolveRef = resolveRef;\n      function inlineOrCompile(sch) {\n        if ((0, resolve_1.inlineRef)(sch.schema, this.opts.inlineRefs))\n          return sch.schema;\n        return sch.validate ? sch : compileSchema.call(this, sch);\n      }\n      function getCompilingSchema(schEnv) {\n        for (const sch of this._compilations) {\n          if (sameSchemaEnv(sch, schEnv))\n            return sch;\n        }\n      }\n      exports.getCompilingSchema = getCompilingSchema;\n      function sameSchemaEnv(s1, s2) {\n        return s1.schema === s2.schema && s1.root === s2.root && s1.baseId === s2.baseId;\n      }\n      function resolve(root, ref) {\n        let sch;\n        while (typeof (sch = this.refs[ref]) == "string")\n          ref = sch;\n        return sch || this.schemas[ref] || resolveSchema.call(this, root, ref);\n      }\n      function resolveSchema(root, ref) {\n        const p = this.opts.uriResolver.parse(ref);\n        const refPath = (0, resolve_1._getFullPath)(this.opts.uriResolver, p);\n        let baseId = (0, resolve_1.getFullPath)(this.opts.uriResolver, root.baseId, void 0);\n        if (Object.keys(root.schema).length > 0 && refPath === baseId) {\n          return getJsonPointer.call(this, p, root);\n        }\n        const id = (0, resolve_1.normalizeId)(refPath);\n        const schOrRef = this.refs[id] || this.schemas[id];\n        if (typeof schOrRef == "string") {\n          const sch = resolveSchema.call(this, root, schOrRef);\n          if (typeof (sch === null || sch === void 0 ? void 0 : sch.schema) !== "object")\n            return;\n          return getJsonPointer.call(this, p, sch);\n        }\n        if (typeof (schOrRef === null || schOrRef === void 0 ? void 0 : schOrRef.schema) !== "object")\n          return;\n        if (!schOrRef.validate)\n          compileSchema.call(this, schOrRef);\n        if (id === (0, resolve_1.normalizeId)(ref)) {\n          const { schema } = schOrRef;\n          const { schemaId } = this.opts;\n          const schId = schema[schemaId];\n          if (schId)\n            baseId = (0, resolve_1.resolveUrl)(this.opts.uriResolver, baseId, schId);\n          return new SchemaEnv({ schema, schemaId, root, baseId });\n        }\n        return getJsonPointer.call(this, p, schOrRef);\n      }\n      exports.resolveSchema = resolveSchema;\n      var PREVENT_SCOPE_CHANGE = /* @__PURE__ */ new Set([\n        "properties",\n        "patternProperties",\n        "enum",\n        "dependencies",\n        "definitions"\n      ]);\n      function getJsonPointer(parsedRef, { baseId, schema, root }) {\n        var _a;\n        if (((_a = parsedRef.fragment) === null || _a === void 0 ? void 0 : _a[0]) !== "/")\n          return;\n        for (const part of parsedRef.fragment.slice(1).split("/")) {\n          if (typeof schema === "boolean")\n            return;\n          const partSchema = schema[(0, util_1.unescapeFragment)(part)];\n          if (partSchema === void 0)\n            return;\n          schema = partSchema;\n          const schId = typeof schema === "object" && schema[this.opts.schemaId];\n          if (!PREVENT_SCOPE_CHANGE.has(part) && schId) {\n            baseId = (0, resolve_1.resolveUrl)(this.opts.uriResolver, baseId, schId);\n          }\n        }\n        let env;\n        if (typeof schema != "boolean" && schema.$ref && !(0, util_1.schemaHasRulesButRef)(schema, this.RULES)) {\n          const $ref = (0, resolve_1.resolveUrl)(this.opts.uriResolver, baseId, schema.$ref);\n          env = resolveSchema.call(this, root, $ref);\n        }\n        const { schemaId } = this.opts;\n        env = env || new SchemaEnv({ schema, schemaId, root, baseId });\n        if (env.schema !== env.root.schema)\n          return env;\n        return void 0;\n      }\n    }\n  });\n\n  // node_modules/ajv/dist/refs/data.json\n  var require_data = __commonJS({\n    "node_modules/ajv/dist/refs/data.json"(exports, module) {\n      module.exports = {\n        $id: "https://raw.githubusercontent.com/ajv-validator/ajv/master/lib/refs/data.json#",\n        description: "Meta-schema for $data reference (JSON AnySchema extension proposal)",\n        type: "object",\n        required: ["$data"],\n        properties: {\n          $data: {\n            type: "string",\n            anyOf: [{ format: "relative-json-pointer" }, { format: "json-pointer" }]\n          }\n        },\n        additionalProperties: false\n      };\n    }\n  });\n\n  // node_modules/fast-uri/lib/utils.js\n  var require_utils = __commonJS({\n    "node_modules/fast-uri/lib/utils.js"(exports, module) {\n      "use strict";\n      var isUUID = RegExp.prototype.test.bind(/^[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}$/iu);\n      var isIPv4 = RegExp.prototype.test.bind(/^(?:(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$/u);\n      var isPort = RegExp.prototype.test.bind(/^\\d*$/u);\n      var isHexPair = RegExp.prototype.test.bind(/^[\\da-f]{2}$/iu);\n      var isUnreserved = RegExp.prototype.test.bind(/^[\\da-z\\-._~]$/iu);\n      var isPathCharacter = RegExp.prototype.test.bind(/^[A-Za-z0-9\\-._~!$&\'()*+,;=:@/]$/u);\n      var isQueryFragmentCharacter = RegExp.prototype.test.bind(/^[A-Za-z0-9\\-._~!$&\'()*+,;=:@/?]$/u);\n      var isUserinfoCharacter = RegExp.prototype.test.bind(/^[A-Za-z0-9\\-._~!$&\'()*+,;=:]$/u);\n      var BYTE_HEX = new Array(256);\n      {\n        const HEX_DIGITS = "0123456789ABCDEF";\n        for (let i = 0; i < 256; i++) {\n          BYTE_HEX[i] = "%" + HEX_DIGITS[i >> 4] + HEX_DIGITS[i & 15];\n        }\n      }\n      function percentEncodeNonAscii(cp) {\n        if (cp < 2048) {\n          return BYTE_HEX[192 | cp >> 6] + BYTE_HEX[128 | cp & 63];\n        }\n        if (cp < 65536) {\n          return BYTE_HEX[224 | cp >> 12] + BYTE_HEX[128 | cp >> 6 & 63] + BYTE_HEX[128 | cp & 63];\n        }\n        return BYTE_HEX[240 | cp >> 18] + BYTE_HEX[128 | cp >> 12 & 63] + BYTE_HEX[128 | cp >> 6 & 63] + BYTE_HEX[128 | cp & 63];\n      }\n      function stringArrayToHexStripped(input) {\n        let acc = "";\n        let code = 0;\n        let i = 0;\n        for (i = 0; i < input.length; i++) {\n          code = input[i].charCodeAt(0);\n          if (code === 48) {\n            continue;\n          }\n          if (!(code >= 48 && code <= 57 || code >= 65 && code <= 70 || code >= 97 && code <= 102)) {\n            return "";\n          }\n          acc += input[i];\n          break;\n        }\n        for (i += 1; i < input.length; i++) {\n          code = input[i].charCodeAt(0);\n          if (!(code >= 48 && code <= 57 || code >= 65 && code <= 70 || code >= 97 && code <= 102)) {\n            return "";\n          }\n          acc += input[i];\n        }\n        return acc;\n      }\n      var isHextet = RegExp.prototype.test.bind(/^[\\dA-Fa-f]{1,4}$/);\n      var isIPvFuture = RegExp.prototype.test.bind(/^[vV][\\dA-Fa-f]+\\.[A-Za-z\\d\\-._~!$&\'()*+,;=:]+$/);\n      var isZoneCharacter = RegExp.prototype.test.bind(/^[A-Za-z\\d\\-._~]$/);\n      var nonSimpleDomain = RegExp.prototype.test.bind(/[^!"$&\'()*+,\\-.;=_`a-z{}~]/u);\n      function isZoneIdentifier(zone) {\n        if (zone.length === 0) return false;\n        for (let i = 0; i < zone.length; i++) {\n          if (isZoneCharacter(zone[i])) continue;\n          if (zone[i] === "%" && i + 2 < zone.length && isHexPair(zone.slice(i + 1, i + 3))) {\n            i += 2;\n            continue;\n          }\n          return false;\n        }\n        return true;\n      }\n      function compressIPv6ZeroRun(hextets) {\n        let bestStart = -1;\n        let bestLength = 0;\n        let runStart = -1;\n        let runLength = 0;\n        for (let i = 0; i < hextets.length; i++) {\n          if (hextets[i] === "0") {\n            if (runStart === -1) runStart = i;\n            runLength++;\n            if (runLength > bestLength) {\n              bestLength = runLength;\n              bestStart = runStart;\n            }\n          } else {\n            runStart = -1;\n            runLength = 0;\n          }\n        }\n        if (bestLength < 2) return hextets.join(":");\n        const head = hextets.slice(0, bestStart).join(":");\n        const tail = hextets.slice(bestStart + bestLength).join(":");\n        return head + "::" + tail;\n      }\n      function normalizeIPv6Address(input) {\n        const compression = input.indexOf("::");\n        if (compression !== -1 && input.indexOf("::", compression + 1) !== -1) return void 0;\n        const left = compression === -1 ? input.split(":") : input.slice(0, compression).split(":");\n        const right = compression === -1 ? [] : input.slice(compression + 2).split(":");\n        if (compression !== -1) {\n          if (left.length === 1 && left[0] === "") left.length = 0;\n          if (right.length === 1 && right[0] === "") right.length = 0;\n        }\n        const parts = left.concat(right);\n        let hextetCount = 0;\n        for (let i = 0; i < parts.length; i++) {\n          const part = parts[i];\n          if (part === "") return void 0;\n          if (part.indexOf(".") !== -1) {\n            if (i !== parts.length - 1 || compression !== -1 && right.length === 0 || !isIPv4(part)) return void 0;\n            hextetCount += 2;\n            continue;\n          }\n          if (!isHextet(part)) return void 0;\n          parts[i] = parseInt(part, 16).toString(16);\n          hextetCount++;\n        }\n        if (compression === -1) {\n          if (hextetCount !== 8) return void 0;\n          return compressIPv6ZeroRun(parts);\n        }\n        if (hextetCount >= 8) return void 0;\n        const expanded = parts.slice(0, left.length);\n        for (let i = hextetCount; i < 8; i++) expanded.push("0");\n        for (let i = left.length; i < parts.length; i++) expanded.push(parts[i]);\n        return compressIPv6ZeroRun(expanded);\n      }\n      function normalizeIPv6(host) {\n        const bracketed = host[0] === "[" && host[host.length - 1] === "]";\n        const hasBracket = host[0] === "[" || host[host.length - 1] === "]";\n        if (hasBracket && !bracketed) return { host, isIPV6: false, error: true };\n        let input = bracketed ? host.slice(1, -1) : host;\n        if (bracketed && isIPvFuture(input)) {\n          input = input.toLowerCase();\n          return { host: `[${input}]`, escapedHost: input, isIPV6: false, isIPVFuture: true };\n        }\n        if (findToken(input, ":") < 2) {\n          return { host, isIPV6: false, error: bracketed };\n        }\n        let zoneIdentifier = "";\n        const zoneSeparator = input.indexOf("%");\n        if (zoneSeparator !== -1) {\n          const separatorLength = input.slice(zoneSeparator, zoneSeparator + 3).toLowerCase() === "%25" ? 3 : 1;\n          zoneIdentifier = input.slice(zoneSeparator + separatorLength);\n          if (!isZoneIdentifier(zoneIdentifier)) return { host, isIPV6: false, error: true };\n          input = input.slice(0, zoneSeparator);\n        }\n        const address = normalizeIPv6Address(input);\n        if (address === void 0) return { host, isIPV6: false, error: true };\n        return {\n          host: address + (zoneIdentifier ? "%" + zoneIdentifier : ""),\n          escapedHost: address + (zoneIdentifier ? "%25" + zoneIdentifier : ""),\n          isIPV6: true\n        };\n      }\n      function findToken(str, token) {\n        let ind = 0;\n        for (let i = 0; i < str.length; i++) {\n          if (str[i] === token) ind++;\n        }\n        return ind;\n      }\n      function removeDotSegments(path) {\n        let input = path;\n        const output = [];\n        let nextSlash = -1;\n        let len = 0;\n        while (len = input.length) {\n          if (len === 1) {\n            if (input === ".") {\n              break;\n            } else if (input === "/") {\n              output.push("/");\n              break;\n            } else {\n              output.push(input);\n              break;\n            }\n          } else if (len === 2) {\n            if (input[0] === ".") {\n              if (input[1] === ".") {\n                break;\n              } else if (input[1] === "/") {\n                input = input.slice(2);\n                continue;\n              }\n            } else if (input[0] === "/") {\n              if (input[1] === "." || input[1] === "/") {\n                output.push("/");\n                break;\n              }\n            }\n          } else if (len === 3) {\n            if (input === "/..") {\n              if (output.length !== 0) {\n                output.pop();\n              }\n              output.push("/");\n              break;\n            }\n          }\n          if (input[0] === ".") {\n            if (input[1] === ".") {\n              if (input[2] === "/") {\n                input = input.slice(3);\n                continue;\n              }\n            } else if (input[1] === "/") {\n              input = input.slice(2);\n              continue;\n            }\n          } else if (input[0] === "/") {\n            if (input[1] === ".") {\n              if (input[2] === "/") {\n                input = input.slice(2);\n                continue;\n              } else if (input[2] === ".") {\n                if (input[3] === "/") {\n                  input = input.slice(3);\n                  if (output.length !== 0) {\n                    output.pop();\n                  }\n                  continue;\n                }\n              }\n            }\n          }\n          if ((nextSlash = input.indexOf("/", 1)) === -1) {\n            output.push(input);\n            break;\n          } else {\n            output.push(input.slice(0, nextSlash));\n            input = input.slice(nextSlash);\n          }\n        }\n        return output.join("");\n      }\n      var HOST_DELIMS = { "@": "%40", "/": "%2F", "?": "%3F", "#": "%23", ":": "%3A" };\n      var HOST_DELIM_RE = /[@/?#:]/g;\n      var HOST_DELIM_NO_COLON_RE = /[@/?#]/g;\n      function reescapeHostDelimiters(host, isIP) {\n        const re = isIP ? HOST_DELIM_NO_COLON_RE : HOST_DELIM_RE;\n        re.lastIndex = 0;\n        return host.replace(re, (ch) => HOST_DELIMS[ch]);\n      }\n      function normalizePercentEncoding(input, decodeUnreserved = false) {\n        if (input.indexOf("%") === -1) {\n          return input;\n        }\n        let output = "";\n        for (let i = 0; i < input.length; i++) {\n          if (input[i] === "%" && i + 2 < input.length) {\n            const hex = input.slice(i + 1, i + 3);\n            if (isHexPair(hex)) {\n              const normalizedHex = hex.toUpperCase();\n              const decoded = String.fromCharCode(parseInt(normalizedHex, 16));\n              if (decodeUnreserved && isUnreserved(decoded)) {\n                output += decoded;\n              } else {\n                output += "%" + normalizedHex;\n              }\n              i += 2;\n              continue;\n            }\n          }\n          output += input[i];\n        }\n        return output;\n      }\n      function normalizePathEncoding(input) {\n        let output = "";\n        for (let i = 0; i < input.length; i++) {\n          const ch = input[i];\n          if (ch === "%" && i + 2 < input.length) {\n            const hex = input.slice(i + 1, i + 3);\n            if (isHexPair(hex)) {\n              const normalizedHex = hex.toUpperCase();\n              const decoded = String.fromCharCode(parseInt(normalizedHex, 16));\n              if (decoded !== "." && isUnreserved(decoded)) {\n                output += decoded;\n              } else {\n                output += "%" + normalizedHex;\n              }\n              i += 2;\n              continue;\n            }\n          }\n          if (isPathCharacter(ch)) {\n            output += ch;\n          } else {\n            const code = input.charCodeAt(i);\n            if (code < 128) {\n              output += isEscapeSafe(code) ? ch : BYTE_HEX[code];\n            } else if (code < 55296 || code > 57343) {\n              output += percentEncodeNonAscii(code);\n            } else if (code <= 56319 && i + 1 < input.length) {\n              const low = input.charCodeAt(i + 1);\n              if (low >= 56320 && low <= 57343) {\n                output += percentEncodeNonAscii(65536 + (code - 55296 << 10) + (low - 56320));\n                i++;\n              } else {\n                output += percentEncodeNonAscii(65533);\n              }\n            } else {\n              output += percentEncodeNonAscii(65533);\n            }\n          }\n        }\n        return output;\n      }\n      function serializePathEncoding(input, pathNoScheme = false) {\n        let output = "";\n        let firstSegment = pathNoScheme && input[0] !== "/";\n        for (let i = 0; i < input.length; i++) {\n          const ch = input[i];\n          if (ch === "%" && i + 2 < input.length) {\n            const hex = input.slice(i + 1, i + 3);\n            if (isHexPair(hex)) {\n              output += "%" + hex.toUpperCase();\n              i += 2;\n              continue;\n            }\n          }\n          if (ch === "/") {\n            firstSegment = false;\n          }\n          if (isPathCharacter(ch) && (ch !== ":" || !firstSegment)) {\n            output += ch;\n          } else {\n            const code = input.charCodeAt(i);\n            if (code < 128) {\n              output += BYTE_HEX[code];\n            } else if (code < 55296 || code > 57343) {\n              output += percentEncodeNonAscii(code);\n            } else if (code <= 56319 && i + 1 < input.length) {\n              const low = input.charCodeAt(i + 1);\n              if (low >= 56320 && low <= 57343) {\n                output += percentEncodeNonAscii(65536 + (code - 55296 << 10) + (low - 56320));\n                i++;\n              } else {\n                output += percentEncodeNonAscii(65533);\n              }\n            } else {\n              output += percentEncodeNonAscii(65533);\n            }\n          }\n        }\n        return output;\n      }\n      function encodeComponent(input, isAllowed) {\n        let output = "";\n        for (let i = 0; i < input.length; i++) {\n          const ch = input[i];\n          if (ch === "%" && i + 2 < input.length) {\n            const hex = input.slice(i + 1, i + 3);\n            if (isHexPair(hex)) {\n              output += "%" + hex.toUpperCase();\n              i += 2;\n              continue;\n            }\n          }\n          if (isAllowed(ch)) {\n            output += ch;\n          } else {\n            const code = input.charCodeAt(i);\n            if (code < 128) {\n              output += BYTE_HEX[code];\n            } else if (code < 55296 || code > 57343) {\n              output += percentEncodeNonAscii(code);\n            } else if (code <= 56319 && i + 1 < input.length) {\n              const low = input.charCodeAt(i + 1);\n              if (low >= 56320 && low <= 57343) {\n                output += percentEncodeNonAscii(65536 + (code - 55296 << 10) + (low - 56320));\n                i++;\n              } else {\n                output += percentEncodeNonAscii(65533);\n              }\n            } else {\n              output += percentEncodeNonAscii(65533);\n            }\n          }\n        }\n        return output;\n      }\n      function encodeUserinfo(input) {\n        return encodeComponent(input, isUserinfoCharacter);\n      }\n      function encodeQuery(input) {\n        return encodeComponent(input, isQueryFragmentCharacter);\n      }\n      function encodeFragment(input) {\n        return encodeComponent(input, isQueryFragmentCharacter);\n      }\n      function isEscapeSafe(cp) {\n        return cp >= 48 && cp <= 57 || cp >= 65 && cp <= 90 || cp >= 97 && cp <= 122 || cp === 42 || cp === 43 || cp === 45 || cp === 46 || cp === 47 || cp === 64 || cp === 95;\n      }\n      function normalizeQueryFragmentEncoding(input) {\n        let output = "";\n        for (let i = 0; i < input.length; i++) {\n          const ch = input[i];\n          if (ch === "%" && i + 2 < input.length) {\n            const hex = input.slice(i + 1, i + 3);\n            if (isHexPair(hex)) {\n              const normalizedHex = hex.toUpperCase();\n              const decoded = String.fromCharCode(parseInt(normalizedHex, 16));\n              if (isUnreserved(decoded)) {\n                output += decoded;\n              } else {\n                output += "%" + normalizedHex;\n              }\n              i += 2;\n              continue;\n            }\n          }\n          if (isQueryFragmentCharacter(ch)) {\n            output += ch;\n          } else {\n            const code = input.charCodeAt(i);\n            if (code < 128) {\n              output += isEscapeSafe(code) ? ch : BYTE_HEX[code];\n            } else if (code < 55296 || code > 57343) {\n              output += percentEncodeNonAscii(code);\n            } else if (code <= 56319 && i + 1 < input.length) {\n              const low = input.charCodeAt(i + 1);\n              if (low >= 56320 && low <= 57343) {\n                output += percentEncodeNonAscii(65536 + (code - 55296 << 10) + (low - 56320));\n                i++;\n              } else {\n                output += percentEncodeNonAscii(65533);\n              }\n            } else {\n              output += percentEncodeNonAscii(65533);\n            }\n          }\n        }\n        return output;\n      }\n      function escapePreservingEscapes(input) {\n        let output = "";\n        for (let i = 0; i < input.length; i++) {\n          if (input[i] === "%" && i + 2 < input.length) {\n            const hex = input.slice(i + 1, i + 3);\n            if (isHexPair(hex)) {\n              output += "%" + hex.toUpperCase();\n              i += 2;\n              continue;\n            }\n          }\n          output += escape(input[i]);\n        }\n        return output;\n      }\n      function recomposeAuthority(component) {\n        const uriTokens = [];\n        if (component.userinfo !== void 0) {\n          uriTokens.push(encodeUserinfo(component.userinfo));\n          uriTokens.push("@");\n        }\n        if (component.host !== void 0) {\n          let host = component.host;\n          if (!isIPv4(host)) {\n            let ipV6res = normalizeIPv6(host);\n            if (ipV6res.isIPV6 !== true && ipV6res.isIPVFuture !== true) {\n              host = normalizePercentEncoding(host, true);\n              ipV6res = normalizeIPv6(host);\n            }\n            if (ipV6res.isIPV6 === true || ipV6res.isIPVFuture === true) {\n              host = `[${ipV6res.escapedHost}]`;\n            } else {\n              host = reescapeHostDelimiters(host, false);\n            }\n          }\n          uriTokens.push(host);\n        }\n        if (typeof component.port === "number" || typeof component.port === "string") {\n          const port = String(component.port);\n          if (!isPort(port)) {\n            throw new TypeError("URI port is malformed.");\n          }\n          uriTokens.push(":");\n          uriTokens.push(port);\n        }\n        return uriTokens.length ? uriTokens.join("") : void 0;\n      }\n      module.exports = {\n        nonSimpleDomain,\n        recomposeAuthority,\n        reescapeHostDelimiters,\n        normalizePercentEncoding,\n        normalizePathEncoding,\n        serializePathEncoding,\n        normalizeQueryFragmentEncoding,\n        encodeUserinfo,\n        encodeQuery,\n        encodeFragment,\n        escapePreservingEscapes,\n        removeDotSegments,\n        isIPv4,\n        isUUID,\n        normalizeIPv6,\n        stringArrayToHexStripped\n      };\n    }\n  });\n\n  // node_modules/fast-uri/lib/schemes.js\n  var require_schemes = __commonJS({\n    "node_modules/fast-uri/lib/schemes.js"(exports, module) {\n      "use strict";\n      var { isUUID } = require_utils();\n      var URN_REG = /^([\\da-z][\\d\\-a-z]{0,31}):((?:[\\w!$\'()*+,\\-./:;=@]|%[\\da-f]{2})+)$/iu;\n      var supportedSchemeNames = (\n        /** @type {const} */\n        [\n          "http",\n          "https",\n          "ws",\n          "wss",\n          "urn",\n          "urn:uuid"\n        ]\n      );\n      function isValidSchemeName(name) {\n        return supportedSchemeNames.indexOf(\n          /** @type {*} */\n          name\n        ) !== -1;\n      }\n      function wsIsSecure(wsComponent) {\n        if (wsComponent.secure === true) {\n          return true;\n        } else if (wsComponent.secure === false) {\n          return false;\n        } else if (wsComponent.scheme) {\n          return wsComponent.scheme.length === 3 && (wsComponent.scheme[0] === "w" || wsComponent.scheme[0] === "W") && (wsComponent.scheme[1] === "s" || wsComponent.scheme[1] === "S") && (wsComponent.scheme[2] === "s" || wsComponent.scheme[2] === "S");\n        } else {\n          return false;\n        }\n      }\n      function httpParse(component) {\n        if (!component.host) {\n          component.error = component.error || "HTTP URIs must have a host.";\n        }\n        return component;\n      }\n      function httpSerialize(component) {\n        const secure = String(component.scheme).toLowerCase() === "https";\n        if (component.port === (secure ? 443 : 80) || component.port === "") {\n          component.port = void 0;\n        }\n        if (!component.path) {\n          component.path = "/";\n        }\n        return component;\n      }\n      function wsParse(wsComponent) {\n        wsComponent.secure = wsIsSecure(wsComponent);\n        wsComponent.resourceName = (wsComponent.path || "/") + (wsComponent.query ? "?" + wsComponent.query : "");\n        wsComponent.path = void 0;\n        wsComponent.query = void 0;\n        return wsComponent;\n      }\n      function wsSerialize(wsComponent) {\n        if (wsComponent.port === (wsIsSecure(wsComponent) ? 443 : 80) || wsComponent.port === "") {\n          wsComponent.port = void 0;\n        }\n        if (typeof wsComponent.secure === "boolean") {\n          wsComponent.scheme = wsComponent.secure ? "wss" : "ws";\n          wsComponent.secure = void 0;\n        }\n        if (wsComponent.resourceName) {\n          const queryIndex = wsComponent.resourceName.indexOf("?");\n          const path = queryIndex === -1 ? wsComponent.resourceName : wsComponent.resourceName.slice(0, queryIndex);\n          wsComponent.path = path && path !== "/" ? path : void 0;\n          wsComponent.query = queryIndex === -1 ? void 0 : wsComponent.resourceName.slice(queryIndex + 1);\n          wsComponent.resourceName = void 0;\n        }\n        wsComponent.fragment = void 0;\n        return wsComponent;\n      }\n      function urnParse(urnComponent, options) {\n        if (!urnComponent.path) {\n          urnComponent.error = "URN can not be parsed";\n          return urnComponent;\n        }\n        const matches = urnComponent.path.match(URN_REG);\n        if (matches && matches[0] === urnComponent.path) {\n          const scheme = options.scheme || urnComponent.scheme || "urn";\n          urnComponent.nid = matches[1].toLowerCase();\n          urnComponent.nss = matches[2];\n          const urnScheme = `${scheme}:${options.nid || urnComponent.nid}`;\n          const schemeHandler = getSchemeHandler(urnScheme);\n          urnComponent.path = void 0;\n          if (schemeHandler) {\n            urnComponent = schemeHandler.parse(urnComponent, options);\n          }\n        } else {\n          urnComponent.error = urnComponent.error || "URN can not be parsed.";\n        }\n        return urnComponent;\n      }\n      function urnSerialize(urnComponent, options) {\n        if (urnComponent.nid === void 0) {\n          throw new Error("URN without nid cannot be serialized");\n        }\n        const scheme = options.scheme || urnComponent.scheme || "urn";\n        const nid = urnComponent.nid.toLowerCase();\n        const urnScheme = `${scheme}:${options.nid || nid}`;\n        const schemeHandler = getSchemeHandler(urnScheme);\n        if (schemeHandler) {\n          urnComponent = schemeHandler.serialize(urnComponent, options);\n        }\n        const uriComponent = urnComponent;\n        const nss = urnComponent.nss;\n        uriComponent.path = `${nid || options.nid}:${nss}`;\n        options.skipEscape = true;\n        return uriComponent;\n      }\n      function urnuuidParse(urnComponent, options) {\n        const uuidComponent = urnComponent;\n        uuidComponent.uuid = uuidComponent.nss;\n        uuidComponent.nss = void 0;\n        if (!options.tolerant && (!uuidComponent.uuid || !isUUID(uuidComponent.uuid))) {\n          uuidComponent.error = uuidComponent.error || "UUID is not valid.";\n        }\n        return uuidComponent;\n      }\n      function urnuuidSerialize(uuidComponent) {\n        const urnComponent = uuidComponent;\n        urnComponent.nss = (uuidComponent.uuid || "").toLowerCase();\n        return urnComponent;\n      }\n      var http = (\n        /** @type {SchemeHandler} */\n        {\n          scheme: "http",\n          domainHost: true,\n          parse: httpParse,\n          serialize: httpSerialize\n        }\n      );\n      var https = (\n        /** @type {SchemeHandler} */\n        {\n          scheme: "https",\n          domainHost: http.domainHost,\n          parse: httpParse,\n          serialize: httpSerialize\n        }\n      );\n      var ws = (\n        /** @type {SchemeHandler} */\n        {\n          scheme: "ws",\n          domainHost: true,\n          parse: wsParse,\n          serialize: wsSerialize\n        }\n      );\n      var wss = (\n        /** @type {SchemeHandler} */\n        {\n          scheme: "wss",\n          domainHost: ws.domainHost,\n          parse: ws.parse,\n          serialize: ws.serialize\n        }\n      );\n      var urn = (\n        /** @type {SchemeHandler} */\n        {\n          scheme: "urn",\n          parse: urnParse,\n          serialize: urnSerialize,\n          skipNormalize: true\n        }\n      );\n      var urnuuid = (\n        /** @type {SchemeHandler} */\n        {\n          scheme: "urn:uuid",\n          parse: urnuuidParse,\n          serialize: urnuuidSerialize,\n          skipNormalize: true\n        }\n      );\n      var SCHEMES = (\n        /** @type {Record<SchemeName, SchemeHandler>} */\n        {\n          http,\n          https,\n          ws,\n          wss,\n          urn,\n          "urn:uuid": urnuuid\n        }\n      );\n      Object.setPrototypeOf(SCHEMES, null);\n      function getSchemeHandler(scheme) {\n        return scheme && (SCHEMES[\n          /** @type {SchemeName} */\n          scheme\n        ] || SCHEMES[\n          /** @type {SchemeName} */\n          scheme.toLowerCase()\n        ]) || void 0;\n      }\n      module.exports = {\n        wsIsSecure,\n        SCHEMES,\n        isValidSchemeName,\n        getSchemeHandler\n      };\n    }\n  });\n\n  // node_modules/fast-uri/index.js\n  var require_fast_uri = __commonJS({\n    "node_modules/fast-uri/index.js"(exports, module) {\n      "use strict";\n      var { normalizeIPv6, removeDotSegments, recomposeAuthority, normalizePercentEncoding, normalizePathEncoding, serializePathEncoding, normalizeQueryFragmentEncoding, encodeQuery, encodeFragment, reescapeHostDelimiters, isIPv4, nonSimpleDomain } = require_utils();\n      var { SCHEMES, getSchemeHandler } = require_schemes();\n      var VALID_SCHEME = /^[A-Za-z][A-Za-z0-9+.-]*$/u;\n      var MALFORMED_SCHEME_ERROR = "URI scheme is malformed.";\n      function decodeValidScheme(scheme) {\n        const decodedScheme = unescape(String(scheme));\n        if (!VALID_SCHEME.test(decodedScheme)) {\n          throw new TypeError(MALFORMED_SCHEME_ERROR);\n        }\n        return decodedScheme;\n      }\n      function normalize(uri, options) {\n        if (typeof uri === "string") {\n          uri = /** @type {T} */\n          normalizeString(uri, options);\n        } else if (typeof uri === "object") {\n          uri = /** @type {T} */\n          parse(serialize(uri, options), options);\n        }\n        return uri;\n      }\n      function resolve(baseURI, relativeURI, options) {\n        const schemelessOptions = options ? Object.assign({ scheme: "null" }, options) : { scheme: "null" };\n        const {\n          parsed: baseParsed,\n          malformedAuthorityOrPort: baseMalformed,\n          malformedPercentEncoding: baseMalformedPercentEncoding,\n          malformedSchemeSpecific: baseMalformedSchemeSpecific,\n          malformedHost: baseMalformedHost,\n          malformedScheme: baseMalformedScheme\n        } = parseWithStatus(baseURI, schemelessOptions);\n        const {\n          parsed: relativeParsed,\n          malformedAuthorityOrPort: relativeMalformed,\n          malformedPercentEncoding: relativeMalformedPercentEncoding,\n          malformedSchemeSpecific: relativeMalformedSchemeSpecific,\n          malformedHost: relativeMalformedHost,\n          malformedScheme: relativeMalformedScheme\n        } = parseWithStatus(relativeURI, schemelessOptions);\n        if (baseMalformed || relativeMalformed || baseMalformedPercentEncoding || relativeMalformedPercentEncoding || baseMalformedSchemeSpecific || relativeMalformedSchemeSpecific || baseMalformedHost || relativeMalformedHost || baseMalformedScheme || relativeMalformedScheme) {\n          throw new Error(baseParsed.error || relativeParsed.error || "URI is malformed.");\n        }\n        const resolved = resolveComponent(baseParsed, relativeParsed, schemelessOptions, true);\n        const resolvedSchemeHandler = getSchemeHandler(options && options.scheme || resolved.scheme);\n        const resolvedHost = resolved.host;\n        const resolvedHostIsIP = resolvedHost !== void 0 && resolvedHost !== "" && (isIPv4(resolvedHost) || normalizeIPv6(resolvedHost).isIPV6);\n        canonicalizeHost(resolved, options || {}, resolvedSchemeHandler, resolvedHostIsIP);\n        const encodedASCIIHost = resolvedHost && resolvedHost.indexOf("%") !== -1 && !/\\P{ASCII}/u.test(resolvedHost);\n        if (resolved.error && !encodedASCIIHost) {\n          throw new Error(resolved.error);\n        }\n        schemelessOptions.skipEscape = true;\n        return serialize(resolved, schemelessOptions);\n      }\n      function resolveComponent(base, relative, options, skipNormalization) {\n        const target2 = {};\n        if (!skipNormalization) {\n          base = parse(serialize(base, options), options);\n          relative = parse(serialize(relative, options), options);\n        }\n        options = options || {};\n        if (!options.tolerant && relative.scheme) {\n          target2.scheme = relative.scheme;\n          target2.userinfo = relative.userinfo;\n          target2.host = relative.host;\n          target2.port = relative.port;\n          target2.path = removeDotSegments(relative.path || "");\n          target2.query = relative.query;\n        } else {\n          if (relative.userinfo !== void 0 || relative.host !== void 0 || relative.port !== void 0) {\n            target2.userinfo = relative.userinfo;\n            target2.host = relative.host;\n            target2.port = relative.port;\n            target2.path = removeDotSegments(relative.path || "");\n            target2.query = relative.query;\n          } else {\n            if (!relative.path) {\n              target2.path = base.path;\n              if (relative.query !== void 0) {\n                target2.query = relative.query;\n              } else {\n                target2.query = base.query;\n              }\n            } else {\n              if (relative.path[0] === "/") {\n                target2.path = removeDotSegments(relative.path);\n              } else {\n                if ((base.userinfo !== void 0 || base.host !== void 0 || base.port !== void 0) && !base.path) {\n                  target2.path = "/" + relative.path;\n                } else if (!base.path) {\n                  target2.path = relative.path;\n                } else {\n                  target2.path = base.path.slice(0, base.path.lastIndexOf("/") + 1) + relative.path;\n                }\n                target2.path = removeDotSegments(target2.path);\n              }\n              target2.query = relative.query;\n            }\n            target2.userinfo = base.userinfo;\n            target2.host = base.host;\n            target2.port = base.port;\n          }\n          target2.scheme = base.scheme;\n        }\n        target2.fragment = relative.fragment;\n        return target2;\n      }\n      function equal(uriA, uriB, options) {\n        const normalizedA = normalizeComparableURI(uriA, options);\n        const normalizedB = normalizeComparableURI(uriB, options);\n        return normalizedA !== void 0 && normalizedB !== void 0 && normalizedA === normalizedB;\n      }\n      function serialize(cmpts, opts) {\n        const component = {\n          host: cmpts.host,\n          scheme: cmpts.scheme,\n          userinfo: cmpts.userinfo,\n          port: cmpts.port,\n          path: cmpts.path,\n          query: cmpts.query,\n          nid: cmpts.nid,\n          nss: cmpts.nss,\n          uuid: cmpts.uuid,\n          fragment: cmpts.fragment,\n          reference: cmpts.reference,\n          resourceName: cmpts.resourceName,\n          secure: cmpts.secure,\n          error: ""\n        };\n        const options = Object.assign({}, opts);\n        const uriTokens = [];\n        if (component.scheme) {\n          component.scheme = decodeValidScheme(component.scheme);\n        }\n        const schemeHandler = getSchemeHandler(options.scheme || component.scheme);\n        if (schemeHandler && schemeHandler.serialize) schemeHandler.serialize(component, options);\n        const hasAuthority = component.userinfo !== void 0 || component.host !== void 0 || component.port !== void 0;\n        const pathNoScheme = !options.skipEscape && component.scheme === void 0 && !hasAuthority;\n        if (component.path !== void 0) {\n          if (!options.skipEscape) {\n            component.path = serializePathEncoding(component.path, pathNoScheme);\n          } else {\n            component.path = normalizePercentEncoding(component.path);\n          }\n        }\n        if (options.reference !== "suffix" && component.scheme) {\n          component.scheme = decodeValidScheme(component.scheme);\n          uriTokens.push(component.scheme, ":");\n        }\n        const authority = recomposeAuthority(component);\n        if (authority !== void 0) {\n          if (options.reference !== "suffix") {\n            uriTokens.push("//");\n          }\n          uriTokens.push(authority);\n          if (component.path && component.path[0] !== "/") {\n            uriTokens.push("/");\n          }\n        }\n        if (component.path !== void 0) {\n          let s = component.path;\n          if (!options.absolutePath && (!schemeHandler || !schemeHandler.absolutePath)) {\n            s = removeDotSegments(s);\n          }\n          if (pathNoScheme) {\n            s = serializePathEncoding(s, true);\n          }\n          if (authority === void 0 && s[0] === "/" && s[1] === "/") {\n            s = "/%2F" + s.slice(2);\n          }\n          uriTokens.push(s);\n        }\n        if (component.query !== void 0) {\n          uriTokens.push("?", encodeQuery(component.query));\n        }\n        if (component.fragment !== void 0) {\n          uriTokens.push("#", encodeFragment(component.fragment));\n        }\n        return uriTokens.join("");\n      }\n      var URI_PARSE = /^(?:([^#/:?]+):)?(?:\\/\\/((?:([^#/?@]*)@)?(\\[[^#/?\\]]+\\]|[^#/:?]*)(?::(\\d*))?))?([^#?]*)(?:\\?([^#]*))?(?:#((?:.|[\\n\\r])*))?/u;\n      var AUTHORITY_PREFIX = /^(?:[^#/:?]+:)?\\/\\/([^/?#]*)/;\n      var AUTHORITY_INTRODUCER_REGION = /^(?:[^#/:?]+:)?([/\\\\\\t\\n\\r]*)/;\n      function getParseError(parsed, matches) {\n        if (matches[2] !== void 0 && parsed.path && parsed.path[0] !== "/") {\n          return \'URI path must start with "/" when authority is present.\';\n        }\n        if (typeof parsed.port === "number" && (parsed.port < 0 || parsed.port > 65535)) {\n          return "URI port is malformed.";\n        }\n        return void 0;\n      }\n      function hasMalformedPercentEncoding(component) {\n        if (component === void 0) return false;\n        let percent = component.indexOf("%");\n        while (percent !== -1) {\n          if (percent + 2 >= component.length || !/^[\\da-f]{2}$/iu.test(component.slice(percent + 1, percent + 3))) {\n            return true;\n          }\n          percent = component.indexOf("%", percent + 3);\n        }\n        return false;\n      }\n      function isIPLiteral(host) {\n        return host[0] === "[" && host[host.length - 1] === "]";\n      }\n      function hasMalformedComponentPercentEncoding(matches) {\n        const host = matches[4];\n        return hasMalformedPercentEncoding(matches[3]) || host !== void 0 && !isIPLiteral(host) && hasMalformedPercentEncoding(host) || hasMalformedPercentEncoding(matches[6]) || hasMalformedPercentEncoding(matches[7]) || hasMalformedPercentEncoding(matches[8]);\n      }\n      function canonicalizeHost(parsed, options, schemeHandler, isIP) {\n        if (!options.unicodeSupport && (!schemeHandler || !schemeHandler.unicodeSupport) && parsed.host && !isIPLiteral(parsed.host) && (options.domainHost || schemeHandler && schemeHandler.domainHost) && isIP === false && nonSimpleDomain(parsed.host)) {\n          try {\n            parsed.host = new URL("http://" + parsed.host).hostname;\n          } catch (e) {\n            parsed.error = parsed.error || "Host\'s domain name can not be converted to ASCII: " + e;\n            return true;\n          }\n        }\n        return false;\n      }\n      function parseWithStatus(uri, opts) {\n        const options = Object.assign({}, opts);\n        const parsed = {\n          scheme: void 0,\n          userinfo: void 0,\n          host: "",\n          port: void 0,\n          path: "",\n          query: void 0,\n          fragment: void 0\n        };\n        let malformedAuthorityOrPort = false;\n        let malformedPercentEncoding = false;\n        let malformedSchemeSpecific = false;\n        let malformedHost = false;\n        let malformedIPLiteral = false;\n        let malformedScheme = false;\n        let isIP = false;\n        if (options.reference === "suffix") {\n          if (options.scheme) {\n            uri = options.scheme + ":" + uri;\n          } else {\n            uri = "//" + uri;\n          }\n        }\n        const authorityMatch = uri.match(AUTHORITY_PREFIX);\n        if (authorityMatch !== null && authorityMatch[1].indexOf("\\\\") !== -1) {\n          parsed.error = "URI authority must not contain a literal backslash.";\n          malformedAuthorityOrPort = true;\n        }\n        const introducerMatch = uri.match(AUTHORITY_INTRODUCER_REGION);\n        if (introducerMatch !== null) {\n          const region = introducerMatch[1];\n          const normalizedRegion = region.replace(/[\\t\\n\\r]/g, "");\n          if (normalizedRegion.length >= 2) {\n            if (normalizedRegion.slice(0, 2) !== "//") {\n              parsed.error = parsed.error || "URI authority must not contain a literal backslash.";\n              malformedAuthorityOrPort = true;\n            } else if (region.length !== normalizedRegion.length) {\n              parsed.error = parsed.error || "URI authority introducer must not contain whitespace.";\n              malformedAuthorityOrPort = true;\n            }\n          }\n        }\n        const matches = uri.match(URI_PARSE);\n        if (matches) {\n          parsed.scheme = matches[1];\n          parsed.userinfo = matches[3];\n          parsed.host = matches[4];\n          parsed.port = parseInt(matches[5], 10);\n          parsed.path = matches[6] || "";\n          parsed.query = matches[7];\n          parsed.fragment = matches[8];\n          if (parsed.scheme !== void 0) {\n            const decodedScheme = unescape(parsed.scheme);\n            if (VALID_SCHEME.test(decodedScheme)) {\n              parsed.scheme = decodedScheme.toLowerCase();\n            } else {\n              parsed.error = parsed.error || MALFORMED_SCHEME_ERROR;\n              malformedScheme = true;\n            }\n          }\n          malformedPercentEncoding = hasMalformedComponentPercentEncoding(matches);\n          if (malformedPercentEncoding) {\n            parsed.error = parsed.error || "URI contains malformed percent-encoding.";\n          }\n          if (isNaN(parsed.port)) {\n            parsed.port = matches[5];\n          }\n          const parseError = getParseError(parsed, matches);\n          if (parseError !== void 0) {\n            parsed.error = parsed.error || parseError;\n            malformedAuthorityOrPort = true;\n          }\n          if (parsed.host) {\n            const ipv4result = isIPv4(parsed.host);\n            if (ipv4result === false) {\n              const bracketedIPLiteral = isIPLiteral(parsed.host);\n              const hasIPLiteralBracket = parsed.host.indexOf("[") !== -1 || parsed.host.indexOf("]") !== -1;\n              const ipv6result = normalizeIPv6(parsed.host);\n              isIP = ipv6result.isIPV6 || ipv6result.isIPVFuture === true;\n              malformedIPLiteral = hasIPLiteralBracket && (!bracketedIPLiteral || ipv6result.error === true);\n              parsed.host = isIP ? ipv6result.host : ipv6result.host.toLowerCase();\n              if (malformedIPLiteral) {\n                parsed.error = parsed.error || "URI host is malformed.";\n                malformedAuthorityOrPort = true;\n              }\n            } else {\n              isIP = true;\n            }\n          }\n          if (parsed.scheme === void 0 && parsed.userinfo === void 0 && parsed.host === void 0 && parsed.port === void 0 && parsed.query === void 0 && !parsed.path) {\n            parsed.reference = "same-document";\n          } else if (parsed.scheme === void 0) {\n            parsed.reference = "relative";\n          } else if (parsed.fragment === void 0) {\n            parsed.reference = "absolute";\n          } else {\n            parsed.reference = "uri";\n          }\n          if (options.reference && options.reference !== "suffix" && options.reference !== parsed.reference) {\n            parsed.error = parsed.error || "URI is not a " + options.reference + " reference.";\n          }\n          const schemeHandler = getSchemeHandler(options.scheme || parsed.scheme);\n          if (!malformedIPLiteral) {\n            malformedHost = canonicalizeHost(parsed, options, schemeHandler, isIP);\n          }\n          if (uri.indexOf("%") !== -1 && parsed.host !== void 0 && !malformedIPLiteral) {\n            let host = isIP ? parsed.host : normalizePercentEncoding(parsed.host, true);\n            if (!isIP) {\n              host = normalizePercentEncoding(host.toLowerCase());\n            }\n            parsed.host = reescapeHostDelimiters(host, isIP);\n          }\n          if (!schemeHandler || schemeHandler && !schemeHandler.skipNormalize) {\n            if (parsed.path) {\n              parsed.path = normalizePathEncoding(parsed.path);\n            }\n            if (parsed.query) {\n              parsed.query = normalizeQueryFragmentEncoding(parsed.query);\n            }\n            if (parsed.fragment) {\n              parsed.fragment = normalizeQueryFragmentEncoding(parsed.fragment);\n            }\n          }\n          if (schemeHandler && schemeHandler.parse) {\n            schemeHandler.parse(parsed, options);\n            if (schemeHandler === SCHEMES.urn && parsed.nid === void 0) {\n              malformedSchemeSpecific = true;\n            }\n          }\n        } else {\n          parsed.error = parsed.error || "URI can not be parsed.";\n        }\n        return { parsed, malformedAuthorityOrPort, malformedPercentEncoding, malformedSchemeSpecific, malformedHost, malformedScheme };\n      }\n      function parse(uri, opts) {\n        return parseWithStatus(uri, opts).parsed;\n      }\n      function normalizeString(uri, opts) {\n        return normalizeStringWithStatus(uri, opts).normalized;\n      }\n      function normalizeStringWithStatus(uri, opts) {\n        const { parsed, malformedAuthorityOrPort, malformedPercentEncoding, malformedSchemeSpecific, malformedHost, malformedScheme } = parseWithStatus(uri, opts);\n        return {\n          normalized: malformedAuthorityOrPort || malformedPercentEncoding || malformedSchemeSpecific || malformedHost || malformedScheme ? uri : serialize(parsed, opts),\n          malformedAuthorityOrPort,\n          malformedPercentEncoding,\n          malformedSchemeSpecific,\n          malformedHost,\n          malformedScheme\n        };\n      }\n      function normalizeComparableURI(uri, opts) {\n        if (typeof uri !== "string" && typeof uri !== "object") {\n          return void 0;\n        }\n        let value;\n        try {\n          value = typeof uri === "string" ? uri : serialize(uri, opts);\n        } catch {\n          return void 0;\n        }\n        const { normalized, malformedAuthorityOrPort, malformedPercentEncoding, malformedSchemeSpecific, malformedHost, malformedScheme } = normalizeStringWithStatus(value, opts);\n        return malformedAuthorityOrPort || malformedPercentEncoding || malformedSchemeSpecific || malformedHost || malformedScheme ? void 0 : normalized;\n      }\n      var fastUri = {\n        SCHEMES,\n        normalize,\n        resolve,\n        resolveComponent,\n        equal,\n        serialize,\n        parse\n      };\n      module.exports = fastUri;\n      module.exports.default = fastUri;\n      module.exports.fastUri = fastUri;\n    }\n  });\n\n  // node_modules/ajv/dist/runtime/uri.js\n  var require_uri = __commonJS({\n    "node_modules/ajv/dist/runtime/uri.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var uri = require_fast_uri();\n      uri.code = \'require("ajv/dist/runtime/uri").default\';\n      exports.default = uri;\n    }\n  });\n\n  // node_modules/ajv/dist/core.js\n  var require_core = __commonJS({\n    "node_modules/ajv/dist/core.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.CodeGen = exports.Name = exports.nil = exports.stringify = exports.str = exports._ = exports.KeywordCxt = void 0;\n      var validate_1 = require_validate();\n      Object.defineProperty(exports, "KeywordCxt", { enumerable: true, get: function() {\n        return validate_1.KeywordCxt;\n      } });\n      var codegen_1 = require_codegen();\n      Object.defineProperty(exports, "_", { enumerable: true, get: function() {\n        return codegen_1._;\n      } });\n      Object.defineProperty(exports, "str", { enumerable: true, get: function() {\n        return codegen_1.str;\n      } });\n      Object.defineProperty(exports, "stringify", { enumerable: true, get: function() {\n        return codegen_1.stringify;\n      } });\n      Object.defineProperty(exports, "nil", { enumerable: true, get: function() {\n        return codegen_1.nil;\n      } });\n      Object.defineProperty(exports, "Name", { enumerable: true, get: function() {\n        return codegen_1.Name;\n      } });\n      Object.defineProperty(exports, "CodeGen", { enumerable: true, get: function() {\n        return codegen_1.CodeGen;\n      } });\n      var validation_error_1 = require_validation_error();\n      var ref_error_1 = require_ref_error();\n      var rules_1 = require_rules();\n      var compile_1 = require_compile();\n      var codegen_2 = require_codegen();\n      var resolve_1 = require_resolve();\n      var dataType_1 = require_dataType();\n      var util_1 = require_util();\n      var $dataRefSchema = require_data();\n      var uri_1 = require_uri();\n      var defaultRegExp = (str, flags) => new RegExp(str, flags);\n      defaultRegExp.code = "new RegExp";\n      var META_IGNORE_OPTIONS = ["removeAdditional", "useDefaults", "coerceTypes"];\n      var EXT_SCOPE_NAMES = /* @__PURE__ */ new Set([\n        "validate",\n        "serialize",\n        "parse",\n        "wrapper",\n        "root",\n        "schema",\n        "keyword",\n        "pattern",\n        "formats",\n        "validate$data",\n        "func",\n        "obj",\n        "Error"\n      ]);\n      var removedOptions = {\n        errorDataPath: "",\n        format: "`validateFormats: false` can be used instead.",\n        nullable: \'"nullable" keyword is supported by default.\',\n        jsonPointers: "Deprecated jsPropertySyntax can be used instead.",\n        extendRefs: "Deprecated ignoreKeywordsWithRef can be used instead.",\n        missingRefs: "Pass empty schema with $id that should be ignored to ajv.addSchema.",\n        processCode: "Use option `code: {process: (code, schemaEnv: object) => string}`",\n        sourceCode: "Use option `code: {source: true}`",\n        strictDefaults: "It is default now, see option `strict`.",\n        strictKeywords: "It is default now, see option `strict`.",\n        uniqueItems: \'"uniqueItems" keyword is always validated.\',\n        unknownFormats: "Disable strict mode or pass `true` to `ajv.addFormat` (or `formats` option).",\n        cache: "Map is used as cache, schema object as key.",\n        serialize: "Map is used as cache, schema object as key.",\n        ajvErrors: "It is default now."\n      };\n      var deprecatedOptions = {\n        ignoreKeywordsWithRef: "",\n        jsPropertySyntax: "",\n        unicode: \'"minLength"/"maxLength" account for unicode characters by default.\'\n      };\n      var MAX_EXPRESSION = 200;\n      function requiredOptions(o) {\n        var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q, _r, _s, _t, _u, _v, _w, _x, _y, _z, _0;\n        const s = o.strict;\n        const _optz = (_a = o.code) === null || _a === void 0 ? void 0 : _a.optimize;\n        const optimize = _optz === true || _optz === void 0 ? 1 : _optz || 0;\n        const regExp = (_c = (_b = o.code) === null || _b === void 0 ? void 0 : _b.regExp) !== null && _c !== void 0 ? _c : defaultRegExp;\n        const uriResolver = (_d = o.uriResolver) !== null && _d !== void 0 ? _d : uri_1.default;\n        return {\n          strictSchema: (_f = (_e = o.strictSchema) !== null && _e !== void 0 ? _e : s) !== null && _f !== void 0 ? _f : true,\n          strictNumbers: (_h = (_g = o.strictNumbers) !== null && _g !== void 0 ? _g : s) !== null && _h !== void 0 ? _h : true,\n          strictTypes: (_k = (_j = o.strictTypes) !== null && _j !== void 0 ? _j : s) !== null && _k !== void 0 ? _k : "log",\n          strictTuples: (_m = (_l = o.strictTuples) !== null && _l !== void 0 ? _l : s) !== null && _m !== void 0 ? _m : "log",\n          strictRequired: (_p = (_o = o.strictRequired) !== null && _o !== void 0 ? _o : s) !== null && _p !== void 0 ? _p : false,\n          code: o.code ? { ...o.code, optimize, regExp } : { optimize, regExp },\n          loopRequired: (_q = o.loopRequired) !== null && _q !== void 0 ? _q : MAX_EXPRESSION,\n          loopEnum: (_r = o.loopEnum) !== null && _r !== void 0 ? _r : MAX_EXPRESSION,\n          meta: (_s = o.meta) !== null && _s !== void 0 ? _s : true,\n          messages: (_t = o.messages) !== null && _t !== void 0 ? _t : true,\n          inlineRefs: (_u = o.inlineRefs) !== null && _u !== void 0 ? _u : true,\n          schemaId: (_v = o.schemaId) !== null && _v !== void 0 ? _v : "$id",\n          addUsedSchema: (_w = o.addUsedSchema) !== null && _w !== void 0 ? _w : true,\n          validateSchema: (_x = o.validateSchema) !== null && _x !== void 0 ? _x : true,\n          validateFormats: (_y = o.validateFormats) !== null && _y !== void 0 ? _y : true,\n          unicodeRegExp: (_z = o.unicodeRegExp) !== null && _z !== void 0 ? _z : true,\n          int32range: (_0 = o.int32range) !== null && _0 !== void 0 ? _0 : true,\n          uriResolver\n        };\n      }\n      var Ajv2 = class {\n        constructor(opts = {}) {\n          this.schemas = {};\n          this.refs = {};\n          this.formats = /* @__PURE__ */ Object.create(null);\n          this._compilations = /* @__PURE__ */ new Set();\n          this._loading = {};\n          this._cache = /* @__PURE__ */ new Map();\n          opts = this.opts = { ...opts, ...requiredOptions(opts) };\n          const { es5, lines } = this.opts.code;\n          this.scope = new codegen_2.ValueScope({ scope: {}, prefixes: EXT_SCOPE_NAMES, es5, lines });\n          this.logger = getLogger(opts.logger);\n          const formatOpt = opts.validateFormats;\n          opts.validateFormats = false;\n          this.RULES = (0, rules_1.getRules)();\n          checkOptions.call(this, removedOptions, opts, "NOT SUPPORTED");\n          checkOptions.call(this, deprecatedOptions, opts, "DEPRECATED", "warn");\n          this._metaOpts = getMetaSchemaOptions.call(this);\n          if (opts.formats)\n            addInitialFormats.call(this);\n          this._addVocabularies();\n          this._addDefaultMetaSchema();\n          if (opts.keywords)\n            addInitialKeywords.call(this, opts.keywords);\n          if (typeof opts.meta == "object")\n            this.addMetaSchema(opts.meta);\n          addInitialSchemas.call(this);\n          opts.validateFormats = formatOpt;\n        }\n        _addVocabularies() {\n          this.addKeyword("$async");\n        }\n        _addDefaultMetaSchema() {\n          const { $data, meta, schemaId } = this.opts;\n          let _dataRefSchema = $dataRefSchema;\n          if (schemaId === "id") {\n            _dataRefSchema = { ...$dataRefSchema };\n            _dataRefSchema.id = _dataRefSchema.$id;\n            delete _dataRefSchema.$id;\n          }\n          if (meta && $data)\n            this.addMetaSchema(_dataRefSchema, _dataRefSchema[schemaId], false);\n        }\n        defaultMeta() {\n          const { meta, schemaId } = this.opts;\n          return this.opts.defaultMeta = typeof meta == "object" ? meta[schemaId] || meta : void 0;\n        }\n        validate(schemaKeyRef, data) {\n          let v;\n          if (typeof schemaKeyRef == "string") {\n            v = this.getSchema(schemaKeyRef);\n            if (!v)\n              throw new Error(`no schema with key or ref "${schemaKeyRef}"`);\n          } else {\n            v = this.compile(schemaKeyRef);\n          }\n          const valid = v(data);\n          if (!("$async" in v))\n            this.errors = v.errors;\n          return valid;\n        }\n        compile(schema, _meta) {\n          const sch = this._addSchema(schema, _meta);\n          return sch.validate || this._compileSchemaEnv(sch);\n        }\n        compileAsync(schema, meta) {\n          if (typeof this.opts.loadSchema != "function") {\n            throw new Error("options.loadSchema should be a function");\n          }\n          const { loadSchema } = this.opts;\n          return runCompileAsync.call(this, schema, meta);\n          async function runCompileAsync(_schema, _meta) {\n            await loadMetaSchema.call(this, _schema.$schema);\n            const sch = this._addSchema(_schema, _meta);\n            return sch.validate || _compileAsync.call(this, sch);\n          }\n          async function loadMetaSchema($ref) {\n            if ($ref && !this.getSchema($ref)) {\n              await runCompileAsync.call(this, { $ref }, true);\n            }\n          }\n          async function _compileAsync(sch) {\n            try {\n              return this._compileSchemaEnv(sch);\n            } catch (e) {\n              if (!(e instanceof ref_error_1.default))\n                throw e;\n              checkLoaded.call(this, e);\n              await loadMissingSchema.call(this, e.missingSchema);\n              return _compileAsync.call(this, sch);\n            }\n          }\n          function checkLoaded({ missingSchema: ref, missingRef }) {\n            if (this.refs[ref]) {\n              throw new Error(`AnySchema ${ref} is loaded but ${missingRef} cannot be resolved`);\n            }\n          }\n          async function loadMissingSchema(ref) {\n            const _schema = await _loadSchema.call(this, ref);\n            if (!this.refs[ref])\n              await loadMetaSchema.call(this, _schema.$schema);\n            if (!this.refs[ref])\n              this.addSchema(_schema, ref, meta);\n          }\n          async function _loadSchema(ref) {\n            const p = this._loading[ref];\n            if (p)\n              return p;\n            try {\n              return await (this._loading[ref] = loadSchema(ref));\n            } finally {\n              delete this._loading[ref];\n            }\n          }\n        }\n        // Adds schema to the instance\n        addSchema(schema, key, _meta, _validateSchema = this.opts.validateSchema) {\n          if (Array.isArray(schema)) {\n            for (const sch of schema)\n              this.addSchema(sch, void 0, _meta, _validateSchema);\n            return this;\n          }\n          let id;\n          if (typeof schema === "object") {\n            const { schemaId } = this.opts;\n            id = schema[schemaId];\n            if (id !== void 0 && typeof id != "string") {\n              throw new Error(`schema ${schemaId} must be string`);\n            }\n          }\n          key = (0, resolve_1.normalizeId)(key || id);\n          this._checkUnique(key);\n          this.schemas[key] = this._addSchema(schema, _meta, key, _validateSchema, true);\n          return this;\n        }\n        // Add schema that will be used to validate other schemas\n        // options in META_IGNORE_OPTIONS are alway set to false\n        addMetaSchema(schema, key, _validateSchema = this.opts.validateSchema) {\n          this.addSchema(schema, key, true, _validateSchema);\n          return this;\n        }\n        //  Validate schema against its meta-schema\n        validateSchema(schema, throwOrLogError) {\n          if (typeof schema == "boolean")\n            return true;\n          let $schema;\n          $schema = schema.$schema;\n          if ($schema !== void 0 && typeof $schema != "string") {\n            throw new Error("$schema must be a string");\n          }\n          $schema = $schema || this.opts.defaultMeta || this.defaultMeta();\n          if (!$schema) {\n            this.logger.warn("meta-schema not available");\n            this.errors = null;\n            return true;\n          }\n          const valid = this.validate($schema, schema);\n          if (!valid && throwOrLogError) {\n            const message = "schema is invalid: " + this.errorsText();\n            if (this.opts.validateSchema === "log")\n              this.logger.error(message);\n            else\n              throw new Error(message);\n          }\n          return valid;\n        }\n        // Get compiled schema by `key` or `ref`.\n        // (`key` that was passed to `addSchema` or full schema reference - `schema.$id` or resolved id)\n        getSchema(keyRef) {\n          let sch;\n          while (typeof (sch = getSchEnv.call(this, keyRef)) == "string")\n            keyRef = sch;\n          if (sch === void 0) {\n            const { schemaId } = this.opts;\n            const root = new compile_1.SchemaEnv({ schema: {}, schemaId });\n            sch = compile_1.resolveSchema.call(this, root, keyRef);\n            if (!sch)\n              return;\n            this.refs[keyRef] = sch;\n          }\n          return sch.validate || this._compileSchemaEnv(sch);\n        }\n        // Remove cached schema(s).\n        // If no parameter is passed all schemas but meta-schemas are removed.\n        // If RegExp is passed all schemas with key/id matching pattern but meta-schemas are removed.\n        // Even if schema is referenced by other schemas it still can be removed as other schemas have local references.\n        removeSchema(schemaKeyRef) {\n          if (schemaKeyRef instanceof RegExp) {\n            this._removeAllSchemas(this.schemas, schemaKeyRef);\n            this._removeAllSchemas(this.refs, schemaKeyRef);\n            return this;\n          }\n          switch (typeof schemaKeyRef) {\n            case "undefined":\n              this._removeAllSchemas(this.schemas);\n              this._removeAllSchemas(this.refs);\n              this._cache.clear();\n              return this;\n            case "string": {\n              const sch = getSchEnv.call(this, schemaKeyRef);\n              if (typeof sch == "object")\n                this._cache.delete(sch.schema);\n              delete this.schemas[schemaKeyRef];\n              delete this.refs[schemaKeyRef];\n              return this;\n            }\n            case "object": {\n              const cacheKey = schemaKeyRef;\n              this._cache.delete(cacheKey);\n              let id = schemaKeyRef[this.opts.schemaId];\n              if (id) {\n                id = (0, resolve_1.normalizeId)(id);\n                delete this.schemas[id];\n                delete this.refs[id];\n              }\n              return this;\n            }\n            default:\n              throw new Error("ajv.removeSchema: invalid parameter");\n          }\n        }\n        // add "vocabulary" - a collection of keywords\n        addVocabulary(definitions) {\n          for (const def of definitions)\n            this.addKeyword(def);\n          return this;\n        }\n        addKeyword(kwdOrDef, def) {\n          let keyword;\n          if (typeof kwdOrDef == "string") {\n            keyword = kwdOrDef;\n            if (typeof def == "object") {\n              this.logger.warn("these parameters are deprecated, see docs for addKeyword");\n              def.keyword = keyword;\n            }\n          } else if (typeof kwdOrDef == "object" && def === void 0) {\n            def = kwdOrDef;\n            keyword = def.keyword;\n            if (Array.isArray(keyword) && !keyword.length) {\n              throw new Error("addKeywords: keyword must be string or non-empty array");\n            }\n          } else {\n            throw new Error("invalid addKeywords parameters");\n          }\n          checkKeyword.call(this, keyword, def);\n          if (!def) {\n            (0, util_1.eachItem)(keyword, (kwd) => addRule.call(this, kwd));\n            return this;\n          }\n          keywordMetaschema.call(this, def);\n          const definition = {\n            ...def,\n            type: (0, dataType_1.getJSONTypes)(def.type),\n            schemaType: (0, dataType_1.getJSONTypes)(def.schemaType)\n          };\n          (0, util_1.eachItem)(keyword, definition.type.length === 0 ? (k) => addRule.call(this, k, definition) : (k) => definition.type.forEach((t) => addRule.call(this, k, definition, t)));\n          return this;\n        }\n        getKeyword(keyword) {\n          const rule = this.RULES.all[keyword];\n          return typeof rule == "object" ? rule.definition : !!rule;\n        }\n        // Remove keyword\n        removeKeyword(keyword) {\n          const { RULES } = this;\n          delete RULES.keywords[keyword];\n          delete RULES.all[keyword];\n          for (const group of RULES.rules) {\n            const i = group.rules.findIndex((rule) => rule.keyword === keyword);\n            if (i >= 0)\n              group.rules.splice(i, 1);\n          }\n          return this;\n        }\n        // Add format\n        addFormat(name, format) {\n          if (typeof format == "string")\n            format = new RegExp(format);\n          this.formats[name] = format;\n          return this;\n        }\n        errorsText(errors2 = this.errors, { separator = ", ", dataVar = "data" } = {}) {\n          if (!errors2 || errors2.length === 0)\n            return "No errors";\n          return errors2.map((e) => `${dataVar}${e.instancePath} ${e.message}`).reduce((text, msg) => text + separator + msg);\n        }\n        $dataMetaSchema(metaSchema, keywordsJsonPointers) {\n          const rules = this.RULES.all;\n          metaSchema = JSON.parse(JSON.stringify(metaSchema));\n          for (const jsonPointer of keywordsJsonPointers) {\n            const segments = jsonPointer.split("/").slice(1);\n            let keywords2 = metaSchema;\n            for (const seg of segments)\n              keywords2 = keywords2[seg];\n            for (const key in rules) {\n              const rule = rules[key];\n              if (typeof rule != "object")\n                continue;\n              const { $data } = rule.definition;\n              const schema = keywords2[key];\n              if ($data && schema)\n                keywords2[key] = schemaOrData(schema);\n            }\n          }\n          return metaSchema;\n        }\n        _removeAllSchemas(schemas, regex) {\n          for (const keyRef in schemas) {\n            const sch = schemas[keyRef];\n            if (!regex || regex.test(keyRef)) {\n              if (typeof sch == "string") {\n                delete schemas[keyRef];\n              } else if (sch && !sch.meta) {\n                this._cache.delete(sch.schema);\n                delete schemas[keyRef];\n              }\n            }\n          }\n        }\n        _addSchema(schema, meta, baseId, validateSchema = this.opts.validateSchema, addSchema = this.opts.addUsedSchema) {\n          let id;\n          const { schemaId } = this.opts;\n          if (typeof schema == "object") {\n            id = schema[schemaId];\n          } else {\n            if (this.opts.jtd)\n              throw new Error("schema must be object");\n            else if (typeof schema != "boolean")\n              throw new Error("schema must be object or boolean");\n          }\n          let sch = this._cache.get(schema);\n          if (sch !== void 0)\n            return sch;\n          baseId = (0, resolve_1.normalizeId)(id || baseId);\n          const localRefs = resolve_1.getSchemaRefs.call(this, schema, baseId);\n          sch = new compile_1.SchemaEnv({ schema, schemaId, meta, baseId, localRefs });\n          this._cache.set(sch.schema, sch);\n          if (addSchema && !baseId.startsWith("#")) {\n            if (baseId)\n              this._checkUnique(baseId);\n            this.refs[baseId] = sch;\n          }\n          if (validateSchema)\n            this.validateSchema(schema, true);\n          return sch;\n        }\n        _checkUnique(id) {\n          if (this.schemas[id] || this.refs[id]) {\n            throw new Error(`schema with key or id "${id}" already exists`);\n          }\n        }\n        _compileSchemaEnv(sch) {\n          if (sch.meta)\n            this._compileMetaSchema(sch);\n          else\n            compile_1.compileSchema.call(this, sch);\n          if (!sch.validate)\n            throw new Error("ajv implementation error");\n          return sch.validate;\n        }\n        _compileMetaSchema(sch) {\n          const currentOpts = this.opts;\n          this.opts = this._metaOpts;\n          try {\n            compile_1.compileSchema.call(this, sch);\n          } finally {\n            this.opts = currentOpts;\n          }\n        }\n      };\n      Ajv2.ValidationError = validation_error_1.default;\n      Ajv2.MissingRefError = ref_error_1.default;\n      exports.default = Ajv2;\n      function checkOptions(checkOpts, options, msg, log = "error") {\n        for (const key in checkOpts) {\n          const opt = key;\n          if (opt in options)\n            this.logger[log](`${msg}: option ${key}. ${checkOpts[opt]}`);\n        }\n      }\n      function getSchEnv(keyRef) {\n        keyRef = (0, resolve_1.normalizeId)(keyRef);\n        return this.schemas[keyRef] || this.refs[keyRef];\n      }\n      function addInitialSchemas() {\n        const optsSchemas = this.opts.schemas;\n        if (!optsSchemas)\n          return;\n        if (Array.isArray(optsSchemas))\n          this.addSchema(optsSchemas);\n        else\n          for (const key in optsSchemas)\n            this.addSchema(optsSchemas[key], key);\n      }\n      function addInitialFormats() {\n        for (const name in this.opts.formats) {\n          const format = this.opts.formats[name];\n          if (format)\n            this.addFormat(name, format);\n        }\n      }\n      function addInitialKeywords(defs) {\n        if (Array.isArray(defs)) {\n          this.addVocabulary(defs);\n          return;\n        }\n        this.logger.warn("keywords option as map is deprecated, pass array");\n        for (const keyword in defs) {\n          const def = defs[keyword];\n          if (!def.keyword)\n            def.keyword = keyword;\n          this.addKeyword(def);\n        }\n      }\n      function getMetaSchemaOptions() {\n        const metaOpts = { ...this.opts };\n        for (const opt of META_IGNORE_OPTIONS)\n          delete metaOpts[opt];\n        return metaOpts;\n      }\n      var noLogs = { log() {\n      }, warn() {\n      }, error() {\n      } };\n      function getLogger(logger) {\n        if (logger === false)\n          return noLogs;\n        if (logger === void 0)\n          return console;\n        if (logger.log && logger.warn && logger.error)\n          return logger;\n        throw new Error("logger must implement log, warn and error methods");\n      }\n      var KEYWORD_NAME = /^[a-z_$][a-z0-9_$:-]*$/i;\n      function checkKeyword(keyword, def) {\n        const { RULES } = this;\n        (0, util_1.eachItem)(keyword, (kwd) => {\n          if (RULES.keywords[kwd])\n            throw new Error(`Keyword ${kwd} is already defined`);\n          if (!KEYWORD_NAME.test(kwd))\n            throw new Error(`Keyword ${kwd} has invalid name`);\n        });\n        if (!def)\n          return;\n        if (def.$data && !("code" in def || "validate" in def)) {\n          throw new Error(\'$data keyword must have "code" or "validate" function\');\n        }\n      }\n      function addRule(keyword, definition, dataType) {\n        var _a;\n        const post = definition === null || definition === void 0 ? void 0 : definition.post;\n        if (dataType && post)\n          throw new Error(\'keyword with "post" flag cannot have "type"\');\n        const { RULES } = this;\n        let ruleGroup = post ? RULES.post : RULES.rules.find(({ type: t }) => t === dataType);\n        if (!ruleGroup) {\n          ruleGroup = { type: dataType, rules: [] };\n          RULES.rules.push(ruleGroup);\n        }\n        RULES.keywords[keyword] = true;\n        if (!definition)\n          return;\n        const rule = {\n          keyword,\n          definition: {\n            ...definition,\n            type: (0, dataType_1.getJSONTypes)(definition.type),\n            schemaType: (0, dataType_1.getJSONTypes)(definition.schemaType)\n          }\n        };\n        if (definition.before)\n          addBeforeRule.call(this, ruleGroup, rule, definition.before);\n        else\n          ruleGroup.rules.push(rule);\n        RULES.all[keyword] = rule;\n        (_a = definition.implements) === null || _a === void 0 ? void 0 : _a.forEach((kwd) => this.addKeyword(kwd));\n      }\n      function addBeforeRule(ruleGroup, rule, before) {\n        const i = ruleGroup.rules.findIndex((_rule) => _rule.keyword === before);\n        if (i >= 0) {\n          ruleGroup.rules.splice(i, 0, rule);\n        } else {\n          ruleGroup.rules.push(rule);\n          this.logger.warn(`rule ${before} is not defined`);\n        }\n      }\n      function keywordMetaschema(def) {\n        let { metaSchema } = def;\n        if (metaSchema === void 0)\n          return;\n        if (def.$data && this.opts.$data)\n          metaSchema = schemaOrData(metaSchema);\n        def.validateSchema = this.compile(metaSchema, true);\n      }\n      var $dataRef = {\n        $ref: "https://raw.githubusercontent.com/ajv-validator/ajv/master/lib/refs/data.json#"\n      };\n      function schemaOrData(schema) {\n        return { anyOf: [schema, $dataRef] };\n      }\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/core/id.js\n  var require_id = __commonJS({\n    "node_modules/ajv/dist/vocabularies/core/id.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var def = {\n        keyword: "id",\n        code() {\n          throw new Error(\'NOT SUPPORTED: keyword "id", use "$id" for schema ID\');\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/core/ref.js\n  var require_ref = __commonJS({\n    "node_modules/ajv/dist/vocabularies/core/ref.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.callRef = exports.getValidate = void 0;\n      var ref_error_1 = require_ref_error();\n      var code_1 = require_code2();\n      var codegen_1 = require_codegen();\n      var names_1 = require_names();\n      var compile_1 = require_compile();\n      var util_1 = require_util();\n      var def = {\n        keyword: "$ref",\n        schemaType: "string",\n        code(cxt) {\n          const { gen, schema: $ref, it } = cxt;\n          const { baseId, schemaEnv: env, validateName, opts, self: self2 } = it;\n          const { root } = env;\n          if (($ref === "#" || $ref === "#/") && baseId === root.baseId)\n            return callRootRef();\n          const schOrEnv = compile_1.resolveRef.call(self2, root, baseId, $ref);\n          if (schOrEnv === void 0)\n            throw new ref_error_1.default(it.opts.uriResolver, baseId, $ref);\n          if (schOrEnv instanceof compile_1.SchemaEnv)\n            return callValidate(schOrEnv);\n          return inlineRefSchema(schOrEnv);\n          function callRootRef() {\n            if (env === root)\n              return callRef(cxt, validateName, env, env.$async);\n            const rootName = gen.scopeValue("root", { ref: root });\n            return callRef(cxt, (0, codegen_1._)`${rootName}.validate`, root, root.$async);\n          }\n          function callValidate(sch) {\n            const v = getValidate(cxt, sch);\n            callRef(cxt, v, sch, sch.$async);\n          }\n          function inlineRefSchema(sch) {\n            const schName = gen.scopeValue("schema", opts.code.source === true ? { ref: sch, code: (0, codegen_1.stringify)(sch) } : { ref: sch });\n            const valid = gen.name("valid");\n            const schCxt = cxt.subschema({\n              schema: sch,\n              dataTypes: [],\n              schemaPath: codegen_1.nil,\n              topSchemaRef: schName,\n              errSchemaPath: $ref\n            }, valid);\n            cxt.mergeEvaluated(schCxt);\n            cxt.ok(valid);\n          }\n        }\n      };\n      function getValidate(cxt, sch) {\n        const { gen } = cxt;\n        return sch.validate ? gen.scopeValue("validate", { ref: sch.validate }) : (0, codegen_1._)`${gen.scopeValue("wrapper", { ref: sch })}.validate`;\n      }\n      exports.getValidate = getValidate;\n      function callRef(cxt, v, sch, $async) {\n        const { gen, it } = cxt;\n        const { allErrors, schemaEnv: env, opts } = it;\n        const passCxt = opts.passContext ? names_1.default.this : codegen_1.nil;\n        if ($async)\n          callAsyncRef();\n        else\n          callSyncRef();\n        function callAsyncRef() {\n          if (!env.$async)\n            throw new Error("async schema referenced by sync schema");\n          const valid = gen.let("valid");\n          gen.try(() => {\n            gen.code((0, codegen_1._)`await ${(0, code_1.callValidateCode)(cxt, v, passCxt)}`);\n            addEvaluatedFrom(v);\n            if (!allErrors)\n              gen.assign(valid, true);\n          }, (e) => {\n            gen.if((0, codegen_1._)`!(${e} instanceof ${it.ValidationError})`, () => gen.throw(e));\n            addErrorsFrom(e);\n            if (!allErrors)\n              gen.assign(valid, false);\n          });\n          cxt.ok(valid);\n        }\n        function callSyncRef() {\n          cxt.result((0, code_1.callValidateCode)(cxt, v, passCxt), () => addEvaluatedFrom(v), () => addErrorsFrom(v));\n        }\n        function addErrorsFrom(source) {\n          const errs = (0, codegen_1._)`${source}.errors`;\n          gen.assign(names_1.default.vErrors, (0, codegen_1._)`${names_1.default.vErrors} === null ? ${errs} : ${names_1.default.vErrors}.concat(${errs})`);\n          gen.assign(names_1.default.errors, (0, codegen_1._)`${names_1.default.vErrors}.length`);\n        }\n        function addEvaluatedFrom(source) {\n          var _a;\n          if (!it.opts.unevaluated)\n            return;\n          const schEvaluated = (_a = sch === null || sch === void 0 ? void 0 : sch.validate) === null || _a === void 0 ? void 0 : _a.evaluated;\n          if (it.props !== true) {\n            if (schEvaluated && !schEvaluated.dynamicProps) {\n              if (schEvaluated.props !== void 0) {\n                it.props = util_1.mergeEvaluated.props(gen, schEvaluated.props, it.props);\n              }\n            } else {\n              const props = gen.var("props", (0, codegen_1._)`${source}.evaluated.props`);\n              it.props = util_1.mergeEvaluated.props(gen, props, it.props, codegen_1.Name);\n            }\n          }\n          if (it.items !== true) {\n            if (schEvaluated && !schEvaluated.dynamicItems) {\n              if (schEvaluated.items !== void 0) {\n                it.items = util_1.mergeEvaluated.items(gen, schEvaluated.items, it.items);\n              }\n            } else {\n              const items = gen.var("items", (0, codegen_1._)`${source}.evaluated.items`);\n              it.items = util_1.mergeEvaluated.items(gen, items, it.items, codegen_1.Name);\n            }\n          }\n        }\n      }\n      exports.callRef = callRef;\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/core/index.js\n  var require_core2 = __commonJS({\n    "node_modules/ajv/dist/vocabularies/core/index.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var id_1 = require_id();\n      var ref_1 = require_ref();\n      var core = [\n        "$schema",\n        "$id",\n        "$defs",\n        "$vocabulary",\n        { keyword: "$comment" },\n        "definitions",\n        id_1.default,\n        ref_1.default\n      ];\n      exports.default = core;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/limitNumber.js\n  var require_limitNumber = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/limitNumber.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var ops = codegen_1.operators;\n      var KWDs = {\n        maximum: { okStr: "<=", ok: ops.LTE, fail: ops.GT },\n        minimum: { okStr: ">=", ok: ops.GTE, fail: ops.LT },\n        exclusiveMaximum: { okStr: "<", ok: ops.LT, fail: ops.GTE },\n        exclusiveMinimum: { okStr: ">", ok: ops.GT, fail: ops.LTE }\n      };\n      var error = {\n        message: ({ keyword, schemaCode }) => (0, codegen_1.str)`must be ${KWDs[keyword].okStr} ${schemaCode}`,\n        params: ({ keyword, schemaCode }) => (0, codegen_1._)`{comparison: ${KWDs[keyword].okStr}, limit: ${schemaCode}}`\n      };\n      var def = {\n        keyword: Object.keys(KWDs),\n        type: "number",\n        schemaType: "number",\n        $data: true,\n        error,\n        code(cxt) {\n          const { keyword, data, schemaCode } = cxt;\n          cxt.fail$data((0, codegen_1._)`${data} ${KWDs[keyword].fail} ${schemaCode} || isNaN(${data})`);\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/multipleOf.js\n  var require_multipleOf = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/multipleOf.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var error = {\n        message: ({ schemaCode }) => (0, codegen_1.str)`must be multiple of ${schemaCode}`,\n        params: ({ schemaCode }) => (0, codegen_1._)`{multipleOf: ${schemaCode}}`\n      };\n      var def = {\n        keyword: "multipleOf",\n        type: "number",\n        schemaType: "number",\n        $data: true,\n        error,\n        code(cxt) {\n          const { gen, data, schemaCode, it } = cxt;\n          const prec = it.opts.multipleOfPrecision;\n          const res = gen.let("res");\n          const invalid = prec ? (0, codegen_1._)`Math.abs(Math.round(${res}) - ${res}) > 1e-${prec}` : (0, codegen_1._)`${res} !== parseInt(${res})`;\n          cxt.fail$data((0, codegen_1._)`(${schemaCode} === 0 || (${res} = ${data}/${schemaCode}, ${invalid}))`);\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/runtime/ucs2length.js\n  var require_ucs2length = __commonJS({\n    "node_modules/ajv/dist/runtime/ucs2length.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      function ucs2length(str) {\n        const len = str.length;\n        let length = 0;\n        let pos = 0;\n        let value;\n        while (pos < len) {\n          length++;\n          value = str.charCodeAt(pos++);\n          if (value >= 55296 && value <= 56319 && pos < len) {\n            value = str.charCodeAt(pos);\n            if ((value & 64512) === 56320)\n              pos++;\n          }\n        }\n        return length;\n      }\n      exports.default = ucs2length;\n      ucs2length.code = \'require("ajv/dist/runtime/ucs2length").default\';\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/limitLength.js\n  var require_limitLength = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/limitLength.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var ucs2length_1 = require_ucs2length();\n      var error = {\n        message({ keyword, schemaCode }) {\n          const comp = keyword === "maxLength" ? "more" : "fewer";\n          return (0, codegen_1.str)`must NOT have ${comp} than ${schemaCode} characters`;\n        },\n        params: ({ schemaCode }) => (0, codegen_1._)`{limit: ${schemaCode}}`\n      };\n      var def = {\n        keyword: ["maxLength", "minLength"],\n        type: "string",\n        schemaType: "number",\n        $data: true,\n        error,\n        code(cxt) {\n          const { keyword, data, schemaCode, it } = cxt;\n          const op = keyword === "maxLength" ? codegen_1.operators.GT : codegen_1.operators.LT;\n          const len = it.opts.unicode === false ? (0, codegen_1._)`${data}.length` : (0, codegen_1._)`${(0, util_1.useFunc)(cxt.gen, ucs2length_1.default)}(${data})`;\n          cxt.fail$data((0, codegen_1._)`${len} ${op} ${schemaCode}`);\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/pattern.js\n  var require_pattern = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/pattern.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var code_1 = require_code2();\n      var util_1 = require_util();\n      var codegen_1 = require_codegen();\n      var error = {\n        message: ({ schemaCode }) => (0, codegen_1.str)`must match pattern "${schemaCode}"`,\n        params: ({ schemaCode }) => (0, codegen_1._)`{pattern: ${schemaCode}}`\n      };\n      var def = {\n        keyword: "pattern",\n        type: "string",\n        schemaType: "string",\n        $data: true,\n        error,\n        code(cxt) {\n          const { gen, data, $data, schema, schemaCode, it } = cxt;\n          const u = it.opts.unicodeRegExp ? "u" : "";\n          if ($data) {\n            const { regExp } = it.opts.code;\n            const regExpCode = regExp.code === "new RegExp" ? (0, codegen_1._)`new RegExp` : (0, util_1.useFunc)(gen, regExp);\n            const valid = gen.let("valid");\n            gen.try(() => gen.assign(valid, (0, codegen_1._)`${regExpCode}(${schemaCode}, ${u}).test(${data})`), () => gen.assign(valid, false));\n            cxt.fail$data((0, codegen_1._)`!${valid}`);\n          } else {\n            const regExp = (0, code_1.usePattern)(cxt, schema);\n            cxt.fail$data((0, codegen_1._)`!${regExp}.test(${data})`);\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/limitProperties.js\n  var require_limitProperties = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/limitProperties.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var error = {\n        message({ keyword, schemaCode }) {\n          const comp = keyword === "maxProperties" ? "more" : "fewer";\n          return (0, codegen_1.str)`must NOT have ${comp} than ${schemaCode} properties`;\n        },\n        params: ({ schemaCode }) => (0, codegen_1._)`{limit: ${schemaCode}}`\n      };\n      var def = {\n        keyword: ["maxProperties", "minProperties"],\n        type: "object",\n        schemaType: "number",\n        $data: true,\n        error,\n        code(cxt) {\n          const { keyword, data, schemaCode } = cxt;\n          const op = keyword === "maxProperties" ? codegen_1.operators.GT : codegen_1.operators.LT;\n          cxt.fail$data((0, codegen_1._)`Object.keys(${data}).length ${op} ${schemaCode}`);\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/required.js\n  var require_required = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/required.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var code_1 = require_code2();\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var error = {\n        message: ({ params: { missingProperty } }) => (0, codegen_1.str)`must have required property \'${missingProperty}\'`,\n        params: ({ params: { missingProperty } }) => (0, codegen_1._)`{missingProperty: ${missingProperty}}`\n      };\n      var def = {\n        keyword: "required",\n        type: "object",\n        schemaType: "array",\n        $data: true,\n        error,\n        code(cxt) {\n          const { gen, schema, schemaCode, data, $data, it } = cxt;\n          const { opts } = it;\n          if (!$data && schema.length === 0)\n            return;\n          const useLoop = schema.length >= opts.loopRequired;\n          if (it.allErrors)\n            allErrorsMode();\n          else\n            exitOnErrorMode();\n          if (opts.strictRequired) {\n            const props = cxt.parentSchema.properties;\n            const { definedProperties } = cxt.it;\n            for (const requiredKey of schema) {\n              if ((props === null || props === void 0 ? void 0 : props[requiredKey]) === void 0 && !definedProperties.has(requiredKey)) {\n                const schemaPath = it.schemaEnv.baseId + it.errSchemaPath;\n                const msg = `required property "${requiredKey}" is not defined at "${schemaPath}" (strictRequired)`;\n                (0, util_1.checkStrictMode)(it, msg, it.opts.strictRequired);\n              }\n            }\n          }\n          function allErrorsMode() {\n            if (useLoop || $data) {\n              cxt.block$data(codegen_1.nil, loopAllRequired);\n            } else {\n              for (const prop of schema) {\n                (0, code_1.checkReportMissingProp)(cxt, prop);\n              }\n            }\n          }\n          function exitOnErrorMode() {\n            const missing = gen.let("missing");\n            if (useLoop || $data) {\n              const valid = gen.let("valid", true);\n              cxt.block$data(valid, () => loopUntilMissing(missing, valid));\n              cxt.ok(valid);\n            } else {\n              gen.if((0, code_1.checkMissingProp)(cxt, schema, missing));\n              (0, code_1.reportMissingProp)(cxt, missing);\n              gen.else();\n            }\n          }\n          function loopAllRequired() {\n            gen.forOf("prop", schemaCode, (prop) => {\n              cxt.setParams({ missingProperty: prop });\n              gen.if((0, code_1.noPropertyInData)(gen, data, prop, opts.ownProperties), () => cxt.error());\n            });\n          }\n          function loopUntilMissing(missing, valid) {\n            cxt.setParams({ missingProperty: missing });\n            gen.forOf(missing, schemaCode, () => {\n              gen.assign(valid, (0, code_1.propertyInData)(gen, data, missing, opts.ownProperties));\n              gen.if((0, codegen_1.not)(valid), () => {\n                cxt.error();\n                gen.break();\n              });\n            }, codegen_1.nil);\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/limitItems.js\n  var require_limitItems = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/limitItems.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var error = {\n        message({ keyword, schemaCode }) {\n          const comp = keyword === "maxItems" ? "more" : "fewer";\n          return (0, codegen_1.str)`must NOT have ${comp} than ${schemaCode} items`;\n        },\n        params: ({ schemaCode }) => (0, codegen_1._)`{limit: ${schemaCode}}`\n      };\n      var def = {\n        keyword: ["maxItems", "minItems"],\n        type: "array",\n        schemaType: "number",\n        $data: true,\n        error,\n        code(cxt) {\n          const { keyword, data, schemaCode } = cxt;\n          const op = keyword === "maxItems" ? codegen_1.operators.GT : codegen_1.operators.LT;\n          cxt.fail$data((0, codegen_1._)`${data}.length ${op} ${schemaCode}`);\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/runtime/equal.js\n  var require_equal = __commonJS({\n    "node_modules/ajv/dist/runtime/equal.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var equal = require_fast_deep_equal();\n      equal.code = \'require("ajv/dist/runtime/equal").default\';\n      exports.default = equal;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/uniqueItems.js\n  var require_uniqueItems = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/uniqueItems.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var dataType_1 = require_dataType();\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var equal_1 = require_equal();\n      var error = {\n        message: ({ params: { i, j } }) => (0, codegen_1.str)`must NOT have duplicate items (items ## ${j} and ${i} are identical)`,\n        params: ({ params: { i, j } }) => (0, codegen_1._)`{i: ${i}, j: ${j}}`\n      };\n      var def = {\n        keyword: "uniqueItems",\n        type: "array",\n        schemaType: "boolean",\n        $data: true,\n        error,\n        code(cxt) {\n          const { gen, data, $data, schema, parentSchema, schemaCode, it } = cxt;\n          if (!$data && !schema)\n            return;\n          const valid = gen.let("valid");\n          const itemTypes = parentSchema.items ? (0, dataType_1.getSchemaTypes)(parentSchema.items) : [];\n          cxt.block$data(valid, validateUniqueItems, (0, codegen_1._)`${schemaCode} === false`);\n          cxt.ok(valid);\n          function validateUniqueItems() {\n            const i = gen.let("i", (0, codegen_1._)`${data}.length`);\n            const j = gen.let("j");\n            cxt.setParams({ i, j });\n            gen.assign(valid, true);\n            gen.if((0, codegen_1._)`${i} > 1`, () => (canOptimize() ? loopN : loopN2)(i, j));\n          }\n          function canOptimize() {\n            return itemTypes.length > 0 && !itemTypes.some((t) => t === "object" || t === "array");\n          }\n          function loopN(i, j) {\n            const item = gen.name("item");\n            const wrongType = (0, dataType_1.checkDataTypes)(itemTypes, item, it.opts.strictNumbers, dataType_1.DataType.Wrong);\n            const indices = gen.const("indices", (0, codegen_1._)`{}`);\n            gen.for((0, codegen_1._)`;${i}--;`, () => {\n              gen.let(item, (0, codegen_1._)`${data}[${i}]`);\n              gen.if(wrongType, (0, codegen_1._)`continue`);\n              if (itemTypes.length > 1)\n                gen.if((0, codegen_1._)`typeof ${item} == "string"`, (0, codegen_1._)`${item} += "_"`);\n              gen.if((0, codegen_1._)`typeof ${indices}[${item}] == "number"`, () => {\n                gen.assign(j, (0, codegen_1._)`${indices}[${item}]`);\n                cxt.error();\n                gen.assign(valid, false).break();\n              }).code((0, codegen_1._)`${indices}[${item}] = ${i}`);\n            });\n          }\n          function loopN2(i, j) {\n            const eql = (0, util_1.useFunc)(gen, equal_1.default);\n            const outer = gen.name("outer");\n            gen.label(outer).for((0, codegen_1._)`;${i}--;`, () => gen.for((0, codegen_1._)`${j} = ${i}; ${j}--;`, () => gen.if((0, codegen_1._)`${eql}(${data}[${i}], ${data}[${j}])`, () => {\n              cxt.error();\n              gen.assign(valid, false).break(outer);\n            })));\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/const.js\n  var require_const = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/const.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var equal_1 = require_equal();\n      var error = {\n        message: "must be equal to constant",\n        params: ({ schemaCode }) => (0, codegen_1._)`{allowedValue: ${schemaCode}}`\n      };\n      var def = {\n        keyword: "const",\n        $data: true,\n        error,\n        code(cxt) {\n          const { gen, data, $data, schemaCode, schema } = cxt;\n          if ($data || schema && typeof schema == "object") {\n            cxt.fail$data((0, codegen_1._)`!${(0, util_1.useFunc)(gen, equal_1.default)}(${data}, ${schemaCode})`);\n          } else {\n            cxt.fail((0, codegen_1._)`${schema} !== ${data}`);\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/enum.js\n  var require_enum = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/enum.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var equal_1 = require_equal();\n      var error = {\n        message: "must be equal to one of the allowed values",\n        params: ({ schemaCode }) => (0, codegen_1._)`{allowedValues: ${schemaCode}}`\n      };\n      var def = {\n        keyword: "enum",\n        schemaType: "array",\n        $data: true,\n        error,\n        code(cxt) {\n          const { gen, data, $data, schema, schemaCode, it } = cxt;\n          if (!$data && schema.length === 0)\n            throw new Error("enum must have non-empty array");\n          const useLoop = schema.length >= it.opts.loopEnum;\n          let eql;\n          const getEql = () => eql !== null && eql !== void 0 ? eql : eql = (0, util_1.useFunc)(gen, equal_1.default);\n          let valid;\n          if (useLoop || $data) {\n            valid = gen.let("valid");\n            cxt.block$data(valid, loopEnum);\n          } else {\n            if (!Array.isArray(schema))\n              throw new Error("ajv implementation error");\n            const vSchema = gen.const("vSchema", schemaCode);\n            valid = (0, codegen_1.or)(...schema.map((_x, i) => equalCode(vSchema, i)));\n          }\n          cxt.pass(valid);\n          function loopEnum() {\n            gen.assign(valid, false);\n            gen.forOf("v", schemaCode, (v) => gen.if((0, codegen_1._)`${getEql()}(${data}, ${v})`, () => gen.assign(valid, true).break()));\n          }\n          function equalCode(vSchema, i) {\n            const sch = schema[i];\n            return typeof sch === "object" && sch !== null ? (0, codegen_1._)`${getEql()}(${data}, ${vSchema}[${i}])` : (0, codegen_1._)`${data} === ${sch}`;\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/validation/index.js\n  var require_validation = __commonJS({\n    "node_modules/ajv/dist/vocabularies/validation/index.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var limitNumber_1 = require_limitNumber();\n      var multipleOf_1 = require_multipleOf();\n      var limitLength_1 = require_limitLength();\n      var pattern_1 = require_pattern();\n      var limitProperties_1 = require_limitProperties();\n      var required_1 = require_required();\n      var limitItems_1 = require_limitItems();\n      var uniqueItems_1 = require_uniqueItems();\n      var const_1 = require_const();\n      var enum_1 = require_enum();\n      var validation = [\n        // number\n        limitNumber_1.default,\n        multipleOf_1.default,\n        // string\n        limitLength_1.default,\n        pattern_1.default,\n        // object\n        limitProperties_1.default,\n        required_1.default,\n        // array\n        limitItems_1.default,\n        uniqueItems_1.default,\n        // any\n        { keyword: "type", schemaType: ["string", "array"] },\n        { keyword: "nullable", schemaType: "boolean" },\n        const_1.default,\n        enum_1.default\n      ];\n      exports.default = validation;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/additionalItems.js\n  var require_additionalItems = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/additionalItems.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.validateAdditionalItems = void 0;\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var error = {\n        message: ({ params: { len } }) => (0, codegen_1.str)`must NOT have more than ${len} items`,\n        params: ({ params: { len } }) => (0, codegen_1._)`{limit: ${len}}`\n      };\n      var def = {\n        keyword: "additionalItems",\n        type: "array",\n        schemaType: ["boolean", "object"],\n        before: "uniqueItems",\n        error,\n        code(cxt) {\n          const { parentSchema, it } = cxt;\n          const { items } = parentSchema;\n          if (!Array.isArray(items)) {\n            (0, util_1.checkStrictMode)(it, \'"additionalItems" is ignored when "items" is not an array of schemas\');\n            return;\n          }\n          validateAdditionalItems(cxt, items);\n        }\n      };\n      function validateAdditionalItems(cxt, items) {\n        const { gen, schema, data, keyword, it } = cxt;\n        it.items = true;\n        const len = gen.const("len", (0, codegen_1._)`${data}.length`);\n        if (schema === false) {\n          cxt.setParams({ len: items.length });\n          cxt.pass((0, codegen_1._)`${len} <= ${items.length}`);\n        } else if (typeof schema == "object" && !(0, util_1.alwaysValidSchema)(it, schema)) {\n          const valid = gen.var("valid", (0, codegen_1._)`${len} <= ${items.length}`);\n          gen.if((0, codegen_1.not)(valid), () => validateItems(valid));\n          cxt.ok(valid);\n        }\n        function validateItems(valid) {\n          gen.forRange("i", items.length, len, (i) => {\n            cxt.subschema({ keyword, dataProp: i, dataPropType: util_1.Type.Num }, valid);\n            if (!it.allErrors)\n              gen.if((0, codegen_1.not)(valid), () => gen.break());\n          });\n        }\n      }\n      exports.validateAdditionalItems = validateAdditionalItems;\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/items.js\n  var require_items = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/items.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.validateTuple = void 0;\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var code_1 = require_code2();\n      var def = {\n        keyword: "items",\n        type: "array",\n        schemaType: ["object", "array", "boolean"],\n        before: "uniqueItems",\n        code(cxt) {\n          const { schema, it } = cxt;\n          if (Array.isArray(schema))\n            return validateTuple(cxt, "additionalItems", schema);\n          it.items = true;\n          if ((0, util_1.alwaysValidSchema)(it, schema))\n            return;\n          cxt.ok((0, code_1.validateArray)(cxt));\n        }\n      };\n      function validateTuple(cxt, extraItems, schArr = cxt.schema) {\n        const { gen, parentSchema, data, keyword, it } = cxt;\n        checkStrictTuple(parentSchema);\n        if (it.opts.unevaluated && schArr.length && it.items !== true) {\n          it.items = util_1.mergeEvaluated.items(gen, schArr.length, it.items);\n        }\n        const valid = gen.name("valid");\n        const len = gen.const("len", (0, codegen_1._)`${data}.length`);\n        schArr.forEach((sch, i) => {\n          if ((0, util_1.alwaysValidSchema)(it, sch))\n            return;\n          gen.if((0, codegen_1._)`${len} > ${i}`, () => cxt.subschema({\n            keyword,\n            schemaProp: i,\n            dataProp: i\n          }, valid));\n          cxt.ok(valid);\n        });\n        function checkStrictTuple(sch) {\n          const { opts, errSchemaPath } = it;\n          const l = schArr.length;\n          const fullTuple = l === sch.minItems && (l === sch.maxItems || sch[extraItems] === false);\n          if (opts.strictTuples && !fullTuple) {\n            const msg = `"${keyword}" is ${l}-tuple, but minItems or maxItems/${extraItems} are not specified or different at path "${errSchemaPath}"`;\n            (0, util_1.checkStrictMode)(it, msg, opts.strictTuples);\n          }\n        }\n      }\n      exports.validateTuple = validateTuple;\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/prefixItems.js\n  var require_prefixItems = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/prefixItems.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var items_1 = require_items();\n      var def = {\n        keyword: "prefixItems",\n        type: "array",\n        schemaType: ["array"],\n        before: "uniqueItems",\n        code: (cxt) => (0, items_1.validateTuple)(cxt, "items")\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/items2020.js\n  var require_items2020 = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/items2020.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var code_1 = require_code2();\n      var additionalItems_1 = require_additionalItems();\n      var error = {\n        message: ({ params: { len } }) => (0, codegen_1.str)`must NOT have more than ${len} items`,\n        params: ({ params: { len } }) => (0, codegen_1._)`{limit: ${len}}`\n      };\n      var def = {\n        keyword: "items",\n        type: "array",\n        schemaType: ["object", "boolean"],\n        before: "uniqueItems",\n        error,\n        code(cxt) {\n          const { schema, parentSchema, it } = cxt;\n          const { prefixItems } = parentSchema;\n          it.items = true;\n          if ((0, util_1.alwaysValidSchema)(it, schema))\n            return;\n          if (prefixItems)\n            (0, additionalItems_1.validateAdditionalItems)(cxt, prefixItems);\n          else\n            cxt.ok((0, code_1.validateArray)(cxt));\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/contains.js\n  var require_contains = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/contains.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var error = {\n        message: ({ params: { min, max } }) => max === void 0 ? (0, codegen_1.str)`must contain at least ${min} valid item(s)` : (0, codegen_1.str)`must contain at least ${min} and no more than ${max} valid item(s)`,\n        params: ({ params: { min, max } }) => max === void 0 ? (0, codegen_1._)`{minContains: ${min}}` : (0, codegen_1._)`{minContains: ${min}, maxContains: ${max}}`\n      };\n      var def = {\n        keyword: "contains",\n        type: "array",\n        schemaType: ["object", "boolean"],\n        before: "uniqueItems",\n        trackErrors: true,\n        error,\n        code(cxt) {\n          const { gen, schema, parentSchema, data, it } = cxt;\n          let min;\n          let max;\n          const { minContains, maxContains } = parentSchema;\n          if (it.opts.next) {\n            min = minContains === void 0 ? 1 : minContains;\n            max = maxContains;\n          } else {\n            min = 1;\n          }\n          const len = gen.const("len", (0, codegen_1._)`${data}.length`);\n          cxt.setParams({ min, max });\n          if (max === void 0 && min === 0) {\n            (0, util_1.checkStrictMode)(it, `"minContains" == 0 without "maxContains": "contains" keyword ignored`);\n            return;\n          }\n          if (max !== void 0 && min > max) {\n            (0, util_1.checkStrictMode)(it, `"minContains" > "maxContains" is always invalid`);\n            cxt.fail();\n            return;\n          }\n          if ((0, util_1.alwaysValidSchema)(it, schema)) {\n            let cond = (0, codegen_1._)`${len} >= ${min}`;\n            if (max !== void 0)\n              cond = (0, codegen_1._)`${cond} && ${len} <= ${max}`;\n            cxt.pass(cond);\n            return;\n          }\n          it.items = true;\n          const valid = gen.name("valid");\n          if (max === void 0 && min === 1) {\n            validateItems(valid, () => gen.if(valid, () => gen.break()));\n          } else if (min === 0) {\n            gen.let(valid, true);\n            if (max !== void 0)\n              gen.if((0, codegen_1._)`${data}.length > 0`, validateItemsWithCount);\n          } else {\n            gen.let(valid, false);\n            validateItemsWithCount();\n          }\n          cxt.result(valid, () => cxt.reset());\n          function validateItemsWithCount() {\n            const schValid = gen.name("_valid");\n            const count = gen.let("count", 0);\n            validateItems(schValid, () => gen.if(schValid, () => checkLimits(count)));\n          }\n          function validateItems(_valid, block) {\n            gen.forRange("i", 0, len, (i) => {\n              cxt.subschema({\n                keyword: "contains",\n                dataProp: i,\n                dataPropType: util_1.Type.Num,\n                compositeRule: true\n              }, _valid);\n              block();\n            });\n          }\n          function checkLimits(count) {\n            gen.code((0, codegen_1._)`${count}++`);\n            if (max === void 0) {\n              gen.if((0, codegen_1._)`${count} >= ${min}`, () => gen.assign(valid, true).break());\n            } else {\n              gen.if((0, codegen_1._)`${count} > ${max}`, () => gen.assign(valid, false).break());\n              if (min === 1)\n                gen.assign(valid, true);\n              else\n                gen.if((0, codegen_1._)`${count} >= ${min}`, () => gen.assign(valid, true));\n            }\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/dependencies.js\n  var require_dependencies = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/dependencies.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.validateSchemaDeps = exports.validatePropertyDeps = exports.error = void 0;\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var code_1 = require_code2();\n      exports.error = {\n        message: ({ params: { property, depsCount, deps } }) => {\n          const property_ies = depsCount === 1 ? "property" : "properties";\n          return (0, codegen_1.str)`must have ${property_ies} ${deps} when property ${property} is present`;\n        },\n        params: ({ params: { property, depsCount, deps, missingProperty } }) => (0, codegen_1._)`{property: ${property},\n    missingProperty: ${missingProperty},\n    depsCount: ${depsCount},\n    deps: ${deps}}`\n        // TODO change to reference\n      };\n      var def = {\n        keyword: "dependencies",\n        type: "object",\n        schemaType: "object",\n        error: exports.error,\n        code(cxt) {\n          const [propDeps, schDeps] = splitDependencies(cxt);\n          validatePropertyDeps(cxt, propDeps);\n          validateSchemaDeps(cxt, schDeps);\n        }\n      };\n      function splitDependencies({ schema }) {\n        const propertyDeps = {};\n        const schemaDeps = {};\n        for (const key in schema) {\n          if (key === "__proto__")\n            continue;\n          const deps = Array.isArray(schema[key]) ? propertyDeps : schemaDeps;\n          deps[key] = schema[key];\n        }\n        return [propertyDeps, schemaDeps];\n      }\n      function validatePropertyDeps(cxt, propertyDeps = cxt.schema) {\n        const { gen, data, it } = cxt;\n        if (Object.keys(propertyDeps).length === 0)\n          return;\n        const missing = gen.let("missing");\n        for (const prop in propertyDeps) {\n          const deps = propertyDeps[prop];\n          if (deps.length === 0)\n            continue;\n          const hasProperty = (0, code_1.propertyInData)(gen, data, prop, it.opts.ownProperties);\n          cxt.setParams({\n            property: prop,\n            depsCount: deps.length,\n            deps: deps.join(", ")\n          });\n          if (it.allErrors) {\n            gen.if(hasProperty, () => {\n              for (const depProp of deps) {\n                (0, code_1.checkReportMissingProp)(cxt, depProp);\n              }\n            });\n          } else {\n            gen.if((0, codegen_1._)`${hasProperty} && (${(0, code_1.checkMissingProp)(cxt, deps, missing)})`);\n            (0, code_1.reportMissingProp)(cxt, missing);\n            gen.else();\n          }\n        }\n      }\n      exports.validatePropertyDeps = validatePropertyDeps;\n      function validateSchemaDeps(cxt, schemaDeps = cxt.schema) {\n        const { gen, data, keyword, it } = cxt;\n        const valid = gen.name("valid");\n        for (const prop in schemaDeps) {\n          if ((0, util_1.alwaysValidSchema)(it, schemaDeps[prop]))\n            continue;\n          gen.if(\n            (0, code_1.propertyInData)(gen, data, prop, it.opts.ownProperties),\n            () => {\n              const schCxt = cxt.subschema({ keyword, schemaProp: prop }, valid);\n              cxt.mergeValidEvaluated(schCxt, valid);\n            },\n            () => gen.var(valid, true)\n            // TODO var\n          );\n          cxt.ok(valid);\n        }\n      }\n      exports.validateSchemaDeps = validateSchemaDeps;\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/propertyNames.js\n  var require_propertyNames = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/propertyNames.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var error = {\n        message: "property name must be valid",\n        params: ({ params }) => (0, codegen_1._)`{propertyName: ${params.propertyName}}`\n      };\n      var def = {\n        keyword: "propertyNames",\n        type: "object",\n        schemaType: ["object", "boolean"],\n        error,\n        code(cxt) {\n          const { gen, schema, data, it } = cxt;\n          if ((0, util_1.alwaysValidSchema)(it, schema))\n            return;\n          const valid = gen.name("valid");\n          gen.forIn("key", data, (key) => {\n            cxt.setParams({ propertyName: key });\n            cxt.subschema({\n              keyword: "propertyNames",\n              data: key,\n              dataTypes: ["string"],\n              propertyName: key,\n              compositeRule: true\n            }, valid);\n            gen.if((0, codegen_1.not)(valid), () => {\n              cxt.error(true);\n              if (!it.allErrors)\n                gen.break();\n            });\n          });\n          cxt.ok(valid);\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/additionalProperties.js\n  var require_additionalProperties = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/additionalProperties.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var code_1 = require_code2();\n      var codegen_1 = require_codegen();\n      var names_1 = require_names();\n      var util_1 = require_util();\n      var error = {\n        message: "must NOT have additional properties",\n        params: ({ params }) => (0, codegen_1._)`{additionalProperty: ${params.additionalProperty}}`\n      };\n      var def = {\n        keyword: "additionalProperties",\n        type: ["object"],\n        schemaType: ["boolean", "object"],\n        allowUndefined: true,\n        trackErrors: true,\n        error,\n        code(cxt) {\n          const { gen, schema, parentSchema, data, errsCount, it } = cxt;\n          if (!errsCount)\n            throw new Error("ajv implementation error");\n          const { allErrors, opts } = it;\n          it.props = true;\n          if (opts.removeAdditional !== "all" && (0, util_1.alwaysValidSchema)(it, schema))\n            return;\n          const props = (0, code_1.allSchemaProperties)(parentSchema.properties);\n          const patProps = (0, code_1.allSchemaProperties)(parentSchema.patternProperties);\n          checkAdditionalProperties();\n          cxt.ok((0, codegen_1._)`${errsCount} === ${names_1.default.errors}`);\n          function checkAdditionalProperties() {\n            gen.forIn("key", data, (key) => {\n              if (!props.length && !patProps.length)\n                additionalPropertyCode(key);\n              else\n                gen.if(isAdditional(key), () => additionalPropertyCode(key));\n            });\n          }\n          function isAdditional(key) {\n            let definedProp;\n            if (props.length > 8) {\n              const propsSchema = (0, util_1.schemaRefOrVal)(it, parentSchema.properties, "properties");\n              definedProp = (0, code_1.isOwnProperty)(gen, propsSchema, key);\n            } else if (props.length) {\n              definedProp = (0, codegen_1.or)(...props.map((p) => (0, codegen_1._)`${key} === ${p}`));\n            } else {\n              definedProp = codegen_1.nil;\n            }\n            if (patProps.length) {\n              definedProp = (0, codegen_1.or)(definedProp, ...patProps.map((p) => (0, codegen_1._)`${(0, code_1.usePattern)(cxt, p)}.test(${key})`));\n            }\n            return (0, codegen_1.not)(definedProp);\n          }\n          function deleteAdditional(key) {\n            gen.code((0, codegen_1._)`delete ${data}[${key}]`);\n          }\n          function additionalPropertyCode(key) {\n            if (opts.removeAdditional === "all" || opts.removeAdditional && schema === false) {\n              deleteAdditional(key);\n              return;\n            }\n            if (schema === false) {\n              cxt.setParams({ additionalProperty: key });\n              cxt.error();\n              if (!allErrors)\n                gen.break();\n              return;\n            }\n            if (typeof schema == "object" && !(0, util_1.alwaysValidSchema)(it, schema)) {\n              const valid = gen.name("valid");\n              if (opts.removeAdditional === "failing") {\n                applyAdditionalSchema(key, valid, false);\n                gen.if((0, codegen_1.not)(valid), () => {\n                  cxt.reset();\n                  deleteAdditional(key);\n                });\n              } else {\n                applyAdditionalSchema(key, valid);\n                if (!allErrors)\n                  gen.if((0, codegen_1.not)(valid), () => gen.break());\n              }\n            }\n          }\n          function applyAdditionalSchema(key, valid, errors2) {\n            const subschema = {\n              keyword: "additionalProperties",\n              dataProp: key,\n              dataPropType: util_1.Type.Str\n            };\n            if (errors2 === false) {\n              Object.assign(subschema, {\n                compositeRule: true,\n                createErrors: false,\n                allErrors: false\n              });\n            }\n            cxt.subschema(subschema, valid);\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/properties.js\n  var require_properties = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/properties.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var validate_1 = require_validate();\n      var code_1 = require_code2();\n      var util_1 = require_util();\n      var additionalProperties_1 = require_additionalProperties();\n      var def = {\n        keyword: "properties",\n        type: "object",\n        schemaType: "object",\n        code(cxt) {\n          const { gen, schema, parentSchema, data, it } = cxt;\n          if (it.opts.removeAdditional === "all" && parentSchema.additionalProperties === void 0) {\n            additionalProperties_1.default.code(new validate_1.KeywordCxt(it, additionalProperties_1.default, "additionalProperties"));\n          }\n          const allProps = (0, code_1.allSchemaProperties)(schema);\n          for (const prop of allProps) {\n            it.definedProperties.add(prop);\n          }\n          if (it.opts.unevaluated && allProps.length && it.props !== true) {\n            it.props = util_1.mergeEvaluated.props(gen, (0, util_1.toHash)(allProps), it.props);\n          }\n          const properties = allProps.filter((p) => !(0, util_1.alwaysValidSchema)(it, schema[p]));\n          if (properties.length === 0)\n            return;\n          const valid = gen.name("valid");\n          for (const prop of properties) {\n            if (hasDefault(prop)) {\n              applyPropertySchema(prop);\n            } else {\n              gen.if((0, code_1.propertyInData)(gen, data, prop, it.opts.ownProperties));\n              applyPropertySchema(prop);\n              if (!it.allErrors)\n                gen.else().var(valid, true);\n              gen.endIf();\n            }\n            cxt.it.definedProperties.add(prop);\n            cxt.ok(valid);\n          }\n          function hasDefault(prop) {\n            return it.opts.useDefaults && !it.compositeRule && schema[prop].default !== void 0;\n          }\n          function applyPropertySchema(prop) {\n            cxt.subschema({\n              keyword: "properties",\n              schemaProp: prop,\n              dataProp: prop\n            }, valid);\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/patternProperties.js\n  var require_patternProperties = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/patternProperties.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var code_1 = require_code2();\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var util_2 = require_util();\n      var def = {\n        keyword: "patternProperties",\n        type: "object",\n        schemaType: "object",\n        code(cxt) {\n          const { gen, schema, data, parentSchema, it } = cxt;\n          const { opts } = it;\n          const patterns = (0, code_1.allSchemaProperties)(schema);\n          const alwaysValidPatterns = patterns.filter((p) => (0, util_1.alwaysValidSchema)(it, schema[p]));\n          if (patterns.length === 0 || alwaysValidPatterns.length === patterns.length && (!it.opts.unevaluated || it.props === true)) {\n            return;\n          }\n          const checkProperties = opts.strictSchema && !opts.allowMatchingProperties && parentSchema.properties;\n          const valid = gen.name("valid");\n          if (it.props !== true && !(it.props instanceof codegen_1.Name)) {\n            it.props = (0, util_2.evaluatedPropsToName)(gen, it.props);\n          }\n          const { props } = it;\n          validatePatternProperties();\n          function validatePatternProperties() {\n            for (const pat of patterns) {\n              if (checkProperties)\n                checkMatchingProperties(pat);\n              if (it.allErrors) {\n                validateProperties(pat);\n              } else {\n                gen.var(valid, true);\n                validateProperties(pat);\n                gen.if(valid);\n              }\n            }\n          }\n          function checkMatchingProperties(pat) {\n            for (const prop in checkProperties) {\n              if (new RegExp(pat).test(prop)) {\n                (0, util_1.checkStrictMode)(it, `property ${prop} matches pattern ${pat} (use allowMatchingProperties)`);\n              }\n            }\n          }\n          function validateProperties(pat) {\n            gen.forIn("key", data, (key) => {\n              gen.if((0, codegen_1._)`${(0, code_1.usePattern)(cxt, pat)}.test(${key})`, () => {\n                const alwaysValid = alwaysValidPatterns.includes(pat);\n                if (!alwaysValid) {\n                  cxt.subschema({\n                    keyword: "patternProperties",\n                    schemaProp: pat,\n                    dataProp: key,\n                    dataPropType: util_2.Type.Str\n                  }, valid);\n                }\n                if (it.opts.unevaluated && props !== true) {\n                  gen.assign((0, codegen_1._)`${props}[${key}]`, true);\n                } else if (!alwaysValid && !it.allErrors) {\n                  gen.if((0, codegen_1.not)(valid), () => gen.break());\n                }\n              });\n            });\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/not.js\n  var require_not = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/not.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var util_1 = require_util();\n      var def = {\n        keyword: "not",\n        schemaType: ["object", "boolean"],\n        trackErrors: true,\n        code(cxt) {\n          const { gen, schema, it } = cxt;\n          if ((0, util_1.alwaysValidSchema)(it, schema)) {\n            cxt.fail();\n            return;\n          }\n          const valid = gen.name("valid");\n          cxt.subschema({\n            keyword: "not",\n            compositeRule: true,\n            createErrors: false,\n            allErrors: false\n          }, valid);\n          cxt.failResult(valid, () => cxt.reset(), () => cxt.error());\n        },\n        error: { message: "must NOT be valid" }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/anyOf.js\n  var require_anyOf = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/anyOf.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var code_1 = require_code2();\n      var def = {\n        keyword: "anyOf",\n        schemaType: "array",\n        trackErrors: true,\n        code: code_1.validateUnion,\n        error: { message: "must match a schema in anyOf" }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/oneOf.js\n  var require_oneOf = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/oneOf.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var error = {\n        message: "must match exactly one schema in oneOf",\n        params: ({ params }) => (0, codegen_1._)`{passingSchemas: ${params.passing}}`\n      };\n      var def = {\n        keyword: "oneOf",\n        schemaType: "array",\n        trackErrors: true,\n        error,\n        code(cxt) {\n          const { gen, schema, parentSchema, it } = cxt;\n          if (!Array.isArray(schema))\n            throw new Error("ajv implementation error");\n          if (it.opts.discriminator && parentSchema.discriminator)\n            return;\n          const schArr = schema;\n          const valid = gen.let("valid", false);\n          const passing = gen.let("passing", null);\n          const schValid = gen.name("_valid");\n          cxt.setParams({ passing });\n          gen.block(validateOneOf);\n          cxt.result(valid, () => cxt.reset(), () => cxt.error(true));\n          function validateOneOf() {\n            schArr.forEach((sch, i) => {\n              let schCxt;\n              if ((0, util_1.alwaysValidSchema)(it, sch)) {\n                gen.var(schValid, true);\n              } else {\n                schCxt = cxt.subschema({\n                  keyword: "oneOf",\n                  schemaProp: i,\n                  compositeRule: true\n                }, schValid);\n              }\n              if (i > 0) {\n                gen.if((0, codegen_1._)`${schValid} && ${valid}`).assign(valid, false).assign(passing, (0, codegen_1._)`[${passing}, ${i}]`).else();\n              }\n              gen.if(schValid, () => {\n                gen.assign(valid, true);\n                gen.assign(passing, i);\n                if (schCxt)\n                  cxt.mergeEvaluated(schCxt, codegen_1.Name);\n              });\n            });\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/allOf.js\n  var require_allOf = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/allOf.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var util_1 = require_util();\n      var def = {\n        keyword: "allOf",\n        schemaType: "array",\n        code(cxt) {\n          const { gen, schema, it } = cxt;\n          if (!Array.isArray(schema))\n            throw new Error("ajv implementation error");\n          const valid = gen.name("valid");\n          schema.forEach((sch, i) => {\n            if ((0, util_1.alwaysValidSchema)(it, sch))\n              return;\n            const schCxt = cxt.subschema({ keyword: "allOf", schemaProp: i }, valid);\n            cxt.ok(valid);\n            cxt.mergeEvaluated(schCxt);\n          });\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/if.js\n  var require_if = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/if.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var util_1 = require_util();\n      var error = {\n        message: ({ params }) => (0, codegen_1.str)`must match "${params.ifClause}" schema`,\n        params: ({ params }) => (0, codegen_1._)`{failingKeyword: ${params.ifClause}}`\n      };\n      var def = {\n        keyword: "if",\n        schemaType: ["object", "boolean"],\n        trackErrors: true,\n        error,\n        code(cxt) {\n          const { gen, parentSchema, it } = cxt;\n          if (parentSchema.then === void 0 && parentSchema.else === void 0) {\n            (0, util_1.checkStrictMode)(it, \'"if" without "then" and "else" is ignored\');\n          }\n          const hasThen = hasSchema(it, "then");\n          const hasElse = hasSchema(it, "else");\n          if (!hasThen && !hasElse)\n            return;\n          const valid = gen.let("valid", true);\n          const schValid = gen.name("_valid");\n          validateIf();\n          cxt.reset();\n          if (hasThen && hasElse) {\n            const ifClause = gen.let("ifClause");\n            cxt.setParams({ ifClause });\n            gen.if(schValid, validateClause("then", ifClause), validateClause("else", ifClause));\n          } else if (hasThen) {\n            gen.if(schValid, validateClause("then"));\n          } else {\n            gen.if((0, codegen_1.not)(schValid), validateClause("else"));\n          }\n          cxt.pass(valid, () => cxt.error(true));\n          function validateIf() {\n            const schCxt = cxt.subschema({\n              keyword: "if",\n              compositeRule: true,\n              createErrors: false,\n              allErrors: false\n            }, schValid);\n            cxt.mergeEvaluated(schCxt);\n          }\n          function validateClause(keyword, ifClause) {\n            return () => {\n              const schCxt = cxt.subschema({ keyword }, schValid);\n              gen.assign(valid, schValid);\n              cxt.mergeValidEvaluated(schCxt, valid);\n              if (ifClause)\n                gen.assign(ifClause, (0, codegen_1._)`${keyword}`);\n              else\n                cxt.setParams({ ifClause: keyword });\n            };\n          }\n        }\n      };\n      function hasSchema(it, keyword) {\n        const schema = it.schema[keyword];\n        return schema !== void 0 && !(0, util_1.alwaysValidSchema)(it, schema);\n      }\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/thenElse.js\n  var require_thenElse = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/thenElse.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var util_1 = require_util();\n      var def = {\n        keyword: ["then", "else"],\n        schemaType: ["object", "boolean"],\n        code({ keyword, parentSchema, it }) {\n          if (parentSchema.if === void 0)\n            (0, util_1.checkStrictMode)(it, `"${keyword}" without "if" is ignored`);\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/applicator/index.js\n  var require_applicator = __commonJS({\n    "node_modules/ajv/dist/vocabularies/applicator/index.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var additionalItems_1 = require_additionalItems();\n      var prefixItems_1 = require_prefixItems();\n      var items_1 = require_items();\n      var items2020_1 = require_items2020();\n      var contains_1 = require_contains();\n      var dependencies_1 = require_dependencies();\n      var propertyNames_1 = require_propertyNames();\n      var additionalProperties_1 = require_additionalProperties();\n      var properties_1 = require_properties();\n      var patternProperties_1 = require_patternProperties();\n      var not_1 = require_not();\n      var anyOf_1 = require_anyOf();\n      var oneOf_1 = require_oneOf();\n      var allOf_1 = require_allOf();\n      var if_1 = require_if();\n      var thenElse_1 = require_thenElse();\n      function getApplicator(draft2020 = false) {\n        const applicator = [\n          // any\n          not_1.default,\n          anyOf_1.default,\n          oneOf_1.default,\n          allOf_1.default,\n          if_1.default,\n          thenElse_1.default,\n          // object\n          propertyNames_1.default,\n          additionalProperties_1.default,\n          dependencies_1.default,\n          properties_1.default,\n          patternProperties_1.default\n        ];\n        if (draft2020)\n          applicator.push(prefixItems_1.default, items2020_1.default);\n        else\n          applicator.push(additionalItems_1.default, items_1.default);\n        applicator.push(contains_1.default);\n        return applicator;\n      }\n      exports.default = getApplicator;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/format/format.js\n  var require_format = __commonJS({\n    "node_modules/ajv/dist/vocabularies/format/format.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var error = {\n        message: ({ schemaCode }) => (0, codegen_1.str)`must match format "${schemaCode}"`,\n        params: ({ schemaCode }) => (0, codegen_1._)`{format: ${schemaCode}}`\n      };\n      var def = {\n        keyword: "format",\n        type: ["number", "string"],\n        schemaType: "string",\n        $data: true,\n        error,\n        code(cxt, ruleType) {\n          const { gen, data, $data, schema, schemaCode, it } = cxt;\n          const { opts, errSchemaPath, schemaEnv, self: self2 } = it;\n          if (!opts.validateFormats)\n            return;\n          if ($data)\n            validate$DataFormat();\n          else\n            validateFormat();\n          function validate$DataFormat() {\n            const fmts = gen.scopeValue("formats", {\n              ref: self2.formats,\n              code: opts.code.formats\n            });\n            const fDef = gen.const("fDef", (0, codegen_1._)`${fmts}[${schemaCode}]`);\n            const fType = gen.let("fType");\n            const format = gen.let("format");\n            gen.if((0, codegen_1._)`typeof ${fDef} == "object" && !(${fDef} instanceof RegExp)`, () => gen.assign(fType, (0, codegen_1._)`${fDef}.type || "string"`).assign(format, (0, codegen_1._)`${fDef}.validate`), () => gen.assign(fType, (0, codegen_1._)`"string"`).assign(format, fDef));\n            cxt.fail$data((0, codegen_1.or)(unknownFmt(), invalidFmt()));\n            function unknownFmt() {\n              if (opts.strictSchema === false)\n                return codegen_1.nil;\n              return (0, codegen_1._)`${schemaCode} && !${format}`;\n            }\n            function invalidFmt() {\n              const callFormat = schemaEnv.$async ? (0, codegen_1._)`(${fDef}.async ? await ${format}(${data}) : ${format}(${data}))` : (0, codegen_1._)`${format}(${data})`;\n              const validData = (0, codegen_1._)`(typeof ${format} == "function" ? ${callFormat} : ${format}.test(${data}))`;\n              return (0, codegen_1._)`${format} && ${format} !== true && ${fType} === ${ruleType} && !${validData}`;\n            }\n          }\n          function validateFormat() {\n            const formatDef = self2.formats[schema];\n            if (!formatDef) {\n              unknownFormat();\n              return;\n            }\n            if (formatDef === true)\n              return;\n            const [fmtType, format, fmtRef] = getFormat(formatDef);\n            if (fmtType === ruleType)\n              cxt.pass(validCondition());\n            function unknownFormat() {\n              if (opts.strictSchema === false) {\n                self2.logger.warn(unknownMsg());\n                return;\n              }\n              throw new Error(unknownMsg());\n              function unknownMsg() {\n                return `unknown format "${schema}" ignored in schema at path "${errSchemaPath}"`;\n              }\n            }\n            function getFormat(fmtDef) {\n              const code = fmtDef instanceof RegExp ? (0, codegen_1.regexpCode)(fmtDef) : opts.code.formats ? (0, codegen_1._)`${opts.code.formats}${(0, codegen_1.getProperty)(schema)}` : void 0;\n              const fmt = gen.scopeValue("formats", { key: schema, ref: fmtDef, code });\n              if (typeof fmtDef == "object" && !(fmtDef instanceof RegExp)) {\n                return [fmtDef.type || "string", fmtDef.validate, (0, codegen_1._)`${fmt}.validate`];\n              }\n              return ["string", fmtDef, fmt];\n            }\n            function validCondition() {\n              if (typeof formatDef == "object" && !(formatDef instanceof RegExp) && formatDef.async) {\n                if (!schemaEnv.$async)\n                  throw new Error("async format in sync schema");\n                return (0, codegen_1._)`await ${fmtRef}(${data})`;\n              }\n              return typeof format == "function" ? (0, codegen_1._)`${fmtRef}(${data})` : (0, codegen_1._)`${fmtRef}.test(${data})`;\n            }\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/format/index.js\n  var require_format2 = __commonJS({\n    "node_modules/ajv/dist/vocabularies/format/index.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var format_1 = require_format();\n      var format = [format_1.default];\n      exports.default = format;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/metadata.js\n  var require_metadata = __commonJS({\n    "node_modules/ajv/dist/vocabularies/metadata.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.contentVocabulary = exports.metadataVocabulary = void 0;\n      exports.metadataVocabulary = [\n        "title",\n        "description",\n        "default",\n        "deprecated",\n        "readOnly",\n        "writeOnly",\n        "examples"\n      ];\n      exports.contentVocabulary = [\n        "contentMediaType",\n        "contentEncoding",\n        "contentSchema"\n      ];\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/draft7.js\n  var require_draft7 = __commonJS({\n    "node_modules/ajv/dist/vocabularies/draft7.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var core_1 = require_core2();\n      var validation_1 = require_validation();\n      var applicator_1 = require_applicator();\n      var format_1 = require_format2();\n      var metadata_1 = require_metadata();\n      var draft7Vocabularies = [\n        core_1.default,\n        validation_1.default,\n        (0, applicator_1.default)(),\n        format_1.default,\n        metadata_1.metadataVocabulary,\n        metadata_1.contentVocabulary\n      ];\n      exports.default = draft7Vocabularies;\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/discriminator/types.js\n  var require_types = __commonJS({\n    "node_modules/ajv/dist/vocabularies/discriminator/types.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.DiscrError = void 0;\n      var DiscrError;\n      (function(DiscrError2) {\n        DiscrError2["Tag"] = "tag";\n        DiscrError2["Mapping"] = "mapping";\n      })(DiscrError || (exports.DiscrError = DiscrError = {}));\n    }\n  });\n\n  // node_modules/ajv/dist/vocabularies/discriminator/index.js\n  var require_discriminator = __commonJS({\n    "node_modules/ajv/dist/vocabularies/discriminator/index.js"(exports) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      var codegen_1 = require_codegen();\n      var types_1 = require_types();\n      var compile_1 = require_compile();\n      var ref_error_1 = require_ref_error();\n      var util_1 = require_util();\n      var error = {\n        message: ({ params: { discrError, tagName } }) => discrError === types_1.DiscrError.Tag ? `tag "${tagName}" must be string` : `value of tag "${tagName}" must be in oneOf`,\n        params: ({ params: { discrError, tag, tagName } }) => (0, codegen_1._)`{error: ${discrError}, tag: ${tagName}, tagValue: ${tag}}`\n      };\n      var def = {\n        keyword: "discriminator",\n        type: "object",\n        schemaType: "object",\n        error,\n        code(cxt) {\n          const { gen, data, schema, parentSchema, it } = cxt;\n          const { oneOf } = parentSchema;\n          if (!it.opts.discriminator) {\n            throw new Error("discriminator: requires discriminator option");\n          }\n          const tagName = schema.propertyName;\n          if (typeof tagName != "string")\n            throw new Error("discriminator: requires propertyName");\n          if (schema.mapping)\n            throw new Error("discriminator: mapping is not supported");\n          if (!oneOf)\n            throw new Error("discriminator: requires oneOf keyword");\n          const valid = gen.let("valid", false);\n          const tag = gen.const("tag", (0, codegen_1._)`${data}${(0, codegen_1.getProperty)(tagName)}`);\n          gen.if((0, codegen_1._)`typeof ${tag} == "string"`, () => validateMapping(), () => cxt.error(false, { discrError: types_1.DiscrError.Tag, tag, tagName }));\n          cxt.ok(valid);\n          function validateMapping() {\n            const mapping = getMapping();\n            gen.if(false);\n            for (const tagValue in mapping) {\n              gen.elseIf((0, codegen_1._)`${tag} === ${tagValue}`);\n              gen.assign(valid, applyTagSchema(mapping[tagValue]));\n            }\n            gen.else();\n            cxt.error(false, { discrError: types_1.DiscrError.Mapping, tag, tagName });\n            gen.endIf();\n          }\n          function applyTagSchema(schemaProp) {\n            const _valid = gen.name("valid");\n            const schCxt = cxt.subschema({ keyword: "oneOf", schemaProp }, _valid);\n            cxt.mergeEvaluated(schCxt, codegen_1.Name);\n            return _valid;\n          }\n          function getMapping() {\n            var _a;\n            const oneOfMapping = {};\n            const topRequired = hasRequired(parentSchema);\n            let tagRequired = true;\n            for (let i = 0; i < oneOf.length; i++) {\n              let sch = oneOf[i];\n              if ((sch === null || sch === void 0 ? void 0 : sch.$ref) && !(0, util_1.schemaHasRulesButRef)(sch, it.self.RULES)) {\n                const ref = sch.$ref;\n                sch = compile_1.resolveRef.call(it.self, it.schemaEnv.root, it.baseId, ref);\n                if (sch instanceof compile_1.SchemaEnv)\n                  sch = sch.schema;\n                if (sch === void 0)\n                  throw new ref_error_1.default(it.opts.uriResolver, it.baseId, ref);\n              }\n              const propSch = (_a = sch === null || sch === void 0 ? void 0 : sch.properties) === null || _a === void 0 ? void 0 : _a[tagName];\n              if (typeof propSch != "object") {\n                throw new Error(`discriminator: oneOf subschemas (or referenced schemas) must have "properties/${tagName}"`);\n              }\n              tagRequired = tagRequired && (topRequired || hasRequired(sch));\n              addMappings(propSch, i);\n            }\n            if (!tagRequired)\n              throw new Error(`discriminator: "${tagName}" must be required`);\n            return oneOfMapping;\n            function hasRequired({ required }) {\n              return Array.isArray(required) && required.includes(tagName);\n            }\n            function addMappings(sch, i) {\n              if (sch.const) {\n                addMapping(sch.const, i);\n              } else if (sch.enum) {\n                for (const tagValue of sch.enum) {\n                  addMapping(tagValue, i);\n                }\n              } else {\n                throw new Error(`discriminator: "properties/${tagName}" must have "const" or "enum"`);\n              }\n            }\n            function addMapping(tagValue, i) {\n              if (typeof tagValue != "string" || tagValue in oneOfMapping) {\n                throw new Error(`discriminator: "${tagName}" values must be unique strings`);\n              }\n              oneOfMapping[tagValue] = i;\n            }\n          }\n        }\n      };\n      exports.default = def;\n    }\n  });\n\n  // node_modules/ajv/dist/refs/json-schema-draft-07.json\n  var require_json_schema_draft_07 = __commonJS({\n    "node_modules/ajv/dist/refs/json-schema-draft-07.json"(exports, module) {\n      module.exports = {\n        $schema: "http://json-schema.org/draft-07/schema#",\n        $id: "http://json-schema.org/draft-07/schema#",\n        title: "Core schema meta-schema",\n        definitions: {\n          schemaArray: {\n            type: "array",\n            minItems: 1,\n            items: { $ref: "#" }\n          },\n          nonNegativeInteger: {\n            type: "integer",\n            minimum: 0\n          },\n          nonNegativeIntegerDefault0: {\n            allOf: [{ $ref: "#/definitions/nonNegativeInteger" }, { default: 0 }]\n          },\n          simpleTypes: {\n            enum: ["array", "boolean", "integer", "null", "number", "object", "string"]\n          },\n          stringArray: {\n            type: "array",\n            items: { type: "string" },\n            uniqueItems: true,\n            default: []\n          }\n        },\n        type: ["object", "boolean"],\n        properties: {\n          $id: {\n            type: "string",\n            format: "uri-reference"\n          },\n          $schema: {\n            type: "string",\n            format: "uri"\n          },\n          $ref: {\n            type: "string",\n            format: "uri-reference"\n          },\n          $comment: {\n            type: "string"\n          },\n          title: {\n            type: "string"\n          },\n          description: {\n            type: "string"\n          },\n          default: true,\n          readOnly: {\n            type: "boolean",\n            default: false\n          },\n          examples: {\n            type: "array",\n            items: true\n          },\n          multipleOf: {\n            type: "number",\n            exclusiveMinimum: 0\n          },\n          maximum: {\n            type: "number"\n          },\n          exclusiveMaximum: {\n            type: "number"\n          },\n          minimum: {\n            type: "number"\n          },\n          exclusiveMinimum: {\n            type: "number"\n          },\n          maxLength: { $ref: "#/definitions/nonNegativeInteger" },\n          minLength: { $ref: "#/definitions/nonNegativeIntegerDefault0" },\n          pattern: {\n            type: "string",\n            format: "regex"\n          },\n          additionalItems: { $ref: "#" },\n          items: {\n            anyOf: [{ $ref: "#" }, { $ref: "#/definitions/schemaArray" }],\n            default: true\n          },\n          maxItems: { $ref: "#/definitions/nonNegativeInteger" },\n          minItems: { $ref: "#/definitions/nonNegativeIntegerDefault0" },\n          uniqueItems: {\n            type: "boolean",\n            default: false\n          },\n          contains: { $ref: "#" },\n          maxProperties: { $ref: "#/definitions/nonNegativeInteger" },\n          minProperties: { $ref: "#/definitions/nonNegativeIntegerDefault0" },\n          required: { $ref: "#/definitions/stringArray" },\n          additionalProperties: { $ref: "#" },\n          definitions: {\n            type: "object",\n            additionalProperties: { $ref: "#" },\n            default: {}\n          },\n          properties: {\n            type: "object",\n            additionalProperties: { $ref: "#" },\n            default: {}\n          },\n          patternProperties: {\n            type: "object",\n            additionalProperties: { $ref: "#" },\n            propertyNames: { format: "regex" },\n            default: {}\n          },\n          dependencies: {\n            type: "object",\n            additionalProperties: {\n              anyOf: [{ $ref: "#" }, { $ref: "#/definitions/stringArray" }]\n            }\n          },\n          propertyNames: { $ref: "#" },\n          const: true,\n          enum: {\n            type: "array",\n            items: true,\n            minItems: 1,\n            uniqueItems: true\n          },\n          type: {\n            anyOf: [\n              { $ref: "#/definitions/simpleTypes" },\n              {\n                type: "array",\n                items: { $ref: "#/definitions/simpleTypes" },\n                minItems: 1,\n                uniqueItems: true\n              }\n            ]\n          },\n          format: { type: "string" },\n          contentMediaType: { type: "string" },\n          contentEncoding: { type: "string" },\n          if: { $ref: "#" },\n          then: { $ref: "#" },\n          else: { $ref: "#" },\n          allOf: { $ref: "#/definitions/schemaArray" },\n          anyOf: { $ref: "#/definitions/schemaArray" },\n          oneOf: { $ref: "#/definitions/schemaArray" },\n          not: { $ref: "#" }\n        },\n        default: true\n      };\n    }\n  });\n\n  // node_modules/ajv/dist/ajv.js\n  var require_ajv = __commonJS({\n    "node_modules/ajv/dist/ajv.js"(exports, module) {\n      "use strict";\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.MissingRefError = exports.ValidationError = exports.CodeGen = exports.Name = exports.nil = exports.stringify = exports.str = exports._ = exports.KeywordCxt = exports.Ajv = void 0;\n      var core_1 = require_core();\n      var draft7_1 = require_draft7();\n      var discriminator_1 = require_discriminator();\n      var draft7MetaSchema = require_json_schema_draft_07();\n      var META_SUPPORT_DATA = ["/properties"];\n      var META_SCHEMA_ID = "http://json-schema.org/draft-07/schema";\n      var Ajv2 = class extends core_1.default {\n        _addVocabularies() {\n          super._addVocabularies();\n          draft7_1.default.forEach((v) => this.addVocabulary(v));\n          if (this.opts.discriminator)\n            this.addKeyword(discriminator_1.default);\n        }\n        _addDefaultMetaSchema() {\n          super._addDefaultMetaSchema();\n          if (!this.opts.meta)\n            return;\n          const metaSchema = this.opts.$data ? this.$dataMetaSchema(draft7MetaSchema, META_SUPPORT_DATA) : draft7MetaSchema;\n          this.addMetaSchema(metaSchema, META_SCHEMA_ID, false);\n          this.refs["http://json-schema.org/schema"] = META_SCHEMA_ID;\n        }\n        defaultMeta() {\n          return this.opts.defaultMeta = super.defaultMeta() || (this.getSchema(META_SCHEMA_ID) ? META_SCHEMA_ID : void 0);\n        }\n      };\n      exports.Ajv = Ajv2;\n      module.exports = exports = Ajv2;\n      module.exports.Ajv = Ajv2;\n      Object.defineProperty(exports, "__esModule", { value: true });\n      exports.default = Ajv2;\n      var validate_1 = require_validate();\n      Object.defineProperty(exports, "KeywordCxt", { enumerable: true, get: function() {\n        return validate_1.KeywordCxt;\n      } });\n      var codegen_1 = require_codegen();\n      Object.defineProperty(exports, "_", { enumerable: true, get: function() {\n        return codegen_1._;\n      } });\n      Object.defineProperty(exports, "str", { enumerable: true, get: function() {\n        return codegen_1.str;\n      } });\n      Object.defineProperty(exports, "stringify", { enumerable: true, get: function() {\n        return codegen_1.stringify;\n      } });\n      Object.defineProperty(exports, "nil", { enumerable: true, get: function() {\n        return codegen_1.nil;\n      } });\n      Object.defineProperty(exports, "Name", { enumerable: true, get: function() {\n        return codegen_1.Name;\n      } });\n      Object.defineProperty(exports, "CodeGen", { enumerable: true, get: function() {\n        return codegen_1.CodeGen;\n      } });\n      var validation_error_1 = require_validation_error();\n      Object.defineProperty(exports, "ValidationError", { enumerable: true, get: function() {\n        return validation_error_1.default;\n      } });\n      var ref_error_1 = require_ref_error();\n      Object.defineProperty(exports, "MissingRefError", { enumerable: true, get: function() {\n        return ref_error_1.default;\n      } });\n    }\n  });\n\n  // src/extensions/data-validation-core.js\n  var import_ajv = __toESM(require_ajv(), 1);\n\n  // src/extensions/protocol.js\n  function validateArguments(args) {\n    if (!Array.isArray(args) || args.length > 4) throw new TypeError("\\u63A5\\u53E3\\u53C2\\u6570\\u5FC5\\u987B\\u662F\\u6700\\u591A\\u56DB\\u9879\\u7684\\u6570\\u7EC4");\n    const ancestors = /* @__PURE__ */ new Set();\n    function visit(value, depth) {\n      if (depth > 32) throw new TypeError("\\u63A5\\u53E3\\u53C2\\u6570\\u5D4C\\u5957\\u8FC7\\u6DF1");\n      if (value === null || typeof value === "string" || typeof value === "boolean") return;\n      if (typeof value === "number" && Number.isFinite(value)) return;\n      if (typeof value !== "object" || ancestors.has(value)) throw new TypeError("\\u63A5\\u53E3\\u53EA\\u63A5\\u53D7\\u6709\\u9650\\u6570\\u503C\\u53CA JSON \\u6570\\u636E");\n      if (!Array.isArray(value) && Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null)\n        throw new TypeError("\\u63A5\\u53E3\\u53EA\\u63A5\\u53D7\\u666E\\u901A JSON \\u5BF9\\u8C61");\n      ancestors.add(value);\n      for (const item of Object.values(value)) visit(item, depth + 1);\n      ancestors.delete(value);\n    }\n    visit(args, 0);\n    const encoded = JSON.stringify(args);\n    if (encoded.length > 128 * 1024 * 1024) throw new TypeError("\\u63A5\\u53E3\\u53C2\\u6570\\u8FC7\\u5927");\n    return JSON.parse(encoded);\n  }\n\n  // src/extensions/data-validation-core.js\n  var clone = (value) => JSON.parse(JSON.stringify(value));\n  var object = (value) => Boolean(value) && typeof value === "object" && !Array.isArray(value);\n  var own = (value, key) => Object.prototype.hasOwnProperty.call(value, key);\n  var escape2 = (value) => value.replace(/~/g, "~0").replace(/\\//g, "~1");\n  var keywords = new Set("$schema $ref $comment title description default examples readOnly writeOnly type enum const multipleOf maximum exclusiveMaximum minimum exclusiveMinimum maxLength minLength pattern format items additionalItems maxItems minItems uniqueItems contains maxProperties minProperties required properties patternProperties additionalProperties dependencies propertyNames allOf anyOf oneOf not if then else definitions contentEncoding contentMediaType".split(" "));\n  var maps = /* @__PURE__ */ new Set(["properties", "patternProperties", "definitions", "dependencies"]);\n  var singles = /* @__PURE__ */ new Set(["additionalItems", "additionalProperties", "contains", "propertyNames", "not", "if", "then", "else"]);\n  var arrays = /* @__PURE__ */ new Set(["allOf", "anyOf", "oneOf"]);\n  var annotations = new Set("$schema $ref $comment title description default examples readOnly writeOnly".split(" "));\n  var DataValidationError = class extends TypeError {\n    constructor(issues) {\n      super(issues.map((issue) => `${issue.path}: ${issue.message}`).join("; "));\n      this.code = "DATA_VALIDATION_FAILED";\n      this.issues = clone(issues);\n    }\n  };\n  var reject = (path, message) => {\n    throw new DataValidationError([{ path, message }]);\n  };\n  function safePattern(pattern, path) {\n    if (typeof pattern !== "string" || pattern.length > 256) reject(path, "pattern must be text of at most 256 characters");\n    let characterClass = false, quantifiers = 0;\n    for (let index = 0; index < pattern.length; index++) {\n      const token = pattern[index];\n      if (token === "\\\\") {\n        const next = pattern[++index];\n        if (!next || !"dDsSwW\\\\.^$[]{}()*+?|-/".includes(next)) reject(path, "unsupported pattern escape");\n        continue;\n      }\n      if (token === "[" && !characterClass) {\n        characterClass = true;\n        continue;\n      }\n      if (token === "]" && characterClass) {\n        characterClass = false;\n        continue;\n      }\n      if (characterClass) {\n        if (token === "[" || token === "&") reject(path, "nested or intersected character classes are not supported");\n        continue;\n      }\n      if ("()|".includes(token)) reject(path, "pattern groups and alternation are not supported");\n      if ("*+?".includes(token)) quantifiers++;\n      if (token === "{") {\n        const end = pattern.indexOf("}", index), repeat = pattern.slice(index + 1, end);\n        if (end < 0 || !/^\\d+(,\\d*)?$/.test(repeat) || repeat.split(",").filter(Boolean).some((n) => Number(n) > 1024)) reject(path, "pattern repeat bounds must be at most 1024");\n        quantifiers++;\n        index = end;\n      }\n      if (quantifiers > 1) reject(path, "patterns may contain at most one quantifier");\n    }\n    if (quantifiers && (!pattern.startsWith("^") || !pattern.endsWith("$") || /\\\\\\$$/.test(pattern))) reject(path, "patterns with quantifiers must be anchored with ^ and $");\n  }\n  function schemaProfile(root, path) {\n    if (!object(root)) reject(path, "schema must be a JSON object");\n    const nodes = /* @__PURE__ */ new Map(), edges = /* @__PURE__ */ new Map();\n    let patterns = 0;\n    function collect(node, pointer, depth) {\n      if (depth > 32 || nodes.size >= 4096) reject(path + pointer, "schema is too large or deeply nested");\n      if (!object(node) && typeof node !== "boolean") reject(path + pointer, "must be a schema object or boolean");\n      nodes.set(pointer, node);\n      edges.set(pointer, []);\n      if (typeof node === "boolean") return;\n      const child = (value, key) => {\n        collect(value, key, depth + 1);\n        edges.get(pointer).push(key);\n      };\n      for (const [key, value] of Object.entries(node)) {\n        if (!keywords.has(key)) reject(path + pointer + "/" + escape2(key), "unsupported schema keyword");\n        if (key === "$schema" && !["http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft-07/schema#"].includes(value)) reject(path + pointer + "/$schema", "only Draft-07 is supported; omit $schema to use Draft-07");\n        if (own(node, "$ref") && !annotations.has(key)) reject(path + pointer, "$ref cannot have assertion siblings");\n        if (key === "pattern") {\n          if (++patterns > 64) reject(path, "schema has too many patterns");\n          safePattern(value, path + pointer + "/pattern");\n        }\n        if (key === "patternProperties" && object(value)) for (const pattern of Object.keys(value)) {\n          if (++patterns > 64) reject(path, "schema has too many patterns");\n          safePattern(pattern, path + pointer + "/patternProperties/" + escape2(pattern));\n        }\n        if (maps.has(key) && object(value)) for (const [name, sub] of Object.entries(value)) {\n          if (key !== "dependencies" || !Array.isArray(sub)) child(sub, `${pointer}/${key}/${escape2(name)}`);\n        }\n        else if ((arrays.has(key) || key === "items") && Array.isArray(value)) value.forEach((sub, index) => child(sub, `${pointer}/${key}/${index}`));\n        else if (singles.has(key) || key === "items") child(value, `${pointer}/${key}`);\n      }\n    }\n    collect(root, "", 0);\n    for (const [pointer, node] of nodes) if (object(node) && own(node, "$ref")) {\n      if (typeof node.$ref !== "string" || !node.$ref.startsWith("#/")) reject(path + pointer + "/$ref", "only local JSON pointer references are supported");\n      const target2 = node.$ref.slice(1);\n      if (!nodes.has(target2)) reject(path + pointer + "/$ref", "reference must point to a declared schema");\n      edges.get(pointer).push(target2);\n    }\n    const active = /* @__PURE__ */ new Set(), done = /* @__PURE__ */ new Set();\n    function visit(pointer, depth) {\n      if (active.has(pointer) || depth > 32) reject(path + pointer, "cyclic or overly deep schema references are not supported");\n      if (done.has(pointer)) return;\n      done.add(pointer);\n      active.add(pointer);\n      edges.get(pointer).forEach((target2) => visit(target2, depth + 1));\n      active.delete(pointer);\n    }\n    visit("", 0);\n    let steps = 0;\n    function expanded(pointer) {\n      if (++steps > 16384) reject(path, "expanded schema exceeds its complexity budget");\n      for (const target2 of edges.get(pointer)) expanded(target2);\n    }\n    expanded("");\n    for (const node of nodes.values()) if (object(node) && own(node, "$schema")) node.$schema = "http://json-schema.org/draft-07/schema#";\n  }\n  var target = { type: "object", required: ["id"], properties: { id: { type: "string", minLength: 1 }, number: { type: "integer", minimum: 1 }, locked: { type: "boolean" }, gradable: { type: "boolean" }, label: { type: "string" } } };\n  var targets = { type: "array", items: target };\n  var errors = { type: "array", items: { type: "string" } };\n  var maximum = { type: "number", exclusiveMinimum: 0 };\n  var outputSchemas = {\n    validate: { type: "object", required: ["errors"], properties: { errors } },\n    validateAnswer: { type: "object", required: ["errors", "empty"], properties: { errors, empty: { type: "boolean" } } },\n    targets: { type: "object", required: ["targets"], properties: { targets } },\n    snapshot: { type: "object", properties: { targets, maxScore: maximum } },\n    grade: { type: "object", required: ["status", "score"], properties: { status: { enum: ["CORRECT", "INCORRECT", "UNSCORED"] }, score: { type: ["number", "null"] }, maxScore: maximum } }\n  };\n  var ajv = new import_ajv.default({ allErrors: true, strict: false, validateFormats: false, coerceTypes: false, useDefaults: false, removeAdditional: false, ownProperties: true });\n  function requireValid(validate, value, path) {\n    if (validate(value)) return;\n    throw new DataValidationError(validate.errors.slice(0, 32).map((error) => ({ path: path + error.instancePath + (error.keyword === "required" ? "/" + escape2(error.params.missingProperty) : error.keyword === "additionalProperties" ? "/" + escape2(error.params.additionalProperty) : ""), message: error.message })));\n  }\n  var outputs = Object.fromEntries(Object.entries(outputSchemas).map(([key, schema]) => [key, ajv.compile(schema)]));\n  function compileDataValidation(type, asset) {\n    function compile(source, path) {\n      try {\n        if ((typeof source === "string" ? source : JSON.stringify(source)).length > 256 * 1024) reject(path, "schema exceeds 256 KiB character limit");\n        const schema = typeof source === "string" ? JSON.parse(source) : clone(source);\n        schemaProfile(schema, path);\n        return ajv.compile(schema);\n      } catch (error) {\n        if (error instanceof DataValidationError) throw error;\n        reject(path, `invalid schema: ${error.message}`);\n      }\n    }\n    const questionSchema = compile(asset.questionSchemaSource, "/schemas/question"), answerSchema = compile(asset.answerSchemaSource, "/schemas/answer");\n    function question(value) {\n      if (!object(value)) reject("/question", "must be an object");\n      validateArguments([value]);\n      for (const key of ["id", "type"]) if (typeof value[key] !== "string" || !value[key].trim()) reject("/question/" + key, "must be nonempty text");\n      if (value.type !== type.id) reject("/question/type", "does not match the registered type");\n      for (const key of ["prompt", "payload", "answerSpec", "scoreSpec"]) if (!object(value[key])) reject("/question/" + key, "must be an object");\n      if (!Number.isFinite(value.scoreSpec.defaultMaxScore) || value.scoreSpec.defaultMaxScore <= 0) reject("/question/scoreSpec/defaultMaxScore", "must be positive and finite");\n      requireValid(questionSchema, value, "/question");\n    }\n    function answer(value) {\n      if (!object(value)) reject("/answer", "must be an object");\n      validateArguments([value]);\n      if (Object.keys(value).length) requireValid(answerSchema, value, "/answer");\n    }\n    function checkTargets(value, path) {\n      const ids = /* @__PURE__ */ new Set(), numbers = /* @__PURE__ */ new Set();\n      value.forEach((item, index) => {\n        if (!item.id.trim() || ids.has(item.id)) reject(`${path}/${index}/id`, "must be nonempty and unique");\n        ids.add(item.id);\n        const number = item.number ?? index + 1;\n        if (numbers.has(number)) reject(`${path}/${index}/number`, "must be unique");\n        numbers.add(number);\n      });\n    }\n    function input(operation, value) {\n      validateArguments([value]);\n      if (value.question !== void 0) question(value.question);\n      if (["validateAnswer", "grade"].includes(operation)) answer(value.answer);\n      if (operation === "grade" && !Object.keys(value.answer).length) reject("/answer", "write an answer first");\n    }\n    function output(operation, value, input2) {\n      validateArguments([value]);\n      if (["createDraft", "duplicate"].includes(operation)) {\n        question(value);\n        return;\n      }\n      if (!outputs[operation]) reject("/rules", "unsupported operation");\n      requireValid(outputs[operation], value, "/rules/" + operation);\n      if (value.targets) checkTargets(value.targets, "/rules/" + operation + "/targets");\n      if (operation === "grade") {\n        const max = input2.maxScore;\n        if (!Number.isFinite(max) || max <= 0) reject("/rules/grade/maxScore", "requires a frozen positive maximum");\n        if (own(value, "maxScore") && value.maxScore !== max) reject("/rules/grade/maxScore", "cannot change the frozen maximum score");\n        if (value.status === "UNSCORED") {\n          if (value.score !== null) reject("/rules/grade/score", "an unscored result requires null");\n        } else if (!Number.isFinite(value.score) || value.score < 0 || value.score > max) reject("/rules/grade/score", "is outside the allowed range");\n        else if (value.status === "CORRECT" !== (value.score === max)) reject("/rules/grade/score", "full credit requires CORRECT status");\n      }\n    }\n    return Object.freeze({ question, answer, input, output });\n  }\n\n  // src/extensions/schema-worker.js\n  var validator = null;\n  self.onmessage = ({ data: message }) => {\n    let value, error;\n    try {\n      if (message.operation === "compile") validator = compileDataValidation(message.type, message.asset);\n      else {\n        if (!validator || !["question", "answer", "input", "output"].includes(message.operation)) throw new TypeError("Invalid validation operation");\n        validator[message.operation](...message.args);\n      }\n      value = true;\n    } catch (failure) {\n      error = { code: failure.code || "DATA_VALIDATION_FAILED", message: failure.message, issues: failure.issues };\n    }\n    self.postMessage({ id: message.id, value, error });\n  };\n})();\n'], { type: "text/javascript" }));
    try {
      return new Worker(url);
    } finally {
      URL.revokeObjectURL(url);
    }
  }
  function createWorkerValidation(type, asset, { workerFactory = createBrowserWorker, timeoutMs = 1500, compileTimeoutMs = 5e3, maxPending = 16 } = {}) {
    let worker, stopped = false, sequence3 = 0, users = 0, retired = false;
    const pending = /* @__PURE__ */ new Map();
    function stop(error = failure("schema validator is closed")) {
      if (stopped) return;
      stopped = true;
      worker?.terminate();
      validators.delete(api);
      for (const entry of pending.values()) {
        clearTimeout(entry.timer);
        entry.reject(error);
      }
      pending.clear();
    }
    function request(operation, args = [], extra = {}) {
      if (stopped) return Promise.reject(failure("schema validator is closed"));
      if (pending.size >= maxPending) return Promise.reject(failure("too many pending schema validations"));
      const id = ++sequence3;
      return new Promise((resolve, reject2) => {
        const timer = setTimeout(() => stop(failure("schema validation exceeded its time budget")), operation === "compile" ? compileTimeoutMs : timeoutMs);
        pending.set(id, { resolve, reject: reject2, timer });
        try {
          worker.postMessage({ id, operation, args, ...extra });
        } catch (error) {
          stop(failure("schema validation transport failed: " + error.message));
        }
      });
    }
    const api = {
      ready: null,
      destroy: stop,
      retain() {
        if (stopped) throw failure("schema validator is closed");
        users++;
      },
      release() {
        users = Math.max(0, users - 1);
        if (retired && !users) stop();
      },
      retire() {
        retired = true;
        if (!users) stop();
      }
    };
    try {
      worker = workerFactory();
    } catch (error) {
      stopped = true;
      api.ready = Promise.reject(error);
      api.ready.catch(() => {
      });
    }
    if (worker) {
      worker.onmessage = ({ data: message }) => {
        const entry = pending.get(message?.id);
        if (!entry) return;
        clearTimeout(entry.timer);
        pending.delete(message.id);
        if (message.error) entry.reject(new DataValidationError(message.error.issues || [{ path: "/schemas", message: message.error.message }]));
        else entry.resolve(message.value);
      };
      worker.onerror = (event) => {
        event.preventDefault?.();
        stop(failure("schema validation worker failed"));
      };
      worker.onmessageerror = () => stop(failure("schema validation worker response is invalid"));
      validators.add(api);
      api.ready = request("compile", [], { type: { id: type.id }, asset: { questionSchemaSource: asset.questionSchemaSource, answerSchemaSource: asset.answerSchemaSource } });
      api.ready.catch((error) => stop(error));
    }
    for (const operation of ["question", "answer", "input", "output"]) api[operation] = async (...args) => {
      await api.ready;
      return request(operation, args);
    };
    return Object.freeze(api);
  }
  if (typeof window !== "undefined") window.addEventListener("pagehide", () => {
    for (const validator of [...validators]) validator.destroy();
  });

  // src/extensions/data-validation.js
  function compileDataValidation2(type, asset) {
    return typeof window === "undefined" ? compileDataValidation(type, asset) : createWorkerValidation(type, asset);
  }
  var then = (value, action) => value?.then ? value.then(action) : action(value);
  function checkedRules(registry, validators2) {
    return Object.freeze({
      destroy() {
        registry.destroy?.();
        for (const validator of validators2.values()) validator.destroy?.();
      },
      retire() {
        for (const validator of validators2.values()) validator.retire?.();
      },
      invoke(type, operation, encoded) {
        const validator = validators2.get(type);
        if (!validator) throw new DataValidationError([{ path: "/question/type", message: "type is not registered" }]);
        const input = JSON.parse(encoded);
        return then(validator.input(operation, input), () => {
          if (operation === "validateAnswer" && !Object.keys(input.answer).length) return JSON.stringify({ errors: [], empty: true });
          return then(registry.invoke(type, operation, encoded), (response) => {
            const value = JSON.parse(response);
            return then(validator.output(operation, value, input), () => JSON.stringify(value));
          });
        });
      }
    });
  }

  // src/extensions/html-ui.js
  var clone2 = (value) => value == null ? value : JSON.parse(JSON.stringify(value));
  var ok = (data) => ({ ok: true, data: clone2(data) });
  var failure2 = (code, message) => ({ ok: false, error: { code, message, retryable: false } });
  var operationFailure = (error, fallback) => error.code === "DATA_VALIDATION_FAILED" ? validationFailure(error) : failure2(
    ["EXTENSION_TIMEOUT", "EXTENSION_FAILED", "EXTENSION_UNAVAILABLE"].includes(error.code) ? error.code : fallback,
    error.message
  );
  function validatePageHtml(html) {
    if (typeof html !== "string" || !html.trim()) throw new TypeError("HTML page is required");
    if (/<\s*(script|iframe|frame|object|embed|base|link|meta)\b/i.test(html) || /\s(?:on\w+|src|srcset|href|action|formaction)\s*=/i.test(html))
      throw new TypeError("HTML may not embed scripts or external resources; use manifest assets");
    return html;
  }
  function mountPage(root2, html, styles) {
    validatePageHtml(html);
    const page = element("section", "qf-extension-page");
    page.__qfAssets = { html, styles };
    root2.append(page);
    return page;
  }
  function optionIds(question) {
    const ids = /* @__PURE__ */ new Set();
    function visit(node) {
      if (Array.isArray(node)) {
        node.forEach(visit);
        return;
      }
      if (!node || typeof node !== "object") return;
      if (typeof node.id === "string" && node.id.startsWith("opt_")) ids.add(node.id);
      Object.values(node).forEach(visit);
    }
    visit(question.payload);
    return ids;
  }
  function parseHtmlPresentation(q) {
    const p = q.presentation;
    if (!p || typeof p.extensionId !== "string" || !p.question || typeof p.question.payload !== "object" || !p.answer || typeof p.answer !== "object" || Array.isArray(p.answer))
      throw new TypeError("Missing HTML extension presentation");
    if (q.state !== "SUBMITTED" && p.reference != null) throw new TypeError("Unsubmitted question exposes its answer");
    const available = optionIds(p.question);
    const selected = p.answer.selectedOptionIds || q.selectedOptionIds || [];
    if (!Array.isArray(selected) || new Set(selected).size !== selected.length || selected.some((id) => !available.has(id))) throw new TypeError("Invalid selected option IDs");
    return { presentation: clone2(p), options: [], available, selectedOptionIds: [...selected] };
  }
  function pageRuntime(page, source, { editor, initial, caps, root: root2, policy, validation, initialValidation }) {
    let current = clone2(initial), draft = editor ? clone2(initial) : null, destroyed = false;
    let readOnly = !editor && caps.mode === RendererMode.READ_ONLY_HISTORY, interaction = "INTERACT";
    let presentationSuspended = false, presentationWritable = false;
    const listeners = [], subscriptions = /* @__PURE__ */ new Set(), pending = /* @__PURE__ */ new Set(), disposers = [];
    let lastError = null, initializationFailed = false, frameHost = null;
    validation.retain?.();
    let preferences = defaultUi(editor ? "EDITOR" : "PRACTICE");
    let layout = defaultLayout(editor ? "EDITOR" : "PRACTICE"), pageReady = false;
    const layoutHost = caps.layoutHost || createDomLayout(
      caps.layoutRoot || (editor ? root2.closest("#qf-editor-shell") : null) || root2,
      { mode: editor ? "EDITOR" : "PRACTICE", getHeight: caps.layoutHeight }
    );
    if (!caps.layoutHost) disposers.push(() => layoutHost.destroy());
    function publishLayout() {
      if (!destroyed) layoutHost.configure(layout, { ready: pageReady });
    }
    publishLayout();
    function publishUi() {
      if (destroyed) return;
      caps.uiChanged?.(preferences);
      if (editor) caps.configureUi?.(preferences);
    }
    const mode = () => editor ? "EDITOR" : caps.preview ? "PREVIEW" : readOnly ? "HISTORY" : "PRACTICE";
    const writable = () => !destroyed && !readOnly && interaction === "INTERACT" && current.state !== "SUBMITTED" && (caps.canInteract?.() ?? true);
    function track(promise) {
      const value = Promise.resolve(promise);
      pending.add(value);
      value.catch((error) => {
        if (error.code !== "DATA_VALIDATION_FAILED") lastError = error;
        showError(error.message);
      }).finally(() => pending.delete(value));
      return value;
    }
    function showError(message) {
      let node = page.querySelector("[data-qf-error]");
      if (!node) {
        node = element("p", "practice-error");
        node.dataset.qfError = "";
        page.append(node);
      }
      node.textContent = message || "";
      node.hidden = !message;
    }
    function notify() {
      if (!destroyed && !presentationSuspended) frameHost?.state();
    }
    disposers.push(policy.subscribe(notify));
    if (caps.subscribeHost) disposers.push(caps.subscribeHost(notify));
    const dom = Object.freeze({
      root: page,
      $(selector) {
        return page.querySelector(selector);
      },
      on(node, event, listener) {
        node.addEventListener(event, listener);
        listeners.push([node, event, listener]);
        return () => node.removeEventListener(event, listener);
      }
    });
    const questionData = () => editor ? draft : current.presentation.question;
    function resultData() {
      if (!current.result) return null;
      return { ...clone2(current.result), reference: clone2(current.presentation.reference) };
    }
    async function shellCommand(action, argument) {
      if (!editor || destroyed) return failure2("CAPABILITY_DENIED", "\u6B64\u9875\u4E0D\u80FD\u64CD\u4F5C\u9898\u5E93\u7F16\u8F91\u4F1A\u8BDD");
      if (!caps.shellCommand) return failure2("CAPABILITY_UNAVAILABLE", "\u6B64\u5BBF\u4E3B\u672A\u63D0\u4F9B\u9898\u5E93\u7F16\u8F91\u64CD\u4F5C");
      const reply = await caps.shellCommand(action, argument);
      if (!destroyed) notify();
      return reply;
    }
    async function pageState() {
      if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
      if (editor) return caps.shellState ? ok(caps.shellState()) : failure2("CAPABILITY_UNAVAILABLE", "\u6B64\u5BBF\u4E3B\u672A\u63D0\u4F9B\u9898\u5E93\u4F1A\u8BDD");
      return caps.pageState ? caps.pageState() : failure2("CAPABILITY_UNAVAILABLE", "\u6B64\u5BBF\u4E3B\u672A\u63D0\u4F9B\u9875\u9762\u4F1A\u8BDD");
    }
    async function pageCommand(action, argument) {
      if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
      if (editor) return action === "learning.mode" ? failure2("CAPABILITY_DENIED", "\u7F16\u8F91\u9875\u4E0D\u80FD\u5207\u6362\u5B66\u4E60\u6A21\u5F0F") : shellCommand(action, argument);
      if (!caps.pageCommand) return failure2("CAPABILITY_UNAVAILABLE", "\u6B64\u5BBF\u4E3B\u672A\u63D0\u4F9B\u9875\u9762\u64CD\u4F5C");
      const reply = await caps.pageCommand(action, argument);
      if (!destroyed) notify();
      return reply;
    }
    async function boardCommand(action, argument) {
      if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
      if (editor || !caps.boardCommand) return failure2("CAPABILITY_UNAVAILABLE", "\u6B64\u9875\u6CA1\u6709\u767D\u677F");
      try {
        return ok(await caps.boardCommand(action, argument));
      } catch (error) {
        return failure2(error.code || "WHITEBOARD_FAILED", error.message);
      }
    }
    const QF = Object.freeze({
      dom,
      host: Object.freeze({
        async getContext() {
          const available = (await pageState()).ok;
          const presentedWritable = presentationSuspended ? presentationWritable && !destroyed && !readOnly && current.state !== "SUBMITTED" : writable();
          return ok({
            mode: mode(),
            state: current.state || null,
            sdk: hostSdk,
            permissions: { declared: policy.declared, granted: policy.granted },
            capabilities: {
              editQuestion: editor && policy.can("editor.update"),
              editAnswer: !editor && presentedWritable && policy.can("answer.update"),
              submit: !editor && presentedWritable && Boolean(caps.requestSubmit) && policy.can("practice.submit"),
              retry: !editor && !readOnly && current.state === "SUBMITTED" && Boolean(caps.requestRetry) && policy.can("practice.retry"),
              ai: false,
              saveBank: editor && available && policy.can("bank.save"),
              navigate: available && policy.can("navigation.goTo"),
              manageSources: editor && available && policy.can("sources.add"),
              viewSources: available && policy.can("sources.open"),
              whiteboard: !editor && Boolean(caps.boardState) && policy.granted.some((name) => name.startsWith("whiteboard.")),
              changeLearningMode: !editor && available && policy.can("learning.setMode"),
              layout: true
            }
          });
        },
        subscribe(listener) {
          subscriptions.add(listener);
          return () => subscriptions.delete(listener);
        }
      }),
      ids: Object.freeze({ create(prefix = "opt_") {
        return caps.newId?.(prefix) || prefix + (globalThis.crypto?.randomUUID?.() || Math.random().toString(36).slice(2)).replace(/-/g, "_");
      } }),
      editor: Object.freeze({
        async getData() {
          return editor ? ok(draft) : failure2("CAPABILITY_DENIED", "\u6B64\u9875\u4E0D\u80FD\u7F16\u8F91\u9898\u76EE");
        },
        async update(patch) {
          if (!editor || destroyed) return failure2("CAPABILITY_DENIED", "\u6B64\u9875\u4E0D\u80FD\u7F16\u8F91\u9898\u76EE");
          if (!patch || typeof patch !== "object" || Array.isArray(patch)) return failure2("INVALID_DATA", "\u9898\u76EE\u66F4\u65B0\u5FC5\u987B\u662F\u5BF9\u8C61");
          if (Object.prototype.hasOwnProperty.call(patch, "id") && patch.id !== draft.id || Object.prototype.hasOwnProperty.call(patch, "type") && patch.type !== draft.type) return failure2("INVALID_DATA", "\u4E0D\u80FD\u66F4\u6539\u9898\u76EE\u8EAB\u4EFD");
          const candidate = { ...draft, ...clone2(patch) };
          try {
            await validation.question(candidate);
            if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
            await caps.changed?.(clone2(candidate));
            draft = candidate;
            return ok(draft);
          } catch (error) {
            return operationFailure(error, "EDITOR_UPDATE_FAILED");
          }
        },
        async save() {
          if (!editor) return failure2("CAPABILITY_DENIED", "\u6B64\u9875\u4E0D\u80FD\u4FDD\u5B58\u9898\u76EE");
          await flush();
          await caps.changed?.(clone2(draft));
          return ok({ savedToDraft: true });
        }
      }),
      bank: Object.freeze({
        getState: () => editor ? pageState() : Promise.resolve(failure2("CAPABILITY_DENIED", "\u6B64\u9875\u4E0D\u80FD\u7F16\u8F91\u9898\u5E93")),
        save: () => shellCommand("save"),
        addQuestion: (type) => shellCommand("add", type),
        duplicateQuestion: () => shellCommand("duplicate"),
        deleteQuestion: () => shellCommand("delete")
      }),
      navigation: Object.freeze({
        getState: pageState,
        goTo: (index) => pageCommand("navigate", index),
        async previous() {
          const state = await pageState();
          return state.ok ? pageCommand("navigate", state.data.index - 1) : state;
        },
        async next() {
          const state = await pageState();
          return state.ok ? pageCommand("navigate", state.data.index + 1) : state;
        }
      }),
      sources: Object.freeze({
        async list() {
          const state = await pageState();
          return state.ok ? ok(state.data.sources) : state;
        },
        add: (link) => shellCommand("source.add", link),
        remove: (index) => shellCommand("source.remove", index),
        open: (index) => pageCommand("source.open", index)
      }),
      learning: Object.freeze({
        async getMode() {
          const state = await pageState();
          return state.ok ? ok(state.data.learningMode || "EDITOR") : state;
        },
        setMode: (mode2) => pageCommand("learning.mode", mode2),
        async toggleMode() {
          const state = await pageState();
          return state.ok ? pageCommand("learning.mode", state.data.learningMode === "DRAFT" ? "PRACTICE" : "DRAFT") : state;
        }
      }),
      whiteboard: Object.freeze({
        async getState() {
          if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
          return !editor && caps.boardState ? ok(caps.boardState()) : failure2("CAPABILITY_UNAVAILABLE", "\u6B64\u9875\u6CA1\u6709\u767D\u677F");
        },
        setTool: (tool) => boardCommand("tool", tool),
        undo: () => boardCommand("undo"),
        redo: () => boardCommand("redo"),
        clear: () => boardCommand("clear"),
        setAppearance: (patch) => boardCommand("paper", patch),
        setZoom: (value) => boardCommand("zoom", value),
        zoomBy: (factor) => boardCommand("zoomBy", factor)
      }),
      practice: Object.freeze({
        async getState() {
          return editor ? failure2("CAPABILITY_DENIED", "\u7F16\u8F91\u9875\u6CA1\u6709\u7EC3\u4E60\u72B6\u6001") : ok({ index: current.index, total: current.total, type: current.type, state: current.state, maxScore: current.maxScore, result: resultData() });
        },
        async getQuestion() {
          return editor ? failure2("CAPABILITY_DENIED", "\u7F16\u8F91\u9875\u8BF7\u8BFB\u53D6\u9898\u76EE\u8349\u7A3F") : ok(questionData());
        },
        async getResult() {
          return editor ? failure2("CAPABILITY_DENIED", "\u7F16\u8F91\u9875\u6CA1\u6709\u7EC3\u4E60\u7ED3\u679C") : ok(resultData());
        },
        async submit() {
          if (editor || !writable()) return failure2("READ_ONLY", "\u5F53\u524D\u72B6\u6001\u4E0D\u53EF\u63D0\u4EA4");
          if (!caps.requestSubmit) return failure2("CAPABILITY_UNAVAILABLE", "\u6B64\u5BBF\u4E3B\u63D0\u4F9B\u72EC\u7ACB\u7684\u63D0\u4EA4\u6309\u94AE");
          try {
            const receipt = await caps.requestSubmit();
            return ok({ confirmationRequired: receipt?.confirmationRequired === true, result: resultData() });
          } catch (error) {
            return operationFailure(error, "SUBMIT_FAILED");
          }
        },
        async retry() {
          if (editor || readOnly || current.state !== "SUBMITTED" || !(caps.canInteract?.() ?? true)) return failure2("READ_ONLY", "\u5F53\u524D\u72B6\u6001\u4E0D\u53EF\u91CD\u8BD5");
          if (!caps.requestRetry) return failure2("CAPABILITY_UNAVAILABLE", "\u6B64\u5BBF\u4E3B\u63D0\u4F9B\u72EC\u7ACB\u7684\u91CD\u8BD5\u6309\u94AE");
          try {
            await caps.requestRetry();
            return ok(null);
          } catch (error) {
            return failure2("RETRY_FAILED", error.message);
          }
        }
      }),
      answer: Object.freeze({
        async get() {
          return editor ? failure2("CAPABILITY_DENIED", "\u7F16\u8F91\u9875\u6CA1\u6709\u7528\u6237\u4F5C\u7B54") : ok(current.presentation.answer);
        },
        async update(answer) {
          if (editor || !writable()) return failure2("READ_ONLY", "\u6B64\u9875\u6216\u5F53\u524D\u72B6\u6001\u4E0D\u53EF\u4F5C\u7B54");
          try {
            await validation.answer(answer);
          } catch (error) {
            return validationFailure(error);
          }
          if (!writable()) return failure2("READ_ONLY", "\u6B64\u9875\u6216\u5F53\u524D\u72B6\u6001\u4E0D\u53EF\u4F5C\u7B54");
          try {
            await track(caps.answerChanged({ answer: clone2(answer), ...answer.selectedOptionIds ? { selectedOptionIds: clone2(answer.selectedOptionIds) } : {} }));
            lastError = null;
            showError("");
            return ok(current.presentation.answer);
          } catch (error) {
            return operationFailure(error, "ANSWER_SAVE_FAILED");
          }
        },
        async flush() {
          await flush();
          return ok(null);
        }
      }),
      content: Object.freeze({
        async resolve(content) {
          if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
          if (!content || !["TEXT", "RICH", "DOCUMENT"].includes(content.kind)) return failure2("INVALID_DATA", "\u65E0\u6548\u5BCC\u6587\u672C\u5185\u5BB9");
          return ok(await caps.resolveContent?.(content) || content);
        },
        async edit(content) {
          if (!editor || destroyed) return failure2("CAPABILITY_DENIED", "\u6B64\u9875\u4E0D\u80FD\u7F16\u8F91\u5BCC\u6587\u672C");
          if (!content || !["TEXT", "RICH", "DOCUMENT"].includes(content.kind)) return failure2("INVALID_DATA", "\u65E0\u6548\u5BCC\u6587\u672C\u5185\u5BB9");
          return ok(await caps.editContent?.(clone2(content)) ?? content);
        }
      }),
      layout: Object.freeze({
        configure(patch) {
          if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
          try {
            const next = configureLayout(layout, patch);
            layoutHost.configure(next, { ready: pageReady });
            layout = next;
            return ok(layout);
          } catch (error) {
            return failure2("INVALID_LAYOUT", error.message);
          }
        },
        getConfiguration() {
          return destroyed ? failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED") : ok(layout);
        },
        getState() {
          return destroyed ? failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED") : ok(layoutHost.getState());
        }
      }),
      ui: Object.freeze({
        configure(patch) {
          if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
          try {
            preferences = configureUi(preferences, patch, editor ? "EDITOR" : "PRACTICE");
            publishUi();
            return ok(preferences);
          } catch (error) {
            return failure2("INVALID_UI", error.message);
          }
        },
        getConfiguration() {
          return ok(preferences);
        },
        mountControls(node) {
          if (!page.contains(node)) throw new TypeError("Controls must belong to this type page");
          node.dataset.qfHostControls = "";
        },
        mountActions(node) {
          node.dataset.qfActions = "";
        },
        notify(message) {
          showError(message);
        }
      })
    });
    let resolveReady, rejectReady;
    const ready = new Promise((resolve, reject2) => {
      resolveReady = resolve;
      rejectReady = reject2;
    });
    ready.catch(() => {
    });
    const names = ["host.getContext", "editor.getData", "editor.update", "editor.save", "bank.getState", "bank.save", "bank.addQuestion", "bank.duplicateQuestion", "bank.deleteQuestion", "navigation.getState", "navigation.goTo", "navigation.previous", "navigation.next", "sources.list", "sources.add", "sources.remove", "sources.open", "learning.getMode", "learning.setMode", "learning.toggleMode", "whiteboard.getState", "whiteboard.setTool", "whiteboard.undo", "whiteboard.redo", "whiteboard.clear", "whiteboard.setAppearance", "whiteboard.setZoom", "whiteboard.zoomBy", "practice.getState", "practice.getQuestion", "practice.getResult", "practice.submit", "practice.retry", "answer.get", "answer.update", "answer.flush", "content.resolve", "content.edit", "ui.configure", "layout.configure"];
    const methods = new Map(names.map((name) => {
      const [group, method] = name.split(".");
      return [name, QF[group][method]];
    }));
    const bytes = new Uint32Array(4);
    crypto.getRandomValues(bytes);
    const pageAssets = page.__qfAssets;
    const startFrame = () => connectFrame(page, source, {
      ...pageAssets,
      boot: { session: Array.from(bytes, (n) => n.toString(16)).join("-"), mode: editor ? "EDITOR" : "PRACTICE", questionId: initial.id || initial.questionId, ui: preferences, layout, layoutState: layoutHost.getState(), relayWheel: Boolean(caps.boardState) },
      invoke(name, args) {
        if (destroyed) return failure2("PAGE_CLOSED", "\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED");
        if (caps.isCurrent && !caps.isCurrent()) return failure2("PAGE_CLOSED", "\u9898\u76EE\u5DF2\u5207\u6362");
        if (caps.isReady && !caps.isReady() && ["answer.update", "practice.submit", "practice.retry", "editor.update", "editor.save", "bank.save", "bank.addQuestion", "bank.duplicateQuestion", "bank.deleteQuestion", "navigation.goTo", "navigation.previous", "navigation.next", "sources.add", "sources.remove", "sources.open", "content.edit"].includes(name))
          return failure2("CAPABILITY_UNAVAILABLE", "\u9898\u5361\u6B63\u5728\u51C6\u5907");
        const method = methods.get(name);
        if (!method) return failure2("UNKNOWN_METHOD", "\u672A\u5F00\u653E\u7684\u9898\u578B\u63A5\u53E3");
        try {
          validateMethodArguments(name, args);
        } catch (error) {
          return failure2("INVALID_ARGUMENT", error.message);
        }
        if (!policy.can(name)) return failure2("PERMISSION_DENIED", "\u62D3\u5C55\u672A\u58F0\u660E\u6216\u672A\u83B7\u6388\u6743\u4F7F\u7528\u8BE5\u63A5\u53E3\uFF1A" + name);
        return method(...args);
      },
      getState: () => ({ layoutState: layoutHost.getState(), interaction }),
      interaction: () => interaction,
      onReady() {
        if (destroyed) return;
        pageReady = true;
        page.dataset.qfReady = "true";
        publishLayout();
        publishUi();
        resolveReady();
        notify();
      },
      onFailure(error) {
        if (destroyed) return;
        initializationFailed = true;
        lastError = error;
        showError(error.message);
        rejectReady(error);
        if (caps.reloadPage && !page.querySelector("[data-qf-reload]")) {
          const button = element("button", "qf-page-reload", "\u91CD\u65B0\u52A0\u8F7D\u9898\u5361");
          button.type = "button";
          button.dataset.qfReload = "";
          page.append(button);
          dom.on(button, "click", async () => {
            button.disabled = true;
            try {
              await caps.reloadPage();
            } catch (failure3) {
              showError(failure3.message);
              button.disabled = false;
            }
          });
        }
      }
    });
    if (initialValidation?.then) initialValidation.then(() => {
      if (!destroyed) frameHost = startFrame();
    }).catch((error) => {
      if (!destroyed) {
        initializationFailed = true;
        lastError = error;
        showError(error.message);
        rejectReady(error);
      }
    });
    else frameHost = startFrame();
    delete page.__qfAssets;
    async function flush() {
      await ready;
      await frameHost.flush();
      while (pending.size) await Promise.all([...pending]);
      if (lastError) throw lastError;
    }
    return {
      ready,
      getDraft: () => clone2(draft),
      getUiPreferences: () => preferences,
      hasInitializationError: () => initializationFailed,
      flush,
      update(next) {
        current = clone2(next);
        notify();
      },
      setReadOnly(value) {
        if (readOnly && !value) throw new TypeError("Read-only capability cannot be upgraded");
        if (readOnly !== value) {
          readOnly = value;
          notify();
        }
      },
      setInteractionMode(value) {
        if (interaction !== value) {
          interaction = value;
          notify();
        }
      },
      suspendPresentation() {
        if (!presentationSuspended) {
          presentationWritable = writable();
          presentationSuspended = true;
        }
      },
      resumePresentation() {
        if (presentationSuspended) {
          presentationSuspended = false;
          notify();
        }
      },
      getAnswerIntent: () => ({ answer: clone2(current.presentation?.answer || {}), ...current.presentation?.answer?.selectedOptionIds ? { selectedOptionIds: clone2(current.presentation.answer.selectedOptionIds) } : {} }),
      hasAnswer() {
        return Object.values(current.presentation?.answer || {}).some((value) => Array.isArray(value) ? value.length > 0 : value != null && value !== "" && (typeof value !== "object" || Object.keys(value).length > 0));
      },
      flushAnswer: flush,
      renderResult() {
        const node = element("section");
        node.hidden = true;
        return node;
      },
      focusTarget(id) {
        return frameHost?.focus(id) || page;
      },
      destroy() {
        if (destroyed) return;
        destroyed = true;
        const completion = frameHost?.destroy() || Promise.resolve();
        rejectReady(new Error("\u9898\u578B\u9875\u9762\u5DF2\u5173\u95ED"));
        subscriptions.clear();
        disposers.forEach((dispose) => dispose());
        listeners.forEach(([node, event, fn]) => node.removeEventListener(event, fn));
        return completion.then(() => {
          validation.release?.();
          const retired = page.closest("[data-qf-retired-root]");
          page.remove();
          if (retired && !retired.querySelector(".qf-type-frame")) retired.remove();
        });
      }
    };
  }
  function createHtmlRenderer(type, asset, granted = [], policy = createPermissionPolicy(type.permissions, granted), validation = compileDataValidation2(type, asset)) {
    validatePageHtml(asset.rendererHtml);
    return {
      id: `html.${type.id}.v2`,
      questionType: type.id,
      label: type.label,
      selectionMode: "EXTENSION",
      parse: parseHtmlPresentation,
      validateQuestion: validation.question,
      mount(root2, question, caps) {
        const initialValidation = validation.answer(question.presentation.answer);
        return pageRuntime(mountPage(root2, asset.rendererHtml, asset.stylesSource), asset.rendererSource, { editor: false, initial: question, caps, root: root2, policy, validation, initialValidation });
      }
    };
  }
  function createHtmlEditor(type, asset, granted = [], policy = createPermissionPolicy(type.permissions, granted), validation = compileDataValidation2(type, asset)) {
    validatePageHtml(asset.editorHtml);
    return { questionType: type.id, mount(root2, question, caps) {
      const initialValidation = validation.question(question);
      return pageRuntime(mountPage(root2, asset.editorHtml, asset.stylesSource), asset.editorSource, { editor: true, initial: question, caps, root: root2, policy, validation, initialValidation });
    } };
  }

  // src/extensions/isolated-rules.js
  function isolatedRules(bundle) {
    if (window.nativeRulesHost) return nativeRules(window.nativeRulesHost);
    let frame2 = null, session = null, ready = null, seq = 0, closed = false;
    const requests2 = /* @__PURE__ */ new Map();
    function receive(event) {
      const m = event.data;
      if (closed || event.source !== frame2?.contentWindow || m?.channel !== "qf-rules-frame" || m.session !== session) return;
      const entry = requests2.get(m.id);
      if (entry) {
        clearTimeout(entry.timeout);
        requests2.delete(m.id);
        m.error ? entry.reject(new Error(m.error)) : entry.resolve(m.value);
      }
    }
    function request(method, args) {
      const id = String(++seq);
      return new Promise((resolve, reject2) => {
        const timeout = setTimeout(() => {
          requests2.delete(id);
          reject2(new Error("\u9898\u578B\u89C4\u5219\u6267\u884C\u8D85\u65F6"));
        }, 15e3);
        requests2.set(id, { resolve, reject: reject2, timeout });
        frame2.contentWindow.postMessage({ channel: "qf-rules-frame", session, id, method, args }, "*");
      });
    }
    function start() {
      if (ready) return ready;
      const bytes = new Uint32Array(4);
      crypto.getRandomValues(bytes);
      session = Array.from(bytes, (n) => n.toString(16)).join("-");
      frame2 = document.createElement("iframe");
      frame2.hidden = true;
      frame2.setAttribute("sandbox", "allow-scripts");
      frame2.title = "\u9898\u578B\u6D4B\u8BD5\u89C4\u5219";
      document.body.append(frame2);
      window.addEventListener("message", receive);
      const init = `(${globalThis.QuestionRules.runtimeSource})(window);const registry=window.QuestionRules;const session=${JSON.stringify(session)};` + bundle.assets.map((asset) => `registry.installDefaultQuestion(${JSON.stringify(asset.typeId)},${JSON.stringify(asset.defaultQuestion)});((QF)=>{
${asset.rulesSource}
})({defineQuestionType:d=>registry.defineQuestionType(d)});`).join("\n") + `window.addEventListener('message',event=>{const m=event.data;if(event.source!==parent||m?.channel!=='qf-rules-frame'||m.session!==session)return;let value,error;try{if(m.method==='invoke')value=registry.invoke(...m.args);else if(m.method==='ready')value=true;else throw Error('\u672A\u77E5\u89C4\u5219\u63A5\u53E3');}catch(e){error=e.message;}parent.postMessage({channel:'qf-rules-frame',session,id:m.id,value,error},'*');});`;
      frame2.srcdoc = `<!doctype html><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-qf-isolated-page-v1'; connect-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'"><meta http-equiv="Content-Security-Policy" content="script-src 'unsafe-inline'"><script nonce="qf-isolated-page-v1">${init.replace(/<\/script/gi, "<\\/script")}<\/script>`;
      ready = new Promise((resolve, reject2) => {
        frame2.addEventListener("load", () => request("ready", []).then(resolve, reject2), { once: true });
      });
      return ready;
    }
    return Object.freeze({ async invoke(...args) {
      if (closed) throw Error("\u89C4\u5219\u9875\u9762\u5DF2\u5173\u95ED");
      await start();
      return request("invoke", args);
    }, destroy() {
      closed = true;
      window.removeEventListener("message", receive);
      frame2?.remove();
      for (const entry of requests2.values()) {
        clearTimeout(entry.timeout);
        entry.reject(new Error("\u89C4\u5219\u9875\u9762\u5DF2\u5173\u95ED"));
      }
      requests2.clear();
    } });
  }
  function nativeRules(host2) {
    let closed = false, seq = 0;
    const requests2 = /* @__PURE__ */ new Map();
    const endpoint2 = Object.freeze({ replyFromJson(encoded) {
      if (closed) return;
      const reply = JSON.parse(encoded), entry = requests2.get(reply.id);
      if (!entry) return;
      clearTimeout(entry.timeout);
      requests2.delete(reply.id);
      if (reply.error) {
        const error = new Error(reply.error.message);
        error.code = reply.error.code;
        entry.reject(error);
      } else entry.resolve(reply.value);
    } });
    window.qfNativeRules = endpoint2;
    return Object.freeze({ invoke(type, operation, input) {
      if (closed) return Promise.reject(new Error("\u5F00\u53D1\u89C4\u5219\u9875\u9762\u5DF2\u5173\u95ED"));
      return new Promise((resolve, reject2) => {
        const id = String(++seq), timeout = setTimeout(() => {
          requests2.delete(id);
          reject2(new Error("\u5F00\u53D1\u89C4\u5219\u8BF7\u6C42\u8D85\u65F6\uFF0C\u8BF7\u8BFB\u53D6\u72B6\u6001\u540E\u91CD\u8BD5"));
        }, 6e4);
        requests2.set(id, { resolve, reject: reject2, timeout });
        try {
          host2.request(id, type, operation, typeof input === "string" ? input : JSON.stringify(input));
        } catch (error) {
          clearTimeout(timeout);
          requests2.delete(id);
          reject2(error);
        }
      });
    }, destroy() {
      closed = true;
      for (const entry of requests2.values()) {
        clearTimeout(entry.timeout);
        entry.reject(new Error("\u5F00\u53D1\u89C4\u5219\u9875\u9762\u5DF2\u5173\u95ED"));
      }
      requests2.clear();
      if (window.qfNativeRules === endpoint2) delete window.qfNativeRules;
    } });
  }

  // src/extensions/editor-transition.js
  function stageEditor(root2, previous, mount, commit) {
    const body = document.createElement("div");
    body.className = "qf-editor-body";
    body.style.display = "flow-root";
    body.style.visibility = "hidden";
    body.inert = true;
    root2.style.position = "relative";
    if (previous) {
      const padding = getComputedStyle(root2);
      Object.assign(body.style, { position: "absolute", left: padding.paddingLeft, right: padding.paddingRight, top: padding.paddingTop });
      previous.body.inert = true;
      previous.instance.suspendPresentation?.();
    }
    root2.append(body);
    let instance, cancelled = false, committed = false;
    try {
      instance = mount(body, () => committed && !cancelled);
    } catch (error) {
      body.remove();
      throw error;
    }
    function reveal() {
      if (cancelled) return;
      const closing2 = previous?.instance.destroy?.();
      if (previous?.body.querySelector(".qf-frame-retiring")) {
        previous.body.dataset.qfRetiredRoot = "";
        previous.body.style.cssText = "position:absolute;visibility:hidden;pointer-events:none";
        previous.body.querySelectorAll("[id]").forEach((node) => node.removeAttribute("id"));
        Promise.resolve(closing2).finally(() => previous.body.remove());
      } else previous?.body.remove();
      committed = true;
      Object.assign(body.style, { position: "", left: "", right: "", top: "", visibility: "" });
      body.inert = false;
      commit({ body, instance });
    }
    const ready = Promise.resolve(instance.ready).then(() => instance.flush?.()).then(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))).then(reveal, (error) => {
      if (cancelled) return;
      reveal();
      throw error;
    });
    ready.catch(() => {
    });
    return { body, instance, ready, cancel() {
      if (committed || cancelled) return;
      cancelled = true;
      instance.destroy();
      body.remove();
    } };
  }

  // src/extensions/sdk.js
  var authorizations = /* @__PURE__ */ new Map();
  function compileQuestionExtension(value, sharedPolicies = null) {
    const bundle = typeof value === "string" ? JSON.parse(value) : value;
    const manifest = bundle?.manifest;
    requireCompatibleManifest(manifest);
    if (!Array.isArray(manifest.types) || !manifest.types.length)
      throw new TypeError("Unsupported question extension manifest: HTML SDK 2 required");
    const assets = bundle.assets || manifest.types.map((type) => ({ typeId: type.id, ...bundle }));
    const renderers2 = [], editors2 = [], checks = [], permissionPolicies = /* @__PURE__ */ new Map(), validators2 = /* @__PURE__ */ new Map(), rules = typeof window === "undefined" ? globalThis.QuestionRules.createRegistry() : isolatedRules({ ...bundle, assets }), types = /* @__PURE__ */ new Set();
    const QF = Object.freeze({
      defineQuestionType: (definition) => rules.defineQuestionType(definition),
      installDefaultQuestion: (type, template) => rules.installDefaultQuestion(type, template)
    });
    try {
      for (const type of manifest.types) {
        readPermissions(type.permissions);
        if (types.has(type.id)) throw new TypeError("Duplicate extension type");
        types.add(type.id);
        const asset = assets.find((a) => a.typeId === type.id);
        if (!asset || typeof asset.rulesSource !== "string" || typeof asset.editorSource !== "string" || typeof asset.rendererSource !== "string")
          throw new TypeError(`Missing HTML package assets for ${type.id}`);
        for (const source of [asset.editorSource, asset.rendererSource])
          new Function("QF", '"use strict"; return (async()=>{\n' + source + "\n})();");
        const template = typeof asset.defaultQuestion === "string" ? JSON.parse(asset.defaultQuestion) : asset.defaultQuestion;
        const validation = compileDataValidation2(type, asset);
        validators2.set(type.id, validation);
        const check = validation.question(template);
        if (check?.then) {
          check.catch(() => {
          });
          checks.push(check);
        }
        new Function("QF", '"use strict";\n' + asset.rulesSource);
        if (typeof window === "undefined") {
          rules.installDefaultQuestion(type.id, template);
          new Function("QF", '"use strict";\n' + asset.rulesSource)(QF);
          if (!rules.has(type.id)) throw new TypeError(`Missing rules for ${type.id}`);
        }
        const granted = readPermissions(bundle.grantedPermissions?.[type.id]);
        const policy = sharedPolicies?.get(type.id) || createPermissionPolicy(type.permissions, granted);
        permissionPolicies.set(type.id, policy);
        renderers2.push(createHtmlRenderer(type, asset, granted, policy, validation));
        editors2.push(createHtmlEditor(type, asset, granted, policy, validation));
      }
    } catch (error) {
      rules.destroy?.();
      for (const validation of validators2.values()) validation.destroy?.();
      throw error;
    }
    const checked = checkedRules(rules, validators2);
    const ready = checks.length ? Promise.all(checks).catch((error) => {
      checked.destroy();
      throw error;
    }) : null;
    ready?.catch(() => {
    });
    return Object.freeze({
      manifest: JSON.parse(JSON.stringify(manifest)),
      renderers: renderers2,
      editors: editors2,
      rules: checked,
      ready,
      assets,
      permissionPolicies,
      stylesSource: ""
    });
  }
  function installQuestionExtension(value) {
    const bundle = typeof value === "string" ? JSON.parse(value) : value;
    const manifest = bundle?.manifest;
    if (QuestionRendererRegistry.extensions().some((m) => m.id === manifest?.id && m.version === manifest?.version))
      return { installed: false, id: manifest.id, version: manifest.version };
    const compiled = compileQuestionExtension(bundle);
    const commit = () => {
      try {
        registerQuestionExtension(manifest, compiled.renderers, compiled.editors);
      } catch (error) {
        compiled.rules.destroy();
        throw error;
      }
      authorizations.set(`${manifest.id}@${manifest.version}`, { sha256: bundle.sha256, policies: compiled.permissionPolicies, rules: compiled.rules });
      return { installed: true, id: manifest.id, version: manifest.version };
    };
    return compiled.ready ? compiled.ready.then(commit) : commit();
  }
  function replaceDevelopmentExtension(value) {
    const bundle = typeof value === "string" ? JSON.parse(value) : value;
    const authorization = authorizations.get(`${bundle.manifest?.id}@${bundle.manifest?.version}`);
    if (authorization && authorization.sha256 !== bundle.sha256) throw new TypeError("Development preview cannot change the installed package hash");
    const compiled = compileQuestionExtension(bundle, authorization?.policies);
    const commit = () => {
      try {
        registerQuestionExtension(compiled.manifest, compiled.renderers, compiled.editors, { replace: true });
      } catch (error) {
        compiled.rules.destroy();
        throw error;
      }
      if (authorization) {
        authorization.rules?.retire?.();
        authorization.rules = compiled.rules;
      }
      if (typeof window !== "undefined") window.dispatchEvent(new CustomEvent("qf-extension-updated", {
        detail: { types: compiled.manifest.types.map((type) => type.id), revision: bundle.revision || bundle.sha256 }
      }));
      return true;
    };
    return compiled.ready ? compiled.ready.then(commit) : commit();
  }
  function updateQuestionExtensionPermissions({ id, version, sha256, grantedPermissions }) {
    const authorization = authorizations.get(`${id}@${version}`);
    if (!authorization) return false;
    if (authorization.sha256 !== sha256) throw new TypeError("Permission update does not match the installed package hash");
    if (!grantedPermissions || typeof grantedPermissions !== "object" || Array.isArray(grantedPermissions)) throw new TypeError("Invalid permission update");
    const checked = /* @__PURE__ */ new Map();
    for (const type of Object.keys(grantedPermissions)) if (!authorization.policies.has(type)) throw new TypeError("Undeclared permission type");
    for (const [type, policy] of authorization.policies) {
      const values = readPermissions(grantedPermissions[type]);
      if (values.some((name) => !policy.declared.includes(name))) throw new TypeError("Cannot grant an undeclared permission");
      checked.set(type, values);
    }
    checked.forEach((values, type) => authorization.policies.get(type).update(values));
    return true;
  }
  function denyAllQuestionExtensionPermissions() {
    authorizations.forEach((record) => record.policies.forEach((policy) => policy.update([])));
  }
  function mountExtensionEditor(root2, type, question, onChange = () => {
  }, overrides = {}) {
    const definition = QuestionRendererRegistry.editor(type);
    if (!definition) throw new TypeError(`Missing editor for ${type}`);
    return definition.mount(root2, JSON.parse(JSON.stringify(question)), Object.freeze({
      element,
      renderContent,
      readContent,
      layoutHeight: () => window.extensionEditorHost?.viewportHeight?.() || window.innerHeight,
      configureUi: (preferences) => window.extensionEditorHost?.configureUi?.(question.id, JSON.stringify(preferences)),
      ...window.bankEditorHost ? {
        shellCommand: (action, argument) => window.qfEditorShell.command(action, argument),
        shellState: () => window.qfEditorShell.getState()
      } : {},
      async editContent(content) {
        const encoded = await window.editorHost?.editContent?.(JSON.stringify(content));
        return encoded ? JSON.parse(encoded) : content;
      },
      async resolveContent(content) {
        const encoded = await window.editorHost?.resolveContent?.(JSON.stringify(content));
        return encoded ? JSON.parse(encoded) : readContent(content, "editor content");
      },
      newId(prefix = "item_") {
        return window.editorHost?.newId?.(prefix) || prefix + (globalThis.crypto?.randomUUID?.() || Math.random().toString(36).slice(2) + Date.now().toString(36)).replace(/-/g, "");
      },
      changed(value) {
        return onChange(JSON.parse(JSON.stringify(value)));
      },
      ...overrides
    }));
  }
  async function flushExtensionEditor(instance, snapshot) {
    if (!instance?.hasInitializationError?.()) await instance?.flush?.();
    return snapshot();
  }
  if (typeof window !== "undefined") {
    let editorMountSequence = 0, editorPresentation = null, pendingEditor = null, editorLayout = null;
    window.questionExtensions = Object.freeze({
      install: installQuestionExtension,
      loadFromSource: installQuestionExtension,
      updatePermissions: updateQuestionExtensionPermissions,
      denyAllPermissions: denyAllQuestionExtensionPermissions,
      replaceDevelopment: replaceDevelopmentExtension,
      list: () => QuestionRendererRegistry.extensions(),
      mountEditor(type, container, question, overrides) {
        return mountExtensionEditor(
          container,
          type,
          typeof question === "string" ? JSON.parse(question) : question,
          (next) => window.editorHost?.changed?.(JSON.stringify(next)),
          overrides
        );
      },
      mountEditorFromJson(type, value) {
        const next = typeof value === "string" ? JSON.parse(value) : value, sequence3 = ++editorMountSequence;
        const mount = () => {
          if (sequence3 !== editorMountSequence) return false;
          const root2 = document.querySelector("#extension-editor");
          if (!root2) throw new Error("Extension editor root is missing");
          let current = next;
          pendingEditor?.cancel();
          if (!editorLayout) {
            editorLayout = createDomLayout(root2.closest("#qf-editor-shell") || root2, { mode: "EDITOR", getHeight: () => window.extensionEditorHost?.viewportHeight?.() || window.innerHeight });
            editorLayout.configure(defaultLayout("EDITOR"));
          }
          let stagedUi = null, stagedLayout = null;
          const transition = stageEditor(root2, editorPresentation, (body, isReady) => mountExtensionEditor(body, type, current, async (next2) => {
            if (sequence3 !== editorMountSequence) throw new Error("\u9898\u76EE\u5DF2\u5207\u6362");
            await window.extensionEditorHost?.changed?.(JSON.stringify(next2));
            current = next2;
          }, {
            isCurrent: () => sequence3 === editorMountSequence,
            isReady,
            layoutHost: { getState: editorLayout.getState, configure(...args) {
              if (sequence3 !== editorMountSequence) return;
              if (isReady()) editorLayout.configure(...args);
              else stagedLayout = args;
            } },
            configureUi(preferences) {
              if (sequence3 !== editorMountSequence) return;
              if (isReady()) {
                window.qfEditorShell?.configureUi?.(preferences);
                window.extensionEditorHost?.configureUi?.(current.id, JSON.stringify(preferences));
              } else stagedUi = preferences;
            },
            async reloadPage() {
              const instance = window.extensionEditorInstance;
              const draft = instance?.getDraft?.() ?? current;
              await window.questionExtensions.mountEditorFromJson(type, draft);
            }
          }), (presentation) => {
            if (sequence3 !== editorMountSequence) return;
            editorPresentation = presentation;
            pendingEditor = null;
            if (stagedLayout) editorLayout.configure(...stagedLayout);
            if (stagedUi) {
              window.qfEditorShell?.configureUi?.(stagedUi);
              window.extensionEditorHost?.configureUi?.(current.id, JSON.stringify(stagedUi));
            }
          });
          pendingEditor = transition;
          window.extensionEditorInstance = transition.instance;
          window.extensionEditorType = type;
          window.extensionEditorSnapshot = () => JSON.stringify(window.extensionEditorInstance?.getDraft?.() ?? current);
          window.extensionEditorFlush = async () => {
            const instance = window.extensionEditorInstance;
            return flushExtensionEditor(instance, () => {
              if (instance !== window.extensionEditorInstance) throw new Error("Editor changed during save");
              return window.extensionEditorSnapshot();
            });
          };
          window.extensionEditorFlushToHost = (id) => window.extensionEditorFlush().then(
            (draft) => window.extensionEditorHost.flushed(id, draft),
            (failure3) => window.extensionEditorHost.flushFailed(id, failure3.message || String(failure3))
          );
          return transition.ready.then(() => sequence3 === editorMountSequence);
        };
        const ready = Promise.resolve(window.__qfExtensionsReady).then(mount);
        window.__qfEditorReady = ready;
        ready.catch((error) => {
          window.qfEditorShell?.errorFromJson(JSON.stringify({ message: error.message }));
          window.extensionEditorHost?.reloadFailed?.(error.message);
        });
        return ready;
      },
      clearEditor() {
        editorMountSequence++;
        pendingEditor?.cancel();
        pendingEditor = null;
        editorPresentation?.instance.destroy();
        editorPresentation = null;
        window.extensionEditorInstance = null;
        window.extensionEditorType = null;
        const root2 = document.querySelector("#extension-editor");
        if (root2) replaceChildrenRetainingFrames(root2);
        editorLayout?.destroy();
        editorLayout = null;
        window.__qfEditorReady = Promise.resolve();
      }
    });
    let editorReload = Promise.resolve();
    window.addEventListener("qf-extension-updated", (event) => {
      if (!event.detail.types.includes(window.extensionEditorType) || !window.extensionEditorInstance) return;
      editorReload = editorReload.then(async () => {
        const instance = window.extensionEditorInstance, type = window.extensionEditorType;
        const draft = instance.hasInitializationError?.() ? window.extensionEditorSnapshot() : await window.extensionEditorFlush();
        if (instance === window.extensionEditorInstance) await window.questionExtensions.mountEditorFromJson(type, draft);
      }).catch((error) => window.extensionEditorHost?.reloadFailed?.("\u5B9E\u65F6\u9884\u89C8\u66F4\u65B0\u5931\u8D25\uFF1A" + error.message));
    });
  }

  // src/learning/editor-shell.html
  var editor_shell_default = '<section id="qf-editor-shell" hidden>\n  <header class="qf-editor-toolbar">\n    <span>\u9898\u5E93\u7F16\u8F91</span>\n    <button id="qbank-save" type="button" title="\u4FDD\u5B58\u9898\u5E93 \xB7 Ctrl+S" aria-label="\u4FDD\u5B58\u9898\u5E93">\n      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h12l3 3v15H4V3h1ZM8 3v7h8V3M8 21v-7h8v7"/></svg>\n    </button>\n  </header>\n  <nav class="qf-editor-navigation" aria-label="\u9898\u76EE\u5BFC\u822A">\n    <button id="qbank-editor-previous" type="button" title="\u4E0A\u4E00\u9898" aria-label="\u4E0A\u4E00\u9898">\u2190</button>\n    <span id="qbank-editor-position"></span>\n    <button id="qbank-editor-next" type="button" title="\u4E0B\u4E00\u9898" aria-label="\u4E0B\u4E00\u9898">\u2192</button>\n    <select id="qbank-add-question" aria-label="\u6DFB\u52A0\u9898\u76EE"><option value="">\uFF0B \u6DFB\u52A0\u9898\u76EE</option></select>\n    <span class="qf-editor-spacer"></span>\n    <span id="qbank-question-type"></span>\n    <button id="qbank-delete-question" type="button" title="\u5220\u9664\u9898\u76EE" aria-label="\u5220\u9664\u9898\u76EE">\n      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7"/></svg>\n    </button>\n    <button id="qbank-duplicate-question" type="button" title="\u590D\u5236\u9898\u76EE" aria-label="\u590D\u5236\u9898\u76EE">\n      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 8h13v13H8zM16 8V3H3v13h5"/></svg>\n    </button>\n  </nav>\n  <p id="qbank-editor-error" role="alert" hidden></p>\n  <section id="qbank-delete-confirm" class="qf-editor-confirm" hidden>\n    <p>\u786E\u5B9A\u5220\u9664\u5F53\u524D\u9898\u76EE\uFF1F\u9898\u5E72\u3001\u9009\u9879\u53CA\u76F8\u5173\u5185\u5BB9\u5C06\u4E00\u5E76\u79FB\u9664\u3002</p>\n    <button type="button" data-cancel-delete>\u53D6\u6D88</button>\n    <button type="button" data-confirm-delete>\u5220\u9664</button>\n  </section>\n  <div id="qbank-editor-empty" class="qf-editor-state" hidden>\u8FD8\u6CA1\u6709\u9898\u76EE\uFF0C\u70B9\u51FB\u201C\u6DFB\u52A0\u9898\u76EE\u201D\u5F00\u59CB\u7F16\u8F91\u3002</div>\n  <div id="qbank-editor-missing" class="qf-editor-state" hidden></div>\n  <main id="extension-editor"></main>\n  <section id="qbank-editor-sources" class="qf-editor-sources">\n    <h3>\u5F15\u7528\u6765\u6E90</h3>\n    <div id="qbank-source-list"></div>\n    <div class="qf-source-input">\n      <input id="qbank-source-link" type="text" placeholder="\u7C98\u8D34 Source Anchor \u94FE\u63A5\u2026" aria-label="\u6765\u6E90\u94FE\u63A5">\n      <button id="qbank-use-source-link" type="button">\uFF0B \u4F7F\u7528\u94FE\u63A5</button>\n    </div>\n  </section>\n</section>\n';

  // src/learning/editor-shell.js
  function createEditorShell(root2, { request, mountEditor, flushEditor, clearEditor, heightChanged = () => {
  } }) {
    let state = null, questionId2 = null, busy = false, disposed = false, mounting = false, updateSequence = 0;
    let pendingUpdate = Promise.resolve();
    const listeners = [];
    const $ = (selector) => root2.querySelector(selector);
    function on(node, event, fn) {
      node.addEventListener(event, fn);
      listeners.push(() => node.removeEventListener(event, fn));
    }
    function error(message = "") {
      $("#qbank-editor-error").textContent = message;
      $("#qbank-editor-error").hidden = !message || state?.ui?.errors === false;
      heightChanged();
    }
    function applyUi() {
      const ui = state?.ui || {};
      const selectors = {
        title: ".qf-editor-toolbar > span",
        save: "#qbank-save",
        position: "#qbank-editor-position",
        typeLabel: "#qbank-question-type",
        add: "#qbank-add-question",
        duplicate: "#qbank-duplicate-question",
        delete: "#qbank-delete-question",
        sources: "#qbank-editor-sources"
      };
      for (const [name, selector] of Object.entries(selectors)) $(selector).hidden = ui[name] === false || name === "sources" && !state.question;
      $(".qf-editor-toolbar").hidden = ui.title === false && ui.save === false;
      if (ui.delete === false) $("#qbank-delete-confirm").hidden = true;
      $("#qbank-editor-error").hidden = ui.errors === false || !$("#qbank-editor-error").textContent;
      heightChanged();
    }
    function controls() {
      const locked = busy || mounting;
      const empty = !state?.question;
      root2.setAttribute("aria-busy", String(locked));
      root2.querySelectorAll("button,select").forEach((node) => {
        if (!node.closest("#extension-editor")) node.disabled = locked || Boolean(node.dataset.unavailable);
      });
      $("#qbank-editor-previous").disabled = locked || empty || state.index === 0;
      $("#qbank-editor-next").disabled = locked || empty || state.index >= state.count - 1;
      $("#qbank-delete-question").disabled = locked || empty;
      $("#qbank-duplicate-question").disabled = locked || empty || !state.editable;
      $("#qbank-use-source-link").disabled = locked || empty;
      $("#qbank-source-link").disabled = locked || empty;
      $("#extension-editor").inert = locked;
      $("#extension-editor").style.pointerEvents = locked ? "none" : "";
    }
    function update(next) {
      if (disposed) return Promise.resolve();
      state = next;
      root2.hidden = false;
      const id = state.question?.id ?? "";
      if (questionId2 !== id) {
        questionId2 = id;
        const sequence3 = ++updateSequence;
        mounting = true;
        controls();
        pendingUpdate = Promise.resolve().then(() => {
          if (disposed || sequence3 !== updateSequence) return;
          return next.editable ? mountEditor(next.question.type, next.question) : clearEditor();
        }).then(() => {
          if (disposed || sequence3 !== updateSequence) return;
          mounting = false;
          $("#qbank-delete-confirm").hidden = true;
          $("#qbank-source-link").value = "";
          error();
          paint();
        }, (failure3) => {
          if (!disposed && sequence3 === updateSequence) {
            mounting = false;
            paint();
            error(failure3.message);
          }
          throw failure3;
        });
        pendingUpdate.catch(() => {
        });
        return pendingUpdate;
      }
      if (mounting) return pendingUpdate;
      paint();
      return Promise.resolve();
    }
    function paint() {
      $("#qbank-editor-position").textContent = state.count ? `${state.index + 1} / ${state.count}` : "0 / 0";
      $("#qbank-question-type").textContent = state.label || "";
      const add = $("#qbank-add-question");
      add.replaceChildren(new Option("\uFF0B \u6DFB\u52A0\u9898\u76EE", ""));
      for (const type of state.types) add.append(new Option(type.label, type.id));
      $("#qbank-editor-empty").hidden = Boolean(state.question);
      $("#qbank-editor-missing").hidden = !state.question || state.editable;
      $("#qbank-editor-missing").textContent = state.question && !state.editable ? `\u7F3A\u5C11 ${state.question.type} \u5BF9\u5E94\u7684\u9898\u578B\u6269\u5C55\u3002\u5B89\u88C5\u540E\u53EF\u7F16\u8F91\uFF0C\u539F\u59CB\u6570\u636E\u548C\u8D44\u6E90\u5DF2\u4FDD\u7559\u3002` : "";
      $("#qbank-editor-sources").hidden = !state.question;
      const rows = $("#qbank-source-list");
      rows.replaceChildren();
      state.sources.forEach((source, index) => {
        const row = document.createElement("div");
        row.className = "qf-source-row";
        const open = document.createElement("button");
        open.type = "button";
        open.className = "qf-source-open";
        open.textContent = source.label;
        open.dataset.sourceIndex = index;
        open.dataset.sourceAction = "open";
        if (!source.navigable) open.dataset.unavailable = "true";
        const message = document.createElement("span");
        message.className = "qf-source-message";
        message.textContent = source.message;
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "\xD7";
        remove.setAttribute("aria-label", "\u79FB\u9664\u5F15\u7528");
        remove.dataset.sourceIndex = index;
        remove.dataset.sourceAction = "remove";
        row.append(open, message, remove);
        rows.append(row);
      });
      controls();
      applyUi();
    }
    async function command(action, argument = null) {
      if (busy || mounting || disposed || !state) return { ok: false, error: { code: "BUSY", message: "\u6B63\u5728\u5904\u7406\u7F16\u8F91\u64CD\u4F5C", retryable: false } };
      busy = true;
      error();
      controls();
      try {
        const id = questionId2;
        const draft = state.editable ? JSON.parse(await flushEditor()) : null;
        if (id !== questionId2 || disposed) throw new Error("\u9898\u76EE\u5DF2\u5207\u6362\uFF0C\u8BF7\u91CD\u65B0\u64CD\u4F5C");
        const reply = await request({ action, argument, questionId: id, draft });
        if (disposed) return reply;
        if (reply.ok) {
          await update(reply.data);
          if (action === "source.add") $("#qbank-source-link").value = "";
        } else {
          error(reply.error.message);
          applyUi();
        }
        return reply;
      } catch (failure3) {
        error(failure3.message || "\u7F16\u8F91\u64CD\u4F5C\u5931\u8D25");
        applyUi();
        return { ok: false, error: { code: "EDITOR_COMMAND_FAILED", message: failure3.message || "\u7F16\u8F91\u64CD\u4F5C\u5931\u8D25", retryable: false } };
      } finally {
        busy = false;
        if (!disposed) controls();
      }
    }
    on($("#qbank-save"), "click", () => command("save"));
    on($("#qbank-editor-previous"), "click", () => command("navigate", state.index - 1));
    on($("#qbank-editor-next"), "click", () => command("navigate", state.index + 1));
    on($("#qbank-add-question"), "change", () => {
      const type = $("#qbank-add-question").value;
      if (type) command("add", type);
    });
    on($("#qbank-duplicate-question"), "click", () => command("duplicate"));
    on($("#qbank-delete-question"), "click", () => {
      if (!busy) {
        $("#qbank-delete-confirm").hidden = false;
        heightChanged();
      }
    });
    on($("[data-cancel-delete]"), "click", () => {
      $("#qbank-delete-confirm").hidden = true;
      heightChanged();
    });
    on($("[data-confirm-delete]"), "click", () => command("delete"));
    on($("#qbank-use-source-link"), "click", () => command("source.add", $("#qbank-source-link").value));
    on($("#qbank-source-link"), "keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        command("source.add", event.target.value);
      }
    });
    on($("#qbank-source-list"), "click", (event) => {
      const node = event.target.closest("[data-source-action]");
      if (node && !node.dataset.unavailable) command("source." + node.dataset.sourceAction, Number(node.dataset.sourceIndex));
    });
    on(document, "keydown", (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        command("save");
      }
    });
    return Object.freeze({
      update,
      command,
      error,
      getState: () => state && JSON.parse(JSON.stringify(state)),
      configureUi(preferences) {
        if (state) {
          state = { ...state, ui: preferences };
          if (!mounting) applyUi();
        }
      },
      destroy() {
        disposed = true;
        updateSequence++;
        listeners.forEach((remove) => remove());
      }
    });
  }

  // src/extensions/editor-app.js
  var legacyRoot = document.querySelector("#extension-editor");
  legacyRoot.insertAdjacentHTML("beforebegin", editor_shell_default.replace('<main id="extension-editor"></main>', "<main data-shell-editor></main>"));
  var shellRoot = document.querySelector("#qf-editor-shell");
  var shell = null;
  var sequence2 = 0;
  var requests = /* @__PURE__ */ new Map();
  function createShell() {
    if (shell) return shell;
    legacyRoot.remove();
    shellRoot.querySelector("[data-shell-editor]").id = "extension-editor";
    shell = createEditorShell(shellRoot, {
      request(value) {
        return new Promise((resolve, reject2) => {
          const id = String(++sequence2);
          const timeout = setTimeout(() => {
            requests.delete(id);
            reject2(new Error("\u7F16\u8F91\u64CD\u4F5C\u8D85\u65F6\uFF0C\u8BF7\u91CD\u8BD5"));
          }, 15e3);
          requests.set(id, { resolve, reject: reject2, timeout });
          try {
            window.bankEditorHost.request(id, JSON.stringify(value));
          } catch (error) {
            clearTimeout(timeout);
            requests.delete(id);
            reject2(error);
          }
        });
      },
      mountEditor: (type, question) => window.questionExtensions.mountEditorFromJson(type, question),
      flushEditor: async () => {
        await window.__qfEditorReady;
        return window.extensionEditorFlush();
      },
      clearEditor: () => window.questionExtensions.clearEditor(),
      heightChanged: reportHeight
    });
    return shell;
  }
  window.qfEditorShell = Object.freeze({
    command: (action, argument) => createShell().command(action, argument),
    getState: () => shell?.getState() ?? null,
    configureUi: (preferences) => createShell().configureUi(preferences),
    updateFromJson(value) {
      return createShell().update(JSON.parse(value));
    },
    errorFromJson(value) {
      createShell().error(JSON.parse(value).message);
    },
    replyFromJson(value) {
      const { requestId, reply } = JSON.parse(value), request = requests.get(requestId);
      if (request) {
        clearTimeout(request.timeout);
        requests.delete(requestId);
        request.resolve(reply);
      }
    }
  });
  var root = document.body;
  var frame = null;
  function reportHeight() {
    if (frame !== null) return;
    frame = requestAnimationFrame(() => {
      frame = null;
      const content = shell ? shellRoot : legacyRoot;
      const style2 = getComputedStyle(content);
      window.extensionEditorHost?.contentHeight?.(Math.ceil(content.getBoundingClientRect().height + parseFloat(style2.marginTop) + parseFloat(style2.marginBottom)));
    });
  }
  var resize = new ResizeObserver(reportHeight);
  resize.observe(root);
  var mutations = new MutationObserver(reportHeight);
  mutations.observe(root, { childList: true, subtree: true, attributes: true, characterData: true });
  window.addEventListener("resize", reportHeight);
  document.fonts?.ready.then(reportHeight);
  window.addEventListener("pagehide", () => {
    shell?.destroy();
    window.questionExtensions.clearEditor();
    for (const request of requests.values()) {
      clearTimeout(request.timeout);
      request.reject(new Error("\u7F16\u8F91\u9875\u9762\u5DF2\u5173\u95ED"));
    }
    requests.clear();
    resize.disconnect();
    mutations.disconnect();
    if (frame !== null) cancelAnimationFrame(frame);
  });
  reportHeight();
})();
