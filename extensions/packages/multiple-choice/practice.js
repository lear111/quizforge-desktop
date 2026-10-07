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

  // extensions/packages/multiple-choice/practice-source.js
  var practice_source_exports = {};
  __export(practice_source_exports, {
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

  // extensions/packages/multiple-choice/practice-source.js
  async function start() {
    const page = await connectPage(QF);
    const multiple = true;
    const $ = QF.dom.$, on = QF.dom.on;
    const reply = await page.question();
    if (!reply.ok) throw new Error(reply.error.message);
    const question = reply.data;
    QF.layout.configure({ cardWidth: 720, maxCardWidth: "100%", horizontalAlign: "center", verticalAlign: "center", padding: 20 });
    QF.ui.configure({ typeLabel: false, position: false, score: false, state: false, submit: false, retry: false, confirmation: false, note: false, sources: false, draftToggle: true, draftToolbar: true, draftZoom: true });
    let confirming = false, actionBusy = false, refreshRevision = 0, answerRevision = 0, answerWrites = 0;
    $("[data-type-label]").textContent = multiple ? "\u591A\u9009\u9898" : "\u5355\u9009\u9898";
    $("[data-choice-hint]").textContent = multiple ? "\u8BF7\u9009\u62E9\u6240\u6709\u7B26\u5408\u9898\u610F\u7684\u9009\u9879" : "\u8BF7\u9009\u62E9\u4E00\u4E2A\u7B26\u5408\u9898\u610F\u7684\u9009\u9879";
    async function runAction(action) {
      if (actionBusy) return;
      actionBusy = true;
      try {
        await refresh();
        const response = await action();
        if (!response.ok) QF.ui.notify(response.error.message);
        return response;
      } finally {
        actionBusy = false;
        await refresh();
      }
    }
    on($("[data-submit-answer]"), "click", () => {
      confirming = true;
      $("[data-answer-confirm]").hidden = false;
      refresh().then(() => $("[data-confirm-answer]").focus());
    });
    on($("[data-cancel-answer]"), "click", () => {
      confirming = false;
      refresh();
    });
    on($("[data-confirm-answer]"), "click", () => {
      confirming = false;
      return runAction(() => page.submit());
    });
    on($("[data-retry-answer]"), "click", () => runAction(() => page.action("retry")));
    on($("[data-sources]"), "click", (event) => {
      const button = event.target.closest("[data-source-index]");
      if (button && !button.disabled) return runAction(() => page.action("openSource", { index: Number(button.dataset.sourceIndex) }));
    });
    $("[data-prompt]").textContent = question.prompt.text;
    const controls = [];
    question.payload.options.forEach((option, index) => {
      const row = document.createElement("label");
      row.className = "qf-choice-option";
      row.dataset.optionId = option.id;
      const input = document.createElement("input");
      input.type = multiple ? "checkbox" : "radio";
      input.name = "qf-choice-" + question.id;
      input.value = option.id;
      const letter = document.createElement("span");
      letter.className = "qf-option-letter";
      letter.textContent = String.fromCharCode(65 + index) + ".";
      letter.setAttribute("aria-hidden", "true");
      const text = document.createElement("span");
      text.className = "qf-option-text";
      text.textContent = option.content.text;
      input.setAttribute("aria-label", String.fromCharCode(65 + index) + ". " + option.content.text);
      const feedback = document.createElement("span");
      feedback.className = "qf-option-feedback";
      feedback.hidden = true;
      row.append(input, letter, text, feedback);
      $("[data-options]").append(row);
      controls.push({ option, input, row, feedback });
      on(input, "change", async () => {
        ++answerRevision;
        ++refreshRevision;
        ++answerWrites;
        const selectedOptionIds = controls.filter((c) => c.input.checked).map((c) => c.option.id);
        row.classList.toggle("selected", input.checked);
        try {
          const response = await page.write({ selectedOptionIds });
          if (!response.ok) QF.ui.notify(response.error.message);
        } finally {
          --answerWrites;
          if (!answerWrites) await refresh();
        }
      });
    });
    async function refresh() {
      const revision = ++refreshRevision, answerVersion = answerRevision;
      const [a, c, r, s, n] = await Promise.all([page.answer(), page.host(), page.result(), page.practiceState(), page.state()]);
      if (revision !== refreshRevision || answerVersion !== answerRevision) return;
      if (!a.ok || !c.ok || !r.ok || !s.ok) return;
      const selected = new Set(a.data.selectedOptionIds || []), result = r.data;
      const capabilities = c.data.capabilities, state = s.data;
      const readOnly = c.data.mode === "HISTORY" || c.data.mode === "PREVIEW";
      const permissions = c.data.permissions.granted;
      $("[data-question-position]").textContent = Number.isInteger(state.index) ? `\u7B2C ${state.index + 1} / ${state.total} \u9898` : "";
      $("[data-max-score]").textContent = `\u5206\u503C\uFF1A${state.maxScore ?? "\u672A\u8BBE\u7F6E"}`;
      $("[data-answer-state]").textContent = { UNANSWERED: "\u672A\u4F5C\u7B54", DRAFT: "\u5DF2\u9009\u62E9", SUBMITTED: "\u5DF2\u63D0\u4EA4", RETRYING: "\u91CD\u8BD5\u4E2D", REVISING: "\u4FEE\u8BA2\u4E2D" }[state.state] || "";
      if (!capabilities.submit || result || c.data.mode === "HISTORY" || c.data.mode === "PREVIEW") confirming = false;
      $("[data-answer-confirm]").hidden = !confirming;
      $("[data-submit-answer]").hidden = readOnly || !permissions.includes("practice.submit") || Boolean(result);
      $("[data-submit-answer]").disabled = actionBusy || confirming || !capabilities.submit || selected.size === 0;
      $("[data-retry-answer]").hidden = readOnly || !permissions.includes("practice.retry") || !result;
      $("[data-retry-answer]").disabled = actionBusy || !capabilities.retry;
      $("[data-confirm-answer]").disabled = actionBusy || !capabilities.submit || selected.size === 0;
      const sourceRows = $("[data-sources]");
      sourceRows.replaceChildren();
      const sources = n.ok ? n.data.sources : [];
      $("[data-source-section]").hidden = !sources.length;
      sources.forEach((source, index) => {
        const row = document.createElement("div");
        row.className = "qf-type-source";
        const button = document.createElement("button");
        button.type = "button";
        button.textContent = source.label;
        button.disabled = actionBusy || !capabilities.viewSources || !source.navigable;
        button.dataset.sourceIndex = index;
        const message = document.createElement("span");
        message.className = "muted";
        message.textContent = source.message;
        row.append(button, message);
        sourceRows.append(row);
      });
      $("[data-stage-note]").textContent = c.data.mode === "HISTORY" ? "\u5386\u53F2\u8349\u7A3F \xB7 \u53EA\u8BFB" : c.data.mode === "PREVIEW" ? "\u53EA\u8BFB\u9884\u89C8" : "\u8349\u7A3F\u81EA\u52A8\u4FDD\u5B58\uFF1B\u63D0\u4EA4\u540E\u51BB\u7ED3\uFF0C\u91CD\u8BD5\u4ECE\u7A7A\u767D\u8349\u7A3F\u5F00\u59CB\u3002";
      const correct = result?.reference?.answerSpec?.correctOptionIds || result?.correctOptionIds || [];
      for (const { option, input, row, feedback } of controls) {
        if (!answerWrites) input.checked = selected.has(option.id);
        input.disabled = actionBusy || confirming || !capabilities.editAnswer;
        row.classList.toggle("selected", input.checked);
        row.classList.toggle("correct", Boolean(result) && correct.includes(option.id));
        row.classList.toggle("incorrect", Boolean(result) && input.checked && !correct.includes(option.id));
        feedback.hidden = !result || !input.checked && !correct.includes(option.id);
        feedback.textContent = correct.includes(option.id) ? input.checked ? "\u6B63\u786E \xB7 \u5DF2\u9009" : "\u6B63\u786E\u7B54\u6848" : "\u4F60\u7684\u7B54\u6848";
      }
      $("[data-result]").hidden = !result;
      if (result) {
        $("[data-verdict]").textContent = result.status === "CORRECT" ? "\u56DE\u7B54\u6B63\u786E" : result.status === "INCORRECT" ? "\u56DE\u7B54\u9519\u8BEF" : "\u672A\u8BC4\u5206";
        $("[data-verdict]").className = result.status === "CORRECT" ? "correct" : "incorrect";
        $("[data-score]").textContent = "\u5F97\u5206\uFF1A" + (result.score ?? "\u672A\u8BC4\u5206") + " / " + result.maxScore;
        $("[data-correct-answer]").textContent = "\u6B63\u786E\u7B54\u6848\uFF1A" + question.payload.options.map((o, i) => correct.includes(o.id) ? String.fromCharCode(65 + i) : null).filter(Boolean).join("\u3001");
        QF.content.render($("[data-analysis]"), result.reference?.analysis || result.analysis || { kind: "TEXT", text: "" });
      }
    }
    page.subscribe(refresh);
    await refresh();
  }
  return __toCommonJS(practice_source_exports);
})();

await QFPage.start();
