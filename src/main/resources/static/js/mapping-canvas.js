(function () {
  const TRANSFORMS = ['identity', 'uppercase', 'lowercase', 'trim', 'toString', 'toNumber',
                      'concat', 'substring', 'dateFormat', 'constant', 'default'];

  const canvas = document.getElementById('mappingCanvas');
  const rulesTextarea = document.getElementById('mappingRulesJson');
  if (!canvas || !rulesTextarea) return;

  let rules = [];
  try {
    const raw = rulesTextarea.value.trim();
    rules = raw ? JSON.parse(raw) : [];
  } catch (e) {
    rules = [];
  }

  render();

  function render() {
    canvas.innerHTML = '';
    const rulesBox = document.createElement('div');
    rulesBox.className = 'mc-rules';
    const header = document.createElement('div');
    header.innerHTML = '<h4 style="margin:0 0 10px;color:var(--muted);font-size:12px;text-transform:uppercase">Mapping rules</h4>';
    rulesBox.appendChild(header);

    rules.forEach((r, idx) => {
      const row = document.createElement('div');
      row.className = 'mc-rule';

      const src = document.createElement('input');
      src.placeholder = '$.source.path (or blank for constant)';
      src.value = r.source || '';
      src.addEventListener('input', () => { r.source = src.value; save(); });

      const arrow = document.createElement('span');
      arrow.className = 'arrow';
      arrow.textContent = '→';

      const tgt = document.createElement('input');
      tgt.placeholder = '$.target.path';
      tgt.value = r.target || '';
      tgt.addEventListener('input', () => { r.target = tgt.value; save(); });

      const xf = document.createElement('select');
      TRANSFORMS.forEach(name => {
        const opt = document.createElement('option');
        opt.value = name; opt.textContent = name;
        if ((r.transform || 'identity') === name) opt.selected = true;
        xf.appendChild(opt);
      });
      xf.addEventListener('change', () => { r.transform = xf.value; save(); });

      const args = document.createElement('input');
      args.placeholder = 'args (JSON array)';
      args.value = r.args ? JSON.stringify(r.args) : '';
      args.title = 'e.g. ["suffix"] or [0, 5]';
      args.addEventListener('input', () => {
        try { r.args = args.value.trim() ? JSON.parse(args.value) : []; save(); }
        catch (e) { /* ignore until valid */ }
      });

      const del = document.createElement('button');
      del.type = 'button';
      del.className = 'del';
      del.textContent = '✕';
      del.addEventListener('click', () => { rules.splice(idx, 1); render(); save(); });

      row.appendChild(src);
      row.appendChild(arrow);
      row.appendChild(tgt);
      row.appendChild(xf);
      row.appendChild(args);
      row.appendChild(del);
      rulesBox.appendChild(row);
    });

    const add = document.createElement('button');
    add.type = 'button';
    add.className = 'mc-add';
    add.textContent = '+ Add mapping rule';
    add.addEventListener('click', () => {
      rules.push({ source: '', target: '', transform: 'identity', args: [] });
      render(); save();
    });
    rulesBox.appendChild(add);

    canvas.appendChild(rulesBox);
  }

  function save() {
    rulesTextarea.value = JSON.stringify(rules, null, 2);
  }
})();
