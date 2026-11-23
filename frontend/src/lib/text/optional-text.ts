const NON_BLANK =
  /[^\t\n\v\f\r \u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]/u;

export function optionalText(value: string): string | null {
  return NON_BLANK.test(value) ? value : null;
}
