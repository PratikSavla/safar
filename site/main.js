// Safar site: theme toggle, header rule on scroll, scroll reveal, and hiding the film if its file is missing.
(function () {
  'use strict';
  var root = document.documentElement;
  var KEY = 'safar-theme';
  var media = window.matchMedia('(prefers-color-scheme: dark)');

  function effectiveTheme() {
    return root.dataset.theme || (media.matches ? 'dark' : 'light');
  }

  function syncToggle() {
    var btn = document.querySelector('[data-theme-toggle]');
    if (!btn) return;
    var dark = effectiveTheme() === 'dark';
    btn.setAttribute('aria-pressed', String(dark));
    btn.setAttribute('aria-label', dark ? 'Dark theme on. Switch to light theme' : 'Light theme on. Switch to dark theme');
    btn.querySelector('[data-theme-label]').textContent = dark ? 'Day' : 'Night';
    var meta = document.querySelectorAll('meta[name="theme-color"]');
    meta.forEach(function (m) { m.setAttribute('content', dark ? '#12161C' : '#F3EEE4'); });
  }

  function setTheme(theme) {
    root.dataset.theme = theme;
    try {
      localStorage.setItem(KEY, theme);
    } catch (e) {
      console.warn('Safar: theme choice could not be saved', e);
    }
    syncToggle();
  }

  function initTheme() {
    var btn = document.querySelector('[data-theme-toggle]');
    btn.addEventListener('click', function () {
      setTheme(effectiveTheme() === 'dark' ? 'light' : 'dark');
    });
    media.addEventListener('change', syncToggle);
    syncToggle();
  }

  function initHeader() {
    var top = document.querySelector('.top');
    var onScroll = function () { top.classList.toggle('scrolled', window.scrollY > 8); };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  function initReveal() {
    var items = document.querySelectorAll('.reveal');
    if (!('IntersectionObserver' in window)) {
      items.forEach(function (el) { el.classList.add('in'); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('in');
        io.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.08 });
    items.forEach(function (el) { io.observe(el); });
  }

  // The build script removes the film section when media/safar-demo.mp4 is absent.
  // This covers the rest: a file that fails to load hides the section instead of showing a broken player.
  function initFilm() {
    var section = document.querySelector('[data-film]');
    if (!section) return;
    var video = section.querySelector('video');
    var source = section.querySelector('source');
    var hide = function (e) {
      console.warn('Safar: demo video could not be loaded, hiding the film section', e && e.type);
      section.hidden = true;
    };
    source.addEventListener('error', hide);
    video.addEventListener('error', hide);
  }

  document.addEventListener('DOMContentLoaded', function () {
    initTheme();
    initHeader();
    initReveal();
    initFilm();
  });
})();
