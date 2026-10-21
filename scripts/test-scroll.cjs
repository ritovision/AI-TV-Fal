/**
 * Dependency-free checks for the remote scrolling code.
 * Run with: node scripts/test-scroll.cjs
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/tv-scroll.js', 'utf8');

function setup(scrollable, mode = 'vertical') {
  const calls = {panel: [], page: []};
  const documentElement = {
    scrollBy: opts => calls.page.push(opts),
    scrollHeight: 5000,
    clientHeight: 540,
    scrollWidth: 960,
    clientWidth: 960
  };
  const body = {parentElement: documentElement};
  const panel = {
    parentElement: body,
    scrollHeight: mode === 'vertical' ? 1000 : 200,
    clientHeight: 200,
    scrollWidth: mode === 'horizontal' ? 1400 : 200,
    clientWidth: 200,
    scrollBy: opts => calls.panel.push(opts)
  };
  const leaf = {parentElement: panel};
  const document = {
    documentElement,
    body,
    scrollingElement: documentElement,
    elementFromPoint: () => scrollable ? leaf : body
  };
  const window = {
    innerWidth: 960,
    innerHeight: 540,
    getComputedStyle: () => ({
      overflowY: mode === 'vertical' ? 'auto' : 'hidden',
      overflowX: mode === 'horizontal' ? 'auto' : 'hidden'
    })
  };
  const sandbox = {window, document};
  vm.runInNewContext(source, sandbox);
  return {scroll: window.__aiTvScroll, calls};
}

const nested = setup(true, 'vertical');
assert.equal(nested.scroll('down', 180, 0.5, 0.5), true);
assert.equal(nested.calls.panel.length, 1, 'scroll a nested panel');
assert.equal(nested.calls.panel[0].top, 180);
assert.equal(nested.calls.page.length, 0);
nested.scroll('up', 180, 0.5, 0.5);
assert.equal(nested.calls.panel[1].top, -180);

const horizontal = setup(true, 'horizontal');
horizontal.scroll('right', 220, 0.5, 0.5);
assert.equal(horizontal.calls.panel[0].left, 220);

const fallback = setup(false);
fallback.scroll('down', 190, 0.5, 0.5);
assert.equal(fallback.calls.panel.length, 0);
assert.equal(fallback.calls.page[0].top, 190, 'scroll document root');

const invalid = setup(false);
assert.equal(invalid.scroll('nope', 100, 0.5, 0.5), false);
assert.equal(invalid.calls.page.length, 0);
console.log('Scroll routing checks passed (nested, horizontal, root, invalid direction).');
