import { useState, useRef, useEffect, useMemo } from "react";
import {
  Building2,
  Users,
  UserCheck,
  DollarSign,
  TrendingUp,
  TrendingDown,
  ChevronDown,
  ChevronUp,
  ArrowUpRight,
  PieChart,
  Search,
  X,
  UserPlus,
  Loader2,
} from "lucide-react";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { RewardBreakdownExpanded } from "@/components/RewardBreakdownExpanded";
import { LocationFilter } from "@/components/LocationFilter";
import { useHomeDashboardState } from "@/components/home/HomeDashboardContext";
import { useProgramPerformance, usePartnerSearch } from "@/hooks/useHomeApi";
import type {
  MetricResponse,
  PartnerCompanySearchResult,
} from "@/types/home.types";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  ResponsiveContainer,
} from "recharts";

type CardKey =
  | "rewards"
  | "budget"
  | "users"
  | "newPartners"
  | "newUsers"
  | "companiesEarning";

/* ── Swap animation styles (injected once) ────────────────────────── */
const SWAP_STYLE_ID = "home-swap-animations";
if (
  typeof document !== "undefined" &&
  !document.getElementById(SWAP_STYLE_ID)
) {
  const style = document.createElement("style");
  style.id = SWAP_STYLE_ID;
  style.textContent = `
    @keyframes swapToHero {
      0%   { opacity: 0; transform: scale(0.82) translateX(30px); filter: blur(4px); }
      50%  { opacity: 0.7; transform: scale(0.95) translateX(6px); filter: blur(1px); }
      100% { opacity: 1; transform: scale(1) translateX(0); filter: blur(0); }
    }
    @keyframes swapToSide {
      0%   { opacity: 0; transform: scale(1.08) translateX(-20px); filter: blur(3px); }
      50%  { opacity: 0.7; transform: scale(1.02) translateX(-4px); filter: blur(1px); }
      100% { opacity: 1; transform: scale(1) translateX(0); filter: blur(0); }
    }
    @keyframes cardFadeIn {
      0%   { opacity: 0; transform: translateY(8px); }
      100% { opacity: 1; transform: translateY(0); }
    }
    .swap-to-hero  { animation: swapToHero 0.45s cubic-bezier(0.22, 1, 0.36, 1) both; }
    .swap-to-side  { animation: swapToSide 0.4s cubic-bezier(0.22, 1, 0.36, 1) both; }
    .card-data-enter { animation: cardFadeIn 0.3s ease-out both; }
  `;
  document.head.appendChild(style);
}

function toChartData(metric: MetricResponse | null | undefined) {
  if (!metric?.trendData) return [];
  return metric.trendData.map((p) => ({ quarter: p.label, value: p.value }));
}

function formatAxisValue(v: number, prefix: string, suffix: string): string {
  if (v >= 1_000_000) return `${prefix}${(v / 1_000_000).toFixed(1)}M${suffix}`;
  if (v >= 1_000) return `${prefix}${(v / 1_000).toFixed(0)}K${suffix}`;
  return `${prefix}${Math.round(v)}${suffix}`;
}

function TrendBadge({
  value,
  label,
}: {
  value: number | null | undefined;
  label: string;
}) {
  if (value === undefined || value === null) return null;
  const positive = value >= 0;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1",
        positive ? "bg-success/10" : "bg-destructive/10",
      )}
    >
      {positive ? (
        <TrendingUp className="h-3 w-3 text-success" />
      ) : (
        <TrendingDown className="h-3 w-3 text-destructive" />
      )}
      <span
        className={cn(
          "text-sm font-semibold tabular-nums",
          positive ? "text-success" : "text-destructive",
        )}
      >
        {positive ? "+" : ""}
        {value}%
      </span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </span>
  );
}

interface CardConfig {
  key: CardKey;
  label: string;
  metric: MetricResponse | null | undefined;
  icon: React.ElementType;
  prefix: string;
  suffix: string;
  accentColor: string;
  accentGradient: string;
  iconBg: string;
  iconColor: string;
  chartColor: string;
}

interface ProgramPerformanceWidgetProps {
  /**
   * Optional callback fired when the user selects / clears a partner in the
   * filter. The Client Admin HomePage uses this to sync a partner name into
   * the page-level greeting banner.
   */
  onPartnerChange?: (partner: PartnerCompanySearchResult | null) => void;
}

