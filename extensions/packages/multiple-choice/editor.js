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

  // extensions/packages/multiple-choice/editor-source.js
  var editor_source_exports = {};
  __export(editor_source_exports, {
    start: () => start
  });

  // extensions/packages/multiple-choice/lib/page-client.js
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

  // extensions/packages/multiple-choice/editor-source.js
  async function start() {
    const page = await connectPage(QF);
    const multiple = true;
    const $ = QF.dom.$, on = QF.dom.on;
    const reply = await page.question();
    if (!reply.ok) throw new Error(reply.error.message);
    let question = reply.data;
    QF.layout.configure({ cardWidth: 720, maxCardWidth: "100%", horizontalAlign: "center", verticalAlign: "top", padding: 20 });
    QF.ui.configure({ title: false, save: false, position: false, typeLabel: false, add: false, duplicate: false, delete: false, sources: false });
    $("[data-type-title]").textContent = multiple ? "\u591A\u9009\u9898\u7F16\u8F91" : "\u5355\u9009\u9898\u7F16\u8F91";
    $("[data-options-hint]").textContent = multiple ? "\u52FE\u9009\u6240\u6709\u6B63\u786E\u7B54\u6848\uFF1B\u9898\u5E72\u4E0E\u9009\u9879\u4F7F\u7528\u666E\u901A\u6587\u672C\u3002" : "\u70B9\u51FB\u5706\u5708\u8BBE\u7F6E\u6B63\u786E\u7B54\u6848\uFF1B\u9898\u5E72\u4E0E\u9009\u9879\u4F7F\u7528\u666E\u901A\u6587\u672C\u3002";
    let actionBusy = false, shellRevision = 0;
    let capabilities = {};
    async function shellState() {
      const revision = ++shellRevision;
      const [response, context] = await Promise.all([page.state(), page.host()]);
      if (revision !== shellRevision) return;
      capabilities = context.ok ? context.data.capabilities : {};
      refreshFields();
      const available = response.ok;
      $("[data-bank-save]").hidden = !available;
      $("[data-bank-actions]").hidden = !available;
      $("[data-source-section]").hidden = !available;
      if (!available) return;
      const state = response.data;
      $("[data-bank-position]").textContent = `\u7B2C ${state.index + 1} / ${state.count} \u9898`;
      const select = $("[data-bank-add]");
      select.replaceChildren(new Option("\uFF0B \u65B0\u589E\u9898\u76EE", ""));
      state.types.forEach((type) => select.append(new Option(type.label, type.id)));
      const rows = $("[data-sources]");
      rows.replaceChildren();
      state.sources.forEach((source, index) => {
        const row = document.createElement("div");
        row.className = "qf-type-source";
        const open = document.createElement("button");
        open.type = "button";
        open.textContent = source.label;
        open.disabled = actionBusy || !capabilities.viewSources || !source.navigable;
        open.title = source.message;
        open.dataset.sourceAction = "open";
        open.dataset.sourceIndex = index;
        const message = document.createElement("span");
        message.className = "muted";
        message.textContent = source.message;
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "\u79FB\u9664";
        remove.setAttribute("aria-label", "\u79FB\u9664\u6765\u6E90 " + source.label);
        remove.dataset.sourceAction = "remove";
        remove.dataset.sourceIndex = index;
        remove.disabled = actionBusy || !capabilities.manageSources;
        row.append(open, message, remove);
        rows.append(row);
      });
      const granted = context.ok ? context.data.permissions.granted : [];
      for (const [selector, permission] of [["[data-bank-save]", "bank.save"], ["[data-bank-add]", "bank.add"], ["[data-bank-duplicate]", "bank.duplicate"], ["[data-bank-delete]", "bank.delete"], ["[data-delete-accept]", "bank.delete"], ["[data-source-add]", "sources.manage"]])
        $(selector).disabled = actionBusy || !state.editable || !granted.includes(permission);
      $("[data-source-link]").disabled = actionBusy || !capabilities.manageSources;
    }
    async function runAction(action) {
      if (actionBusy) return;
      actionBusy = true;
      try {
        await shellState();
        const response = await action();
        if (!response.ok) QF.ui.notify(response.error.message);
        return response;
      } finally {
        actionBusy = false;
        await shellState();
      }
    }
    on($("[data-bank-save]"), "click", () => runAction(() => page.commit()));
    on($("[data-bank-add]"), "change", () => {
      const type = $("[data-bank-add]").value;
      if (type) return runAction(() => page.action("addQuestion", { type }));
    });
    on($("[data-bank-duplicate]"), "click", () => runAction(() => page.action("duplicateQuestion")));
    on($("[data-bank-delete]"), "click", () => {
      $("[data-delete-confirm]").hidden = false;
    });
    on($("[data-delete-cancel]"), "click", () => {
      $("[data-delete-confirm]").hidden = true;
    });
    on($("[data-delete-accept]"), "click", () => runAction(() => page.action("deleteQuestion")));
    async function addSource() {
      const link = $("[data-source-link]").value;
      const response = await runAction(() => page.action("addSource", { link }));
      if (response?.ok) $("[data-source-link]").value = "";
    }
    on($("[data-source-add]"), "click", addSource);
    on($("[data-sources]"), "click", (event) => {
      const button = event.target.closest("[data-source-action]");
      if (button && !button.disabled) return runAction(() => button.dataset.sourceAction === "open" ? page.action("openSource", { index: Number(button.dataset.sourceIndex) }) : page.action("removeSource", { index: Number(button.dataset.sourceIndex) }));
    });
    on($("[data-source-link]"), "keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        return addSource();
      }
    });
    let editRevision = 0;
    async function update(patch) {
      const revision = ++editRevision;
      question = { ...question, ...patch };
      const response = await page.edit(patch);
      if (response.ok) {
        if (revision === editRevision) question = response.data;
      } else {
        QF.ui.notify(response.error.message);
        const saved = await page.question();
        if (revision === editRevision && saved.ok) question = saved.data;
      }
      return response;
    }
    function refreshFields() {
      const disabled = actionBusy || !capabilities.editQuestion;
      for (const input of QF.dom.root.querySelectorAll("textarea, input, [data-add-option]")) {
        if (!input.matches("[data-source-link]")) input.disabled = disabled;
      }
      for (const button of QF.dom.root.querySelectorAll("[data-option-remove]"))
        button.disabled = disabled || question.payload.options.length <= 2;
    }
    function updateScore() {
      const value = $("[data-score]").value;
      const score = Number(value);
      if (!value.trim() || !Number.isFinite(score) || score <= 0) {
        QF.ui.notify("\u5206\u503C\u5FC5\u987B\u662F\u5927\u4E8E 0 \u7684\u6570\u5B57");
        return;
      }
      return update({ scoreSpec: { ...question.scoreSpec, defaultMaxScore: score } });
    }
    $("[data-prompt]").value = question.prompt.text;
    on($("[data-prompt]"), "input", () => update({ prompt: { kind: "TEXT", text: $("[data-prompt]").value } }));
    $("[data-score]").value = question.scoreSpec.defaultMaxScore;
    on($("[data-score]"), "input", updateScore);
    function refreshCorrect() {
      question.payload.options.forEach((option, index) => {
        const row = $("[data-options]").children[index];
        const input = row.querySelector("input");
        input.checked = question.answerSpec.correctOptionIds.includes(option.id);
        row.classList.toggle("is-correct", input.checked);
      });
    }
    function options() {
      const box = $("[data-options]");
      box.replaceChildren();
      question.payload.options.forEach((option, index) => {
        const row = document.createElement("div");
        row.className = "qf-choice-edit-row";
        const correct = document.createElement("input");
        correct.type = multiple ? "checkbox" : "radio";
        correct.name = "qf-correct-" + question.id;
        correct.checked = question.answerSpec.correctOptionIds.includes(option.id);
        correct.title = "\u8BBE\u4E3A\u6B63\u786E\u7B54\u6848";
        row.classList.toggle("is-correct", correct.checked);
        const letter = document.createElement("span");
        letter.className = "qf-option-letter";
        letter.textContent = String.fromCharCode(65 + index) + ".";
        correct.setAttribute("aria-label", letter.textContent + " \u6B63\u786E\u7B54\u6848");
        const text = document.createElement("textarea");
        text.rows = 1;
        text.value = option.content.text;
        text.setAttribute("aria-label", letter.textContent + " \u9009\u9879");
        const remove = document.createElement("button");
        remove.type = "button";
        remove.dataset.optionRemove = "";
        remove.textContent = "\u5220\u9664";
        remove.disabled = question.payload.options.length <= 2;
        row.append(correct, letter, text, remove);
        box.append(row);
        on(text, "input", () => {
          const next = question.payload.options.map((o) => o.id === option.id ? { ...o, content: { kind: "TEXT", text: text.value } } : o);
          return update({ payload: { kind: "CHOICE", options: next } });
        });
        on(correct, "change", async () => {
          const ids = new Set(question.answerSpec.correctOptionIds);
          if (multiple) {
            correct.checked ? ids.add(option.id) : ids.delete(option.id);
          } else {
            ids.clear();
            ids.add(option.id);
          }
          await update({ answerSpec: { kind: "CHOICE", correctOptionIds: [...ids] } });
          refreshCorrect();
        });
        on(remove, "click", async () => {
          await update({ payload: { kind: "CHOICE", options: question.payload.options.filter((o) => o.id !== option.id) }, answerSpec: { kind: "CHOICE", correctOptionIds: question.answerSpec.correctOptionIds.filter((id) => id !== option.id) } });
          options();
        });
      });
      refreshFields();
    }
    options();
    on($("[data-add-option]"), "click", async () => {
      await update({ payload: { kind: "CHOICE", options: [...question.payload.options, { id: QF.ids.create("opt_"), content: { kind: "TEXT", text: "" } }] } });
      options();
    });
    QF.content.mountEditor($("[data-analysis]"), { value: question.analysis, formatting: true, onChange: (analysis) => update({ analysis }) });
    page.subscribe(shellState);
    await shellState();
  }
  return __toCommonJS(editor_source_exports);
})();

await QFPage.start();
