var QFPage = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // extensions/packages/reading/practice-source.js
  var practice_source_exports = {};
  __export(practice_source_exports, {
    start: () => start
  });

  // extensions/shared/legacy-data.js
  var clone = (value) => JSON.parse(JSON.stringify(value));
  var textContent = (text) => ({ kind: "TEXT", text });
  var payload = (q) => q.payload.data || q.payload;
  var spec = (q) => q.answerSpec?.data || q.answerSpec || {};
  var groups = (q) => payload(q).blanks || payload(q).items || [];
  function plain(content) {
    if (!content) return "";
    if (typeof content.text === "string") return content.text;
    const walk = (node) => {
      if (Array.isArray(node)) return node.map(walk).join("");
      if (!node || typeof node !== "object") return "";
      if (node.type === "image" || node.type === "IMAGE") return "";
      if (typeof node.text === "string") return node.text;
      if (typeof node.value === "string") return node.value.replace(/\u200b/g, "");
      return walk(node.children || node.valueList || node.blocks || node.items || node.trList || node.tdList || node.value || []);
    };
    return content.kind === "RICH" ? content.document.blocks.map(walk).join("\n") : walk(content.document?.data?.main || []);
  }
  function markers(text, numeric = false) {
    const found = [], errors = [];
    for (let i = 0; i < text.length; i++) {
      if (text[i] === "\\") {
        if (text.slice(i + 1, i + 3) === "{{") {
          const end2 = text.indexOf("}}", i + 3);
          i = end2 < 0 ? text.length : end2 + 1;
        } else i++;
        continue;
      }
      if (text.slice(i, i + 2) === "}}") {
        errors.push("\u5B58\u5728\u591A\u4F59\u7684\u7ED3\u675F\u6807\u8BB0");
        i++;
        continue;
      }
      if (text.slice(i, i + 2) !== "{{") continue;
      const start2 = i;
      let end = i + 2;
      while (end < text.length && text.slice(end, end + 2) !== "}}") end++;
      if (end === text.length) {
        errors.push("\u6807\u8BB0\u7F3A\u5C11 }}");
        break;
      }
      const value = text.slice(start2 + 2, end);
      if (value.includes("{{") || !value.trim() || numeric && !/^[1-9][0-9]*$/.test(value)) errors.push("\u6807\u8BB0\u5185\u5BB9\u65E0\u6548");
      else found.push({ start: start2, end: end + 2, value, number: numeric ? Number(value) : found.length + 1 });
      i = end + 1;
    }
    return { found, errors };
  }

  // extensions/shared/page-client.js
  async function connectPage(QF2) {
    let context, question, answer = {}, acceptedQuestion, acceptedAnswer = {}, writes = 0;
    const listeners = /* @__PURE__ */ new Set(), copy = (v) => v == null ? v : JSON.parse(JSON.stringify(v)), ok = (data) => Promise.resolve({ ok: true, data: copy(data) });
    const api = {
      question: () => ok(question),
      answer: () => ok(answer),
      result: () => ok(context.attempt?.result || null),
      state: () => ok({ ...context.navigation, sources: context.sources, types: context.types, editable: context.permissions.manageQuestions, learningMode: context.learningMode.toUpperCase() }),
      practiceState: () => ok({ ...context.navigation, type: context.question.type, state: context.attempt?.status.toUpperCase(), maxScore: context.question.maxScore, result: context.attempt?.result }),
      host: () => ok({ mode: context.mode.toUpperCase(), capabilities: { ...context.permissions, editAnswer: context.permissions.writeAnswer }, permissions: { granted: context.grantedPermissions } }),
      subscribe(fn) {
        listeners.add(fn);
        return () => listeners.delete(fn);
      },
      async edit(patch) {
        question = { ...question, ...copy(patch) };
        const candidate = copy(question);
        writes++;
        try {
          const reply = await QF2.save({ purpose: "editDraft", data: { questionData: candidate } });
          if (reply.ok) acceptedQuestion = candidate;
          else if (writes === 1) question = copy(acceptedQuestion);
          return reply.ok ? ok(question) : reply;
        } finally {
          writes--;
        }
      },
      async write(value) {
        answer = copy(value);
        const candidate = copy(answer);
        writes++;
        try {
          const reply = await QF2.save({ purpose: "draft", data: { answer: candidate } });
          if (reply.ok) acceptedAnswer = candidate;
          else if (writes === 1) answer = copy(acceptedAnswer);
          return reply;
        } finally {
          writes--;
        }
      },
      commit: () => QF2.save({ purpose: "edit", data: { questionData: question } }),
      submit: () => QF2.save({ purpose: "submit", data: { answer } }),
      action: (action, params = {}) => QF2.requestAction({ action, params }),
      get context() {
        return context;
      }
    };
    await QF2.page.register({
      async onLoad(value) {
        context = value;
        if (!writes) {
          question = acceptedQuestion = copy(value.question.data);
          answer = acceptedAnswer = copy(value.attempt?.answer || {});
        }
        for (const fn of listeners) await fn();
      },
      // Changes are sent immediately; the SDK waits for accepted saves before navigation.
      onBeforeLeave: () => ({ ok: true, data: { pendingSave: null } }),
      onDispose: () => listeners.clear()
    });
    return api;
  }

  // extensions/shared/page.js
  var $ = (selector) => QF.dom.$(selector);
  var on = (node, event, handler) => node.addEventListener(event, handler);
  function element(tag, cls = "", text) {
    const node = document.createElement(tag);
    node.className = cls;
    if (text !== void 0) node.textContent = text;
    return node;
  }
  async function rich(node, value) {
    await QF.content.renderAsync(node, value || textContent(""));
  }
  function selection(options, value, label) {
    const select = element("select", "qf-letter-select");
    select.setAttribute("aria-label", label);
    select.append(new Option("\u2014", ""));
    options.forEach((option, index) => select.append(new Option(option.label || String.fromCharCode(65 + index), option.id)));
    select.value = value || "";
    return select;
  }
  async function practiceBase(label, onState) {
    const page = await connectPage(QF);
    const reply = await page.question();
    if (!reply.ok) throw new Error(reply.error.message);
    const question = reply.data;
    let answer = {}, context = {}, result = null, state = {}, confirming = false, busy = false, writes = 0, revision = 0;
    QF.layout.configure({ cardWidth: 820, maxCardWidth: "100%", horizontalAlign: "center", verticalAlign: "center", padding: 20 });
    QF.ui.configure({ typeLabel: false, position: false, score: false, state: false, submit: false, retry: false, confirmation: false, note: false, draftToggle: true, draftToolbar: true, draftZoom: true });
    $("[data-type-label]").textContent = label;
    const api = {
      question,
      get answer() {
        return answer;
      },
      get result() {
        return result;
      },
      get writable() {
        return !busy && !confirming && context.capabilities?.editAnswer;
      },
      number(id, fallback) {
        return page.context.navigation.targets?.find((target) => target.id === id)?.number ?? fallback;
      },
      async write(value) {
        answer = clone(value);
        writes++;
        revision++;
        const response = await page.write(value);
        if (!response.ok) QF.ui.notify(response.error.message);
        writes--;
        if (!writes) await refresh();
        return response;
      },
      async refresh() {
        return refresh();
      }
    };
    async function refresh() {
      const current = ++revision;
      const [a, c, r, s] = await Promise.all([page.answer(), page.host(), page.result(), page.practiceState()]);
      if (current !== revision || !a.ok || !c.ok || !r.ok || !s.ok) return;
      if (!writes) answer = a.data;
      context = c.data;
      result = r.data;
      state = s.data;
      const readOnly = ["HISTORY", "PREVIEW"].includes(context.mode), permissions = context.permissions.granted;
      $("[data-position]").textContent = Number.isInteger(state.index) ? `\u7B2C ${state.index + 1} / ${state.total} \u9898` : "";
      $("[data-max-score]").textContent = "\u5206\u503C\uFF1A" + (state.maxScore ?? "\u672A\u8BBE\u7F6E");
      $("[data-state]").textContent = { UNANSWERED: "\u672A\u4F5C\u7B54", DRAFT: "\u4F5C\u7B54\u4E2D", SUBMITTED: "\u5DF2\u63D0\u4EA4", RETRYING: "\u91CD\u8BD5\u4E2D", REVISING: "\u4FEE\u8BA2\u4E2D" }[state.state] || "";
      if (readOnly || result) confirming = false;
      $("[data-confirm]").hidden = !confirming;
      const remaining = groups(question).filter((g) => !g.locked && !answer[g.id]?.text?.trim() && !(typeof answer[g.id] === "string" && answer[g.id])).length;
      $("[data-confirm-hint]").textContent = remaining ? `\u8FD8\u6709 ${remaining} \u9053\u672A\u5B8C\u6210\uFF0C\u786E\u8BA4\u63D0\u4EA4\u5F53\u524D\u7B54\u6848\uFF1F` : "\u786E\u8BA4\u63D0\u4EA4\u5F53\u524D\u7B54\u6848\uFF1F";
      $("[data-submit]").hidden = readOnly || !permissions.includes("practice.submit") || Boolean(result);
      $("[data-submit]").disabled = busy || confirming || !context.capabilities.submit || !Object.keys(answer).length;
      $("[data-accept]").disabled = busy || !context.capabilities.submit;
      $("[data-retry]").hidden = readOnly || !result || !permissions.includes("practice.retry");
      $("[data-retry]").disabled = busy || !context.capabilities.retry;
      $("[data-note]").textContent = readOnly ? "\u53EA\u8BFB\u8BB0\u5F55" : "\u8349\u7A3F\u81EA\u52A8\u4FDD\u5B58\uFF1B\u63D0\u4EA4\u540E\u51BB\u7ED3\uFF0C\u91CD\u8BD5\u4ECE\u7A7A\u767D\u8349\u7A3F\u5F00\u59CB\u3002";
      $("[data-result]").hidden = !result;
      if (result) {
        $("[data-verdict]").textContent = { CORRECT: "\u56DE\u7B54\u6B63\u786E", INCORRECT: "\u56DE\u7B54\u9519\u8BEF", UNSCORED: "\u5F85\u8BC4\u5206" }[result.status] || "\u5F85\u8BC4\u5206";
        $("[data-verdict]").className = result.status === "CORRECT" ? "correct" : result.status === "INCORRECT" ? "incorrect" : "muted";
        $("[data-result-score]").textContent = result.score == null ? "\u5F85\u8BC4\u5206" : `\u5F97\u5206\uFF1A${result.score} / ${result.maxScore}`;
      }
      await onState(api, { answer, context, result, state, writes });
    }
    async function action(fn) {
      if (busy) return;
      busy = true;
      try {
        await refresh();
        const reply2 = await fn();
        if (!reply2.ok) QF.ui.notify(reply2.error.message);
      } finally {
        busy = false;
        await refresh();
      }
    }
    on($("[data-submit]"), "click", () => {
      confirming = true;
      $("[data-confirm]").hidden = false;
      refresh();
    });
    on($("[data-cancel]"), "click", () => {
      confirming = false;
      refresh();
    });
    on($("[data-accept]"), "click", () => {
      confirming = false;
      return action(() => page.submit());
    });
    on($("[data-retry]"), "click", () => action(() => page.action("retry")));
    page.subscribe(refresh);
    return api;
  }
  function decorateMarkers(root, numeric, create) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT), nodes = [];
    let text = "", node;
    while (node = walker.nextNode()) {
      nodes.push({ node, start: text.length, end: text.length + node.data.length });
      text += node.data;
    }
    const { found } = markers(text, numeric);
    for (let i = found.length - 1; i >= 0; i--) {
      const mark = found[i], first = nodes.find((n) => n.end > mark.start), last = nodes.find((n) => n.end >= mark.end);
      if (!first || !last) continue;
      const range = document.createRange();
      range.setStart(first.node, mark.start - first.start);
      range.setEnd(last.node, mark.end - last.start);
      const fragment = range.extractContents();
      range.insertNode(create(mark, i, fragment));
      range.detach();
    }
    const literal = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    while (node = literal.nextNode()) node.data = node.data.replace(/\\\{\{/g, "{{");
  }
  function stripBraces(fragment) {
    const walker = document.createTreeWalker(fragment, NodeFilter.SHOW_TEXT), nodes = [];
    let node;
    while (node = walker.nextNode()) nodes.push(node);
    let count = 2;
    for (const n of nodes) {
      const size = Math.min(count, n.data.length);
      n.data = n.data.slice(size);
      count -= size;
      if (!count) break;
    }
    count = 2;
    for (const n of [...nodes].reverse()) {
      const size = Math.min(count, n.data.length);
      n.data = n.data.slice(0, n.data.length - size);
      count -= size;
      if (!count) break;
    }
    return fragment;
  }
  function fitOptions(root) {
    const rows = [...root.children];
    if (!rows.length) return;
    const gap = 12, width = root.clientWidth;
    if (width <= 0) return;
    const probe = document.createElement("span");
    probe.style.cssText = "position:fixed;visibility:hidden;white-space:pre;width:max-content;pointer-events:none";
    document.body.append(probe);
    let widest = 0;
    try {
      for (const row of rows) {
        const label = row.querySelector(".qf-option-text");
        if (!label) continue;
        const style = getComputedStyle(label);
        probe.style.font = style.font;
        probe.style.letterSpacing = style.letterSpacing;
        probe.style.wordSpacing = style.wordSpacing;
        probe.textContent = label.textContent;
        widest = Math.max(widest, probe.getBoundingClientRect().width + 64);
      }
    } finally {
      probe.remove();
    }
    const columns = `repeat(${[4, 2, 1].find((n) => n === 1 || (width - gap * (n - 1)) / n >= widest)}, minmax(0px, 1fr))`;
    if (root.style.gridTemplateColumns !== columns) root.style.gridTemplateColumns = columns;
  }
  function observeOptions(root) {
    let frame = 0, lastWidth = -1, disposed = false;
    const schedule = () => {
      if (!disposed && !frame) frame = requestAnimationFrame(() => {
        frame = 0;
        if (!disposed) fitOptions(root);
      });
    };
    const resize = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width ?? root.clientWidth;
      if (Math.abs(width - lastWidth) < 0.5) return;
      lastWidth = width;
      schedule();
    });
    resize.observe(root);
    fitOptions(root);
    document.fonts?.ready.then(schedule);
    return { disconnect() {
      disposed = true;
      resize.disconnect();
      if (frame) cancelAnimationFrame(frame);
      frame = 0;
    } };
  }

  // extensions/shared/rich-answer.js
  function answerContent(value) {
    if (value?.kind === "CANVAS_DOCUMENT") {
      try {
        return { kind: "DOCUMENT", text: value.text || "", document: JSON.parse(value.document) };
      } catch {
        return textContent(value.text || "");
      }
    }
    return value && ["TEXT", "RICH", "DOCUMENT"].includes(value.kind) ? value : textContent("");
  }
  function serialize(root) {
    const main = [];
    function inline(node, items) {
      if (node.nodeType === Node.TEXT_NODE) {
        if (!node.data) return;
        const parent = node.parentElement, style = getComputedStyle(parent);
        const item = { value: node.data, font: style.fontFamily, size: parseFloat(style.fontSize), color: style.color };
        item.bold = Number(style.fontWeight) >= 600 || style.fontWeight === "bold";
        item.italic = style.fontStyle === "italic";
        for (let current = parent; current && current !== root; current = current.parentElement) {
          const decoration = getComputedStyle(current).textDecorationLine;
          if (decoration.includes("underline")) item.underline = true;
          if (decoration.includes("line-through")) item.strikeout = true;
        }
        items.push(item);
        return;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return;
      if (node.tagName === "IMG") {
        if (/^data:image\//i.test(node.src)) items.push({ type: "image", value: node.src, width: node.width, height: node.height });
        return;
      }
      if (node.tagName === "BR") {
        items.push({ value: "\n" });
        return;
      }
      for (const child of node.childNodes) inline(child, items);
    }
    function append(items, node) {
      if (!items.length) items.push({ value: "" });
      const style = getComputedStyle(node.nodeType === Node.ELEMENT_NODE ? node : root);
      if (main.length) main.push({ value: "\n" });
      const flex = style.textAlign === "justify" ? "alignment" : style.textAlign;
      if (parseFloat(style.textIndent) > 0) items.unshift({ value: "\u2003\u2003" });
      items.forEach((item) => item.rowFlex = flex);
      main.push(...items);
    }
    function block(node) {
      if (node.nodeType === Node.ELEMENT_NODE && node.tagName === "TABLE") {
        main.push({ type: "table", value: "", trList: [...node.rows].map((row) => ({ tdList: [...row.cells].map((cell) => {
          const value = [];
          inline(cell, value);
          return { colspan: cell.colSpan, rowspan: cell.rowSpan, value };
        }) })) });
        return;
      }
      const items = [];
      inline(node, items);
      append(items, node);
    }
    let runs = [];
    for (const node of root.childNodes) {
      if (node.nodeType === Node.ELEMENT_NODE && ["DIV", "P", "TABLE", "UL", "OL", "BLOCKQUOTE", "H1", "H2", "H3"].includes(node.tagName)) {
        if (runs.length) {
          append(runs, root);
          runs = [];
        }
        block(node);
      } else inline(node, runs);
    }
    if (runs.length) append(runs, root);
    const document2 = { data: { header: [], main, footer: [] }, options: { defaultFont: "Arial", defaultSize: 16 } };
    return { kind: "CANVAS_DOCUMENT", text: root.innerText, document: JSON.stringify(document2) };
  }
  async function richAnswer(root, onChange, placeholder = "\u8BF7\u8F93\u5165\u7B54\u6848") {
    const heading = element("div", "qf-input-heading"), toggle = element("button", "", "\u7F16\u8F91\u683C\u5F0F");
    toggle.type = "button";
    heading.append(toggle);
    const tools = element("div", "qf-format-toolbar");
    tools.hidden = true;
    const body = element("div", "qf-answer-input");
    body.contentEditable = "false";
    body.setAttribute("role", "textbox");
    body.setAttribute("aria-multiline", "true");
    body.setAttribute("aria-label", placeholder);
    body.dataset.placeholder = placeholder;
    root.append(heading, tools, body);
    let formatting = false, writable = false, lastValue = "", lastRange = null, revision = 0, composing = false, loading = false, allowed = false, disposed = false;
    function remember() {
      const selection2 = window.getSelection();
      if (selection2?.rangeCount && body.contains(selection2.anchorNode)) lastRange = selection2.getRangeAt(0).cloneRange();
    }
    const selectionListener = () => remember();
    document.addEventListener("selectionchange", selectionListener);
    window.addEventListener("pagehide", () => {
      disposed = true;
      revision++;
      applyPermissions();
      document.removeEventListener("selectionchange", selectionListener);
    }, { once: true });
    function emit() {
      if (!writable || composing) return;
      const value = serialize(body);
      lastValue = JSON.stringify(value);
      return onChange(value);
    }
    function restore() {
      body.focus();
      if (lastRange && body.contains(lastRange.commonAncestorContainer)) {
        const selection2 = window.getSelection();
        selection2.removeAllRanges();
        selection2.addRange(lastRange);
      }
    }
    function command(name, value) {
      if (!writable) return;
      restore();
      document.execCommand(name, false, value);
      remember();
      emit();
    }
    for (const [label, name] of [["\u21B6", "undo"], ["\u21B7", "redo"], ["B", "bold"], ["I", "italic"], ["U", "underline"], ["S", "strikeThrough"], ["\u5DE6", "justifyLeft"], ["\u4E2D", "justifyCenter"], ["\u53F3", "justifyRight"], ["\u9F50", "justifyFull"], ["\u7F29\u8FDB", "indent"]]) {
      const button = element("button", "", label);
      button.type = "button";
      button.title = name;
      on(button, "pointerdown", (event) => event.preventDefault());
      on(button, "click", () => command(name));
      tools.append(button);
    }
    const font = element("select");
    font.setAttribute("aria-label", "\u5B57\u4F53");
    ["Arial", "Times New Roman", "Microsoft YaHei", "SimSun"].forEach((name) => font.append(new Option(name, name)));
    on(font, "change", () => command("fontName", font.value));
    tools.append(font);
    const size = element("select");
    size.setAttribute("aria-label", "\u5B57\u53F7");
    [12, 14, 16, 18, 20, 24, 28, 32].forEach((value) => size.append(new Option(value, value)));
    size.value = "16";
    on(size, "change", () => {
      restore();
      document.execCommand("fontSize", false, "7");
      for (const node of body.querySelectorAll('font[size="7"]')) {
        node.removeAttribute("size");
        node.style.fontSize = size.value + "px";
      }
      remember();
      emit();
    });
    tools.append(size);
    const color = element("input");
    color.type = "color";
    color.value = "#000000";
    color.setAttribute("aria-label", "\u6587\u5B57\u989C\u8272");
    on(color, "input", () => command("foreColor", color.value));
    tools.append(color);
    on(toggle, "pointerdown", (event) => {
      remember();
      event.preventDefault();
    });
    on(toggle, "click", () => {
      formatting = !formatting;
      tools.hidden = !formatting;
      toggle.textContent = formatting ? "\u5B8C\u6210\u7F16\u8F91" : "\u7F16\u8F91\u683C\u5F0F";
    });
    on(body, "input", emit);
    on(body, "compositionstart", () => {
      composing = true;
    });
    on(body, "compositionend", () => {
      composing = false;
      emit();
    });
    on(body, "paste", (event) => {
      if (!writable) return;
      event.preventDefault();
      document.execCommand("insertText", false, event.clipboardData.getData("text/plain"));
      emit();
    });
    function applyPermissions() {
      writable = allowed && !loading && !disposed;
      body.contentEditable = String(writable);
      body.setAttribute("aria-readonly", String(!writable));
      toggle.disabled = !writable;
      tools.hidden = !writable || !formatting;
      tools.querySelectorAll("input,select,button").forEach((node) => node.disabled = !writable);
    }
    return { async update(value, enabled) {
      if (disposed) return;
      allowed = enabled;
      const encoded = JSON.stringify(value || {});
      if (encoded === lastValue) {
        if (loading) {
          revision++;
          loading = false;
        }
        applyPermissions();
        return;
      }
      if (composing) {
        applyPermissions();
        return;
      }
      const current = ++revision;
      loading = true;
      applyPermissions();
      const staging = element("div");
      staging.hidden = true;
      root.append(staging);
      try {
        await rich(staging, answerContent(value));
        if (disposed || current !== revision) return;
        body.replaceChildren(...staging.childNodes);
        lastValue = encoded;
        lastRange = null;
        body.classList.toggle("shared-content", staging.classList.contains("shared-content"));
        body.classList.toggle("document-content", staging.classList.contains("document-content"));
        body.classList.toggle("qf-answer-empty", !plain(answerContent(value)).trim());
        loading = false;
        applyPermissions();
      } finally {
        staging.remove();
      }
    }, getValue() {
      if (loading || disposed) throw new Error("\u7B54\u6848\u4ECD\u5728\u52A0\u8F7D\u6216\u9875\u9762\u5DF2\u5173\u95ED");
      return serialize(body);
    } };
  }

  // extensions/shared/reading-type-practice.js
  async function startPractice(type, label) {
    const choiceControls = [], inlineControls = [], slotControls = [], answerInputs = [], observers = [];
    let resultKey = "";
    const objective = ["CLOZE", "READING", "MATCHING"].includes(type);
    const api = await practiceBase(label, async (page, { answer, result, writes }) => {
      const correct = spec({ answerSpec: result?.reference?.answerSpec }).answers || [];
      function right(id) {
        return correct.find((a) => (a.blankId || a.itemId) === id)?.correctOptionId;
      }
      for (const control of choiceControls) {
        if (!writes) control.input.checked = answer[control.group.id] === control.option.id;
        control.input.disabled = !page.writable;
        control.row.classList.toggle("selected", control.input.checked);
        control.row.classList.toggle("correct", Boolean(result) && right(control.group.id) === control.option.id);
        control.row.classList.toggle("incorrect", Boolean(result) && control.input.checked && right(control.group.id) !== control.option.id);
      }
      for (const control of [...inlineControls, ...slotControls]) {
        if (control.given) continue;
        if (!writes) control.select.value = answer[control.group.id] || "";
        control.select.disabled = !page.writable;
        control.row.classList.toggle("correct", Boolean(result) && right(control.group.id) === answer[control.group.id]);
        control.row.classList.toggle("incorrect", Boolean(result) && right(control.group.id) !== answer[control.group.id]);
        control.row.title = result ? right(control.group.id) === answer[control.group.id] ? "\u56DE\u7B54\u6B63\u786E" : "\u6B63\u786E\u7B54\u6848\uFF1A" + letterFor(control.group, right(control.group.id)) : "";
      }
      for (const { id, input } of answerInputs) await input.update(type === "ESSAY" ? answer : answer[id], page.writable);
      const key = JSON.stringify(result?.reference || null);
      if (key !== resultKey) {
        resultKey = key;
        const references = $("[data-references]");
        references.replaceChildren();
        if (result) {
          if (objective) {
            references.append(element("p", "qf-correct-answer", "\u6B63\u786E\u7B54\u6848\uFF1A" + groups(question).filter((g) => !g.locked).map((g, index) => page.number(g.id, index + 1) + ". " + letterFor(g, right(g.id))).join("\u3000")));
          } else if (type === "ESSAY") {
            references.append(element("strong", "", "\u53C2\u8003\u7B54\u6848"));
            const body = element("div", "qf-reference");
            references.append(body);
            await rich(body, spec({ answerSpec: result.reference?.answerSpec }).referenceAnswer);
          } else {
            for (const group of groups(question)) {
              const section = element("section", "qf-reference");
              section.append(element("strong", "", page.number(group.id, group.number) + ". \u53C2\u8003\u8BD1\u6587"));
              const body = element("div");
              section.append(body);
              references.append(section);
              await rich(body, spec({ answerSpec: result.reference?.answerSpec }).answers?.find((a) => a.itemId === group.id)?.referenceAnswer);
            }
          }
          const analysis = result.reference?.analysis || result.analysis || textContent("");
          await rich($("[data-analysis]"), type === "CLOZE" ? textContent(plain(analysis)) : analysis);
        }
      }
    });
    const question = api.question, data = payload(question), zone = $("[data-answer-zone]");
    function letterFor(group, id) {
      const options = group.options || data.options || [];
      const index = options.findIndex((o) => o.id === id);
      return index < 0 ? "\u672A\u8BBE\u7F6E" : options[index].label || String.fromCharCode(65 + index);
    }
    function choose(group, value) {
      const answer = { ...api.answer };
      if (value) answer[group.id] = value;
      else delete answer[group.id];
      return api.write(answer);
    }
    await rich($("[data-prompt]"), question.prompt);
    if (type === "CLOZE") {
      decorateMarkers($("[data-prompt]"), true, (mark) => {
        const group = groups(question).find((g) => g.number === mark.number);
        if (!group) return document.createTextNode(mark.value);
        const number = api.number(group.id, mark.number), row = element("span", "qf-inline-blank");
        row.append(element("span", "", number + "."));
        const select = selection(group.options, "", "\u7A7A\u4F4D " + number);
        select.options[0].textContent = "______";
        group.options.forEach((o, index) => select.options[index + 1].textContent = String.fromCharCode(65 + index) + ". " + o.content.text);
        row.append(select);
        inlineControls.push({ group, select, row });
        on(select, "change", () => choose(group, select.value));
        return row;
      });
    }
    if (type === "CLOZE" || type === "READING") {
      for (const group of groups(question)) {
        const section = element("section", "qf-subquestion");
        section.dataset.targetId = group.id;
        zone.append(section);
        const heading = element("h3");
        section.append(heading);
        if (type === "READING") {
          heading.append(element("span", "", api.number(group.id, group.number) + ". "));
          const prompt = element("span");
          heading.append(prompt);
          await rich(prompt, group.prompt);
        } else heading.textContent = api.number(group.id, group.number) + ".";
        const options = element("div", "qf-options");
        section.append(options);
        for (const [index, option] of group.options.entries()) {
          const row = element("label", "qf-choice-option"), input = element("input");
          input.type = "radio";
          input.name = "answer-" + group.id;
          input.value = option.id;
          input.setAttribute("aria-label", String.fromCharCode(65 + index) + ". " + option.content.text);
          row.append(input, element("span", "qf-option-letter", String.fromCharCode(65 + index) + "."), element("span", "qf-option-text", option.content.text));
          options.append(row);
          choiceControls.push({ group, option, input, row });
          on(input, "change", () => {
            if (input.checked) return choose(group, option.id);
          });
        }
        observers.push(observeOptions(options));
      }
    }
    if (type === "MATCHING") {
      const slots = element("div", "qf-matching-slots");
      zone.append(slots);
      const locked = new Set(groups(question).filter((g) => g.locked).map((g) => g.givenOptionId)), options = data.options.filter((o) => !locked.has(o.id));
      let number = 0;
      for (const group of groups(question)) {
        const row = element("div", "qf-order-slot");
        slots.append(row);
        if (group.locked) {
          row.classList.add("given");
          const given = data.options.find((o) => o.id === group.givenOptionId);
          row.textContent = given?.label || "\u63D0\u793A\u7F3A\u5931";
          row.title = "\u5DF2\u7ED9\u51FA\u7684\u63D0\u793A";
          slotControls.push({ group, row, given: true });
          continue;
        }
        row.dataset.targetId = group.id;
        number++;
        const displayNumber = api.number(group.id, number);
        row.append(element("span", "", displayNumber + "."));
        const select = selection(options, "", "\u5F85\u7B54\u4F4D\u7F6E " + displayNumber);
        row.append(select);
        slotControls.push({ group, row, select });
        on(select, "change", () => choose(group, select.value));
      }
    }
    if (type === "ESSAY") {
      zone.append(element("strong", "", "\u6B63\u5F0F\u7B54\u6848"));
      const root = element("div");
      zone.append(root);
      answerInputs.push({ input: await richAnswer(root, (value) => api.write(value), data.placeholder || "\u8BF7\u8F93\u5165\u4F5C\u6587") });
    }
    if (type === "TRANSLATION") {
      const marks = [];
      decorateMarkers($("[data-prompt]"), false, (mark, index, fragment) => {
        const group = groups(question).find((g) => g.number === index + 1), number = api.number(group?.id, index + 1);
        const span = element("span", "qf-translation-mark");
        span.append(element("sup", "", number + ". "), stripBraces(fragment));
        span.tabIndex = 0;
        span.setAttribute("role", "button");
        span.setAttribute("aria-label", "\u7FFB\u8BD1\u53E5\u5B50 " + number);
        marks.push({ span, index });
        return span;
      });
      for (const group of groups(question)) {
        const number = api.number(group.id, group.number), section = element("section", "qf-subquestion");
        section.dataset.targetId = group.id;
        section.append(element("h3", "", number + ". " + group.text));
        zone.append(section);
        const root = element("div");
        section.append(root);
        const input = await richAnswer(root, (value) => api.write({ ...api.answer, [group.id]: value }), "\u8BF7\u8F93\u5165\u7B2C " + number + " \u53E5\u8BD1\u6587");
        answerInputs.push({ id: group.id, input });
        const mark = marks.find((m) => m.index === group.number - 1)?.span;
        if (mark) {
          const focus = () => {
            section.scrollIntoView({ block: "nearest" });
            root.querySelector("[role=textbox]").focus();
          };
          on(mark, "click", focus);
          on(mark, "keydown", (event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              focus();
            }
          });
        }
      }
    }
    window.addEventListener("pagehide", () => observers.forEach((o) => o.disconnect()), { once: true });
    if (type !== "ESSAY") $("[data-confirm-hint]").textContent = "\u786E\u8BA4\u63D0\u4EA4\u672C\u5F20\u9898\u5361\uFF1F\u672A\u586B\u5199\u7684\u4F4D\u7F6E\u5C06\u4FDD\u7559\u4E3A\u7A7A\u3002";
    await api.refresh();
  }

  // extensions/packages/reading/practice-source.js
  var start = () => startPractice("READING", "\u9605\u8BFB\u7406\u89E3");
  return __toCommonJS(practice_source_exports);
})();

await QFPage.start();
