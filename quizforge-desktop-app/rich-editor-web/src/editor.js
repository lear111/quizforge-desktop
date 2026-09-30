import { Editor, Node as TiptapNode } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import TextAlign from '@tiptap/extension-text-align'
import { NodeSelection } from '@tiptap/pm/state'

let imageData = {}
let savedPosition = null
let editor = null

const ResourceImage = TiptapNode.create({
  name: 'image', group: 'block', atom: true, selectable: true,
  addAttributes() {
    return {
      resourceId: { default: null }, alt: { default: null }, caption: { default: null },
      widthPercent: { default: null }, alignment: { default: null },
    }
  },
  parseHTML() { return [] },
  renderHTML({ node }) {
    const width = [25, 50, 75, 100].includes(node.attrs.widthPercent) ? node.attrs.widthPercent : 100
    const align = ['LEFT', 'CENTER', 'RIGHT'].includes(node.attrs.alignment) ? node.attrs.alignment.toLowerCase() : 'left'
    const src = imageData[node.attrs.resourceId] || ''
    return ['div', { class: 'qf-image', 'data-resource-id': node.attrs.resourceId, style: `text-align:${align}` },
      ['img', { src, alt: node.attrs.alt || '图片', style: `width:${width}%;max-width:100%;height:auto` }],
      ...(node.attrs.caption ? [['div', { class: 'qf-image-caption' }, node.attrs.caption]] : [])]
  },
})
const InlineResourceImage = ResourceImage.extend({
  name: 'inlineImage', group: 'inline', inline: true,
  renderHTML({ node }) {
    return ['span', { class: 'qf-image qf-inline-image', 'data-resource-id': node.attrs.resourceId },
      ['img', { src: imageData[node.attrs.resourceId] || '', alt: node.attrs.alt || '图片' }]]
  },
})

function heightChanged() {
  if (window.contentBridge) window.contentBridge.heightChanged(Math.max(400, document.getElementById('surface').scrollHeight + 80))
}
function selection() { return editor.state.selection }
function selectedImage() {
  const current = selection()
  return current instanceof NodeSelection && ['image', 'inlineImage'].includes(current.node.type.name)
}
function restoreSelection() {
  if (savedPosition !== null) editor.commands.setTextSelection(Math.min(savedPosition, editor.state.doc.content.size))
}
function renderImages() {
  if (editor) editor.view.updateState(editor.state)
}

editor = new Editor({
  element: document.getElementById('surface'),
  extensions: [
    StarterKit.configure({ heading: { levels: [1, 2, 3] }, code: false, codeBlock: false, horizontalRule: false }),
    TextAlign.configure({ types: ['paragraph', 'heading'], alignments: ['left', 'center', 'right'] }),
    ResourceImage,
    InlineResourceImage,
  ],
  content: { type: 'doc', content: [{ type: 'paragraph' }] },
  onUpdate: heightChanged,
  onCreate: heightChanged,
})

document.addEventListener('click', event => {
  const link = event.target.closest && event.target.closest('a')
  if (link) event.preventDefault()
  const image = event.target.closest && event.target.closest('.qf-image')
  if (image && editor) {
    const position = editor.view.posAtDOM(image, 0)
    if (['image', 'inlineImage'].includes(editor.state.doc.nodeAt(position)?.type.name)) {
      editor.view.dispatch(editor.state.tr.setSelection(NodeSelection.create(editor.state.doc, position)))
      editor.view.focus()
    }
  }
}, true)
document.addEventListener('dragover', event => event.preventDefault())
document.addEventListener('drop', event => event.preventDefault())

window.richEditor = {
  load(json, images, config) {
    imageData = JSON.parse(images)
    const layout = JSON.parse(config)
    document.documentElement.style.setProperty('--qf-content-width', layout.width + 'px')
    document.documentElement.style.setProperty('--qf-font-family', layout.fontFamily)
    document.documentElement.style.setProperty('--qf-font-size', layout.fontSize + 'px')
    document.documentElement.style.setProperty('--qf-line-height', layout.lineHeight + 'px')
    document.documentElement.style.setProperty('--qf-paragraph-spacing', layout.paragraphSpacing + 'px')
    editor.commands.setContent(JSON.parse(json), { emitUpdate: false })
    savedPosition = null
    setTimeout(heightChanged, 0)
  },
  exportContent() { return JSON.stringify(editor.getJSON()) },
  captureSelection() { savedPosition = selection().from },
  imageSelected: selectedImage,
  command(name, arg) {
    const commands = {
      undo: () => editor.commands.undo(), redo: () => editor.commands.redo(),
      bold: () => editor.chain().focus().toggleBold().run(),
      italic: () => editor.chain().focus().toggleItalic().run(),
      underline: () => editor.chain().focus().toggleUnderline().run(),
      strike: () => editor.chain().focus().toggleStrike().run(),
      clear: () => editor.chain().focus().unsetAllMarks().clearNodes().run(),
      paragraph: () => editor.chain().focus().setParagraph().run(),
      heading1: () => editor.chain().focus().toggleHeading({ level: 1 }).run(),
      heading2: () => editor.chain().focus().toggleHeading({ level: 2 }).run(),
      heading3: () => editor.chain().focus().toggleHeading({ level: 3 }).run(),
      left: () => editor.chain().focus().setTextAlign('left').run(),
      center: () => editor.chain().focus().setTextAlign('center').run(),
      right: () => editor.chain().focus().setTextAlign('right').run(),
      bullet: () => editor.chain().focus().toggleBulletList().run(),
      ordered: () => editor.chain().focus().toggleOrderedList().run(),
      quote: () => editor.chain().focus().toggleBlockquote().run(),
      link: () => editor.chain().focus().setLink({ href: arg }).run(),
      unlink: () => editor.chain().focus().unsetLink().run(),
    }
    if (!Object.prototype.hasOwnProperty.call(commands, name)) throw new Error('Unsupported command: ' + name)
    return commands[name]()
  },
  insertImage(id) {
    restoreSelection()
    editor.chain().focus().insertContent({ type: 'image', attrs: { resourceId: id } }).run()
    heightChanged()
  },
  replaceImage(id) {
    if (!selectedImage()) throw new Error('Select an image first')
    editor.chain().focus().updateAttributes(selection().node.type.name, { resourceId: id }).run()
    heightChanged()
  },
  deleteImage() {
    if (!selectedImage()) throw new Error('Select an image first')
    editor.chain().focus().deleteSelection().run()
    heightChanged()
  },
  setImageWidth(percent) {
    if (!selectedImage() || selection().node.type.name !== 'image' || ![25, 50, 75, 100].includes(percent)) throw new Error('Select a block image first')
    editor.chain().focus().updateAttributes('image', { widthPercent: percent }).run()
    heightChanged()
  },
  setImageAlignment(value) {
    if (!selectedImage() || selection().node.type.name !== 'image' || !['LEFT', 'CENTER', 'RIGHT'].includes(value)) throw new Error('Select a block image first')
    editor.chain().focus().updateAttributes('image', { alignment: value }).run()
    heightChanged()
  },
  updateImages(images) { imageData = JSON.parse(images); renderImages() },
}
