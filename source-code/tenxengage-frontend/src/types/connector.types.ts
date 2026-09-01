export type ConnectorType =
  | "SALESFORCE"
  | "MICROSOFT_DYNAMICS_365"
  | "SNOWFLAKE"
  | "HUBSPOT";
export type ConnectorStatus =
  | "DISCONNECTED"
  | "CONNECTED"
  | "ERROR"
  | "SYNCING";

export interface ConnectorResponse {
  id: string;
  connectorType: ConnectorType;
  name: string;
  status: ConnectorStatus;
  authType: string | null;
  lastSyncAt: string | null;
  lastSyncStatus: string | null;
  configSummary: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConnectorRequest {
  connectorType: ConnectorType;
  name: string;
  config: Record<string, string>;
  authType?: string;
}

export interface UpdateConnectorRequest {
  name?: string;
  config?: Record<string, string>;
  authType?: string;
}

export interface TestConnectionResponse {
  success: boolean;
  message: string;
}

/** Config field definitions per connector type */
export interface ConnectorConfigField {
  key: string;
  label: string;
  type: "text" | "password";
  required: boolean;
  placeholder?: string;
}

export const CONNECTOR_CONFIG_FIELDS: Record<
  ConnectorType,
  ConnectorConfigField[]
> = {
  SALESFORCE: [
    { key: "clientId", label: "Client ID", type: "text", required: true },
    {
      key: "clientSecret",
      label: "Client Secret",
      type: "password",
      required: true,
    },
    {
      key: "baseUrl",
      label: "Login URL",
      type: "text",
      required: true,
      placeholder: "https://login.salesforce.com",
    },
  ],
  MICROSOFT_DYNAMICS_365: [
    { key: "clientId", label: "Client ID", type: "text", required: true },
    {
      key: "clientSecret",
      label: "Client Secret",
      type: "password",
      required: true,
    },
    {
      key: "oauthAuthority",
      label: "OAuth Authority",
      type: "text",
      required: true,
      placeholder: "https://login.microsoftonline.com/{tenantId}",
    },
    {
      key: "resource",
      label: "Dynamics URL",
      type: "text",
      required: true,
      placeholder: "https://org.crm.dynamics.com",
    },
  ],
  SNOWFLAKE: [
    {
      key: "url",
      label: "Account URL",
      type: "text",
      required: true,
      placeholder: "account.snowflakecomputing.com",
    },
    { key: "username", label: "Username", type: "text", required: true },
    { key: "password", label: "Password", type: "password", required: true },
    { key: "role", label: "Role", type: "text", required: false },
  ],
  HUBSPOT: [
    { key: "apiKey", label: "API Key", type: "password", required: true },
    { key: "portalId", label: "Portal ID", type: "text", required: true },
  ],
};

export const CONNECTOR_TYPE_LABELS: Record<ConnectorType, string> = {
  SALESFORCE: "Salesforce",
  MICROSOFT_DYNAMICS_365: "Microsoft Dynamics 365",
  SNOWFLAKE: "Snowflake",
  HUBSPOT: "HubSpot",
};

export const CONNECTOR_AUTH_TYPES: Record<
  ConnectorType,
  { value: string; label: string }[]
> = {
  SALESFORCE: [{ value: "OAUTH", label: "OAuth 2.0 Client Credentials" }],
  MICROSOFT_DYNAMICS_365: [
    { value: "OAUTH", label: "OAuth 2.0 Client Credentials" },
  ],
  SNOWFLAKE: [
    { value: "LOGIN", label: "Username & Password" },
    { value: "PASSKEY", label: "Key Pair (JWT)" },
    { value: "OAUTH", label: "OAuth" },
  ],
  HUBSPOT: [{ value: "API_KEY", label: "API Key" }],
};
