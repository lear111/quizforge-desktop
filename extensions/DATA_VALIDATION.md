# 宿主数据校验

> 2026-10-07 整理。适用于 HTML SDK 2.x，当前新页面使用 [SDK 2.3 精简接口](SIMPLE_PAGE_API.md)。Schema 是宿主执行的约束，扩展业务校验不能取消它。

## 执行位置

| 数据                      | 检查时机                                                                          | 拒绝后的行为                                           |
| ------------------------- | --------------------------------------------------------------------------------- | ------------------------------------------------------ |
| Schema 与`default.json` | 安装审查、安装、扫描已安装包、加载开发目录                                        | 拒绝候选；不执行扩展代码、不替换已安装包或上次可用页面 |
| 完整题目                  | 编辑更新、编辑保存、读取题库、创建/复制、生成快照、调用规则、网页预览投影前       | 保留上次接受的题目草稿；不修改题库                     |
| 用户作答                  | SDK 2.3 `save draft/submit`、宿主保存草稿、调用校验/评分规则                     | 保留已接受答案；不写入非法答案                         |
| 规则返回值                | `validate`、`validateAnswer`、`targets`、`snapshot`、`grade` 的返回边界 | 拒绝无效返回值；评分失败不产生作答记录或消耗重试状态   |

浏览区与独立开发预览的 Schema 编译及校验在可信宿主创建的独立 Worker 中执行，题型 iframe 不持有校验器；桌面持久化与评分再次在 Java 宿主检查。扩展不能通过改写自己的 `validate` 或规则运行环境绕过这些检查。Worker 只加载应用打包的校验器，没有扩展脚本或原生桥。

## 计算预算（2026-10-06 审查修复）

- 浏览器编译预算 5 秒，每次校验预算 1.5 秒；超时销毁整个 Worker，拒绝排队请求，保留上次接受的数据。最多 16 个待处理请求。不能使用 Worker 的页面拒绝校验，不回退到可信 UI 线程同步执行。
- 单份 Schema 最多 256 KiB 字符，保留 4096 节点/32 层限制；引用展开最多 16384 步，重复引用也计数，防止小型 DAG 指数展开。
- `pattern` 与 `patternProperties` 合计最多 64 个，单个最多 256 字符。采用浏览器/Java 一致的保守正则子集：禁止分组、分支、反向引用、复杂转义、嵌套/交集字符类；最多一个量词，含量词必须以 `^`、`$` 锚定，数字量词边界最多 1024。简单 `^[A-Z]+$` 可用，`^(a+)+$`、`a+b` 等被拒绝。
- 此安全限制也在 Java 安装、读取、编辑和保存校验中执行，不能通过直接导入包绕过。复杂业务文本条件应在有超时保护的规则运行时实现。
- 页面销毁时终止校验 Worker。热更新后的旧 Worker 在旧题卡完成已受理操作并释放后回收，不中断其合法回执。

浏览器包安装、开发替换与题目预览投影可能异步完成，调用方需 `await`，验证通过后才登记/替换定义。失败保留上次可用版本。新页面使用 `QF.save`；旧包的 `QF.editor/answer` 继续走兼容适配，二者都受相同宿主数据约束。

每个安装版本持有自己的 Schema。旧练习的答案与评分仍由冻结版本检查；新一轮使用当前版本。主浏览区开发热更新只更新展示资产，不替换安装版本的 Schema；独立开发预览才会更新 Schema 并重置测试作答。

## Schema 约定

- `questionSchema` 检查完整 Question，即 `default.json` 与编辑 SDK 使用的数据结构。`answerSchema` 检查用户作答对象，与 `answerSpec`（标准答案）不同。
- 当前支持 **JSON Schema Draft-07**。省略 `$schema` 时默认 Draft-07；也接受 `http://json-schema.org/draft-07/schema#` 与其 HTTPS 写法。其他方言应先更新宿主支持，不能混用关键字。
- 支持 Draft-07 的类型、必填、额外属性、枚举、常量、长度、数量、数值、组合、条件、依赖、`definitions` 与本文件内 JSON Pointer `$ref` 等约束。根必须为 Schema 对象；子 Schema 可以是布尔值。
- `$ref` 只接受 `#/...` 且必须指向本文件中的 Schema 节点。不支持远程引用、文件引用、跨文件引用、`$id`、循环引用或 `$ref` 的断言兄弟字段。不会发起网络请求或读取外部文件。
- 未知关键字（包括拼写错误）直接拒绝；Schema 的结构也由标准 meta-schema 校验。深度及引用链最长 32，最多 4096 个 Schema 节点。
- `format`、`contentEncoding`、`contentMediaType` 作为注解；不会验证邮箱、日期或解码内容。需要此类约束时另写业务校验。`default` 与 `examples` 不会填充字段。
- 不做类型转换、补默认值或删除额外字段。Schema 未禁止额外属性时，它们可通过 Schema，但宿主保留的身份、来源等字段仍不能由题型更新替换。自定义题目数据应存于 `payload.data` 和 `answerSpec.data`。
- 桌面数值必须有限。正则应采用 JavaScript 与 Java 都支持的写法；桌面与浏览器分别使用各自的正则引擎，不应依赖某一引擎特有语法。

