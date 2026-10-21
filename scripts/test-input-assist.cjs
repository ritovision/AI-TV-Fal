const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const code = fs.readFileSync("app/src/main/assets/tv-input-assist.js", "utf8");
const listeners = {};
class MockElement {
  constructor(editable = false) { this.editable = editable; this.parentElement = null; }
  closest(selector) {
    assert.ok(selector.includes("contenteditable"));
    return this.editable ? this : null;
  }
}
const win = {};
const doc = {addEventListener(type, handler, capture) {
  assert.equal(type, "pointerdown");
  assert.equal(capture, true);
  listeners[type] = handler;
}};
const context = {window: win, document: doc, Element: MockElement};
vm.runInNewContext(code, context);
assert.equal(win.__aiTvInputAssistInstalled, true);
assert.equal(win.__aiTvTappedEditable, false);

listeners.pointerdown({target: new MockElement(true)});
assert.equal(win.__aiTvTappedEditable, true, "clicked rich editor gets keyboard eligibility");
listeners.pointerdown({target: new MockElement(false)});
assert.equal(win.__aiTvTappedEditable, false, "model button click does not force keyboard");

// Multiple page-script injections must not add a second handler.
const first = listeners.pointerdown;
vm.runInNewContext(code, context);
assert.equal(listeners.pointerdown, first);
console.log("Input assist tests passed (editable tap, model-button tap, install-once).");
