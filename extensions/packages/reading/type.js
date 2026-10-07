(() => {
  // extensions/shared/legacy-data.js
  var clone = (value) => JSON.parse(JSON.stringify(value));
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
      const start = i;
      let end = i + 2;
      while (end < text.length && text.slice(end, end + 2) !== "}}") end++;
      if (end === text.length) {
        errors.push("\u6807\u8BB0\u7F3A\u5C11 }}");
        break;
      }
      const value = text.slice(start + 2, end);
      if (value.includes("{{") || !value.trim() || numeric && !/^[1-9][0-9]*$/.test(value)) errors.push("\u6807\u8BB0\u5185\u5BB9\u65E0\u6548");
      else found.push({ start, end: end + 2, value, number: numeric ? Number(value) : found.length + 1 });
      i = end + 1;
    }
    return { found, errors };
  }
  function allocateQuestion(source, ids) {
    const q = clone(source), mapping = /* @__PURE__ */ new Map([[q.id, ids.question]]), counters = { opt: 0, blank: 0, item: 0 };
    q.id = ids.question;
    function walk(value) {
      if (Array.isArray(value)) {
        value.forEach(walk);
        return;
      }
      if (!value || typeof value !== "object" || ["TEXT", "RICH", "DOCUMENT"].includes(value.kind)) return;
      const prefix = typeof value.id === "string" ? Object.keys(counters).find((p) => value.id.startsWith(p + "_")) : null;
      if (prefix) {
        const n = counters[prefix]++, fresh = ids[prefix + "s"]?.[n] || `${prefix}_${ids.question}_${n + 1}`;
        mapping.set(value.id, fresh);
        value.id = fresh;
      }
      Object.values(value).forEach(walk);
    }
    function references(value) {
      if (typeof value === "string") return mapping.get(value) || value;
      if (Array.isArray(value)) return value.map(references);
      if (!value || typeof value !== "object" || ["TEXT", "RICH", "DOCUMENT"].includes(value.kind)) return value;
      return Object.fromEntries(Object.entries(value).map(([key, v]) => [key, references(v)]));
    }
    walk(q.payload);
    q.payload = references(q.payload);
    q.answerSpec = references(q.answerSpec);
    return q;
  }
  function validateSelection(question, answer, key) {
    const errors = [], items = groups(question), p = payload(question);
    for (const [id, value] of Object.entries(answer)) {
      const group = items.find((item) => item.id === id);
      if (!group || typeof value !== "string") errors.push("\u4F5C\u7B54\u5305\u542B\u672A\u77E5\u4F4D\u7F6E");
      else if (group.locked) errors.push("\u63D0\u793A\u4F4D\u7F6E\u4E0D\u53EF\u63D0\u4EA4\u4F5C\u7B54");
      else if (!(group.options || p.options || []).some((o) => o.id === value)) errors.push("\u4F5C\u7B54\u5F15\u7528\u672A\u77E5\u9009\u9879");
      else if (key === "MATCHING" && items.some((b) => b.locked && spec(question).answers?.some((a) => a.blankId === b.id && a.correctOptionId === value))) errors.push("\u63D0\u793A\u5B57\u6BCD\u4E0D\u53EF\u91CD\u590D\u9009\u62E9");
    }
    return { errors, empty: Object.keys(answer).length === 0 };
  }
  function gradeSelection({ question, answer, maxScore, reportResult }) {
    const items = groups(question).filter((i) => !i.locked), answers = spec(question).answers || [];
    const right = items.filter((item) => answers.some((a) => (a.blankId || a.itemId) === item.id && a.correctOptionId === answer[item.id])).length;
    return reportResult({ score: right === items.length ? maxScore : Number((maxScore * right / items.length).toFixed(8)) });
  }

  // extensions/shared/reading-type-rules.js
  function defineReadingType(type) {
    const objective = ["CLOZE", "READING", "MATCHING"].includes(type);
    QF.defineQuestionType({
      type,
      allocateQuestion,
      maxScore(q) {
        return q.scoreSpec.defaultMaxScore * (type === "ESSAY" ? 1 : groups(q).filter((i) => !i.locked).length);
      },
      targets(q) {
        return type === "ESSAY" ? [{ id: q.id, number: 1, locked: false, gradable: true }] : groups(q).filter((i) => !i.locked).map((i, index) => ({ id: i.id, number: index + 1, locked: false, gradable: true }));
      },
      publicPayload(q) {
        if (type !== "MATCHING") return q.payload;
        const data = payload(q), answers = spec(q).answers || [];
        return { kind: "EXTENSION", data: { ...data, blanks: data.blanks.map((b) => ({ ...b, ...b.locked ? { givenOptionId: answers.find((a) => a.blankId === b.id)?.correctOptionId } : {} })) } };
      },
      validate(q) {
        const errors = [], data = payload(q), standard = spec(q), items = groups(q);
        if (!plain(q.prompt).trim()) errors.push("\u8BF7\u586B\u5199\u9898\u5E72");
        if (!(q.scoreSpec.defaultMaxScore > 0)) errors.push("\u5206\u503C\u5FC5\u987B\u4E3A\u6B63\u6570");
        if (type === "ESSAY") return errors;
        if (!items.length) errors.push("\u81F3\u5C11\u9700\u8981\u4E00\u9053\u5C0F\u9898");
        if (new Set(items.map((i) => i.id)).size !== items.length) errors.push("\u5C0F\u9898\u6807\u8BC6\u91CD\u590D");
        if (items.some((i, n) => i.number !== n + 1)) errors.push("\u5C0F\u9898\u7F16\u53F7\u9700\u8981\u8FDE\u7EED");
        if (objective) {
          const options = type === "MATCHING" ? data.options : items.flatMap((i) => i.options);
          if (new Set(options.map((o) => o.id)).size !== options.length) errors.push("\u9009\u9879\u6807\u8BC6\u91CD\u590D");
          if (type !== "MATCHING" && options.some((o) => o.content?.kind !== "TEXT" || !o.content.text.trim())) errors.push("\u9009\u9879\u5FC5\u987B\u4E3A\u975E\u7A7A\u666E\u901A\u6587\u672C");
          if (type !== "MATCHING" && items.some((i) => i.options.length < 2)) errors.push("\u6BCF\u9053\u5C0F\u9898\u81F3\u5C11\u9700\u8981\u4E24\u4E2A\u9009\u9879");
          if (standard.answers?.length !== items.length || items.some((i) => standard.answers.filter((a) => (a.blankId || a.itemId) === i.id).length !== 1)) errors.push("\u6BCF\u9053\u5C0F\u9898\u9700\u8981\u4E00\u4E2A\u6807\u51C6\u7B54\u6848");
          if (items.some((i) => !(i.options || data.options || []).some((o) => o.id === standard.answers?.find((a) => (a.blankId || a.itemId) === i.id)?.correctOptionId))) errors.push("\u6B63\u786E\u7B54\u6848\u5F15\u7528\u4E86\u65E0\u6548\u9009\u9879");
        }
        if (type === "CLOZE") {
          const parsed = markers(plain(q.prompt), true);
          errors.push(...parsed.errors);
          const numbers = [...new Set(parsed.found.map((m) => m.number))].sort((a, b) => a - b);
          if (numbers.length !== items.length || numbers.some((n, index) => n !== index + 1)) errors.push("\u6B63\u6587\u6807\u8BB0\u4E0E\u7A7A\u4F4D\u987B\u4E3A\u8FDE\u7EED\u7684 {{1}}\u3001{{2}}\u2026");
        }
        if (type === "READING" && items.some((i) => !plain(i.prompt).trim())) errors.push("\u8BF7\u586B\u5199\u6BCF\u9053\u5C0F\u9898\u7684\u9898\u5E72");
        if (type === "MATCHING") {
          if (items.length !== 8 || data.options.length !== 8 || data.options.some((o, n) => o.label !== String.fromCharCode(65 + n))) errors.push("\u6BB5\u843D\u6392\u5E8F\u56FA\u5B9A\u4E3A A\u2013H \u516B\u4E2A\u9009\u9879");
          if (items.filter((i) => i.locked).length !== 3) errors.push("\u8BF7\u8BBE\u7F6E\u4E09\u4E2A\u63D0\u793A\u4F4D\u7F6E");
          if (new Set((standard.answers || []).map((a) => a.correctOptionId)).size !== 8) errors.push("\u6807\u51C6\u7B54\u6848\u5E94\u4E3A\u516B\u4E2A\u5B57\u6BCD\u7684\u5B8C\u6574\u6392\u5217");
        }
        if (type === "TRANSLATION") {
          const parsed = markers(plain(q.prompt));
          errors.push(...parsed.errors);
          if (parsed.found.length !== items.length || items.some((item, n) => item.text !== parsed.found[n]?.value)) errors.push("\u8BD1\u6587\u4F4D\u7F6E\u4E0E\u6B63\u6587\u6807\u8BB0\u4E0D\u4E00\u81F4\uFF0C\u8BF7\u540C\u6B65\u6807\u8BB0");
          if (standard.answers?.length !== items.length || items.some((i) => standard.answers.filter((a) => a.itemId === i.id).length !== 1)) errors.push("\u8BF7\u4E3A\u6BCF\u53E5\u4FDD\u7559\u5BF9\u5E94\u7684\u53C2\u8003\u8BD1\u6587");
        }
        return errors;
      },
      validateAnswer(q, a) {
        if (objective) return validateSelection(q, a, type);
        const check = (value) => value && ["TEXT", "CANVAS_DOCUMENT"].includes(value.kind) && typeof value.text === "string" && (value.kind !== "CANVAS_DOCUMENT" || typeof value.document === "string");
        if (type === "ESSAY") return { errors: check(a) ? [] : ["\u65E0\u6548\u7684\u4F5C\u6587\u7B54\u6848"], empty: !a.text?.trim() && !(a.document && a.document.includes('"type":"image"')) };
        const errors = [];
        for (const [id, value] of Object.entries(a)) if (!groups(q).some((i) => i.id === id) || !check(value)) errors.push("\u8BD1\u6587\u5305\u542B\u65E0\u6548\u53E5\u5B50\u6216\u7B54\u6848");
        return { errors, empty: !Object.values(a).some((v) => v.text?.trim()) };
      },
      grade(ctx) {
        return objective ? gradeSelection(ctx) : ctx.reportResult({ score: null });
      }
    });
  }

  // extensions/packages/reading/type-source.js
  defineReadingType("READING");
})();
