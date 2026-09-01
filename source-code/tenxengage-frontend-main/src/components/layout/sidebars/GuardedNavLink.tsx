import { NavLink, type NavLinkProps } from "react-router-dom";
import { useNavigationGuard } from "@/contexts/NavigationGuardContext";

export function GuardedNavLink({ to, onClick, ...rest }: NavLinkProps) {
  const { checkGuard } = useNavigationGuard();

  function handleClick(e: React.MouseEvent<HTMLAnchorElement>) {
    const path = typeof to === "string" ? to : (to.pathname ?? "");
    if (!checkGuard(path)) {
      e.preventDefault();
      return;
    }
    onClick?.(e);
  }

  return <NavLink to={to} onClick={handleClick} {...rest} />;
}
