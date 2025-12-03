// Transform a <select> into an accessible horizontal tablist preserving the native select.
(function () {
  'use strict';

  function createEl(tag, attrs = {}, children = []) {
    const el = document.createElement(tag);
    Object.keys(attrs).forEach(k => {
      const v = attrs[k];
      if (v === null || v === undefined) return;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else el.setAttribute(k, String(v));
    });
    (children || []).forEach(c => el.appendChild(c));
    return el;
  }

  function fireEvent(el, name) {
    try {
      const ev = new Event(name, { bubbles: true });
      el.dispatchEvent(ev);
    } catch (e) {
      const ev = document.createEvent('HTMLEvents');
      ev.initEvent(name, true, false);
      el.dispatchEvent(ev);
    }
  }

  function inlineHideSelect(select) {
    try {
      select.classList.add('bulb-select-hidden');
      select.style.position = 'absolute';
      select.style.width = '1px';
      select.style.height = '1px';
      select.style.padding = '0';
      select.style.margin = '-1px';
      select.style.overflow = 'hidden';
      select.style.clip = 'rect(0, 0, 0, 0)';
      select.style.whiteSpace = 'nowrap';
      select.style.border = '0';
      select.setAttribute('aria-hidden', 'true');
      select.tabIndex = -1;
    } catch (e) {
      // ignore
    }
  }

  // Ensure a tab el is fully visible within the tablist container
  function ensureVisible(el, container) {
    if (!el || !container) return;
    const elRect = el.getBoundingClientRect();
    const contRect = container.getBoundingClientRect();

    // Already fully visible
    if (elRect.left >= contRect.left && elRect.right <= contRect.right) return;

    const elOffsetLeft = el.offsetLeft;
    const elWidth = el.offsetWidth;
    const containerWidth = container.clientWidth;

    // Center preferred
    let target = elOffsetLeft - Math.floor((containerWidth - elWidth) / 2);

    const maxScroll = Math.max(0, container.scrollWidth - containerWidth);
    if (target < 0) target = 0;
    if (target > maxScroll) target = maxScroll;

    try {
      container.scrollTo({ left: target, behavior: 'smooth' });
    } catch (e) {
      container.scrollLeft = target;
    }
  }

  function addTablistInteractivity(tablist) {
    tablist.tabIndex = 0;

    // Keyboard scrolling when the tablist itself has focus
    tablist.addEventListener('keydown', function (ev) {
      const key = ev.key;
      if (document.activeElement !== tablist) return;

      const page = tablist.clientWidth || 300;
      const step = Math.max(100, Math.floor(page * 0.4));
      const maxScroll = Math.max(0, tablist.scrollWidth - tablist.clientWidth);
      const cur = tablist.scrollLeft;

      if (key === 'ArrowRight') {
        // if already at the right end, do nothing
        if (cur >= maxScroll - 1) return;
        ev.preventDefault();
        tablist.scrollBy({ left: step, behavior: 'smooth' });
      } else if (key === 'ArrowLeft') {
        if (cur <= 1) return;
        ev.preventDefault();
        tablist.scrollBy({ left: -step, behavior: 'smooth' });
      } else if (key === 'PageDown') {
        ev.preventDefault();
        tablist.scrollBy({ left: page, behavior: 'smooth' });
      } else if (key === 'PageUp') {
        ev.preventDefault();
        tablist.scrollBy({ left: -page, behavior: 'smooth' });
      } else if (key === 'Home') {
        ev.preventDefault();
        tablist.scrollTo({ left: 0, behavior: 'smooth' });
      } else if (key === 'End') {
        ev.preventDefault();
        tablist.scrollTo({ left: tablist.scrollWidth, behavior: 'smooth' });
      }
    });

    // Wheel -> horizontal scroll
    tablist.addEventListener('wheel', function (ev) {
      if (ev.shiftKey) return;
      const delta = Math.abs(ev.deltaY) > Math.abs(ev.deltaX) ? ev.deltaY : ev.deltaX;
      if (!delta) return;
      ev.preventDefault();
      tablist.scrollLeft += delta;
    }, { passive: false });
  }

  function buildTabsFromSelect(select) {
    const prev = select._bulbTablist;
    if (prev && prev.parentNode) prev.parentNode.removeChild(prev);

    const tablist = createEl('div', {
      class: 'bulb-tablist',
      role: 'tablist',
      'aria-label': select.getAttribute('aria-label') || select.name || 'Resource'
    });

    const options = Array.from(select.options);
    options.forEach((opt, index) => {
      const btn = createEl('button', {
        type: 'button',
        class: 'bulb-tab',
        role: 'tab',
        'data-value': opt.value,
        'aria-selected': (select.selectedIndex === index) ? 'true' : 'false',
        tabindex: (select.selectedIndex === index) ? '0' : '-1',
        text: opt.text
      });

      if (select.selectedIndex === index) btn.classList.add('active');
      if (opt.value === null || opt.value == '') btn.classList.add('bulb-select-hidden');

      btn.addEventListener('click', () => {
        if (select.value !== opt.value) {
          select.value = opt.value;
          select.selectedIndex = index;
          fireEvent(select, 'input');
          fireEvent(select, 'change');
        }
        setActive(index, select, tablist);
        btn.focus();
      });

      btn.addEventListener('keydown', (ev) => {
        const key = ev.key;
        const len = options.length;

        if (key === 'ArrowRight' || key === 'ArrowDown') {
          ev.preventDefault();
          const next = Math.min(index + 1, len - 1);
          if (next !== index) {
            const nextBtn = tablist.querySelectorAll('.bulb-tab')[next];
            if (nextBtn) {
              nextBtn.focus();
              ensureVisible(nextBtn, tablist);
            }
          }
        } else if (key === 'ArrowLeft' || key === 'ArrowUp') {
          ev.preventDefault();
          const prevIndex = Math.max(index - 1, 0);
          if (prevIndex !== index) {
            const prevBtn = tablist.querySelectorAll('.bulb-tab')[prevIndex];
            if (prevBtn) {
              prevBtn.focus();
              ensureVisible(prevBtn, tablist);
            }
          }
        } else if (key === 'Home') {
          ev.preventDefault();
          const first = tablist.querySelectorAll('.bulb-tab')[0];
          if (first) { first.focus(); ensureVisible(first, tablist); }
        } else if (key === 'End') {
          ev.preventDefault();
          const last = tablist.querySelectorAll('.bulb-tab')[len - 1];
          if (last) { last.focus(); ensureVisible(last, tablist); }
        } else if (key === 'Enter' || key === ' ') {
          ev.preventDefault();
          btn.click();
        }
      });

      // when a tab receives focus, ensure it's visible
      btn.addEventListener('focus', () => {
        ensureVisible(btn, tablist);
      });

      tablist.appendChild(btn);
    });

    if (select.parentNode) select.parentNode.insertBefore(tablist, select.nextSibling);
    select._bulbTablist = tablist;

    addTablistInteractivity(tablist);

    // ensure initially-selected tab is visible
    const initialIndex = select.selectedIndex;
    if (typeof initialIndex === 'number' && initialIndex >= 0) {
      const tabs = tablist.querySelectorAll('.bulb-tab');
      const active = tabs[initialIndex];
      if (active) ensureVisible(active, tablist);
    }

    return tablist;
  }

  function setActive(index, select, tablist) {
    const tabs = Array.from(tablist.querySelectorAll('.bulb-tab'));
    tabs.forEach((t, i) => {
      const isActive = (i === index);
      t.setAttribute('aria-selected', isActive ? 'true' : 'false');
      t.tabIndex = isActive ? 0 : -1;
      if (isActive) t.classList.add('active'); else t.classList.remove('active');
    });

    if (select.selectedIndex !== index) {
      select.selectedIndex = index;
      fireEvent(select, 'input');
      fireEvent(select, 'change');
    }

    const active = tabs[index];
    if (active) ensureVisible(active, tablist);
  }

  function enhanceSelect(select) {
    if (!select || select._bulbEnhanced) return;
    const tablist = buildTabsFromSelect(select);
    inlineHideSelect(select);
    select._bulbEnhanced = true;

    select.addEventListener('change', () => {
      const idx = select.selectedIndex;
      if (typeof idx === 'number') setActive(idx, select, tablist);
    });

    const mo = new MutationObserver(() => {
      buildTabsFromSelect(select);
    });
    mo.observe(select, { childList: true, subtree: true, characterData: true });
    select._bulbMutationObserver = mo;
  }

  function enhanceSelectFromId(id) {
    const s = document.getElementById(id);
    if (s) enhanceSelect(s);
    return !!s;
  }
  function rebuild(id) {
    const s = document.getElementById(id);
    if (s) buildTabsFromSelect(s);
  }

  function tryAutoInit() {
    const sel = document.getElementById('sb_resource');
    if (sel) {
      enhanceSelect(sel);
      return true;
    }
    return false;
  }

  if (!tryAutoInit()) {
    document.addEventListener('DOMContentLoaded', () => { tryAutoInit(); });
    const docObserver = new MutationObserver(() => {
      if (tryAutoInit()) docObserver.disconnect();
    });
    docObserver.observe(document.documentElement || document.body, { childList: true, subtree: true });
  }

  window.BulbTabs = window.BulbTabs || {};
  window.BulbTabs.enhanceSelectFromId = enhanceSelectFromId;
  window.BulbTabs.rebuild = rebuild;

})();
