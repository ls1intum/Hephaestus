import fs from 'node:fs';
import path from 'node:path';

// Simple script to copy environment.prod.ts to environment.ts before build
console.log('Copying index.prod.ts to index.ts for production build...');

const srcDir = path.resolve(process.cwd(), 'src/environment');
const sourcePath = path.resolve(srcDir, 'index.prod.ts');
const destPath = path.resolve(srcDir, 'index.ts');

// Debug paths
console.log(`Source directory: ${srcDir}`);
console.log(`Source file path: ${sourcePath}`);
console.log(`Destination file path: ${destPath}`);

// Ensure source file exists
if (!fs.existsSync(sourcePath)) {
  console.error(`Error: Source file not found: ${sourcePath}`);
  process.exit(1);
}

// Copy file
try {
  const content = fs.readFileSync(sourcePath, 'utf8');
  fs.writeFileSync(destPath, content, 'utf8');
  console.log('Environment file successfully copied for production build.');
} catch (error) {
  console.error('Error copying environment file:', error);
  process.exit(1);
}