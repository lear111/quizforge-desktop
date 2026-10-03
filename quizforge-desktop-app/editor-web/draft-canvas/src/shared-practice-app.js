import './style.css';
import { mountDraftCanvas } from './canvas/core.js';
import { mountPracticeCard } from './practice/renderer.js';
import { practiceChannel } from './bridge/practice.js';

const object = document.querySelector('#question-card');
const canvas = mountDraftCanvas(object);
const channel = practiceChannel(() => window.practiceHost);
const practice = mountPracticeCard(object, channel.send, () => canvas.diagnostics().mode === 'INTERACT');
window.draftCanvas = canvas;
window.sharedPractice = Object.freeze({ ...practice, bindHost: channel.ready, diagnostics: channel.diagnostics });
canvas.addDisposer(practice.destroy);
document.querySelector('#mode-help').textContent = '交互：选择答案、提交或重试。';
