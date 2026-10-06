import './style.css';
import './extensions/sdk.js';
import { mountSharedLearningSurface } from './learning/surface.js';
import { practiceChannel } from './bridge/practice.js';
const surface = mountSharedLearningSurface(document.querySelector('#question-card'), practiceChannel(() => window.practiceHost));
window.draftCanvas = surface.canvas;
window.sharedPractice = surface.practice;
window.sharedLearningSurface = surface;
