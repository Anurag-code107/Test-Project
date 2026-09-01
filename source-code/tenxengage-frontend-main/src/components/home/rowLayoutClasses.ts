const ROW_LAYOUT_CLASSES: Record<string, string> = {
  full: "grid grid-cols-1 gap-6",
  "half-half": "grid grid-cols-1 md:grid-cols-2 gap-6",
};

export function rowLayoutClass(layout: string): string | undefined {
  return ROW_LAYOUT_CLASSES[layout];
}

export function isKnownRowLayout(layout: string): boolean {
  return layout in ROW_LAYOUT_CLASSES;
}
