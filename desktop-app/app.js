(function () {
  'use strict';

  const STORAGE_KEY = 'hsc_2028_study_state_v1';
  const PLAN_START = '2026-11-24';
  const PLAN_END = '2027-05-23';
  const TOTAL_WEEKS = 26;

  const STATUS_LIST = [
    { id: 'NOT_STARTED', bn: 'শুরু হয়নি', en: 'Not started' },
    { id: 'IN_PROGRESS', bn: 'চলছে', en: 'In progress' },
    { id: 'FIRST_READING_DONE', bn: 'প্রথমবার পড়া শেষ', en: 'First reading done' },
    { id: 'PRACTICE_DONE', bn: 'অনুশীলন শেষ', en: 'Practice done' },
    { id: 'REVISION_DONE', bn: 'রিভিশন শেষ', en: 'Revision done' },
    { id: 'EXAM_TAKEN', bn: 'পরীক্ষা দেওয়া হয়েছে', en: 'Exam taken' }
  ];

  const BN_DIGITS = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
  function toBnDigits(val) {
    return String(val).replace(/[0-9]/g, (d) => BN_DIGITS[Number(d)]);
  }

  function todayIso() {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  function parseIsoLocal(iso) {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d, 0, 0, 0, 0);
  }

  function daysBetween(fromIso, toIso) {
    const a = parseIsoLocal(fromIso).getTime();
    const b = parseIsoLocal(toIso).getTime();
    return Math.round((b - a) / 86400000);
  }

  function currentWeekNumber(tIso = todayIso()) {
    const diff = daysBetween(PLAN_START, tIso);
    if (diff < 0) return 1;
    return Math.min(TOTAL_WEEKS, Math.max(1, Math.floor(diff / 7) + 1));
  }

  function weekStartIso(w) {
    const d = parseIsoLocal(PLAN_START);
    d.setDate(d.getDate() + (w - 1) * 7);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  function weekDeadlineIso(w) {
    if (w >= TOTAL_WEEKS) return PLAN_END;
    const d = parseIsoLocal(PLAN_START);
    d.setDate(d.getDate() + w * 7 - 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  function dayRoutineKey(tIso = todayIso()) {
    const day = parseIsoLocal(tIso).getDay();
    return ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][day];
  }

  function formatDate(iso, lang) {
    const d = parseIsoLocal(iso);
    if (lang === 'en') {
      return d.toLocaleDateString('en-US', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' });
    }
    const days = ['রবিবার', 'সোমবার', 'মঙ্গলবার', 'বুধবার', 'বৃহস্পতিবার', 'শুক্রবার', 'শনিবার'];
    const months = ['জানুয়ারি', 'ফেব্রুয়ারি', 'মার্চ', 'এপ্রিল', 'মে', 'জুন', 'জুলাই', 'আগস্ট', 'সেপ্টেম্বর', 'অক্টোবর', 'নভেম্বর', 'ডিসেম্বর'];
    return `${days[d.getDay()]}, ${toBnDigits(d.getDate())} ${months[d.getMonth()]} ${toBnDigits(d.getFullYear())}`;
  }

  function formatShortDate(iso, lang) {
    const d = parseIsoLocal(iso);
    if (lang === 'en') {
      return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' });
    }
    const months = ['জানু', 'ফেব্রু', 'মার্চ', 'এপ্রিল', 'মে', 'জুন', 'জুলাই', 'আগস্ট', 'সেপ্টে', 'অক্টো', 'নভে', 'ডিসে'];
    return `${toBnDigits(d.getDate())} ${months[d.getMonth()]} ${toBnDigits(d.getFullYear())}`;
  }

  function phaseMinutes(dailyHours) {
    return dailyHours <= 3 ? [30, 12, 3] : [50, 20, 5];
  }

  function isFinishedForRollover(statusId) {
    return statusId === 'PRACTICE_DONE' || statusId === 'REVISION_DONE' || statusId === 'EXAM_TAKEN';
  }

  function el(tag, attrs = {}, children = []) {
    const node = document.createElement(tag);
    Object.entries(attrs).forEach(([k, v]) => {
      if (k === 'className') node.className = v;
      else if (k === 'text') node.textContent = v;
      else if (k === 'style') node.setAttribute('style', v);
      else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2).toLowerCase(), v);
      else if (v !== undefined && v !== null && v !== false) node.setAttribute(k, v === true ? '' : v);
    });
    (Array.isArray(children) ? children : [children]).forEach((ch) => {
      if (ch === null || ch === undefined) return;
      if (typeof ch === 'string' || typeof ch === 'number') node.appendChild(document.createTextNode(String(ch)));
      else node.appendChild(ch);
    });
    return node;
  }

  const PLAN = window.STUDY_PLAN;

  function buildInitialState() {
    const chapters = [];
    PLAN.subjects.forEach((subj) => {
      subj.chapters.forEach((ch) => {
        chapters.push({
          id: ch.id,
          subjectId: subj.id,
          subjectGroup: subj.group,
          paper: ch.paper,
          startWeek: ch.startWeek || 1,
          weekNumber: ch.weekNumber,
          title: ch.title,
          deadline: ch.deadline,
          status: 'NOT_STARTED',
          isEditablePlaceholder: Boolean(ch.isEditablePlaceholder)
        });
      });
    });
    return {
      version: 1,
      settings: {
        dailyHours: 5,
        language: 'bn',
        darkMode: true,
        notificationsEnabled: false
      },
      chapters,
      weeklyTasksCompleted: {},
      fridayChecklistCompleted: {},
      dailyCompletedSlots: {},
      mistakes: []
    };
  }

  function loadState() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return buildInitialState();
      const parsed = JSON.parse(raw);
      if (!parsed || !Array.isArray(parsed.chapters) || parsed.chapters.length === 0) {
        return buildInitialState();
      }
      return parsed;
    } catch {
      return buildInitialState();
    }
  }

  let appState = loadState();
  function saveState() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(appState));
  }

  let currentScreen = 'today';
  let roadmapSubject = 'ALL';
  let roadmapFilter = 'ALL';
  let roadmapQuery = '';
  let weeklySelectedWeek = currentWeekNumber();
  let mistakeFilter = 'ALL';
  let mistakeSubjectFilter = 'ALL';
  let settingsEditSubject = PLAN.subjects[0].id;

  // Live session timers (slot 1..4)
  const timers = {};
  let breakTimer = { remaining: 0, running: false };

  function ensureTimers() {
    const [p1, p2, p3] = phaseMinutes(appState.settings.dailyHours);
    const totalSec = (p1 + p2 + p3) * 60;
    [1, 2, 3, 4].forEach((slot) => {
      if (!timers[slot]) {
        timers[slot] = { total: totalSec, remaining: totalSec, running: false };
      } else {
        timers[slot].total = totalSec;
        if (timers[slot].remaining > totalSec || (!timers[slot].running && timers[slot].remaining === 0)) {
          timers[slot].remaining = totalSec;
        }
      }
    });
  }

  function notify(title, body) {
    if (!appState.settings.notificationsEnabled || !('Notification' in window)) return;
    if (Notification.permission === 'granted') {
      try {
        new Notification(title, { body });
      } catch {}
    }
  }

  setInterval(() => {
    let completedAny = false;
    const lang = appState.settings.language;
    const [p1, p2, p3] = phaseMinutes(appState.settings.dailyHours);
    const p1Sec = p1 * 60;
    const p2Sec = p2 * 60;
    const p3Sec = p3 * 60;

    Object.entries(timers).forEach(([slot, t]) => {
      if (t.running && t.remaining > 0) {
        const beforeElapsed = t.total - t.remaining;
        t.remaining -= 1;
        const afterElapsed = t.total - t.remaining;
        ticked = true;

        if (beforeElapsed < p1Sec && afterElapsed >= p1Sec) {
          notify(lang === 'bn' ? `সেশন ${toBnDigits(slot)}: অনুশীলন ধাপ` : `Session ${slot}: Practice Phase`,
            lang === 'bn' ? 'অঙ্ক, MCQ বা সৃজনশীল অনুশীলন শুরু করুন।' : 'Start math, MCQ, or CQ practice.');
        } else if (beforeElapsed < p1Sec + p2Sec && afterElapsed >= p1Sec + p2Sec) {
          notify(lang === 'bn' ? `সেশন ${toBnDigits(slot)}: বই বন্ধ করে মনে করুন` : `Session ${slot}: Closed-book Recall`,
            lang === 'bn' ? '৫ মিনিট বই বন্ধ করে নিজের ভাষায় মনে করুন।' : 'Close your book for 5 minutes of recall.');
        }

        if (t.remaining === 0) {
          t.running = false;
          completedAny = true;
          const tIso = todayIso();
          const done = new Set(appState.dailyCompletedSlots[tIso] || []);
          done.add(Number(slot));
          appState.dailyCompletedSlots[tIso] = Array.from(done);
          saveState();
          notify(lang === 'bn' ? `সেশন ${toBnDigits(slot)} সম্পন্ন!` : `Session ${slot} completed!`,
            lang === 'bn' ? '১০–১৫ মিনিট বিরতি নিন।' : 'Take a 10–15 minute break.');
        } else if (currentScreen === 'today') {
          const readout = document.getElementById(`timer-readout-${slot}`);
          const phaseEl = document.getElementById(`phase-label-${slot}`);
          const f1 = document.getElementById(`p1-fill-${slot}`);
          const f2 = document.getElementById(`p2-fill-${slot}`);
          const f3 = document.getElementById(`p3-fill-${slot}`);
          if (readout) readout.textContent = formatMmSs(t.remaining);
          if (phaseEl) {
            phaseEl.textContent = afterElapsed < p1Sec
              ? (lang === 'bn' ? `ধাপ ১/৩ (${num(p1)} মি.): নতুন টপিক পড়া` : `Phase 1/3 (${p1}m): New topic`)
              : afterElapsed < p1Sec + p2Sec
              ? (lang === 'bn' ? `ধাপ ২/৩ (${num(p2)} মি.): অঙ্ক, MCQ বা সৃজনশীল` : `Phase 2/3 (${p2}m): Practice`)
              : (lang === 'bn' ? `ধাপ ৩/৩ (${num(p3)} মি.): বই বন্ধ করে মনে করা` : `Phase 3/3 (${p3}m): Closed-book recall`);
          }
          if (f1) f1.style.width = `${Math.min(100, Math.max(0, (afterElapsed / p1Sec) * 100))}%`;
          if (f2) f2.style.width = `${Math.min(100, Math.max(0, ((afterElapsed - p1Sec) / p2Sec) * 100))}%`;
          if (f3) f3.style.width = `${Math.min(100, Math.max(0, ((afterElapsed - p1Sec - p2Sec) / p3Sec) * 100))}%`;
        }
      }
    });

    if (breakTimer.running && breakTimer.remaining > 0) {
      breakTimer.remaining -= 1;
      ticked = true;
      if (breakTimer.remaining === 0) {
        breakTimer.running = false;
        completedAny = true;
        notify(lang === 'bn' ? 'বিরতি শেষ!' : 'Break finished!',
          lang === 'bn' ? 'পরের সেশন শুরু করুন।' : 'Start your next study session.');
      } else if (currentScreen === 'today') {
        const breakEl = document.getElementById('break-status-text');
        if (breakEl) {
          breakEl.textContent = lang === 'bn'
            ? `বিরতি চলছে: ${formatMmSs(breakTimer.remaining)}`
            : `Break: ${formatMmSs(breakTimer.remaining)}`;
        }
      }
    }

    if (completedAny && currentScreen === 'today') {
      render();
    }
  }, 1000);

  function t(bn, en) {
    return appState.settings.language === 'bn' ? bn : en;
  }

  function num(n) {
    return appState.settings.language === 'bn' ? toBnDigits(n) : String(n);
  }

  function formatMmSs(sec) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    const raw = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return appState.settings.language === 'bn' ? toBnDigits(raw) : raw;
  }

  function getChaptersWithRollover() {
    const tIso = todayIso();
    const curWeek = currentWeekNumber(tIso);
    const subjMap = Object.fromEntries(PLAN.subjects.map((s) => [s.id, s]));

    return appState.chapters.map((ch) => {
      const subj = subjMap[ch.subjectId];
      const overdue = !isFinishedForRollover(ch.status) && daysBetween(ch.deadline, tIso) > 0;
      const effectiveWeek = overdue && curWeek > ch.weekNumber ? curWeek : ch.weekNumber;
      const rolledOver = overdue && curWeek > ch.weekNumber;
      const isCurrentWeek = curWeek >= ch.startWeek && curWeek <= effectiveWeek;
      return {
        ...ch,
        subjectNameBn: subj ? subj.nameBn : ch.subjectId,
        subjectNameEn: subj ? subj.nameEn : ch.subjectId,
        isOverdue: overdue,
        isRolledOver: rolledOver,
        effectiveWeek,
        isCurrentWeek
      };
    });
  }

  function computeWeekPercent(week, enrichedChapters) {
    const totalTasks = PLAN.weeklyMandatoryTasks.length + PLAN.fridayExamChecklist.length;
    const doneTasks = (appState.weeklyTasksCompleted[week] || []).length +
      (appState.fridayChecklistCompleted[week] || []).length;
    const weekCh = enrichedChapters.filter((c) => week >= c.startWeek && week <= c.effectiveWeek);
    const doneCh = weekCh.filter((c) => isFinishedForRollover(c.status)).length;
    const total = totalTasks + weekCh.length;
    if (total === 0) return 0;
    return Math.round(((doneTasks + doneCh) * 100) / total);
  }

  function openModal(contentNode) {
    const card = document.getElementById('modalCard');
    card.innerHTML = '';
    card.appendChild(contentNode);
    document.getElementById('modalBackdrop').classList.add('open');
  }

  function closeModal() {
    document.getElementById('modalBackdrop').classList.remove('open');
  }

  document.getElementById('modalBackdrop').addEventListener('click', (e) => {
    if (e.target.id === 'modalBackdrop') closeModal();
  });

  function renderStatusSelect(ch, onChange) {
    const sel = el('select', { style: 'width:auto;min-width:170px' });
    STATUS_LIST.forEach((st) => {
      const opt = el('option', { value: st.id, text: t(st.bn, st.en) });
      if (st.id === ch.status) opt.selected = true;
      sel.appendChild(opt);
    });
    sel.addEventListener('change', (e) => onChange(e.target.value));
    return sel;
  }

  function openEditChapterModal(ch) {
    let title = ch.title;
    let week = ch.weekNumber;
    let deadline = ch.deadline;

    const titleIn = el('input', { type: 'text', value: title });
    const weekIn = el('input', { type: 'number', min: 1, max: 26, value: week });
    const dlIn = el('input', { type: 'date', value: deadline });

    weekIn.addEventListener('input', () => {
      const w = Math.min(26, Math.max(1, Number(weekIn.value) || 1));
      dlIn.value = weekDeadlineIso(w);
    });

    const node = el('div', { className: 'stack' }, [
      el('h3', { text: t('অধ্যায় ও ডেডলাইন এডিট', 'Edit Chapter & Deadline'), style: 'margin:0' }),
      el('label', { text: t('অধ্যায় / ইউনিটের নাম', 'Chapter / Unit Title') }),
      titleIn,
      el('label', { text: t('সপ্তাহ (১–২৬)', 'Week (1–26)') }),
      weekIn,
      el('label', { text: t('ডেডলাইন', 'Deadline') }),
      dlIn,
      el('div', { className: 'row', style: 'justify-content:flex-end;margin-top:8px' }, [
        el('button', { className: 'btn', text: t('বাতিল', 'Cancel'), onClick: closeModal }),
        el('button', {
          className: 'btn primary',
          text: t('সংরক্ষণ', 'Save'),
          onClick: () => {
            appState.chapters = appState.chapters.map((item) =>
              item.id === ch.id
                ? {
                    ...item,
                    title: titleIn.value.trim() || item.title,
                    weekNumber: Math.min(26, Math.max(1, Number(weekIn.value) || item.weekNumber)),
                    deadline: dlIn.value || item.deadline,
                    isEditablePlaceholder: false
                  }
                : item
            );
            saveState();
            closeModal();
            render();
          }
        })
      ])
    ]);
    openModal(node);
  }

  function renderToday(enriched) {
    ensureTimers();
    const lang = appState.settings.language;
    const tIso = todayIso();
    const curWeek = currentWeekNumber(tIso);
    const dayKey = dayRoutineKey(tIso);
    const routine = PLAN.weeklyRoutine[dayKey] || [];
    const doneSlots = new Set(appState.dailyCompletedSlots[tIso] || []);
    const [p1, p2, p3] = phaseMinutes(appState.settings.dailyHours);
    const rolloverCount = enriched.filter((c) => c.isRolledOver).length;
    const pct = Math.round((doneSlots.size * 100) / Math.max(1, routine.length));

    const heroCard = el('div', { className: 'card hero stack' }, [
      el('div', { className: 'row-between' }, [
        el('strong', { text: formatDate(tIso, lang) }),
        el('span', { className: 'badge primary', text: t(`সপ্তাহ ${num(curWeek)}/২৬`, `Week ${curWeek}/26`) })
      ]),
      el('div', {
        className: 'muted',
        text: t(
          `দৈনিক ${num(appState.settings.dailyHours)} ঘণ্টা • ${num(p1 + p2 + p3)} মি. × ৪ সেশন (${num(p1)}/${num(p2)}/${num(p3)})`,
          `Daily ${appState.settings.dailyHours}h • ${p1 + p2 + p3} min × 4 sessions (${p1}/${p2}/${p3})`
        )
      }),
      el('div', { className: 'row-between' }, [
        el('span', { text: t('আজকের অগ্রগতি', 'Daily Progress') }),
        el('strong', { text: `${num(doneSlots.size)}/${num(routine.length)} (${num(pct)}%)` })
      ]),
      el('div', { className: 'progress-track' }, [
        el('div', { className: 'progress-fill', style: `width:${pct}%` })
      ])
    ]);

    const breakCard = el('div', { className: 'card row-between' }, [
      el('span', {
        id: 'break-status-text',
        text: breakTimer.running
          ? t(`বিরতি চলছে: ${formatMmSs(breakTimer.remaining)}`, `Break: ${formatMmSs(breakTimer.remaining)}`)
          : t('সেশনের মাঝে ১০–১৫ মি. বিরতি', '10–15 min break between sessions')
      }),
      breakTimer.running
        ? el('button', {
            className: 'btn small danger',
            text: t('বন্ধ', 'Stop'),
            onClick: () => {
              breakTimer = { remaining: 0, running: false };
              render();
            }
          })
        : el('div', { className: 'row' }, [
            el('button', {
              className: 'btn small',
              text: t('১০ মি.', '10m'),
              onClick: () => {
                breakTimer = { remaining: 600, running: true };
                render();
              }
            }),
            el('button', {
              className: 'btn small',
              text: t('১৫ মি.', '15m'),
              onClick: () => {
                breakTimer = { remaining: 900, running: true };
                render();
              }
            })
          ])
    ]);

    const sideCol = el('div', { className: 'stack' }, [heroCard]);
    if (rolloverCount > 0) {
      sideCol.appendChild(
        el('div', { className: 'card overdue stack', style: 'cursor:pointer', onClick: () => { currentScreen = 'roadmap'; render(); } }, [
          el('strong', { text: t(`স্মার্ট রোলওভার: ${num(rolloverCount)}টি অধ্যায় বকেয়া`, `Smart Rollover: ${rolloverCount} overdue`) }),
          el('div', { className: 'muted', text: t('কঠিন অধ্যায় বাদ না দিয়ে এই সপ্তাহে শেষ করুন। রোডম্যাপ দেখুন →', 'Finish rolled-over chapters this week. Open Roadmap →') })
        ])
      );
    }
    sideCol.appendChild(breakCard);

    const sessionCards = routine.map((slot) => {
      const targetGroups = slot.group === 'chemistry_biology'
        ? ['chemistry', 'biology']
        : slot.group === 'bangla_english_ict'
        ? ['bangla', 'english', 'ict']
        : [slot.group];

      const matches = enriched
        .filter((c) => targetGroups.includes(c.subjectGroup) && curWeek >= c.startWeek && curWeek <= c.effectiveWeek)
        .sort((a, b) => Number(b.isRolledOver) - Number(a.isRolledOver) || Number(isFinishedForRollover(a.status)) - Number(isFinishedForRollover(b.status)));

      const activeCh = matches.find((c) => !isFinishedForRollover(c.status)) || matches[0];
      const timer = timers[slot.slot];
      const elapsed = timer.total - timer.remaining;
      const p1Sec = p1 * 60;
      const p2Sec = p2 * 60;
      const p3Sec = p3 * 60;

      const phaseLabel = elapsed < p1Sec
        ? t(`ধাপ ১/৩ (${num(p1)} মি.): নতুন টপিক পড়া`, `Phase 1/3 (${p1}m): New topic`)
        : elapsed < p1Sec + p2Sec
        ? t(`ধাপ ২/৩ (${num(p2)} মি.): অঙ্ক, MCQ বা সৃজনশীল`, `Phase 2/3 (${p2}m): Practice`)
        : t(`ধাপ ৩/৩ (${num(p3)} মি.): বই বন্ধ করে মনে করা`, `Phase 3/3 (${p3}m): Closed-book recall`);

      const p1Fill = Math.min(100, Math.max(0, (elapsed / p1Sec) * 100));
      const p2Fill = Math.min(100, Math.max(0, ((elapsed - p1Sec) / p2Sec) * 100));
      const p3Fill = Math.min(100, Math.max(0, ((elapsed - p1Sec - p2Sec) / p3Sec) * 100));

      const isDone = doneSlots.has(slot.slot);
      const doneCb = el('input', { type: 'checkbox', checked: isDone });
      doneCb.addEventListener('change', (e) => {
        const set = new Set(appState.dailyCompletedSlots[tIso] || []);
        if (e.target.checked) set.add(slot.slot);
        else set.delete(slot.slot);
        appState.dailyCompletedSlots[tIso] = Array.from(set);
        saveState();
        render();
      });

      const children = [
        el('div', { className: 'row-between' }, [
          el('div', { className: 'row' }, [
            el('span', { className: 'badge primary', text: num(slot.slot) }),
            el('strong', { text: lang === 'bn' ? slot.labelBn : slot.labelEn })
          ]),
          el('label', { className: 'row', style: 'cursor:pointer' }, [
            el('span', { className: 'muted', text: t('সম্পন্ন', 'Done') }),
            doneCb
          ])
        ])
      ];

      if (activeCh) {
        children.push(
          el('div', { className: `card stack ${activeCh.isOverdue ? 'overdue' : ''}`, style: 'padding:12px' }, [
            el('div', { className: 'row-between' }, [
              el('span', {
                className: `badge ${activeCh.isOverdue ? 'error' : ''}`,
                text: activeCh.isRolledOver
                  ? t(`রোলওভার (সপ্তাহ ${num(activeCh.weekNumber)})`, `Rolled over (Wk ${activeCh.weekNumber})`)
                  : activeCh.paper
              }),
              el('span', { className: 'muted', text: formatShortDate(activeCh.deadline, lang) })
            ]),
            el('strong', { text: activeCh.title }),
            renderStatusSelect(activeCh, (val) => {
              appState.chapters = appState.chapters.map((c) => (c.id === activeCh.id ? { ...c, status: val } : c));
              saveState();
              render();
            })
          ])
        );
      }

      children.push(
        el('div', { id: `phase-label-${slot.slot}`, className: 'muted', text: phaseLabel }),
        el('div', { className: 'phase-bars' }, [
          el('div', { className: 'progress-track' }, [el('div', { id: `p1-fill-${slot.slot}`, className: 'progress-fill', style: `width:${p1Fill}%` })]),
          el('div', { className: 'progress-track' }, [el('div', { id: `p2-fill-${slot.slot}`, className: 'progress-fill', style: `width:${p2Fill}%;background:var(--secondary)` })]),
          el('div', { className: 'progress-track' }, [el('div', { id: `p3-fill-${slot.slot}`, className: 'progress-fill', style: `width:${p3Fill}%;background:var(--tertiary)` })])
        ]),
        el('div', { className: 'row-between' }, [
          el('div', { id: `timer-readout-${slot.slot}`, className: 'timer-readout', text: formatMmSs(timer.remaining) }),
          el('div', { className: 'row' }, [
            el('button', {
              className: 'btn small',
              text: t('রিসেট', 'Reset'),
              onClick: () => {
                timer.running = false;
                timer.remaining = timer.total;
                render();
              }
            }),
            el('button', {
              className: 'btn primary small',
              text: timer.running ? t('বিরতি', 'Pause') : t('শুরু', 'Start'),
              onClick: () => {
                const next = !timer.running;
                Object.values(timers).forEach((o) => { o.running = false; });
                timer.running = next;
                render();
              }
            })
          ])
        ])
      );

      return el('div', { className: `card stack ${timer.running ? 'running' : ''}` }, children);
    });

    return el('div', { className: 'desktop-split' }, [
      el('div', { className: 'grid-2' }, sessionCards),
      sideCol
    ]);
  }

  function renderRoadmap(enriched) {
    const lang = appState.settings.language;
    const curWeek = currentWeekNumber();

    const subjSidebar = el('div', { className: 'card stack' }, [
      el('strong', { text: t('বিষয়সমূহ', 'Subjects') }),
      el('button', {
        className: `subject-item ${roadmapSubject === 'ALL' ? 'active' : ''}`,
        onClick: () => { roadmapSubject = 'ALL'; render(); }
      }, [
        el('span', { text: t('সব বিষয়', 'All Subjects') }),
        el('span', { className: 'muted', text: `${num(enriched.length)} ${t('অধ্যায়', 'chapters')}` })
      ])
    ]);

    PLAN.subjects.forEach((subj) => {
      const list = enriched.filter((c) => c.subjectId === subj.id);
      const done = list.filter((c) => isFinishedForRollover(c.status)).length;
      const pct = list.length ? Math.round((done * 100) / list.length) : 0;
      subjSidebar.appendChild(
        el('button', {
          className: `subject-item ${roadmapSubject === subj.id ? 'active' : ''}`,
          onClick: () => { roadmapSubject = subj.id; render(); }
        }, [
          el('div', { className: 'row-between' }, [
            el('span', { text: lang === 'bn' ? subj.nameBn : subj.nameEn }),
            el('span', { className: 'muted', text: `${num(done)}/${num(list.length)}` })
          ]),
          el('div', { className: 'progress-track', style: 'height:5px' }, [
            el('div', { className: 'progress-fill', style: `width:${pct}%` })
          ])
        ])
      );
    });

    const searchIn = el('input', {
      type: 'text',
      value: roadmapQuery,
      placeholder: t('অধ্যায় বা বিষয় খুঁজুন...', 'Search chapter or subject...')
    });
    searchIn.addEventListener('input', (e) => {
      roadmapQuery = e.target.value;
      render();
      const restored = document.querySelector('.roadmap-search');
      if (restored) {
        restored.focus();
        restored.setSelectionRange(roadmapQuery.length, roadmapQuery.length);
      }
    });
    searchIn.className = 'roadmap-search';

    const filtered = enriched.filter((c) => {
      const mSubj = roadmapSubject === 'ALL' || c.subjectId === roadmapSubject;
      const mFilter = roadmapFilter === 'ALL' ||
        (roadmapFilter === 'THIS_WEEK' && c.isCurrentWeek) ||
        (roadmapFilter === 'OVERDUE' && c.isOverdue);
      const q = roadmapQuery.trim().toLowerCase();
      const mQ = !q || c.title.toLowerCase().includes(q) || c.subjectNameBn.toLowerCase().includes(q) || c.paper.toLowerCase().includes(q);
      return mSubj && mFilter && mQ;
    });

    const activeSubj = PLAN.subjects.find((s) => s.id === roadmapSubject);

    const rightPane = el('div', { className: 'stack' }, [
      el('div', { className: 'row-between' }, [
        el('div', { style: 'flex:1;min-width:220px' }, [searchIn]),
        el('div', { className: 'row' }, [
          el('button', { className: `chip ${roadmapFilter === 'ALL' ? 'active' : ''}`, text: t('সব', 'All'), onClick: () => { roadmapFilter = 'ALL'; render(); } }),
          el('button', { className: `chip ${roadmapFilter === 'THIS_WEEK' ? 'active' : ''}`, text: t(`এই সপ্তাহ (${num(curWeek)})`, `Week ${curWeek}`), onClick: () => { roadmapFilter = 'THIS_WEEK'; render(); } }),
          el('button', { className: `chip ${roadmapFilter === 'OVERDUE' ? 'active' : ''}`, text: t(`বকেয়া (${num(enriched.filter((c) => c.isOverdue).length)})`, `Overdue`), onClick: () => { roadmapFilter = 'OVERDUE'; render(); } })
        ])
      ])
    ]);

    if (activeSubj && activeSubj.noteBn) {
      rightPane.appendChild(el('div', { className: 'card', style: 'border-color:var(--secondary)' }, [
        el('span', { className: 'muted', text: activeSubj.noteBn })
      ]));
    }

    const cardsGrid = el('div', { className: 'grid-2' }, filtered.map((ch) => {
      const cls = ch.isOverdue ? 'overdue' : ch.isCurrentWeek ? 'current-week' : '';
      return el('div', { className: `card stack ${cls}` }, [
        el('div', { className: 'row-between' }, [
          el('span', { className: 'badge', text: `${lang === 'bn' ? ch.subjectNameBn : ch.subjectNameEn} • ${ch.paper}` }),
          ch.isOverdue
            ? el('span', { className: 'badge error', text: t('বকেয়া', 'OVERDUE') })
            : ch.isCurrentWeek
            ? el('span', { className: 'badge primary', text: t('এই সপ্তাহ', 'THIS WEEK') })
            : null
        ]),
        el('div', { className: 'row-between' }, [
          el('strong', { text: ch.title, style: 'flex:1' }),
          el('button', { className: 'btn small', text: '✏️', onClick: () => openEditChapterModal(ch) })
        ]),
        el('div', { className: 'row-between' }, [
          el('span', {
            className: 'muted',
            text: `${t('সপ্তাহ', 'Wk')} ${num(ch.weekNumber)} • ${formatShortDate(ch.deadline, lang)}`
          }),
          renderStatusSelect(ch, (val) => {
            appState.chapters = appState.chapters.map((item) => (item.id === ch.id ? { ...item, status: val } : item));
            saveState();
            render();
          })
        ])
      ]);
    }));

    rightPane.appendChild(cardsGrid);
    return el('div', { className: 'roadmap-split' }, [subjSidebar, rightPane]);
  }

  function renderWeekly(enriched) {
    const lang = appState.settings.language;
    const w = weeklySelectedWeek;
    const pct = computeWeekPercent(w, enriched);
    const belowGoal = pct < 80;
    const tasksDone = new Set(appState.weeklyTasksCompleted[w] || []);
    const fridayDone = new Set(appState.fridayChecklistCompleted[w] || []);
    const weekCh = enriched.filter((c) => w >= c.startWeek && w <= c.effectiveWeek);

    const leftPane = el('div', { className: 'stack' }, [
      el('div', { className: 'card stack' }, [
        el('div', { className: 'row-between' }, [
          el('button', {
            className: 'btn small',
            text: '◀',
            disabled: w <= 1,
            onClick: () => { if (weeklySelectedWeek > 1) { weeklySelectedWeek--; render(); } }
          }),
          el('div', { style: 'text-align:center' }, [
            el('strong', { text: t(`সপ্তাহ ${num(w)} / ২৬`, `Week ${w} / 26`) }),
            el('div', { className: 'muted', text: `${formatShortDate(weekStartIso(w), lang)} – ${formatShortDate(weekDeadlineIso(w), lang)}` })
          ]),
          el('button', {
            className: 'btn small',
            text: '▶',
            disabled: w >= 26,
            onClick: () => { if (weeklySelectedWeek < 26) { weeklySelectedWeek++; render(); } }
          })
        ]),
        el('div', { className: 'row-between' }, [
          el('span', { text: t('সাপ্তাহিক লক্ষ্য: ৮০–৯০%', 'Weekly Target: 80–90%') }),
          el('strong', { text: `${num(pct)}%`, style: `color:${belowGoal ? 'var(--error)' : 'var(--primary)'}` })
        ]),
        el('div', { className: 'progress-track' }, [
          el('div', { className: 'progress-fill', style: `width:${pct}%;background:${belowGoal ? 'var(--error)' : 'var(--primary)'}` })
        ])
      ]),
      el('div', { className: `card ${belowGoal ? 'overdue' : 'current-week'}` }, [
        el('strong', {
          text: belowGoal
            ? t(`সতর্কতা: সম্পন্ন হার ৮০%-এর নিচে (${num(pct)}%)। কঠিন অধ্যায় বাদ না দিয়ে পরের সপ্তাহে রোলওভার করে শেষ করুন।`,
                `Warning: Completion is below 80% (${pct}%). Never skip tough chapters—roll them over.`)
            : t('দারুণ! সাপ্তাহিক লক্ষ্য (৮০%+) অর্জিত হয়েছে।', 'Great! 80%+ weekly goal reached.')
        })
      ]),
      el('strong', { text: t('প্রতি সপ্তাহের বাধ্যতামূলক কাজ', 'Weekly Mandatory Tasks') }),
      ...PLAN.weeklyMandatoryTasks.map((task) => {
        const checked = tasksDone.has(task.id);
        const cb = el('input', { type: 'checkbox', checked });
        cb.addEventListener('change', () => {
          const set = new Set(appState.weeklyTasksCompleted[w] || []);
          if (set.has(task.id)) set.delete(task.id); else set.add(task.id);
          appState.weeklyTasksCompleted[w] = Array.from(set);
          saveState();
          render();
        });
        return el('label', { className: `check-row ${checked ? 'checked' : ''}` }, [
          cb,
          el('span', { text: lang === 'bn' ? task.bn : task.en })
        ]);
      }),
      el('strong', { text: t('শুক্রবার — সাপ্তাহিক পরীক্ষা চেকলিস্ট', 'Friday — Exam Checklist'), style: 'margin-top:8px' }),
      ...PLAN.fridayExamChecklist.map((item) => {
        const checked = fridayDone.has(item.id);
        const cb = el('input', { type: 'checkbox', checked });
        cb.addEventListener('change', () => {
          const set = new Set(appState.fridayChecklistCompleted[w] || []);
          if (set.has(item.id)) set.delete(item.id); else set.add(item.id);
          appState.fridayChecklistCompleted[w] = Array.from(set);
          saveState();
          render();
        });
        return el('label', { className: `check-row ${checked ? 'checked' : ''}` }, [
          cb,
          el('span', { text: lang === 'bn' ? item.bn : item.en })
        ]);
      })
    ]);

    const rightPane = el('div', { className: 'stack' }, [
      el('strong', { text: t(`সপ্তাহ ${num(w)}-এর অধ্যায়সমূহ (${num(weekCh.length)})`, `Chapters in Week ${w} (${weekCh.length})`) }),
      ...weekCh.map((ch) => el('div', { className: `card row-between ${ch.isOverdue ? 'overdue' : ''}` }, [
        el('div', { style: 'flex:1' }, [
          el('div', { className: 'muted', text: `${lang === 'bn' ? ch.subjectNameBn : ch.subjectNameEn} • ${ch.paper}` }),
          el('strong', { text: ch.title })
        ]),
        renderStatusSelect(ch, (val) => {
          appState.chapters = appState.chapters.map((item) => (item.id === ch.id ? { ...item, status: val } : item));
          saveState();
          render();
        })
      ]))
    ]);

    return el('div', { className: 'grid-2' }, [leftPane, rightPane]);
  }

  function openAddMistakeModal() {
    const subjSel = el('select');
    PLAN.subjects.forEach((s) => subjSel.appendChild(el('option', { value: s.id, text: t(s.nameBn, s.nameEn) })));
    const chIn = el('input', { type: 'text', placeholder: t('অধ্যায়ের নাম', 'Chapter title') });
    const qIn = el('textarea', { rows: 3, placeholder: t('ভুল প্রশ্নটি লিখুন...', 'Enter the wrong question...') });
    const aIn = el('textarea', { rows: 3, placeholder: t('সঠিক উত্তর / ব্যাখ্যা...', 'Correct answer / explanation...') });
    const dIn = el('input', { type: 'date', value: todayIso() });

    openModal(el('div', { className: 'stack' }, [
      el('h3', { text: t('ভুলের খাতায় নতুন এন্ট্রি', 'Add Mistake Entry'), style: 'margin:0' }),
      subjSel,
      chIn,
      qIn,
      aIn,
      dIn,
      el('div', { className: 'row', style: 'justify-content:flex-end' }, [
        el('button', { className: 'btn', text: t('বাতিল', 'Cancel'), onClick: closeModal }),
        el('button', {
          className: 'btn primary',
          text: t('সংরক্ষণ', 'Save'),
          onClick: () => {
            if (!qIn.value.trim() || !aIn.value.trim()) return;
            const subj = PLAN.subjects.find((s) => s.id === subjSel.value);
            appState.mistakes.unshift({
              id: `m_${Date.now()}`,
              subjectId: subjSel.value,
              subjectNameBn: subj ? subj.nameBn : '',
              subjectNameEn: subj ? subj.nameEn : '',
              chapterTitle: chIn.value.trim(),
              question: qIn.value.trim(),
              correctAnswer: aIn.value.trim(),
              date: dIn.value || todayIso(),
              revised: false
            });
            saveState();
            closeModal();
            render();
          }
        })
      ])
    ]));
  }

  function renderMistakes() {
    const lang = appState.settings.language;
    const filtered = appState.mistakes.filter((m) => {
      const mRev = mistakeFilter === 'ALL' || (mistakeFilter === 'UNREVISED' && !m.revised) || (mistakeFilter === 'REVISED' && m.revised);
      const mSub = mistakeSubjectFilter === 'ALL' || m.subjectId === mistakeSubjectFilter;
      return mRev && mSub;
    });

    const header = el('div', { className: 'row-between' }, [
      el('div', { className: 'row' }, [
        el('button', { className: `chip ${mistakeFilter === 'ALL' ? 'active' : ''}`, text: t(`সব (${num(appState.mistakes.length)})`, `All (${appState.mistakes.length})`), onClick: () => { mistakeFilter = 'ALL'; render(); } }),
        el('button', { className: `chip ${mistakeFilter === 'UNREVISED' ? 'active' : ''}`, text: t('রিভিশন বাকি', 'Unrevised'), onClick: () => { mistakeFilter = 'UNREVISED'; render(); } }),
        el('button', { className: `chip ${mistakeFilter === 'REVISED' ? 'active' : ''}`, text: t('রিভাইজড', 'Revised'), onClick: () => { mistakeFilter = 'REVISED'; render(); } })
      ]),
      el('button', { className: 'btn primary', text: t('+ ভুল যোগ করুন', '+ Add Mistake'), onClick: openAddMistakeModal })
    ]);

    const subjRow = el('div', { className: 'row' }, [
      el('button', { className: `chip ${mistakeSubjectFilter === 'ALL' ? 'active' : ''}`, text: t('সব বিষয়', 'All Subjects'), onClick: () => { mistakeSubjectFilter = 'ALL'; render(); } }),
      ...PLAN.subjects.map((s) => el('button', {
        className: `chip ${mistakeSubjectFilter === s.id ? 'active' : ''}`,
        text: lang === 'bn' ? s.nameBn : s.nameEn,
        onClick: () => { mistakeSubjectFilter = s.id; render(); }
      }))
    ]);

    if (filtered.length === 0) {
      return el('div', { className: 'stack' }, [
        header,
        subjRow,
        el('div', { className: 'card stack', style: 'text-align:center;padding:36px' }, [
          el('strong', { text: t('ভুলের খাতা এখন খালি', 'Mistake Notebook is empty') }),
          el('span', { className: 'muted', text: t('অনুশীলন বা সাপ্তাহিক পরীক্ষায় ভুল হওয়া প্রশ্ন ও সঠিক উত্তর এখানে সংরক্ষণ করুন।', 'Log wrong questions and correct answers here to revise later.') })
        ])
      ]);
    }

    const grid = el('div', { className: 'grid-2' }, filtered.map((m) =>
      el('div', { className: `card stack ${m.revised ? 'current-week' : ''}` }, [
        el('div', { className: 'row-between' }, [
          el('span', { className: 'badge primary', text: lang === 'bn' ? m.subjectNameBn : m.subjectNameEn }),
          el('span', { className: 'muted', text: formatShortDate(m.date, lang) })
        ]),
        m.chapterTitle ? el('span', { className: 'badge warn', text: m.chapterTitle }) : null,
        el('strong', { text: `${t('প্রশ্ন:', 'Q:')} ${m.question}` }),
        el('div', { className: 'card', style: 'background:var(--primary-bg);border:none;padding:10px' }, [
          el('span', { text: `${t('সঠিক উত্তর:', 'Answer:')} ${m.correctAnswer}` })
        ]),
        el('div', { className: 'row-between' }, [
          el('button', {
            className: `chip ${m.revised ? 'active' : ''}`,
            text: m.revised ? t('✓ রিভিশন সম্পন্ন', '✓ Revised') : t('রিভাইজড চিহ্নিত করুন', 'Mark Revised'),
            onClick: () => {
              appState.mistakes = appState.mistakes.map((x) => (x.id === m.id ? { ...x, revised: !x.revised } : x));
              saveState();
              render();
            }
          }),
          el('button', {
            className: 'btn small danger',
            text: t('মুছুন', 'Delete'),
            onClick: () => {
              appState.mistakes = appState.mistakes.filter((x) => x.id !== m.id);
              saveState();
              render();
            }
          })
        ])
      ])
    ));

    return el('div', { className: 'stack' }, [header, subjRow, grid]);
  }

  function renderProgress(enriched) {
    const lang = appState.settings.language;
    const tIso = todayIso();
    const curWeek = currentWeekNumber(tIso);
    const daysLeft = Math.max(0, daysBetween(tIso, PLAN_END));
    const total = enriched.length;
    const done = enriched.filter((c) => isFinishedForRollover(c.status)).length;
    const overallPct = total ? Math.round((done * 100) / total) : 0;

    let weeklyStreak = 0;
    for (let w = curWeek; w >= 1; w--) {
      if (computeWeekPercent(w, enriched) >= 80) weeklyStreak++;
      else if (w < curWeek) break;
    }

    let dailyStreak = 0;
    let cursor = parseIsoLocal(tIso);
    const keyOf = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    if (!(appState.dailyCompletedSlots[keyOf(cursor)] || []).length) {
      cursor.setDate(cursor.getDate() - 1);
    }
    while ((appState.dailyCompletedSlots[keyOf(cursor)] || []).length > 0 && dailyStreak < 365) {
      dailyStreak++;
      cursor.setDate(cursor.getDate() - 1);
    }

    const topGrid = el('div', { className: 'grid-3' }, [
      el('div', { className: 'card hero stack' }, [
        el('span', { className: 'muted', text: t('২৩ মে ২০২৭ পর্যন্ত বাকি', 'Countdown to 23 May 2027') }),
        el('div', { className: 'timer-readout', text: t(`${num(daysLeft)} দিন`, `${daysLeft} days`) }),
        el('div', { className: 'row-between' }, [
          el('span', { text: t(`সামগ্রিক: ${num(done)}/${num(total)}`, `Overall: ${done}/${total}`) }),
          el('strong', { text: `${num(overallPct)}%` })
        ]),
        el('div', { className: 'progress-track' }, [el('div', { className: 'progress-fill', style: `width:${overallPct}%` })])
      ]),
      el('div', { className: 'card stack' }, [
        el('span', { className: 'muted', text: t('সাপ্তাহিক স্ট্রিক (৮০%+)', 'Weekly Streak (80%+)') }),
        el('div', { className: 'timer-readout', style: 'color:var(--primary)', text: t(`${num(weeklyStreak)} সপ্তাহ`, `${weeklyStreak} wks`) })
      ]),
      el('div', { className: 'card stack' }, [
        el('span', { className: 'muted', text: t('দৈনিক স্টাডি স্ট্রিক', 'Daily Study Streak') }),
        el('div', { className: 'timer-readout', style: 'color:var(--secondary)', text: t(`${num(dailyStreak)} দিন`, `${dailyStreak} days`) })
      ])
    ]);

    const subjGrid = el('div', { className: 'grid-2' }, PLAN.subjects.map((subj) => {
      const list = enriched.filter((c) => c.subjectId === subj.id);
      const sDone = list.filter((c) => isFinishedForRollover(c.status)).length;
      const sOver = list.filter((c) => c.isOverdue).length;
      const pct = list.length ? Math.round((sDone * 100) / list.length) : 0;
      return el('div', { className: 'card stack' }, [
        el('div', { className: 'row-between' }, [
          el('strong', { text: lang === 'bn' ? subj.nameBn : subj.nameEn }),
          el('strong', { text: `${num(pct)}%`, style: 'color:var(--primary)' })
        ]),
        el('div', { className: 'muted', text: `${num(sDone)}/${num(list.length)} ${t('সম্পন্ন', 'done')}${sOver ? ` • ${num(sOver)} ${t('বকেয়া', 'overdue')}` : ''}` }),
        el('div', { className: 'progress-track' }, [el('div', { className: 'progress-fill', style: `width:${pct}%` })])
      ]);
    }));

    return el('div', { className: 'stack' }, [
      topGrid,
      el('strong', { text: t('বিষয়ভিত্তিক অগ্রগতি', 'Subject Progress'), style: 'margin-top:8px' }),
      subjGrid
    ]);
  }

  function renderSettings(enriched) {
    const lang = appState.settings.language;

    const hoursRow = el('div', { className: 'card stack' }, [
      el('strong', { text: t('দৈনিক পড়ার সময়', 'Daily Study Hours') }),
      el('div', { className: 'row' }, [
        el('button', {
          className: `chip ${appState.settings.dailyHours === 5 ? 'active' : ''}`,
          text: t('৫ ঘণ্টা (৭৫ মি. × ৪)', '5h (75m × 4)'),
          onClick: () => { appState.settings.dailyHours = 5; saveState(); render(); }
        }),
        el('button', {
          className: `chip ${appState.settings.dailyHours === 3 ? 'active' : ''}`,
          text: t('৩ ঘণ্টা (৪৫ মি. × ৪)', '3h (45m × 4)'),
          onClick: () => { appState.settings.dailyHours = 3; saveState(); render(); }
        })
      ])
    ]);

    const notifCard = el('div', { className: 'card stack' }, [
      el('strong', { text: t('ডেস্কটপ নোটিফিকেশন ও ব্যাকআপ', 'Desktop Notifications & Backup') }),
      el('div', { className: 'row' }, [
        el('button', {
          className: `chip ${appState.settings.notificationsEnabled ? 'active' : ''}`,
          text: appState.settings.notificationsEnabled ? t('নোটিফিকেশন চালু আছে', 'Notifications ON') : t('নোটিফিকেশন চালু করুন', 'Enable Notifications'),
          onClick: async () => {
            if ('Notification' in window) {
              const perm = await Notification.requestPermission();
              appState.settings.notificationsEnabled = perm === 'granted';
              saveState();
              if (perm === 'granted') notify('HSC স্টাডি', t('নোটিফিকেশন চালু হয়েছে!', 'Notifications enabled!'));
              render();
            }
          }
        }),
        el('button', {
          className: 'btn small',
          text: t('JSON ব্যাকআপ ডাউনলোড', 'Export JSON'),
          onClick: () => {
            const blob = new Blob([JSON.stringify(appState, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `hsc_study_backup_${todayIso()}.json`;
            a.click();
            URL.revokeObjectURL(url);
          }
        }),
        el('label', { className: 'btn small', style: 'cursor:pointer' }, [
          el('span', { text: t('JSON ইমপোর্ট', 'Import JSON') }),
          (() => {
            const inp = el('input', { type: 'file', accept: '.json,application/json', style: 'display:none' });
            inp.addEventListener('change', (e) => {
              const file = e.target.files[0];
              if (!file) return;
              const reader = new FileReader();
              reader.onload = () => {
                try {
                  const data = JSON.parse(reader.result);
                  if (data && Array.isArray(data.chapters) && data.chapters.length) {
                    appState = data;
                    saveState();
                    render();
                  }
                } catch {}
              };
              reader.readAsText(file);
            });
            return inp;
          })()
        ]),
        el('button', {
          className: 'btn small danger',
          text: t('সব রিসেট', 'Reset All'),
          onClick: () => {
            if (confirm(t('সব অগ্রগতি রিসেট করবেন?', 'Reset all progress to default?'))) {
              appState = buildInitialState();
              saveState();
              render();
            }
          }
        })
      ])
    ]);

    const subjTabs = el('div', { className: 'row' }, PLAN.subjects.map((s) =>
      el('button', {
        className: `chip ${settingsEditSubject === s.id ? 'active' : ''}`,
        text: lang === 'bn' ? s.nameBn : s.nameEn,
        onClick: () => { settingsEditSubject = s.id; render(); }
      })
    ));

    const chList = enriched.filter((c) => c.subjectId === settingsEditSubject);
    const editorGrid = el('div', { className: 'grid-2' }, chList.map((ch) =>
      el('div', { className: 'card row-between' }, [
        el('div', { style: 'flex:1' }, [
          el('strong', { text: ch.title }),
          el('div', { className: 'muted', text: `${ch.paper} • ${t('সপ্তাহ', 'Wk')} ${num(ch.weekNumber)} • ${ch.deadline}` })
        ]),
        el('div', { className: 'row' }, [
          el('button', { className: 'btn small', text: '✏️', onClick: () => openEditChapterModal(ch) }),
          el('button', {
            className: 'btn small danger',
            text: '🗑️',
            onClick: () => {
              appState.chapters = appState.chapters.filter((x) => x.id !== ch.id);
              saveState();
              render();
            }
          })
        ])
      ])
    ));

    return el('div', { className: 'stack' }, [
      el('div', { className: 'grid-2' }, [hoursRow, notifCard]),
      el('strong', { text: t('অধ্যায় ও ডেডলাইন এডিট (২০২৮ সিলেবাস / ইংরেজি ইউনিট)', 'Edit Chapters & Deadlines') }),
      subjTabs,
      editorGrid
    ]);
  }

  function render() {
    const lang = appState.settings.language;
    document.body.classList.toggle('light', !appState.settings.darkMode);

    const navDefs = [
      { id: 'today', icon: '📅', label: t('আজ', 'Today') },
      { id: 'roadmap', icon: '🗺️', label: t('রোডম্যাপ', 'Roadmap') },
      { id: 'weekly', icon: '✅', label: t('সাপ্তাহিক', 'Weekly') },
      { id: 'mistakes', icon: '📓', label: t('ভুলের খাতা', 'Mistakes') },
      { id: 'progress', icon: '📊', label: t('অগ্রগতি', 'Progress') },
      { id: 'settings', icon: '⚙️', label: t('সেটিংস', 'Settings') }
    ];

    document.getElementById('brandTitle').textContent = t('HSC ২০২৮ বিজ্ঞান', 'HSC 2028 Science');
    document.getElementById('brandSub').textContent = t('২৬ সপ্তাহের ডেস্কটপ স্টাডি প্ল্যান', '26-Week Desktop Study Plan');
    document.getElementById('langToggleBtn').textContent = lang === 'bn' ? 'English' : 'বাংলা';
    document.getElementById('themeToggleBtn').textContent = appState.settings.darkMode ? '☀️ Light' : '🌙 Dark';

    const curWeek = currentWeekNumber();
    const daysLeft = Math.max(0, daysBetween(todayIso(), PLAN_END));
    document.getElementById('topbarWeekBadge').textContent = t(`সপ্তাহ ${num(curWeek)}/২৬`, `Week ${curWeek}/26`);
    document.getElementById('sidebarCountdown').textContent = t(`২৩ মে ২০২৭ বাকি: ${num(daysLeft)} দিন`, `${daysLeft} days to 23 May 2027`);

    const navList = document.getElementById('navList');
    navList.innerHTML = '';
    navDefs.forEach((n) => {
      const btn = el('button', {
        className: `nav-btn ${currentScreen === n.id ? 'active' : ''}`,
        onClick: () => { currentScreen = n.id; render(); }
      }, [`${n.icon} ${n.label}`]);
      navList.appendChild(btn);
    });

    const activeNav = navDefs.find((n) => n.id === currentScreen);
    document.getElementById('pageTitle').textContent = activeNav ? activeNav.label : '';

    const enriched = getChaptersWithRollover();
    const main = document.getElementById('mainContent');
    main.innerHTML = '';

    if (currentScreen === 'today') main.appendChild(renderToday(enriched));
    else if (currentScreen === 'roadmap') main.appendChild(renderRoadmap(enriched));
    else if (currentScreen === 'weekly') main.appendChild(renderWeekly(enriched));
    else if (currentScreen === 'mistakes') main.appendChild(renderMistakes());
    else if (currentScreen === 'progress') main.appendChild(renderProgress(enriched));
    else if (currentScreen === 'settings') main.appendChild(renderSettings(enriched));
  }

  document.getElementById('langToggleBtn').addEventListener('click', () => {
    appState.settings.language = appState.settings.language === 'bn' ? 'en' : 'bn';
    saveState();
    render();
  });

  document.getElementById('themeToggleBtn').addEventListener('click', () => {
    appState.settings.darkMode = !appState.settings.darkMode;
    saveState();
    render();
  });

  // PWA Install button on Desktop Chrome/Edge
  let deferredInstallPrompt = null;
  const installBtn = document.getElementById('installPromptBtn');
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredInstallPrompt = e;
    installBtn.style.display = 'inline-flex';
  });
  installBtn.addEventListener('click', async () => {
    if (!deferredInstallPrompt) return;
    deferredInstallPrompt.prompt();
    await deferredInstallPrompt.userChoice;
    deferredInstallPrompt = null;
    installBtn.style.display = 'none';
  });

  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }

  render();
})();
