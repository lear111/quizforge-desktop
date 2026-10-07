/** DOM-free SDK 2 rules adapter. Also loaded by the hidden JavaFX grading engine. */
(function installQuestionRules(global) {
  if (global.QuestionRules?.apiMajor === 2) return;
  const clone = value => JSON.parse(JSON.stringify(value));
  const operations = ['createDraft', 'validate', 'duplicate', 'snapshot', 'targets', 'validateAnswer', 'grade'];
  function createRegistry() {
    const definitions = Object.create(null), templates = Object.create(null);
    function allocate(source, ids) {
      const question = clone(source), replacements = new Map([[question.id, ids.question]]);
      question.id = ids.question;
      let counter = 0;
      function rekey(value) {
        if (Array.isArray(value)) { value.forEach(rekey); return; }
        if (!value || typeof value !== 'object') return;
        if (typeof value.id === 'string' && value.id.startsWith('opt_')) {
          const previous = value.id, next = (ids.opts || ids.options || [])[counter] || `opt_${ids.question}_${counter + 1}`;
          counter++; replacements.set(previous, next); value.id = next;
        }
        Object.values(value).forEach(rekey);
      }
      rekey(question.payload);
      function references(value) {
        if (typeof value === 'string') return replacements.get(value) || value;
        if (Array.isArray(value)) return value.map(references);
        if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, references(v)]));
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
        const q = typeof template === 'string' ? JSON.parse(template) : template;
        if (!q || q.type !== type || !q.id || !q.payload || !q.answerSpec || !q.scoreSpec) throw new TypeError('Invalid full Question default.json');
        templates[type] = clone(q);
      },
      register(type, definition) {
        if (!type || definitions[type]) throw new TypeError('Duplicate or invalid rules type');
        for (const operation of operations) if (typeof definition[operation] !== 'function') throw new TypeError(`Missing rules operation: ${operation}`);
        definitions[type] = Object.freeze(definition);
      },
      defineQuestionType(definition) {
        const type = definition.type;
        if (typeof type !== 'string' || typeof definition.grade !== 'function') throw new TypeError('Type and grade are required');
        const targets = q => definition.targets ? definition.targets(q) : [{ id: q.id, number: 1, label: '', locked: false, gradable: true }];
        const maximum = q => definition.maxScore ? definition.maxScore(clone(q)) : q.scoreSpec?.defaultMaxScore ?? q.maxScore ?? null;
        const allocateQuestion = (question, ids) => definition.allocateQuestion ? definition.allocateQuestion(clone(question), clone(ids)) : allocate(question, ids);
        registry.register(type, {
          createDraft({ ids }) {
            if (!templates[type]) throw new TypeError(`Missing default.json for ${type}`);
            return allocateQuestion(templates[type], ids);
          },
          duplicate({ question, ids }) { return allocateQuestion(question, ids); },
          validate({ question }) { return { errors: definition.validate?.(clone(question)) || [] }; },
          validateAnswer({ question, answer }) {
            const result = definition.validateAnswer?.(clone(question), clone(answer || {}));
            return result || { errors: [], empty: !Object.keys(answer || {}).length };
          },
          targets({ question }) { return { targets: targets(question) }; },
          snapshot({ question }) { return { targets: targets(question), maxScore: maximum(question), ...(definition.publicPayload?{publicPayload:definition.publicPayload(clone(question))}:{}) }; },
          grade(input) {
            const maxScore = input.maxScore ?? maximum(input.question);
            let reported = null, active = true;
            const ctx = Object.freeze({ question: clone(input.question), answer: clone(input.answer), maxScore,
              reportResult(result) {
                if (!active || reported) throw new TypeError('Result already reported or grading finished');
                const score = result.score;
                if (score !== null && (!Number.isFinite(score) || score < 0 || maxScore == null || score > maxScore)) throw new TypeError('Invalid reported score');
                reported = { status: score == null ? 'UNSCORED' : score === maxScore ? 'CORRECT' : 'INCORRECT', score, maxScore,
                  ...(result.feedback ? { feedback: clone(result.feedback) } : {}) };
                return { ok: true, data: { staged: true } };
              }
            });
            try {
              const reply = definition.grade(ctx);
              if (reply?.then) throw new TypeError('Async grading is not implemented in SDK 2.0');
              if (reply?.ok === false) throw new TypeError(reply.error?.message || 'Grading failed');
              if (!reported) throw new TypeError('Grading result missing');
              return reported;
            } finally { active = false; }
          }
        });
      },
      has(type) { return Boolean(definitions[type]); },
      invoke(type, operation, input) {
        if (!definitions[type] || !operations.includes(operation)) throw new TypeError('Unsupported rules operation');
        const result = definitions[type][operation](typeof input === 'string' ? JSON.parse(input) : input);
        if (!result || Array.isArray(result) || typeof result !== 'object' || result.then) throw new TypeError('Rules must return a synchronous JSON object');
        return JSON.stringify(result);
      }
    };
    return Object.freeze(registry);
  }
  global.QuestionRules = createRegistry();
  global.QF = Object.freeze({ defineQuestionType: definition => global.QuestionRules.defineQuestionType(definition),
    installDefaultQuestion: (type, template) => global.QuestionRules.installDefaultQuestion(type, template) });
})(typeof window === 'undefined' ? globalThis : window);
