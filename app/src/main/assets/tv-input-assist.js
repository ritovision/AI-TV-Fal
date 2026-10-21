(function () {
  "use strict";
  if (window.__aiTvInputAssistInstalled) return true;
  window.__aiTvInputAssistInstalled = true;
  window.__aiTvTappedEditable = false;
  // This records one boolean about the actual element touched, before a model
  // picker can auto-focus its search input. It never reads user-entered text.
  document.addEventListener("pointerdown", function (event) {
    const element = event.target instanceof Element ? event.target : event.target?.parentElement;
    const editable = element && element.closest(
      'textarea,input[type="text"],input[type="search"],' +
      '[contenteditable]:not([contenteditable="false"])'
    );
    window.__aiTvTappedEditable = Boolean(editable);
  }, true);
  return true;
})()