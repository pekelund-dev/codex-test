import js from '@eslint/js';
import globals from 'globals';

export default [
  {
    files: ['src/main/frontend/**/*.js'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: globals.browser
    },
    rules: {
      ...js.configs.recommended.rules
    }
  },
  {
    files: ['src/main/resources/static/js/main.js'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: globals.browser
    },
    rules: {
      ...js.configs.recommended.rules
    }
  }
];
