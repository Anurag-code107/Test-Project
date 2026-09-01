export interface ProductSKU {
  id: string;
  name: string;
  category: string;
}

export interface ProductCategory {
  name: string;
  products: ProductSKU[];
}

export const productCatalog: ProductCategory[] = [
  {
    name: "Servers",
    products: [
      { id: "srv-001", name: "ProLiant DL380 Gen11", category: "Servers" },
      { id: "srv-002", name: "PowerEdge R760", category: "Servers" },
      { id: "srv-003", name: "ThinkSystem SR650 V3", category: "Servers" },
      { id: "srv-004", name: "Synergy 480 Gen11", category: "Servers" },
      { id: "srv-005", name: "UCS C240 M7", category: "Servers" },
    ],
  },
  {
    name: "Routers",
    products: [
      { id: "rtr-001", name: "Catalyst 8300 Series", category: "Routers" },
      { id: "rtr-002", name: "NetEngine 8000 F8", category: "Routers" },
      { id: "rtr-003", name: "MX304 Universal", category: "Routers" },
      { id: "rtr-004", name: "ASR 1001-HX", category: "Routers" },
    ],
  },
  {
    name: "Switches",
    products: [
      { id: "swt-001", name: "Nexus 9300-GX2", category: "Switches" },
      { id: "swt-002", name: "FlexFabric 5960", category: "Switches" },
      { id: "swt-003", name: "CloudEngine S12700E", category: "Switches" },
      { id: "swt-004", name: "EX4400 Multigigabit", category: "Switches" },
    ],
  },
  {
    name: "Storage",
    products: [
      { id: "stg-001", name: "PowerStore 3200T", category: "Storage" },
      { id: "stg-002", name: "NetApp AFF A900", category: "Storage" },
      { id: "stg-003", name: "FlashSystem 9500", category: "Storage" },
      { id: "stg-004", name: "Alletra MP 9200", category: "Storage" },
    ],
  },
  {
    name: "Security",
    products: [
      {
        id: "sec-001",
        name: "Enterprise Security Suite",
        category: "Security",
      },
      { id: "sec-002", name: "FortiGate 600F", category: "Security" },
      { id: "sec-003", name: "PA-5400 Series", category: "Security" },
      { id: "sec-004", name: "Firepower 4200", category: "Security" },
    ],
  },
  {
    name: "Software & Licensing",
    products: [
      {
        id: "sfw-001",
        name: "Standard License",
        category: "Software & Licensing",
      },
      {
        id: "sfw-002",
        name: "Premium License",
        category: "Software & Licensing",
      },
      {
        id: "sfw-003",
        name: "Cloud Services",
        category: "Software & Licensing",
      },
      { id: "sfw-004", name: "SMB Package", category: "Software & Licensing" },
    ],
  },
];

export function getAllProducts(): ProductSKU[] {
  return productCatalog.flatMap((cat) => cat.products);
}
