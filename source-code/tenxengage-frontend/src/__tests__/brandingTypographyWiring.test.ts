import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Regression test for BUG-060.
 *
 * The bug was that BrandingContext set `--font-heading` / `--font-body` on
 * `:root` but no CSS rule referenced them, and 11 of the 12 dropdown fonts
 * were never loaded by the browser. This test asserts the full wiring:
 *
 *   1. index.css's body and h1-h6 rules consume the CSS variables.
 *   2. index.html's stylesheet link loads every font listed in the
 *      BrandingSection dropdown.
 */

const FRONTEND_ROOT = resolve(__dirname, "..", "..");
const indexCss = readFileSync(
  resolve(FRONTEND_ROOT, "src", "index.css"),
  "utf-8",
);
const indexHtml = readFileSync(resolve(FRONTEND_ROOT, "index.html"), "utf-8");

describe("branding typography wiring (BUG-060)", () => {
  it("body rule consumes --font-body so saved branding actually applies", () => {
    const bodyRule = indexCss.match(/\bbody\s*\{[^}]*\}/);
    expect(bodyRule, "body rule must exist in index.css").not.toBeNull();
    expect(bodyRule![0]).toMatch(/font-family\s*:[^;]*var\(\s*--font-body/);
  });

  it("heading rule consumes --font-heading", () => {
    const headingRule = indexCss.match(/h1\s*,\s*h2[^{]*\{[^}]*\}/);
    expect(headingRule, "h1-h6 rule must exist in index.css").not.toBeNull();
    expect(headingRule![0]).toMatch(
      /font-family\s*:[^;]*var\(\s*--font-heading/,
    );
  });

  it.each([
    ["Inter", "Inter"],
    ["Roboto", "Roboto"],
    ["Open Sans", "Open+Sans"],
    ["Lato", "Lato"],
    ["Poppins", "Poppins"],
    ["Montserrat", "Montserrat"],
    ["Source Sans 3", "Source+Sans+3"],
    ["Nunito", "Nunito"],
    ["Raleway", "Raleway"],
    ["Ubuntu", "Ubuntu"],
    ["Playfair Display", "Playfair+Display"],
    ["Merriweather", "Merriweather"],
  ])("loads the %s font family from Google Fonts", (_label, urlSegment) => {
    expect(indexHtml).toContain(`family=${urlSegment}`);
  });
});
