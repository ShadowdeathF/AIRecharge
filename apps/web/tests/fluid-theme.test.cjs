const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function loadMotion(document = {}) {
  const listeners = new Set();
  const preference = {
    matches: false,
    addEventListener: (_, listener) => listeners.add(listener),
    removeEventListener: (_, listener) => listeners.delete(listener),
  };
  const module = { exports: {} };
  const source = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../lib/motion.ts'), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  vm.runInNewContext('(function(module,exports){' + source + '\n})', { document, window: { matchMedia: () => preference } })(module, module.exports);
  return { ...module.exports, preference, listeners };
}

test('theme updates synchronously when native view transitions are unavailable or motion is reduced', () => {
  let updates = 0;
  const motion = loadMotion();
  motion.transitionTheme(() => updates++);
  assert.equal(updates, 1);
  motion.preference.matches = true;
  motion.transitionTheme(() => updates++);
  assert.equal(updates, 2);
});

test('a new theme change skips the previous transition and every update is applied once', async () => {
  const transitions = [];
  const document = { startViewTransition(update) {
    update();
    const transition = { skipped: 0, skipTransition() { this.skipped++; }, ready: Promise.resolve(), finished: Promise.resolve() };
    transitions.push(transition);
    return transition;
  } };
  const motion = loadMotion(document);
  let updates = 0;
  motion.transitionTheme(() => updates++);
  motion.transitionTheme(() => updates++);
  assert.equal(updates, 2);
  assert.equal(transitions[0].skipped, 1);
  motion.preference.matches = true;
  motion.listeners.forEach(listener => listener());
  assert.equal(transitions[1].skipped, 1);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(motion.listeners.size, 0);
});

test('reduced motion subscription responds to changes and removes its listener on disposal', () => {
  const motion = loadMotion();
  let changes = 0;
  const dispose = motion.subscribeReducedMotion(() => changes++);
  assert.equal(motion.getReducedMotion(), false);
  motion.preference.matches = true;
  motion.listeners.forEach(listener => listener());
  assert.equal(motion.getReducedMotion(), true);
  assert.equal(changes, 1);
  dispose();
  assert.equal(motion.listeners.size, 0);
});

test('a deferred old theme callback cannot overwrite a newer immediate choice', () => {
  const callbacks = [];
  const motion = loadMotion({ startViewTransition(update) {
    callbacks.push(update);
    return { skipTransition() {}, ready: Promise.resolve(), finished: Promise.resolve() };
  } });
  let theme = 'light';
  motion.transitionTheme(() => { theme = 'dark'; });
  motion.preference.matches = true;
  motion.transitionTheme(() => { theme = 'light'; });
  callbacks[0]();
  assert.equal(theme, 'light');
});
