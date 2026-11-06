// Phone-only tweaks applied on top of Ghost Hunter 5.1 (index.html is left unchanged).
(function () {
  // 1. The flapping corner bat covers buttons on a phone: leave it out.
  var bat = document.getElementById('ghostBatCorner');
  if (bat) bat.remove();

  // 2. Android plays and steps through a video more reliably when the video is part of
  //    the page, so attach the app's hidden video (kept invisible).
  try {
    if (typeof video !== 'undefined' && video && !video.isConnected) {
      video.setAttribute('playsinline', '');
      video.style.cssText = 'position:fixed;left:0;bottom:0;width:4px;height:4px;opacity:0.01;pointer-events:none;z-index:1';
      document.body.appendChild(video);
    }
  } catch (e) {}

  // 3. Never leave "Count bats" greyed out with no explanation.
  var go = document.getElementById('go'), stat = document.getElementById('vstat');
  function show(msg) {
    var box = document.getElementById('ghMobileMsg');
    if (!box) {
      box = document.createElement('p');
      box.id = 'ghMobileMsg';
      box.style.cssText = 'color:#e05252;font-weight:600;margin:8px 0 0';
      go.parentNode.insertBefore(box, go.nextSibling);
    }
    box.textContent = msg;
  }
  function unstick(reason) {
    show('Counting stopped: ' + reason + ' Tap Count bats to try again, and send Josh\'s helper this message if it keeps happening.');
    go.disabled = false;
    var rb = document.getElementById('runBox'); if (rb) rb.classList.add('hide');
  }
  window.addEventListener('error', function (e) { if (go && go.disabled) unstick('error "' + (e.message || e) + '".'); });
  window.addEventListener('unhandledrejection', function (e) {
    if (go && go.disabled) unstick('error "' + ((e.reason && e.reason.message) || e.reason) + '".');
  });
  // Android can pause a background video while the app is counting it. Keep it playing.
  var stopped = false;
  var stopBtn = document.getElementById('stop');
  if (stopBtn) stopBtn.addEventListener('click', function () {
    stopped = true;
    // If the video is frozen, Stop would wait forever: free the button ourselves.
    setTimeout(function () { if (go.disabled) unstick('stopped.'); }, 3000);
  });
  if (go) go.addEventListener('click', function () { stopped = false; });
  setInterval(function () {
    try {
      if (go && go.disabled && !stopped && typeof video !== 'undefined'
          && video.paused && !video.ended && video.currentTime > 0) video.play().catch(function () {});
    } catch (e) {}
  }, 1000);

  // Watchdog: if counting shows no progress for 30 seconds, say where it got stuck.
  var last = '', since = Date.now();
  setInterval(function () {
    if (!go || !go.disabled) { last = ''; since = Date.now(); return; }
    var now = (stat && stat.textContent) + '|' + (typeof video !== 'undefined' ? video.currentTime.toFixed(2) : '');
    if (now !== last) { last = now; since = Date.now(); return; }
    if (Date.now() - since > 30000) {
      unstick('no progress for 30 seconds at "' + (stat && stat.textContent) + '".');
      try { stopFlag = true; video.pause(); } catch (e) {}
    }
  }, 2000);
})();
