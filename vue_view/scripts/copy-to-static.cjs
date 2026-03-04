const fs = require('fs');
const path = require('path');

const src = path.join(__dirname, '..', 'dist');
const dest = path.join(__dirname, '..', '..', 'java-work', 'src', 'main', 'resources', 'static');

if (!fs.existsSync(src)) {
  console.error('dist 不存在，请先执行 npm run build');
  process.exit(1);
}
if (!fs.existsSync(dest)) fs.mkdirSync(dest, { recursive: true });

function copyDir(from, to) {
  for (const name of fs.readdirSync(from)) {
    const s = path.join(from, name);
    const d = path.join(to, name);
    if (fs.statSync(s).isDirectory()) {
      if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
      copyDir(s, d);
    } else {
      fs.copyFileSync(s, d);
    }
  }
}

copyDir(src, dest);
console.log('已复制 Vue 构建产物到 java-work/src/main/resources/static');
