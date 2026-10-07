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

  // extensions/packages/reading/editor-source.js
  var editor_source_exports = {};
  __export(editor_source_exports, {
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
  function synchronizeTranslations(q, prompt, newId) {
    const parsed = markers(plain(prompt));
    if (parsed.errors.length) return null;
    const previous = groups(q), used = /* @__PURE__ */ new Set(), answers = spec(q).answers || [];
    const items = parsed.found.map((mark, index) => {
      const old = previous.find((item) => !used.has(item.id) && item.text === mark.value);
      if (old) used.add(old.id);
      return { id: old?.id || newId("item_"), number: index + 1, text: mark.value };
    });
    return { items, answers: items.map((item) => answers.find((a) => a.itemId === item.id) || { itemId: item.id, referenceAnswer: textContent("") }) };
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
  function editContent(node, value, onChange, formatting = true) {
    return QF.content.mountEditor(node, { value, onChange, formatting });
  }
  function bodyData(q, data) {
    return { payload: { kind: "EXTENSION", data } };
  }
  function answerData(data) {
    return { answerSpec: { kind: "EXTENSION", data } };
  }
  async function editorBase(label) {
    const page = await connectPage(QF);
    const reply = await page.question();
    if (!reply.ok) throw new Error(reply.error.message);
    let question = reply.data, revision = 0, capabilities = {};
    QF.layout.configure({ cardWidth: 820, maxCardWidth: "100%", horizontalAlign: "center", verticalAlign: "top", padding: 20 });
    QF.ui.configure({ typeLabel: false });
    $("[data-title]").textContent = label + "\u7F16\u8F91";
    async function update(patch) {
      const current = ++revision;
      question = { ...question, ...clone(patch) };
      const response = await page.edit(patch);
      if (current === revision) {
        if (!response.ok) {
          QF.ui.notify(response.error.message);
          const accepted = await page.question();
          if (accepted.ok) question = accepted.data;
        } else question = response.data;
      }
      return response;
    }
    const score = $("[data-score]");
    score.value = question.scoreSpec.defaultMaxScore;
    on(score, "input", () => {
      const value = Number(score.value);
      if (score.value.trim() && Number.isFinite(value) && value > 0) return update({ scoreSpec: { defaultMaxScore: value } });
    });
    async function refresh() {
      const context = await page.host();
      if (!context.ok) return;
      capabilities = context.data.capabilities;
      for (const node of QF.dom.root.querySelectorAll("input,select,textarea,button")) node.disabled = !capabilities.editQuestion;
    }
    page.subscribe(refresh);
    return { get question() {
      return question;
    }, update, refresh, get writable() {
      return capabilities.editQuestion;
    } };
  }

  // extensions/shared/reading-type-editor.js
  async function startEditor(type, label) {
    const editor = await editorBase(label), list = $("[data-items]"), mounted = [];
    $("[data-score-label]").textContent = type === "ESSAY" ? "\u5206\u503C" : "\u6BCF\u5C0F\u9898\u5206\u503C\uFF08\u6392\u5E8F\u63D0\u793A\u4E0D\u8BA1\u5206\uFF09";
    const patchData = (data, standard) => editor.update({ ...bodyData(editor.question, data), ...standard ? answerData(standard) : {} });
    function newGroup(number) {
      const prefix = type === "CLOZE" ? "blank_" : "item_", id = QF.ids.create(prefix);
      const options = ["A", "B", "C", "D"].map((letter) => ({ id: QF.ids.create("opt_"), content: textContent("\u9009\u9879 " + letter) }));
      return { id, number, ...type === "READING" ? { prompt: textContent("\u8BF7\u8F93\u5165\u5C0F\u9898\u9898\u5E72") } : {}, options };
    }
    async function articleChange(prompt) {
      const question = editor.question;
      if (type === "TRANSLATION") {
        const sync2 = synchronizeTranslations(question, prompt, QF.ids.create);
        const response = await editor.update({ prompt, ...sync2 ? { ...bodyData(question, { ...payload(question), items: sync2.items }), ...answerData({ ...spec(question), answers: sync2.answers }) } : {} });
        if (response.ok && sync2) items();
        return response;
      }
      if (type === "CLOZE") {
        const parsed = markers(plain(prompt), true), numbers = [...new Set(parsed.found.map((m) => m.number))].sort((a, b) => a - b);
        if (!parsed.errors.length && numbers.every((n, index) => n === index + 1)) {
          const previous = groups(question), blanks = numbers.map((number) => previous.find((b) => b.number === number) || newGroup(number));
          const answers = blanks.map((blank) => spec(question).answers.find((a) => a.blankId === blank.id) || { blankId: blank.id, correctOptionId: blank.options[0].id });
          const structural = blanks.length !== previous.length || blanks.some((b, n) => b.id !== previous[n]?.id);
          const response = await editor.update({ prompt, ...bodyData(question, { ...payload(question), blanks }), ...answerData({ ...spec(question), answers }) });
          if (response.ok && structural) items();
          return response;
        }
      }
      return editor.update({ prompt });
    }
    editContent($("[data-prompt]"), editor.question.prompt, articleChange);
    editContent($("[data-analysis]"), type === "CLOZE" ? textContent(plain(editor.question.analysis)) : editor.question.analysis, (analysis) => editor.update({ analysis }), type !== "CLOZE");
    function current(id) {
      return groups(editor.question).find((item) => item.id === id);
    }
    function updateGroup(id, patch) {
      const data = payload(editor.question), key = type === "CLOZE" ? "blanks" : "items";
      return patchData({ ...data, [key]: groups(editor.question).map((item) => item.id === id ? { ...item, ...patch } : item) });
    }
    function setCorrect(id, optionId) {
      const data = payload(editor.question), standard = spec(editor.question), key = type === "READING" ? "itemId" : "blankId";
      const answers = standard.answers.map((a) => a[key] === id ? { ...a, correctOptionId: optionId } : a);
      return patchData(type === "MATCHING" ? { ...data, blanks: data.blanks.map((b) => b.id === id && b.locked ? { ...b, givenOptionId: optionId } : b) } : data, { ...standard, answers });
    }
    function items() {
      mounted.splice(0).forEach((editor2) => editor2.destroy());
      list.replaceChildren();
      if (type === "ESSAY") {
        const reference = element("section", "qf-editor-section");
        reference.append(element("strong", "", "\u53C2\u8003\u7B54\u6848"));
        const body = element("div");
        reference.append(body);
        list.append(reference);
        mounted.push(editContent(body, spec(editor.question).referenceAnswer, (value) => editor.update(answerData({ ...spec(editor.question), referenceAnswer: value }))));
        return;
      }
      if (type === "MATCHING") {
        const slots = element("div", "qf-matching-slots");
        list.append(slots);
        groups(editor.question).forEach((item, index) => {
          const box = element("div", "qf-editor-order"), line = element("div", "qf-order-slot");
          line.append(element("span", "", index + 1 + "."));
          const select = element("select", "qf-letter-select");
          select.setAttribute("aria-label", "\u4F4D\u7F6E " + (index + 1) + " \u7684\u6B63\u786E\u7B54\u6848");
          payload(editor.question).options.forEach((option) => select.append(new Option(option.label, option.id)));
          select.value = spec(editor.question).answers.find((a) => a.blankId === item.id)?.correctOptionId || "";
          line.append(select);
          box.append(line);
          const lock = element("button", "", item.locked ? "\u{1F512} \u5DF2\u7ED9\u51FA" : "\u8BBE\u4E3A\u63D0\u793A");
          lock.type = "button";
          lock.setAttribute("aria-pressed", String(item.locked));
          box.append(lock);
          slots.append(box);
          on(select, "change", () => setCorrect(item.id, select.value));
          on(lock, "click", async () => {
            const data = payload(editor.question), old = data.blanks.find((b) => b.id === item.id), locked = !old.locked, givenOptionId = spec(editor.question).answers.find((a) => a.blankId === old.id)?.correctOptionId;
            await patchData({ ...data, blanks: data.blanks.map((b) => b.id === old.id ? { id: b.id, number: b.number, locked, ...locked ? { givenOptionId } : {} } : b) });
            items();
          });
        });
        list.append(element("p", "muted", `\u5DF2\u7ED9\u51FA ${groups(editor.question).filter((b) => b.locked).length} \u4E2A\u4F4D\u7F6E\uFF1B\u4FDD\u5B58\u65F6\u9700\u4E09\u4E2A\u63D0\u793A\uFF0C\u5269\u4F59\u4E94\u4E2A\u4F4D\u7F6E\u8BA1\u5206\u3002`));
        editor.refresh();
        return;
      }
      groups(editor.question).forEach((item) => {
        const section = element("section", "qf-editor-item");
        section.dataset.targetId = item.id;
        const heading = element("div", "qf-editor-item-header");
        heading.append(element("strong", "", (type === "CLOZE" ? "\u7A7A\u4F4D " : type === "TRANSLATION" ? "\u53E5\u5B50 " : "\u5C0F\u9898 ") + item.number));
        section.append(heading);
        list.append(section);
        if (type === "TRANSLATION") {
          section.append(element("p", "", item.text), element("strong", "", "\u53C2\u8003\u8BD1\u6587"));
          const reference = element("div");
          section.append(reference);
          mounted.push(editContent(reference, spec(editor.question).answers.find((a) => a.itemId === item.id)?.referenceAnswer, (value) => editor.update(answerData({ ...spec(editor.question), answers: spec(editor.question).answers.map((a) => a.itemId === item.id ? { ...a, referenceAnswer: value } : a) }))));
          return;
        }
        if (type === "READING") {
          const remove = element("button", "", "\u5220\u9664\u5C0F\u9898");
          remove.type = "button";
          heading.append(remove);
          on(remove, "click", async () => {
            const data = payload(editor.question), items2 = data.items.filter((i) => i.id !== item.id).map((i, n) => ({ ...i, number: n + 1 }));
            await patchData({ ...data, items: items2 }, { ...spec(editor.question), answers: spec(editor.question).answers.filter((a) => a.itemId !== item.id) });
            itemsRender();
          });
          const prompt = element("div");
          section.append(prompt);
          mounted.push(editContent(prompt, item.prompt, (value) => updateGroup(item.id, { prompt: value })));
        }
        item.options.forEach((option, index) => {
          const row = element("div", "qf-editor-row"), correct = element("input");
          correct.type = "radio";
          correct.name = "correct-" + item.id;
          correct.checked = spec(editor.question).answers.some((a) => (a.itemId || a.blankId) === item.id && a.correctOptionId === option.id);
          correct.setAttribute("aria-label", "\u5C06 " + String.fromCharCode(65 + index) + " \u8BBE\u4E3A\u6B63\u786E\u7B54\u6848");
          const input = element("input");
          input.type = "text";
          input.value = option.content.text;
          input.setAttribute("aria-label", "\u9009\u9879 " + String.fromCharCode(65 + index));
          row.append(correct, element("span", "", String.fromCharCode(65 + index) + "."), input);
          section.append(row);
          on(input, "input", () => updateGroup(item.id, { options: current(item.id).options.map((o) => o.id === option.id ? { ...o, content: textContent(input.value) } : o) }));
          on(correct, "change", () => setCorrect(item.id, option.id));
        });
      });
      editor.refresh();
    }
    const itemsRender = items;
    items();
    const add = $("[data-add-item]");
    add.hidden = type !== "READING";
    on(add, "click", async () => {
      const data = payload(editor.question), item = newGroup(data.items.length + 1);
      await patchData({ ...data, items: [...data.items, item] }, { ...spec(editor.question), answers: [...spec(editor.question).answers, { itemId: item.id, correctOptionId: item.options[0].id }] });
      items();
    });
    const sync = $("[data-sync]");
    sync.hidden = !["CLOZE", "TRANSLATION"].includes(type);
    on(sync, "click", () => articleChange(editor.question.prompt));
    $("[data-hint]").textContent = { CLOZE: "\u4F7F\u7528 {{1}}\u3001{{2}} \u6807\u8BB0\u7A7A\u4F4D\u3002\u4FEE\u6539\u6B63\u6587\u6807\u8BB0\u540E\u81EA\u52A8\u540C\u6B65\u9009\u9879\u7EC4\uFF1B\u91CD\u590D\u7F16\u53F7\u53EA\u8BA1\u4E00\u6B21\u3002", READING: "\u6587\u7AE0\u4E0E\u5C0F\u9898\u9898\u5E72\u652F\u6301\u5BCC\u6587\u672C\uFF0C\u9009\u9879\u4F7F\u7528\u666E\u901A\u6587\u5B57\u3002", MATCHING: "\u9898\u5E72\u76F4\u63A5\u586B\u5199 A\u2013H \u516B\u6BB5\u6750\u6599\uFF1B\u8BBE\u7F6E\u5B8C\u6574\u987A\u5E8F\uFF0C\u5E76\u9501\u5B9A\u4E09\u4E2A\u5DF2\u6709\u63D0\u793A\u3002", TRANSLATION: "\u4F7F\u7528 {{\u9700\u8981\u7FFB\u8BD1\u7684\u53E5\u5B50}} \u6807\u8BB0\u76EE\u6807\u53E5\uFF0C\u4E0D\u5199\u5E8F\u53F7\uFF1B\u81EA\u52A8\u6309\u987A\u5E8F\u7F16\u53F7\u3002\u53C2\u8003\u8BD1\u6587\u8DDF\u968F\u53E5\u5B50\u8EAB\u4EFD\u3002", ESSAY: "\u9898\u5E72\u3001\u6B63\u5F0F\u56DE\u7B54\u3001\u53C2\u8003\u7B54\u6848\u53CA\u89E3\u6790\u5747\u652F\u6301\u5BCC\u6587\u672C\u3002\u5C0F\u4F5C\u6587\u548C\u5927\u4F5C\u6587\u590D\u7528\u540C\u4E00\u9898\u578B\u3002" }[type];
    await editor.refresh();
  }

  // extensions/packages/reading/editor-source.js
  var start = () => startEditor("READING", "\u9605\u8BFB\u7406\u89E3");
  return __toCommonJS(editor_source_exports);
})();

await QFPage.start();
