import "@testing-library/jest-dom";

// Radix UI components (Select, Checkbox, etc.) use ResizeObserver — not in jsdom
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Radix UI Select uses hasPointerCapture / setPointerCapture — not in jsdom
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {};
}
// Radix UI Select uses scrollIntoView for highlighted items — not in jsdom
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}

// File API object URLs — not in jsdom
if (!global.URL.createObjectURL) {
  global.URL.createObjectURL = () => "blob:mock";
}
if (!global.URL.revokeObjectURL) {
  global.URL.revokeObjectURL = () => {};
}
