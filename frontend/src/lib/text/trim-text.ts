const EDGE_WHITESPACE = /^[ \t\n\r]+|[ \t\n\r]+$/g;

export function trimText(value: string): string {
  return value.replace(EDGE_WHITESPACE, "");
}
