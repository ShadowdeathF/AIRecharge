const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require('typescript');

function loadDialogFocus() {
  const filename = path.join(__dirname, '../lib/dialog-focus.ts');
  const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const module = { exports: {} };
  const document = { body: { style: { overflow: 'auto' } } };
  vm.runInNewContext('(function(module,exports){' + source + '\n})', { document })(module, module.exports);
  return { ...module.exports, document };
}

test('nested dialogs keep the page locked until both owners close', () => {
  const { lockPageScroll, document } = loadDialogFocus();
  const closeFirst = lockPageScroll();
  const closeSecond = lockPageScroll();
  closeFirst();
  assert.equal(document.body.style.overflow, 'hidden');
  closeSecond();
  assert.equal(document.body.style.overflow, 'auto');
});

test('scroll lock release is safe to repeat', () => {
  const { lockPageScroll, document } = loadDialogFocus();
  const closeFirst = lockPageScroll();
  closeFirst();
  closeFirst();
  const closeSecond = lockPageScroll();
  assert.equal(document.body.style.overflow, 'hidden');
  closeSecond();
  assert.equal(document.body.style.overflow, 'auto');
});

test('an already hidden overflow value survives opening and closing a dialog', () => {
  const { lockPageScroll, document } = loadDialogFocus();
  document.body.style.overflow = 'hidden';
  const close = lockPageScroll();
  close();
  assert.equal(document.body.style.overflow, 'hidden');
});
