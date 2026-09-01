import { lazy, Suspense } from "react";
import { useParams, Link } from "react-router-dom";

type GlobModule = () => Promise<{ default: React.ComponentType }>;

const mockupModules = import.meta.glob("./*/*.tsx", {
  eager: false,
}) as Record<string, GlobModule>;

function kebabToPascal(str: string): string {
  return str
    .split("-")
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join("");
}

function MockupIndex() {
  const links = Object.keys(mockupModules).map((key) => {
    // key: "./enablement-courses/CourseListPage.tsx"
    const [, folder, file] = key.match(/^\.\/([^/]+)\/(.+)\.tsx$/) ?? [];
    if (!folder || !file) return null;
    // Convert PascalCase filename to kebab-case for URL
    const slug = file
      .replace(/([A-Z])/g, (_m, l, i) => (i === 0 ? l : "-" + l))
      .toLowerCase();
    return { href: `/mockup/${folder}/${slug}`, label: `${folder} / ${file}` };
  });

  return (
    <div style={{ fontFamily: "monospace", padding: "2rem" }}>
      <h1 style={{ fontSize: "1.25rem", marginBottom: "1rem" }}>
        Mockup Index
      </h1>
      <ul style={{ listStyle: "none", padding: 0, lineHeight: 2 }}>
        {links.map((link) =>
          link ? (
            <li key={link.href}>
              <Link to={link.href} style={{ color: "#6366f1" }}>
                {link.href}
              </Link>{" "}
              <span style={{ color: "#888" }}>({link.label})</span>
            </li>
          ) : null
        )}
      </ul>
    </div>
  );
}

export default function MockupRouter() {
  const { "*": splat } = useParams();
  const parts = (splat ?? "").split("/").filter(Boolean);

  if (parts.length === 0) {
    return <MockupIndex />;
  }

  const folder = parts[0];
  const fileName =
    parts.length >= 2 ? kebabToPascal(parts[1]!) : "FullFeatureMockup";
  const key = `./${folder}/${fileName}.tsx`;
  const loader = mockupModules[key];

  if (!loader) {
    return <MockupIndex />;
  }

  const Component = lazy(loader);

  return (
    <Suspense fallback={null}>
      <Component />
    </Suspense>
  );
}
