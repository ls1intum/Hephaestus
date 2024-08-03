const tsPlugin = require('@typescript-eslint/eslint-plugin');
const angularPlugin = require('@angular-eslint/eslint-plugin');
const angularTemplateParser = require('@angular-eslint/template-parser');
const typescriptParser = require('@typescript-eslint/parser');
const prettierPlugin = require('eslint-plugin-prettier');

module.exports = [
  {
    ignores: [
      '.cache/',
      '.git/',
      '.github/',
      'build/',
      'coverage/',
      'node/',
      'node_modules/',
      'src/app/core/',
    ],
  },
  {
    files: ['src/**/*.ts'],
    languageOptions: {
      parser: typescriptParser,
      parserOptions: {
        project: [
          './tsconfig.json',
          './tsconfig.app.json',
          './tsconfig.spec.json',
        ],
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      '@angular-eslint': angularPlugin,
      'prettier': prettierPlugin,
    },
    rules: {
      ...prettierPlugin.configs.recommended.rules,
      ...tsPlugin.configs.recommended.rules,
      ...angularPlugin.configs.recommended.rules,
      '@angular-eslint/directive-selector': [
        'warn',
        {
          type: 'attribute',
          prefix: 'jhi',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'warn',
        {
          type: 'element',
          prefix: 'jhi',
          style: 'kebab-case',
        },
      ],
      '@typescript-eslint/no-non-null-assertion': 'off',
      '@typescript-eslint/no-unsafe-return': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-floating-promises': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@angular-eslint/no-output-on-prefix': 'off',
      '@typescript-eslint/ban-ts-comment': 'warn',
      '@typescript-eslint/no-empty-function': 'off',
      '@typescript-eslint/no-non-null-asserted-optional-chain': 'warn',
      '@typescript-eslint/no-explicit-any': 'off',
      'no-case-declarations': 'off',
      'prefer-const': 'warn',
      'prefer-spread': 'warn',
      'no-var': 'error',
      'sort-imports': [
        'error',
        {
          ignoreDeclarationSort: true,
        },
      ],
      '@typescript-eslint/no-unused-vars': ['error', { caughtErrors: 'none' }],
    },
  },
  {
    files: ['src/**/*.html'],
    languageOptions: {
      parser: angularTemplateParser,
    },
    plugins: {
      '@angular-eslint': angularPlugin,
      'prettier': prettierPlugin,
    },
    rules: {
      'prettier/prettier': ['error', { parser: 'angular' }],
    },
  },
];
