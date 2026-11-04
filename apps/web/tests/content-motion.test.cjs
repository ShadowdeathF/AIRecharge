const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function loadMotion() {
  const filename = path.join(__dirname, '../lib/motion.ts');
  const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext('(function(module,exports){' + source + '\n})')(module, module.exports);
  return module.exports;
}

test('content enters gently and returns an animation that its owner can cancel', () => {
  const { animateContent } = loadMotion();
  const calls = [];
  let cancelled = false;
  const animation = { cancel() { cancelled = true; } };
  const element = { animate(frames, options) { calls.push({ frames, options }); return animation; } };
  const result = animateContent(element, false);
  assert.equal(result, animation);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].frames[0].opacity, 0);
  assert.equal(calls[0].frames[1].opacity, 1);
  assert.equal(calls[0].frames[1].transform, 'translateY(0)');
  assert.equal(calls[0].options.duration, 420);
  assert.equal(calls[0].options.fill, 'none');
  result.cancel();
  assert.equal(cancelled, true);
});

test('reduced motion leaves the content immediately visible without starting animation', () => {
  const { animateContent } = loadMotion();
  let started = false;
  const result = animateContent({ animate() { started = true; } }, true);
  assert.equal(result, undefined);
  assert.equal(started, false);
});

test('missing elements and browsers without Web Animations render normally', () => {
  const { animateContent } = loadMotion();
  assert.equal(animateContent(null, false), undefined);
  assert.equal(animateContent({}, false), undefined);
});

function loadContentHook(element, preference, native = false) {
  const filename = path.join(__dirname, '../hooks/use-content-motion.ts');
  const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const effects = [];
  const module = { exports: {} };
  const requireDependency = name => {
    if (name === 'react') return {
      useRef: () => ({ current: element }),
      useEffect: (effect, dependencies) => effects.push({ effect, dependencies }),
    };
    if (name === '@/lib/motion') return loadMotion();
    throw new Error('Unexpected dependency ' + name);
  };
  vm.runInNewContext('(function(module,exports,require){' + source + '\n})', {
    window: { matchMedia: () => preference },
    document: native ? { startViewTransition() {} } : {},
  })(module, module.exports, requireDependency);
  return { useContentMotion: module.exports.useContentMotion, effects };
}

test('content hook cancels the old animation and removes its listener on trigger change and unmount', () => {
  const listeners = new Set();
  const preference = { matches: false, addEventListener: (_, listener) => listeners.add(listener), removeEventListener: (_, listener) => listeners.delete(listener) };
  let started = 0;
  let cancelled = 0;
  const { useContentMotion, effects } = loadContentHook({ animate() { started++; return { cancel() { cancelled++; } }; } }, preference);
  useContentMotion('first');
  const firstCleanup = effects[0].effect();
  assert.equal(effects[0].dependencies[0], 'first');
  assert.equal(started, 1);
  assert.equal(listeners.size, 1);
  useContentMotion('second');
  firstCleanup();
  const secondCleanup = effects[1].effect();
  assert.equal(effects[1].dependencies[0], 'second');
  assert.equal(started, 2);
  assert.equal(cancelled, 1);
  assert.equal(listeners.size, 1);
  secondCleanup();
  assert.equal(cancelled, 2);
  assert.equal(listeners.size, 0);
});

test('content hook honors reduced motion on mount and cancels when the preference changes', () => {
  const listeners = new Set();
  const preference = { matches: true, addEventListener: (_, listener) => listeners.add(listener), removeEventListener: (_, listener) => listeners.delete(listener) };
  let started = 0;
  let cancelled = 0;
  const { useContentMotion, effects } = loadContentHook({ animate() { started++; return { cancel() { cancelled++; } }; } }, preference);
  useContentMotion('reduced');
  effects[0].effect()();
  assert.equal(started, 0);
  assert.equal(listeners.size, 0);
  preference.matches = false;
  useContentMotion('animated');
  const cleanup = effects[1].effect();
  assert.equal(started, 1);
  preference.matches = true;
  listeners.forEach(listener => listener());
  assert.equal(cancelled, 1);
  cleanup();
  assert.equal(listeners.size, 0);
});

test('native route transitions skip the secondary content animation', () => {
  const preference = { matches: false };
  let started = 0;
  const { useContentMotion, effects } = loadContentHook({ animate() { started++; } }, preference, true);
  useContentMotion('/cart', { native: true });
  assert.equal(effects[0].effect(), undefined);
  assert.equal(started, 0);
});

test('cached history restoration uses the fallback once even with native transitions available', () => {
  const preference = { matches: false, addEventListener() {}, removeEventListener() {} };
  let started = 0;
  let cancelled = 0;
  const { useContentMotion, effects } = loadContentHook({ animate() {
    started++;
    return { cancel() { cancelled++; } };
  } }, preference, true);
  const historyPath = { current: '/cart' };
  useContentMotion('/cart', { native: true, historyPath });
  const cleanup = effects[0].effect();
  assert.equal(started, 1);
  assert.equal(historyPath.current, null);
  cleanup();
  assert.equal(cancelled, 1);
  useContentMotion('/profile', { native: true, historyPath });
  assert.equal(effects[1].effect(), undefined);
  assert.equal(started, 1);
});
