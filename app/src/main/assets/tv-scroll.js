(function () {
  "use strict";

  function isVisible(element) {
    if (!element || !element.getBoundingClientRect) return false;
    const rect = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    return rect.width >= 8 && rect.height >= 8 &&
      rect.right > 0 && rect.left < innerWidth &&
      rect.bottom > 0 && rect.top < innerHeight &&
      style.visibility !== "hidden" && style.display !== "none";
  }

  function canScroll(element, vertical) {
    if (!element || !isVisible(element)) return false;
    const style = getComputedStyle(element);
    const overflow = vertical ? style.overflowY : style.overflowX;
    const sizeExceeded = vertical ?
      element.scrollHeight > element.clientHeight + 3 :
      element.scrollWidth > element.clientWidth + 3;
    return sizeExceeded && /^(auto|scroll|overlay)$/.test(overflow);
  }

  function ancestorScrollable(element, vertical, stopAt) {
    for (let current = element; current; current = current.parentElement) {
      if (canScroll(current, vertical)) return current;
      if (current === stopAt || current === document.body) break;
    }
    return null;
  }

  function visibleOpenPopup() {
    // Prefer the highest visible popover. This prevents scrolling the
    // page behind the model picker when its search box has focus.
    const possible = Array.from(document.querySelectorAll(
      '[role="dialog"],[role="listbox"],[role="menu"],' +
      '[data-radix-popper-content-wrapper]'
    ));
    return possible.filter(el => {
      if (el.getAttribute("data-state") === "closed" || !isVisible(el)) return false;
      const r = el.getBoundingClientRect();
      const x = Math.max(0, Math.min(innerWidth - 1, r.left + r.width / 2));
      const y = Math.max(0, Math.min(innerHeight - 1, r.top + r.height / 2));
      const top = document.elementFromPoint(x, y);
      return top && (el === top || el.contains(top));
    }).pop() || null;
  }

  function popupScrollable(popup, vertical) {
    if (canScroll(popup, vertical)) return popup;
    // Radix/dialog model lists often store their scroll viewport under
    // a non-scrolling search input wrapper. Avoid page-level fallback.
    const descendants = Array.from(popup.querySelectorAll("*")).slice(0, 500);
    const scrollable = descendants.filter(el => canScroll(el, vertical));
    return scrollable.sort((a, b) => {
      const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
      return br.width * br.height - ar.width * ar.height;
    })[0] || null;
  }

  window.__aiTvScroll = function (direction, amount, xRatio, yRatio) {
    if (!["up", "down", "left", "right"].includes(direction)) return false;
    const vertical = direction === "up" || direction === "down";
    const distance = Math.max(20, Math.min(Number(amount) || 180, 500));
    const offset = (direction === "up" || direction === "left") ? -distance : distance;
    const px = Math.max(0, Math.min(innerWidth - 1, Number(xRatio) * innerWidth));
    const py = Math.max(0, Math.min(innerHeight - 1, Number(yRatio) * innerHeight));
    const hit = document.elementFromPoint(px, py);
    const popup = visibleOpenPopup();

    let target = null;
    if (popup) {
      if (hit && popup.contains(hit)) {
        target = ancestorScrollable(hit, vertical, popup);
      }
      if (!target && document.activeElement && popup.contains(document.activeElement)) {
        target = ancestorScrollable(document.activeElement, vertical, popup);
      }
      target = target || popupScrollable(popup, vertical);
      // A visible but non-scrollable dialog must not scroll the page behind it.
      if (!target) return false;
    } else {
      target = ancestorScrollable(hit, vertical, document.body) ||
        document.scrollingElement || document.documentElement;
    }
    if (vertical) target.scrollBy({top: offset, behavior: "instant"});
    else target.scrollBy({left: offset, behavior: "instant"});
    return true;
  };
  return true;
})()