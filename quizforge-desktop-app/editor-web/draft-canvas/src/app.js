import './style.css';
import { mountDraftCanvas } from './canvas/core.js';

// Keep the original independent DOM-interaction demo; it is not a Practice runtime.
const canvas = mountDraftCanvas(document.querySelector('#question-card'));
window.draftCanvas = canvas;
const form = document.querySelector('#question-form');
const submitted = event => {
  event.preventDefault();
  const chosen = document.querySelector('input[name=answer]:checked')?.value;
  document.querySelector('#card-feedback').textContent = chosen
    ? `已提交测试答案 ${chosen}。${document.querySelector('#answer-note').value}` : '请先选择一个答案。';
};
form.addEventListener('submit', submitted);
canvas.addDisposer(() => form.removeEventListener('submit', submitted));
