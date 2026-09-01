// Countries supported for mobile payouts — MUST stay in sync with the backend PhoneDialCodes table
// (com.tenxengage.app.service.xtrm.PhoneDialCodes). Used by the mobile-number country selector.

export interface MobileCountry {
  iso2: string;
  name: string;
  dialCode: string;
}

export const SUPPORTED_MOBILE_COUNTRIES: MobileCountry[] = [
  { iso2: "US", name: "United States", dialCode: "1" },
  { iso2: "CA", name: "Canada", dialCode: "1" },
  { iso2: "GB", name: "United Kingdom", dialCode: "44" },
  { iso2: "IN", name: "India", dialCode: "91" },
  { iso2: "AU", name: "Australia", dialCode: "61" },
  { iso2: "DE", name: "Germany", dialCode: "49" },
  { iso2: "FR", name: "France", dialCode: "33" },
  { iso2: "ES", name: "Spain", dialCode: "34" },
  { iso2: "IT", name: "Italy", dialCode: "39" },
  { iso2: "NL", name: "Netherlands", dialCode: "31" },
  { iso2: "SE", name: "Sweden", dialCode: "46" },
  { iso2: "NO", name: "Norway", dialCode: "47" },
  { iso2: "DK", name: "Denmark", dialCode: "45" },
  { iso2: "FI", name: "Finland", dialCode: "358" },
  { iso2: "IE", name: "Ireland", dialCode: "353" },
  { iso2: "CH", name: "Switzerland", dialCode: "41" },
  { iso2: "AT", name: "Austria", dialCode: "43" },
  { iso2: "BE", name: "Belgium", dialCode: "32" },
  { iso2: "PT", name: "Portugal", dialCode: "351" },
  { iso2: "PL", name: "Poland", dialCode: "48" },
  { iso2: "BR", name: "Brazil", dialCode: "55" },
  { iso2: "MX", name: "Mexico", dialCode: "52" },
  { iso2: "AR", name: "Argentina", dialCode: "54" },
  { iso2: "JP", name: "Japan", dialCode: "81" },
  { iso2: "CN", name: "China", dialCode: "86" },
  { iso2: "KR", name: "South Korea", dialCode: "82" },
  { iso2: "SG", name: "Singapore", dialCode: "65" },
  { iso2: "HK", name: "Hong Kong", dialCode: "852" },
  { iso2: "MY", name: "Malaysia", dialCode: "60" },
  { iso2: "ID", name: "Indonesia", dialCode: "62" },
  { iso2: "PH", name: "Philippines", dialCode: "63" },
  { iso2: "TH", name: "Thailand", dialCode: "66" },
  { iso2: "VN", name: "Vietnam", dialCode: "84" },
  { iso2: "AE", name: "United Arab Emirates", dialCode: "971" },
  { iso2: "SA", name: "Saudi Arabia", dialCode: "966" },
  { iso2: "ZA", name: "South Africa", dialCode: "27" },
  { iso2: "NG", name: "Nigeria", dialCode: "234" },
  { iso2: "KE", name: "Kenya", dialCode: "254" },
  { iso2: "EG", name: "Egypt", dialCode: "20" },
  { iso2: "NZ", name: "New Zealand", dialCode: "64" },
  { iso2: "IL", name: "Israel", dialCode: "972" },
  { iso2: "TR", name: "Turkey", dialCode: "90" },
  { iso2: "RU", name: "Russia", dialCode: "7" },
  { iso2: "UA", name: "Ukraine", dialCode: "380" },
  { iso2: "PK", name: "Pakistan", dialCode: "92" },
  { iso2: "BD", name: "Bangladesh", dialCode: "880" },
  { iso2: "LK", name: "Sri Lanka", dialCode: "94" },
  { iso2: "NP", name: "Nepal", dialCode: "977" },
];

const BY_ISO2 = new Map(SUPPORTED_MOBILE_COUNTRIES.map((c) => [c.iso2, c]));

export function dialCodeFor(iso2: string | null | undefined): string | null {
  if (!iso2) return null;
  return BY_ISO2.get(iso2.toUpperCase())?.dialCode ?? null;
}
