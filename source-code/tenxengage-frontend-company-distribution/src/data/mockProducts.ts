import type { ProductCategory } from "@/types/incentive.types";

export const productCatalog: ProductCategory[] = [
  {
    name: "Servers",
    products: [
      {
        id: "srv-001",
        sku: "SRV-PE-R760",
        name: "PowerEdge R760",
        category: "Servers",
      },
      {
        id: "srv-002",
        sku: "SRV-PE-R660",
        name: "PowerEdge R660",
        category: "Servers",
      },
      {
        id: "srv-003",
        sku: "SRV-PL-DL380",
        name: "ProLiant DL380",
        category: "Servers",
      },
      {
        id: "srv-004",
        sku: "SRV-PL-DL360",
        name: "ProLiant DL360",
        category: "Servers",
      },
      {
        id: "srv-005",
        sku: "SRV-TS-SR650",
        name: "ThinkSystem SR650",
        category: "Servers",
      },
    ],
  },
  {
    name: "Routers",
    products: [
      {
        id: "rtr-001",
        sku: "RTR-CAT-8500",
        name: "Catalyst 8500",
        category: "Routers",
      },
      {
        id: "rtr-002",
        sku: "RTR-CAT-8300",
        name: "Catalyst 8300",
        category: "Routers",
      },
      {
        id: "rtr-003",
        sku: "RTR-ISR-4000",
        name: "ISR 4000 Series",
        category: "Routers",
      },
      {
        id: "rtr-004",
        sku: "RTR-ASR-1000",
        name: "ASR 1000 Series",
        category: "Routers",
      },
    ],
  },
  {
    name: "Switches",
    products: [
      {
        id: "swt-001",
        sku: "SWT-CAT-9300",
        name: "Catalyst 9300",
        category: "Switches",
      },
      {
        id: "swt-002",
        sku: "SWT-CAT-9200",
        name: "Catalyst 9200",
        category: "Switches",
      },
      {
        id: "swt-003",
        sku: "SWT-NEX-9000",
        name: "Nexus 9000",
        category: "Switches",
      },
      {
        id: "swt-004",
        sku: "SWT-MER-MS390",
        name: "Meraki MS390",
        category: "Switches",
      },
    ],
  },
  {
    name: "Storage",
    products: [
      {
        id: "str-001",
        sku: "STR-PS-500T",
        name: "PowerStore 500T",
        category: "Storage",
      },
      {
        id: "str-002",
        sku: "STR-UXT-480",
        name: "Unity XT 480",
        category: "Storage",
      },
      {
        id: "str-003",
        sku: "STR-AFF-A250",
        name: "NetApp AFF A250",
        category: "Storage",
      },
    ],
  },
  {
    name: "Security",
    products: [
      {
        id: "sec-001",
        sku: "SEC-FP-4100",
        name: "Firepower 4100",
        category: "Security",
      },
      {
        id: "sec-002",
        sku: "SEC-SFW-3100",
        name: "Secure Firewall 3100",
        category: "Security",
      },
      {
        id: "sec-003",
        sku: "SEC-FG-600F",
        name: "FortiGate 600F",
        category: "Security",
      },
    ],
  },
  {
    name: "Software & Licensing",
    products: [
      {
        id: "sw-001",
        sku: "SWL-DNA-ESS",
        name: "DNA Essentials",
        category: "Software & Licensing",
      },
      {
        id: "sw-002",
        sku: "SWL-DNA-ADV",
        name: "DNA Advantage",
        category: "Software & Licensing",
      },
      {
        id: "sw-003",
        sku: "SWL-MER-ENT",
        name: "Meraki Enterprise License",
        category: "Software & Licensing",
      },
      {
        id: "sw-004",
        sku: "SWL-SCX-PLT",
        name: "SecureX Platform",
        category: "Software & Licensing",
      },
    ],
  },
];

export const allProducts = productCatalog.flatMap((cat) => cat.products);
