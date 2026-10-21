(function() {
  "use strict";
  window.__aiTvScroll = function(direction, amount, xRatio, yRatio) {
    if (!["up", "down", "left", "right"].includes(direction)) return false;
    var delta = Math.max(20, Math.min(Number(amount) || 180, 500));
    var vertical = direction === "up" || direction === "down";
    var offset = (direction === "up" || direction === "left") ? -delta : delta;
    var px = Math.max(0, Math.min(window.innerWidth - 1, Number(xRatio) * window.innerWidth));
    var py = Math.max(0, Math.min(window.innerHeight - 1, Number(yRatio) * window.innerHeight));
    var element = document.elementFromPoint(px, py);
    while (element && element !== document.documentElement && element !== document.body) {
      var styles = window.getComputedStyle(element);
      var overflow = vertical ? styles.overflowY : styles.overflowX;
      var hasOverflow = vertical
        ? element.scrollHeight > element.clientHeight + 3
        : element.scrollWidth > element.clientWidth + 3;
      if (hasOverflow && /^(auto|scroll|overlay)$/.test(overflow)) {
        if (vertical) element.scrollBy({top: offset, behavior: "instant"});
        else element.scrollBy({left: offset, behavior: "instant"});
        return true;
      }
      element = element.parentElement;
    }
    var page = document.scrollingElement || document.documentElement;
    if (vertical) page.scrollBy({top: offset, behavior: "instant"});
    else page.scrollBy({left: offset, behavior: "instant"});
    return true;
  };
  return true;
})()