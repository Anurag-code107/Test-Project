import "@testing-library/jest-dom";

// Radix UI components (Select, Checkbox, etc.) use ResizeObserver — not in jsdom
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};