export function ProgramPerformanceWidget({
  onPartnerChange,
}: ProgramPerformanceWidgetProps = {}) {
  const { setSelectedPartnerName } = useHomeDashboardState();
  const [selectedRegion, setSelectedRegion] = useState<string>("GLOBAL");
  const [showRewardBreakdown, setShowRewardBreakdown] = useState(false);
  const incentivePerformanceEndRef = useRef<HTMLDivElement>(null);

  // Partner search
  const [partnerSearch, setPartnerSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [selectedPartner, setSelectedPartnerState] =
    useState<PartnerCompanySearchResult | null>(null);
  const [showPartnerSuggestions, setShowPartnerSuggestions] = useState(false);
  const partnerSearchRef = useRef<HTMLDivElement>(null);

  const setSelectedPartner = (partner: PartnerCompanySearchResult | null) => {
    setSelectedPartnerState(partner);
    setSelectedPartnerName(partner?.name ?? null);
    onPartnerChange?.(partner);
  };

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(partnerSearch), 300);
    return () => clearTimeout(timer);
  }, [partnerSearch]);

  const {
    data: partnerPages,
    isLoading: isSearching,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = usePartnerSearch(
    debouncedSearch,
    showPartnerSuggestions && !selectedPartner,
  );
  const filteredPartners = useMemo(
    () => partnerPages?.pages.flatMap((p) => p.data) ?? [],
    [partnerPages],
  );
  const partnerListRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        partnerSearchRef.current &&
        !partnerSearchRef.current.contains(event.target as Node)
      ) {
        setShowPartnerSuggestions(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const params = useMemo(
    () => ({
      region: selectedRegion,
      ...(selectedPartner ? { partnerCompanyId: selectedPartner.id } : {}),
    }),
    [selectedRegion, selectedPartner],
  );

  const { data: perfData, isLoading } = useProgramPerformance(params);

  useEffect(() => {
    if (showRewardBreakdown && incentivePerformanceEndRef.current) {
      setTimeout(() => {
        incentivePerformanceEndRef.current?.scrollIntoView({
          behavior: "smooth",
          block: "end",
        });
      }, 150);
    }
  }, [showRewardBreakdown]);

  const [cardOrder, setCardOrder] = useState<CardKey[]>([
    "rewards",
    "budget",
    "users",
    "newPartners",
    "newUsers",
    "companiesEarning",
  ]);
  const heroKey = cardOrder[0]!;
  const swapCounter = useRef(0);
  const prevHeroRef = useRef<CardKey>("rewards");

  const handleSwap = (key: CardKey) => {
    if (key === heroKey) return;
    prevHeroRef.current = heroKey;
    swapCounter.current += 1;
    setCardOrder((prev) => {
      const idx = prev.indexOf(key);
      const next = [...prev];
      next[0] = key;
      next[idx] = prev[0]!;
      return next;
    });
  };

  const comparisonLabel = "vs same period last quarter";

  const metricValue = (
    metric: MetricResponse | null | undefined,
    prefix = "",
    suffix = "",
  ) => {
    if (!metric) return "—";
    return `${prefix}${Math.round(metric.value).toLocaleString()}${suffix}`;
  };

  const pf = perfData?.partnerFiltered ?? false;

  const allCards: Record<CardKey, CardConfig> = useMemo(
    () => ({
      rewards: {
        key: "rewards",
        label: "Total Rewards Earned",
        metric: perfData?.totalRewardsEarned,
        icon: DollarSign,
        prefix: "$",
        suffix: "",
        accentColor: "hsl(217 91% 60%)",
        accentGradient:
          "linear-gradient(to bottom, hsl(217 91% 60%), hsl(160 60% 45%))",
        iconBg: "hsl(217_91%_60%/0.08)",
        iconColor: "text-[hsl(217_91%_55%)]",
        chartColor: "hsl(217, 91%, 60%)",
      },
      budget: {
        key: "budget",
        label: "Budget Utilized",
        metric: perfData?.budgetUtilized,
        icon: PieChart,
        prefix: "",
        suffix: "%",
        accentColor: "hsl(30 80% 52%)",
        accentGradient: "hsl(30 80% 52%)",
        iconBg: "hsl(30_70%_50%/0.08)",
        iconColor: "text-[hsl(30_70%_45%)]",
        chartColor: "hsl(30, 80%, 52%)",
      },
      users: {
        key: "users",
        label: "Users Participating",
        metric: perfData?.usersParticipating,
        icon: Users,
        prefix: "",
        suffix: "%",
        accentColor: "hsl(175 60% 42%)",
        accentGradient: "hsl(175 60% 42%)",
        iconBg: "hsl(175_60%_40%/0.08)",
        iconColor: "text-[hsl(175_60%_35%)]",
        chartColor: "hsl(175, 60%, 42%)",
      },
      newPartners: {
        key: "newPartners",
        label: pf ? "Partner Users Enrolled" : "New Partners",
        metric: pf
          ? perfData?.partnerEnrolledUsers
          : perfData?.partnerCompaniesEnrolled,
        icon: pf ? Users : Building2,
        prefix: "",
        suffix: "",
        accentColor: "hsl(260 60% 58%)",
        accentGradient: "hsl(260 60% 58%)",
        iconBg: "hsl(260_60%_55%/0.08)",
        iconColor: "text-[hsl(260_60%_48%)]",
        chartColor: "hsl(260, 60%, 58%)",
      },
      newUsers: {
        key: "newUsers",
        label: pf ? "Users Earning Rewards" : "New Users",
        metric: pf
          ? perfData?.usersEarningRewards
          : perfData?.partnerUsersEnrolled,
        icon: pf ? UserCheck : UserPlus,
        prefix: "",
        suffix: pf ? "%" : "",
        accentColor: "hsl(152 56% 39%)",
        accentGradient: "hsl(152 56% 39%)",
        iconBg: "hsl(152_50%_40%/0.08)",
        iconColor: "text-[hsl(152_50%_35%)]",
        chartColor: "hsl(152, 56%, 39%)",
      },
      companiesEarning: {
        key: "companiesEarning",
        label: pf ? "User Claims Made" : "Companies Earning Rewards",
        metric: pf
          ? perfData?.userClaimsMade
          : perfData?.companiesEarningRewards,
        icon: pf ? UserCheck : UserCheck,
        prefix: "",
        suffix: pf ? "" : "%",
        accentColor: "hsl(340 65% 55%)",
        accentGradient: "hsl(340 65% 55%)",
        iconBg: "hsl(340_60%_55%/0.08)",
        iconColor: "text-[hsl(340_60%_48%)]",
        chartColor: "hsl(340, 65%, 55%)",
      },
    }),
    [perfData, pf],
  );

  const heroConfig = allCards[heroKey];
  const sideKeys: CardKey[] = [cardOrder[1]!, cardOrder[2]!];
  const flatKeys: CardKey[] = [cardOrder[3]!, cardOrder[4]!, cardOrder[5]!];

  return (
    <section className="w-full" data-tour="program-performance-section">
      {/* Section title row with inline filters */}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-base font-semibold text-foreground">
          Program Performance
        </h2>

        <div className="flex items-center gap-2">
          {/* Partner search */}
          <div className="relative" ref={partnerSearchRef}>
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <input
                type="text"
                placeholder="Filter by partner"
                value={selectedPartner ? selectedPartner.name : partnerSearch}
                onChange={(e) => {
                  setPartnerSearch(e.target.value);
                  setSelectedPartner(null);
                  setShowPartnerSuggestions(true);
                }}
                onFocus={() => setShowPartnerSuggestions(true)}
                className="h-8 w-[180px] rounded-md border border-border bg-background pl-8 pr-7 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring/20 focus:border-ring/40 transition-colors"
              />
              {(selectedPartner || partnerSearch) && (
                <button
                  onClick={() => {
                    setPartnerSearch("");
                    setSelectedPartner(null);
                    setShowPartnerSuggestions(false);
                  }}
                  className="absolute right-1.5 top-1/2 -translate-y-1/2 p-0.5 rounded hover:bg-muted transition-colors"
                >
                  <X className="h-3 w-3 text-muted-foreground" />
                </button>
              )}
            </div>

            {showPartnerSuggestions && !selectedPartner && (
              <div className="absolute top-full left-0 right-0 mt-1 z-50 rounded-lg border bg-popover shadow-md overflow-hidden">
                <Command className="rounded-lg">
                  <CommandList
                    ref={partnerListRef}
                    className="max-h-[220px] overflow-y-auto"
                    onScroll={(e) => {
                      const target = e.currentTarget;
                      if (
                        target.scrollHeight -
                          target.scrollTop -
                          target.clientHeight <
                          40 &&
                        hasNextPage &&
                        !isFetchingNextPage
                      ) {
                        fetchNextPage();
                      }
                    }}
                  >
                    {isSearching && filteredPartners.length === 0 ? (
                      <div className="py-3 text-center">
                        <Loader2 className="h-4 w-4 animate-spin mx-auto text-muted-foreground" />
                      </div>
                    ) : filteredPartners.length === 0 ? (
                      <CommandEmpty className="py-3 text-center text-sm text-muted-foreground">
                        No partners found
                      </CommandEmpty>
                    ) : (
                      <CommandGroup>
                        {filteredPartners.map((partner) => (
                          <CommandItem
                            key={partner.id}
                            onSelect={() => {
                              setSelectedPartner(partner);
                              setPartnerSearch("");
                              setShowPartnerSuggestions(false);
                            }}
                            className="cursor-pointer group"
                          >
                            <Building2 className="mr-2 h-3.5 w-3.5 text-muted-foreground group-data-[selected=true]:text-accent-foreground" />
                            <span className="text-sm">{partner.name}</span>
                            <span className="ml-auto text-xs text-muted-foreground group-data-[selected=true]:text-accent-foreground tabular-nums">
                              {partner.activeUserCount}
                            </span>
                          </CommandItem>
                        ))}
                        {isFetchingNextPage && (
                          <div className="py-2 text-center">
                            <Loader2 className="h-3.5 w-3.5 animate-spin mx-auto text-muted-foreground" />
                          </div>
                        )}
                      </CommandGroup>
                    )}
                  </CommandList>
                </Command>
              </div>
            )}
          </div>

          <div className="w-px h-4 bg-border" />

          {/* Location filter dropdown */}
          <LocationFilter
            value={selectedRegion}
            onChange={setSelectedRegion}
            className="h-8 w-[140px] text-sm border-border"
          />

          {isLoading && (
            <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground" />
          )}
        </div>
      </div>

      {/* ── Hero card + 2 side cards ────────────────────────────────── */}
      <div
        className="grid grid-cols-1 lg:grid-cols-[2fr_1fr] gap-4 lg:h-[444px]"
        data-tour="metrics-cards"
        style={{ alignItems: "stretch" }}
      >
        {/* Hero card */}
        <Card
          key={`hero-${heroKey}-${swapCounter.current}`}
          className="hero-rewards-card swap-to-hero group relative flex flex-col rounded-2xl shadow-none transition-[box-shadow,border-color,transform] duration-300 overflow-hidden"
        >
          <div
            className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-2xl"
            style={{ background: heroConfig.accentGradient }}
          />
          <div className="relative z-10 p-6 flex flex-col flex-1">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <div
                  className={`flex items-center justify-center w-7 h-7 rounded-lg bg-[${heroConfig.iconBg}]`}
                >
                  <heroConfig.icon
                    className={`h-3.5 w-3.5 ${heroConfig.iconColor}`}
                  />
                </div>
                <p className="text-sm font-medium text-muted-foreground">
                  {heroConfig.label}
                </p>
              </div>
              <TrendBadge
                value={heroConfig.metric?.trendPercent}
                label={comparisonLabel}
              />
            </div>
            <p className="text-[1.75rem] font-semibold tracking-tight text-foreground leading-none tabular-nums mt-1">
              {metricValue(
                heroConfig.metric,
                heroConfig.prefix,
                heroConfig.suffix,
              )}
            </p>
            {heroConfig.metric?.subValue && (
              <p className="text-xs text-muted-foreground mt-0.5">
                {heroConfig.metric.subValue.replace(
                  /\$(\d+)/g,
                  (_: string, n: string) => "$" + Number(n).toLocaleString(),
                )}
              </p>
            )}
            <div className="mt-4 -mx-1 flex-1 min-h-0 flex flex-col overflow-hidden">
              <div
                className={cn(
                  "flex-1 min-h-0 transition-[max-height] duration-500 ease-in-out",
                  showRewardBreakdown && heroKey === "rewards"
                    ? "max-h-[80px]"
                    : "max-h-[400px]",
                )}
              >
                {toChartData(heroConfig.metric).length >= 2 && (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                      data={toChartData(heroConfig.metric)}
                      margin={{ top: 8, right: 8, bottom: 20, left: 50 }}
                    >
                      <defs>
                        <linearGradient
                          id="heroGradient"
                          x1="0"
                          y1="0"
                          x2="0"
                          y2="1"
                        >
                          <stop
                            offset="0%"
                            stopColor={heroConfig.chartColor}
                            stopOpacity={0.18}
                          />
                          <stop
                            offset="100%"
                            stopColor={heroConfig.chartColor}
                            stopOpacity={0.02}
                          />
                        </linearGradient>
                      </defs>
                      <CartesianGrid
                        strokeDasharray="3 3"
                        stroke="hsl(var(--border))"
                        vertical={false}
                      />
                      <XAxis
                        dataKey="quarter"
                        tickLine={false}
                        axisLine={false}
                        fontSize={11}
                        tick={{ fill: "hsl(var(--muted-foreground))" }}
                        dy={4}
                      />
                      <YAxis
                        tickLine={false}
                        axisLine={false}
                        fontSize={11}
                        tick={{ fill: "hsl(var(--muted-foreground))" }}
                        tickFormatter={(v) =>
                          formatAxisValue(
                            v,
                            heroConfig.prefix,
                            heroConfig.suffix,
                          )
                        }
                        width={46}
                      />
                      <Area
                        type="monotone"
                        dataKey="value"
                        stroke={heroConfig.chartColor}
                        strokeWidth={2}
                        fill="url(#heroGradient)"
                        dot={{
                          fill: "white",
                          stroke: heroConfig.chartColor,
                          strokeWidth: 2,
                          r: 3,
                        }}
                        animationDuration={500}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>
              {heroKey === "rewards" && (
                <div
                  className="overflow-hidden transition-[max-height,opacity] duration-500 ease-in-out shrink-0"
                  style={{
                    maxHeight:
                      showRewardBreakdown && perfData?.rewardBreakdown
                        ? 220
                        : 0,
                    opacity: showRewardBreakdown ? 1 : 0,
                  }}
                >
                  {perfData?.rewardBreakdown && (
                    <RewardBreakdownExpanded
                      breakdown={perfData.rewardBreakdown}
                      formatAmount={(amount) =>
                        "$" + Math.round(amount).toLocaleString()
                      }
                    />
                  )}
                </div>
              )}
            </div>
            {heroKey === "rewards" && (
              <div className="flex items-center justify-between mt-3 pt-3 border-t border-border shrink-0">
                <button
                  className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors"
                  onClick={() => setShowRewardBreakdown(!showRewardBreakdown)}
                >
                  {showRewardBreakdown ? "Hide breakdown" : "Show breakdown"}
                  {showRewardBreakdown ? (
                    <ChevronUp className="h-3 w-3" />
                  ) : (
                    <ChevronDown className="h-3 w-3" />
                  )}
                </button>
              </div>
            )}
          </div>
        </Card>

        {/* Side cards */}
        <div className="flex flex-col gap-4">
          {sideKeys.map((key, idx) => {
            const cfg = allCards[key];
            const Icon = cfg.icon;
            const wasHero =
              key === prevHeroRef.current && swapCounter.current > 0;
            return (
              <Card
                key={`side-${key}-${swapCounter.current}`}
                className={cn(
                  "group relative flex-1 flex flex-col rounded-2xl shadow-none p-5 cursor-pointer transition-[box-shadow,transform] duration-300 overflow-hidden hover:shadow-[0_4px_16px_hsl(var(--muted-foreground)/0.08)] hover:-translate-y-0.5",
                  wasHero && "swap-to-side",
                )}
                style={
                  wasHero ? undefined : { animationDelay: `${idx * 80}ms` }
                }
                onClick={() => handleSwap(key)}
              >
                <div
                  className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-2xl transition-opacity duration-300 group-hover:opacity-0"
                  style={{ backgroundColor: cfg.accentColor }}
                />
                <div
                  className="absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"
                  style={{
                    background: `linear-gradient(to right, ${cfg.accentColor.replace(")", " / 0.07)")} 0%, ${cfg.accentColor.replace(")", " / 0.035)")} 35%, transparent 80%)`,
                  }}
                />
                <div className="relative z-10 flex flex-col flex-1">
                  <div className="flex items-center gap-2 mb-3">
                    <div
                      className={`flex items-center justify-center w-7 h-7 rounded-lg bg-[${cfg.iconBg}]`}
                    >
                      <Icon className={`h-3.5 w-3.5 ${cfg.iconColor}`} />
                    </div>
                    <p className="text-sm font-medium text-muted-foreground">
                      {cfg.label}
                    </p>
                  </div>
                  <p className="text-[1.75rem] font-semibold tracking-tight text-foreground tabular-nums">
                    {metricValue(cfg.metric, cfg.prefix, cfg.suffix)}
                  </p>
                  {cfg.metric?.subValue && (
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {cfg.metric.subValue.replace(
                        /\$(\d+)/g,
                        (_, n: string) => "$" + Number(n).toLocaleString(),
                      )}
                    </p>
                  )}
                  <div className="mt-auto pt-3">
                    <TrendBadge
                      value={cfg.metric?.trendPercent}
                      label={comparisonLabel}
                    />
                  </div>
                  <div className="mt-3 pt-3 border-t border-border flex items-center text-xs font-medium text-primary">
                    <span>View Trend</span>
                    <ArrowUpRight className="h-3 w-3 ml-auto opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-[opacity,transform] duration-200" />
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      </div>
      <div ref={incentivePerformanceEndRef} />

      {/* ── Flat cards row ──────────────────────────────────────────── */}
      <div
        className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4"
        data-tour="participation-section"
      >
        {flatKeys.map((key) => {
          const cfg = allCards[key];
          const Icon = cfg.icon;
          const isFirstCard = flatKeys[0] === key;
          const tourAttr = isFirstCard
            ? "participation-section"
            : "incentive-performance-section";
          return (
            <Card
              key={`flat-${key}`}
              className="group relative flex flex-col rounded-2xl shadow-none p-5 cursor-pointer transition-[box-shadow,transform] duration-300 overflow-hidden hover:shadow-[0_4px_16px_hsl(var(--muted-foreground)/0.08)] hover:-translate-y-0.5"
              onClick={() => handleSwap(key)}
              data-tour={tourAttr}
            >
              <div
                className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-2xl transition-opacity duration-300 group-hover:opacity-0"
                style={{ backgroundColor: cfg.accentColor }}
              />
              <div
                className="absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"
                style={{
                  background: `linear-gradient(to right, ${cfg.accentColor.replace(")", " / 0.07)")} 0%, ${cfg.accentColor.replace(")", " / 0.035)")} 35%, transparent 80%)`,
                }}
              />
              <div className="relative z-10 flex flex-col flex-1">
                <div className="flex items-center gap-2 mb-3">
                  <div
                    className={`flex items-center justify-center w-7 h-7 rounded-lg bg-[${cfg.iconBg}]`}
                  >
                    <Icon className={`h-3.5 w-3.5 ${cfg.iconColor}`} />
                  </div>
                  <p className="text-sm font-medium text-muted-foreground">
                    {cfg.label}
                  </p>
                </div>
                <div className="flex items-baseline gap-2">
                  <p className="text-[clamp(1.5rem,2.5vw,1.875rem)] font-semibold tracking-[-0.02em] text-foreground tabular-nums">
                    {metricValue(cfg.metric, cfg.prefix, cfg.suffix)}
                  </p>
                  {cfg.metric?.subValue && (
                    <p className="text-sm text-muted-foreground">
                      {cfg.metric.subValue}
                    </p>
                  )}
                </div>
                {cfg.metric?.trendPercent != null && (
                  <div className="mt-2">
                    <TrendBadge
                      value={cfg.metric.trendPercent}
                      label={comparisonLabel}
                    />
                  </div>
                )}
                <div className="flex-1" />
                <div className="mt-4 pt-3 border-t border-border flex items-center text-xs font-medium text-primary">
                  <span>View Trend</span>
                  <ArrowUpRight className="h-3 w-3 ml-auto opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-[opacity,transform] duration-200" />
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </section>
  );
}
