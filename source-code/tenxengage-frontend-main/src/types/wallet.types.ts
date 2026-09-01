export type WalletType = "INDIVIDUAL" | "COMPANY";

export interface RewardWalletResponse {
  id: string;
  walletType: WalletType;
  currencyId: string;
  availableBalance: string;
  reservedBalance: string;
}
