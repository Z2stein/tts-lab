import { readdir } from 'node:fs/promises';
import { join, relative } from 'node:path';
import { spawnSync } from 'node:child_process';

async function main() {
  const srcDir = join(process.cwd(), 'src');
  const files = await collectTsFiles(srcDir);
  if (files.length === 0) {
    throw new Error('No frontend source files found to lint.');
  }

  const eslintBin = join(process.cwd(), 'node_modules', 'eslint', 'bin', 'eslint.js');
  const result = spawnSync(process.execPath, [eslintBin, ...files, '--max-warnings=0'], {
    stdio: 'inherit',
    cwd: process.cwd()
  });

  process.exit(result.status ?? 1);
}

async function collectTsFiles(root) {
  const entries = await readdir(root, { withFileTypes: true });
  const results = [];

  for (const entry of entries) {
    const fullPath = join(root, entry.name);
    if (entry.isDirectory()) {
      results.push(...(await collectTsFiles(fullPath)));
      continue;
    }

    if (!entry.isFile() || !fullPath.endsWith('.ts')) {
      continue;
    }

    const relativePath = relative(process.cwd(), fullPath).replace(/\\/g, '/');
    if (relativePath.endsWith('.spec.ts')) {
      continue;
    }

    if (relativePath === 'src/app/shared/api-contract.generated.ts') {
      continue;
    }

    results.push(relativePath);
  }

  return results;
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
