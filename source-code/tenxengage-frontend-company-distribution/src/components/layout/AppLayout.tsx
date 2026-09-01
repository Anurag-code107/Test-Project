import BaseLayout from "@/components/layout/BaseLayout";
import { RoleSidebar } from "@/components/layout/sidebars/RoleSidebar";
import { sidebarConfig } from "@/components/layout/sidebars/sidebarConfigs";

function AppLayout() {
  return (
    <BaseLayout
      sidebar={<RoleSidebar config={sidebarConfig} />}
    />
  );
}

export default AppLayout;
