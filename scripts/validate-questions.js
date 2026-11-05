#!/usr/bin/env node
/**
 * 题库校验脚本（零依赖，本地和 GitHub Actions 都能跑）。
 *
 *   node scripts/validate-questions.js question-bank/questions.json app/src/main/assets/questions.json
 *
 * 为什么需要它：题库是要下发到电视上的，一道「答案下标越界」或「四个选项重复」的题
 * 会让孩子答不出来、卡在锁屏页。CI 里先挡一道，比装到电视上再发现便宜得多。
 * 校验规则和 App 里 QuestionUpdate.validate() 保持一致。
 */
const fs = require('fs');
const path = require('path');

const files = process.argv.slice(2).length
  ? process.argv.slice(2)
  : ['question-bank/questions.json', 'app/src/main/assets/questions.json'];

let errors = 0;

for (const file of files) {
  const abs = path.resolve(file);
  if (!fs.existsSync(abs)) {
    console.error(`✗ 找不到文件：${file}`);
    errors++;
    continue;
  }

  let data;
  try {
    data = JSON.parse(fs.readFileSync(abs, 'utf8'));
  } catch (e) {
    console.error(`✗ ${file} 不是合法 JSON：${e.message}`);
    errors++;
    continue;
  }

  const problems = [];
  if (!Number.isInteger(data.version) || data.version < 1) {
    problems.push('缺少正整数 version（在线题库靠它判断要不要更新）');
  }

  const summary = {};
  for (const subject of ['chinese', 'english']) {
    const arr = data[subject];
    if (!Array.isArray(arr) || arr.length === 0) {
      problems.push(`${subject} 必须是非空数组`);
      continue;
    }
    const byGrade = {};
    arr.forEach((q, i) => {
      const tag = `${subject}[${i}]`;
      if (!q || typeof q !== 'object') {
        problems.push(`${tag} 不是对象`);
        return;
      }
      if (!Number.isInteger(q.g) || q.g < 1 || q.g > 6) problems.push(`${tag} 年级 g 必须是 1~6`);
      if (typeof q.q !== 'string' || !q.q.trim()) problems.push(`${tag} 题干为空`);
      if (!Array.isArray(q.o) || q.o.length < 2) {
        problems.push(`${tag} 选项少于 2 个`);
      } else {
        if (q.o.some((o) => typeof o !== 'string' || !o.trim())) problems.push(`${tag} 有空选项`);
        if (new Set(q.o).size !== q.o.length) problems.push(`${tag} 选项重复：${q.o.join(' / ')}`);
        if (!Number.isInteger(q.a) || q.a < 0 || q.a >= q.o.length) problems.push(`${tag} 答案下标 a 越界`);
      }
      byGrade[q.g] = (byGrade[q.g] || 0) + 1;
    });
    summary[subject] = byGrade;
  }

  if (problems.length) {
    console.error(`✗ ${file}：发现 ${problems.length} 个问题`);
    problems.slice(0, 20).forEach((p) => console.error(`   - ${p}`));
    if (problems.length > 20) console.error(`   ... 还有 ${problems.length - 20} 个`);
    errors += problems.length;
  } else {
    const total = Object.values(summary).reduce(
      (n, grades) => n + Object.values(grades).reduce((a, b) => a + b, 0),
      0
    );
    console.log(`✓ ${file}  version=${data.version}  共 ${total} 题  ${JSON.stringify(summary)}`);
  }
}

if (errors) {
  console.error(`\n共 ${errors} 个问题，构建中止`);
  process.exit(1);
}
console.log('\n题库校验通过');