即使 Schema 很宽松，完整题目仍必须具有非空 `id`、匹配题型的 `type`，以及对象形式的 `prompt`、`payload`、`answerSpec`、`scoreSpec`。`scoreSpec.defaultMaxScore` 必须为正有限数。已解析的内容仍接受宿主内容格式与业务校验。

示例作答 Schema：

```json
{
  "type": "object",
  "required": ["selectedOptionIds"],
  "additionalProperties": false,
  "properties": {
    "selectedOptionIds": {
      "type": "array",
      "items": { "type": "string" },
      "uniqueItems": true
    }
  }
}
```

结构校验不等于业务校验：例如选项 ID 是否属于当前题目、单选是否只选一项，仍由题型规则校验。扩展自己的 `validate` / `validateAnswer` 可以增加约束，不能取消 Schema 或宿主的约束。

## 空答案

`{}` 是宿主保留的“未作答”状态，首次打开、清空和重试可以使用它。它不必满足扩展的 `required`，也不调用扩展的 `validateAnswer`；宿主直接认定为空，不能提交评分。

其他对象必须符合 `answerSchema`。例如 `{ "selectedOptionIds": [] }` 先通过 Schema，再由业务校验返回 `empty:true`。不能把空字符串、数组或 `null` 作为 SDK 作答对象。

## 规则返回约束

| 操作                            | 宿主要求                                                                                                                                                    |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `createDraft` / `duplicate` | 完整题目符合对应版本的`questionSchema`（桌面默认模板创建/复制亦检查）                                                                                     |
| `validate`                    | `errors` 必须为字符串数组                                                                                                                                 |
| `validateAnswer`              | `errors` 为字符串数组，`empty` 为布尔值                                                                                                                 |
| `targets`                     | `targets` 为对象数组；`id` 非空且唯一，题号为正整数且唯一；可选 `locked` / `gradable` 为布尔值，`label` 为文本                                    |
| `snapshot`                    | 可选`maxScore` 为正有限数；可选 `targets` 同上；不提供时使用题目分值及 targets 规则                                                                     |
| `grade`                       | `status` 仅为 `CORRECT` / `INCORRECT` / `UNSCORED`；`score` 必填，评分时为范围内有限数，未评分时为 `null`；可选 `maxScore` 必须等于冻结最大分 |

`CORRECT` 必须满分，`INCORRECT` 不能满分。允许部分得分。扩展不能增加冻结最大分。扩展附加的 JSON 信息不会改变宿主题目身份、权限或练习状态；`feedback` 暂不写入桌面历史。

## 错误与恢复

```js
const reply = await QF.save({purpose: 'draft', data: {answer: {selectedOptionIds: [42]}}});
// reply:
{
  ok: false,
  error: {
    code: "DATA_VALIDATION_FAILED",
    message: "/answer/selectedOptionIds/0: must be string",
    retryable: false,
    issues: [{path: "/answer/selectedOptionIds/0", message: "must be string"}]
  }
}
```

`issues[].path` 为 JSON Pointer；前缀可能是 `/question`、`/answer`、`/schemas` 或 `/rules`。具体描述文本可能随实现不同，开发者应根据错误码与路径定位，不解析自然语言。每次最多返回 32 项 Schema 错误。

更新失败后宿主保留上次接受的值；新页面结合保存 Reply、本地输入与后续 onLoad 上下文修正后重新保存，不能调用未暴露的旧 editor.getData/answer.get。校验失败不会把错误数据发布到题库或答案草稿。INVALID_REQUEST 用于精简请求白名单检查，INVALID_ARGUMENT 用于底层 JSON 传输或方法参数检查；权限、只读和业务错误仍可能从宿主流程返回。

打包工具保留独立 Node 单文件的使用方式；安装审查及开发目录加载是执行完整 Schema 校验的宿主入口。包能被压缩成功并不代表已经通过安装校验。
