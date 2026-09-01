export type BannerTheme =
  | "default"
  | "home"
  | "incentives"
  | "claims"
  | "reports"
  | "activity"
  | "profile"
  | "users"
  | "settings"
  | "builder-ai"
  | "builder-manual"
  | "rewards"
  | "deal-qualifier"
  | "view-incentives";

interface PageBannerProps {
  title: string;
  subtitle: React.ReactNode;
  /** Optional action buttons rendered on the right side */
  actions?: React.ReactNode;
  /** Visual theme — shapes relate to the page's purpose */
  theme?: BannerTheme;
  /** Optional back-navigation handler — renders a back arrow before the title */
  onBack?: () => void;
}

/* ─── Themed SVG art per page ──────────────────────────────────────────────── */

function DefaultArt() {
  return (
    <>
      {/* Flowing curves */}
      <path
        d="M-40 110 C120 80, 280 140, 440 100 S700 50, 860 90 S1040 130, 1060 100"
        stroke="hsl(217 91% 60% / 0.28)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 130 C140 95, 320 155, 480 120 S740 60, 900 110 S1060 145, 1080 120"
        stroke="hsl(199 89% 48% / 0.22)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      <path
        d="M-60 45 C100 70, 260 25, 400 55 S620 95, 800 45 S960 30, 1060 55"
        stroke="hsl(95 55% 50% / 0.20)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-3"
      />
      {/* Geometric shapes */}
      <circle
        cx="840"
        cy="40"
        r="28"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      <rect
        x="900"
        y="95"
        width="38"
        height="38"
        rx="8"
        stroke="hsl(199 89% 48% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
        transform="rotate(12 919 114)"
      />
      <circle
        cx="760"
        cy="130"
        r="15"
        stroke="hsl(95 55% 50% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="700"
        cy="35"
        r="2.5"
        fill="hsl(217 91% 60% / 0.30)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="65"
        r="2"
        fill="hsl(95 55% 50% / 0.28)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="800"
        cy="90"
        r="2.5"
        fill="hsl(199 89% 48% / 0.28)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function IncentivesArt() {
  return (
    <>
      {/* Flowing reward curves */}
      <path
        d="M-30 105 C150 75, 300 130, 460 95 S680 55, 840 85 S1020 120, 1060 95"
        stroke="hsl(38 90% 50% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 135 C180 100, 350 150, 500 115 S720 65, 880 100 S1040 140, 1070 115"
        stroke="hsl(217 91% 60% / 0.20)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* Cash — DollarSign (emerald) — circle with $ */}
      <circle
        cx="820"
        cy="42"
        r="26"
        stroke="hsl(160 60% 40% / 0.28)"
        strokeWidth="1.2"
        className="home-banner-shape home-banner-shape-1"
      />
      <text
        x="820"
        y="49"
        textAnchor="middle"
        fontSize="20"
        fontWeight="600"
        fill="hsl(160 60% 40% / 0.22)"
        className="home-banner-shape home-banner-shape-1"
      >
        $
      </text>
      {/* Tickets — Ticket (orange) — rounded rect with perforated line + stub */}
      <rect
        x="900"
        y="82"
        width="52"
        height="32"
        rx="6"
        stroke="hsl(25 95% 53% / 0.26)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-2"
      />
      <line
        x1="920"
        y1="82"
        x2="920"
        y2="114"
        stroke="hsl(25 95% 53% / 0.18)"
        strokeWidth="1"
        strokeDasharray="3 3"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Ticket notches */}
      <circle
        cx="920"
        cy="82"
        r="3"
        fill="white"
        stroke="hsl(25 95% 53% / 0.18)"
        strokeWidth="0.8"
        className="home-banner-shape home-banner-shape-2"
      />
      <circle
        cx="920"
        cy="114"
        r="3"
        fill="white"
        stroke="hsl(25 95% 53% / 0.18)"
        strokeWidth="0.8"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Credits — Award/Medal (violet) — circle with ribbon tails */}
      <circle
        cx="760"
        cy="118"
        r="16"
        stroke="hsl(263 50% 55% / 0.26)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-3"
      />
      <circle
        cx="760"
        cy="118"
        r="9"
        stroke="hsl(263 50% 55% / 0.16)"
        strokeWidth="0.8"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Medal star inside */}
      <path
        d="M760 111 l1.8 3.6 4 0.6 -2.9 2.8 0.7 4 -3.6 -1.9 -3.6 1.9 0.7 -4 -2.9 -2.8 4 -0.6Z"
        fill="hsl(263 50% 55% / 0.14)"
        stroke="hsl(263 50% 55% / 0.20)"
        strokeWidth="0.6"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Medal ribbon tails */}
      <path
        d="M750 132 l-5 14 5 -3 4 4 -1 -15"
        stroke="hsl(263 50% 55% / 0.18)"
        strokeWidth="0.8"
        fill="none"
        className="home-banner-shape home-banner-shape-3"
      />
      <path
        d="M770 132 l5 14 -5 -3 -4 4 1 -15"
        stroke="hsl(263 50% 55% / 0.18)"
        strokeWidth="0.8"
        fill="none"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Points — Gift box (blue) — box with ribbon + bow */}
      <rect
        x="682"
        y="48"
        width="28"
        height="22"
        rx="3"
        stroke="hsl(217 91% 60% / 0.26)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Gift lid */}
      <rect
        x="679"
        y="42"
        width="34"
        height="8"
        rx="2"
        stroke="hsl(217 91% 60% / 0.24)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Vertical ribbon */}
      <line
        x1="696"
        y1="50"
        x2="696"
        y2="70"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Horizontal ribbon */}
      <line
        x1="682"
        y1="59"
        x2="710"
        y2="59"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Bow loops */}
      <path
        d="M696 42 c-5 -6 -12 -4 -8 0 s7 1 8 0"
        stroke="hsl(217 91% 60% / 0.20)"
        strokeWidth="0.8"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      <path
        d="M696 42 c5 -6 12 -4 8 0 s-7 1 -8 0"
        stroke="hsl(217 91% 60% / 0.20)"
        strokeWidth="0.8"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Dots — scattered rewards feel */}
      <circle
        cx="860"
        cy="130"
        r="2"
        fill="hsl(160 60% 40% / 0.30)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="55"
        r="2.5"
        fill="hsl(25 95% 53% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="730"
        cy="38"
        r="2"
        fill="hsl(263 50% 55% / 0.28)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function ClaimsArt() {
  return (
    <>
      {/* Flowing curves */}
      <path
        d="M-30 100 C130 70, 290 135, 450 95 S690 50, 850 88 S1030 125, 1060 100"
        stroke="hsl(217 91% 60% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-10 140 C160 105, 340 148, 490 118 S710 70, 870 105 S1040 138, 1065 118"
        stroke="hsl(160 55% 42% / 0.20)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* Clipboard / receipt */}
      <rect
        x="815"
        y="25"
        width="32"
        height="42"
        rx="4"
        stroke="hsl(217 91% 60% / 0.25)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-1"
      />
      <rect
        x="823"
        y="22"
        width="16"
        height="6"
        rx="2"
        stroke="hsl(217 91% 60% / 0.20)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="823"
        y1="38"
        x2="839"
        y2="38"
        stroke="hsl(217 91% 60% / 0.16)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="823"
        y1="44"
        x2="835"
        y2="44"
        stroke="hsl(217 91% 60% / 0.12)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="823"
        y1="50"
        x2="839"
        y2="50"
        stroke="hsl(217 91% 60% / 0.12)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Checkmark circle — approval */}
      <circle
        cx="910"
        cy="100"
        r="20"
        stroke="hsl(95 55% 42% / 0.24)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-2"
      />
      <path
        d="M900 100 l6 6 14 -14"
        stroke="hsl(95 55% 42% / 0.28)"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Dollar badge — payout */}
      <circle
        cx="760"
        cy="130"
        r="14"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <text
        x="760"
        y="135"
        textAnchor="middle"
        fontSize="14"
        fontWeight="600"
        fill="hsl(38 80% 50% / 0.18)"
        className="home-banner-shape home-banner-shape-3"
      >
        $
      </text>
      {/* Dots */}
      <circle
        cx="700"
        cy="40"
        r="2"
        fill="hsl(217 91% 60% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="50"
        r="2.5"
        fill="hsl(95 55% 42% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="870"
        cy="135"
        r="2"
        fill="hsl(38 80% 50% / 0.28)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function ReportsArt() {
  return (
    <>
      {/* Flowing data curves */}
      <path
        d="M-30 115 C140 80, 310 140, 470 100 S700 55, 860 90 S1020 130, 1060 105"
        stroke="hsl(160 55% 42% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-10 45 C120 68, 280 30, 430 55 S640 90, 800 50 S960 35, 1060 55"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* Bar chart */}
      <rect
        x="790"
        y="60"
        width="10"
        height="35"
        rx="2"
        stroke="hsl(217 91% 60% / 0.24)"
        strokeWidth="1"
        fill="hsl(217 91% 60% / 0.06)"
        className="home-banner-shape home-banner-shape-1"
      />
      <rect
        x="806"
        y="45"
        width="10"
        height="50"
        rx="2"
        stroke="hsl(160 55% 42% / 0.24)"
        strokeWidth="1"
        fill="hsl(160 55% 42% / 0.06)"
        className="home-banner-shape home-banner-shape-1"
      />
      <rect
        x="822"
        y="55"
        width="10"
        height="40"
        rx="2"
        stroke="hsl(199 89% 48% / 0.24)"
        strokeWidth="1"
        fill="hsl(199 89% 48% / 0.06)"
        className="home-banner-shape home-banner-shape-1"
      />
      <rect
        x="838"
        y="35"
        width="10"
        height="60"
        rx="2"
        stroke="hsl(38 80% 50% / 0.24)"
        strokeWidth="1"
        fill="hsl(38 80% 50% / 0.06)"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="785"
        y1="95"
        x2="853"
        y2="95"
        stroke="hsl(200 10% 60% / 0.20)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Document / page icon */}
      <rect
        x="910"
        y="80"
        width="30"
        height="38"
        rx="4"
        stroke="hsl(270 55% 55% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      <path
        d="M930 80 v10 h10"
        stroke="hsl(270 55% 55% / 0.18)"
        strokeWidth="1"
        fill="none"
        className="home-banner-shape home-banner-shape-2"
      />
      <line
        x1="917"
        y1="95"
        x2="933"
        y2="95"
        stroke="hsl(270 55% 55% / 0.14)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      <line
        x1="917"
        y1="101"
        x2="929"
        y2="101"
        stroke="hsl(270 55% 55% / 0.12)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      <line
        x1="917"
        y1="107"
        x2="933"
        y2="107"
        stroke="hsl(270 55% 55% / 0.12)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Pie chart slice */}
      <circle
        cx="740"
        cy="130"
        r="16"
        stroke="hsl(95 55% 42% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <path
        d="M740 130 l0 -16 A16 16 0 0 1 753.3 138Z"
        stroke="hsl(95 55% 42% / 0.22)"
        strokeWidth="1"
        fill="hsl(95 55% 42% / 0.06)"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="700"
        cy="45"
        r="2"
        fill="hsl(160 55% 42% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="55"
        r="2.5"
        fill="hsl(270 55% 55% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="870"
        cy="130"
        r="2"
        fill="hsl(217 91% 60% / 0.25)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function ActivityArt() {
  return (
    <>
      {/* Timeline flow curves */}
      <path
        d="M-30 110 C130 78, 280 138, 440 98 S680 52, 850 88 S1030 125, 1060 100"
        stroke="hsl(270 55% 55% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 50 C120 72, 300 28, 450 55 S650 90, 810 48 S970 30, 1060 52"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* Clock face */}
      <circle
        cx="830"
        cy="48"
        r="24"
        stroke="hsl(270 55% 55% / 0.25)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="830"
        y1="48"
        x2="830"
        y2="32"
        stroke="hsl(270 55% 55% / 0.28)"
        strokeWidth="1.5"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="830"
        y1="48"
        x2="844"
        y2="52"
        stroke="hsl(270 55% 55% / 0.22)"
        strokeWidth="1.2"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <circle
        cx="830"
        cy="48"
        r="2"
        fill="hsl(270 55% 55% / 0.20)"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Timeline — vertical dotted line with events */}
      <line
        x1="920"
        y1="25"
        x2="920"
        y2="135"
        stroke="hsl(200 10% 60% / 0.18)"
        strokeWidth="1"
        strokeDasharray="4 4"
        className="home-banner-shape home-banner-shape-2"
      />
      <circle
        cx="920"
        cy="45"
        r="5"
        stroke="hsl(217 91% 60% / 0.25)"
        strokeWidth="1"
        fill="hsl(217 91% 60% / 0.06)"
        className="home-banner-shape home-banner-shape-2"
      />
      <circle
        cx="920"
        cy="80"
        r="5"
        stroke="hsl(38 80% 50% / 0.25)"
        strokeWidth="1"
        fill="hsl(38 80% 50% / 0.06)"
        className="home-banner-shape home-banner-shape-2"
      />
      <circle
        cx="920"
        cy="115"
        r="5"
        stroke="hsl(95 55% 42% / 0.25)"
        strokeWidth="1"
        fill="hsl(95 55% 42% / 0.06)"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Activity lightning bolt */}
      <path
        d="M755 115 l8 0 -3 12 10 -15 -8 0 3 -12Z"
        stroke="hsl(38 80% 50% / 0.24)"
        strokeWidth="1"
        fill="none"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="700"
        cy="38"
        r="2"
        fill="hsl(270 55% 55% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="65"
        r="2.5"
        fill="hsl(217 91% 60% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="780"
        cy="135"
        r="2"
        fill="hsl(38 80% 50% / 0.28)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function ProfileArt() {
  return (
    <>
      {/* Soft curves */}
      <path
        d="M-30 108 C140 76, 300 135, 460 98 S690 55, 850 85 S1030 122, 1060 100"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 45 C100 65, 270 28, 420 52 S630 85, 790 48 S960 32, 1060 50"
        stroke="hsl(199 89% 48% / 0.18)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* User avatar silhouette */}
      <circle
        cx="825"
        cy="38"
        r="14"
        stroke="hsl(217 91% 60% / 0.25)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-1"
      />
      <path
        d="M804 72 a28 20 0 0 1 42 0"
        stroke="hsl(217 91% 60% / 0.20)"
        strokeWidth="1.1"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* ID badge */}
      <rect
        x="900"
        y="82"
        width="36"
        height="46"
        rx="5"
        stroke="hsl(160 55% 42% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      <circle
        cx="918"
        cy="97"
        r="7"
        stroke="hsl(160 55% 42% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      <line
        x1="908"
        y1="112"
        x2="928"
        y2="112"
        stroke="hsl(160 55% 42% / 0.14)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      <line
        x1="911"
        y1="118"
        x2="925"
        y2="118"
        stroke="hsl(160 55% 42% / 0.12)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Lock / security */}
      <rect
        x="748"
        y="118"
        width="20"
        height="16"
        rx="3"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <path
        d="M752 118 v-6 a6 6 0 0 1 12 0 v6"
        stroke="hsl(38 80% 50% / 0.20)"
        strokeWidth="1"
        fill="none"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="700"
        cy="42"
        r="2"
        fill="hsl(217 91% 60% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="60"
        r="2.5"
        fill="hsl(160 55% 42% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="790"
        cy="90"
        r="2"
        fill="hsl(38 80% 50% / 0.25)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function UsersArt() {
  return (
    <>
      {/* Flowing curves */}
      <path
        d="M-30 112 C150 78, 310 140, 470 100 S700 52, 860 88 S1030 128, 1060 102"
        stroke="hsl(199 89% 48% / 0.22)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 48 C110 68, 280 30, 430 55 S640 88, 800 48 S960 30, 1060 48"
        stroke="hsl(270 55% 55% / 0.18)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* People group — three overlapping user silhouettes */}
      <circle
        cx="810"
        cy="35"
        r="11"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      <path
        d="M795 58 a18 14 0 0 1 30 0"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      <circle
        cx="835"
        cy="38"
        r="11"
        stroke="hsl(199 89% 48% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      <path
        d="M820 61 a18 14 0 0 1 30 0"
        stroke="hsl(199 89% 48% / 0.18)"
        strokeWidth="1"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Shield — permissions */}
      <path
        d="M915 82 l0 28 c0 12 -15 18 -15 18 s-15 -6 -15 -18 l0 -28Z"
        stroke="hsl(95 55% 42% / 0.24)"
        strokeWidth="1.1"
        fill="none"
        className="home-banner-shape home-banner-shape-2"
      />
      <path
        d="M895 98 l6 6 14 -14"
        stroke="hsl(95 55% 42% / 0.20)"
        strokeWidth="1.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Key — roles */}
      <circle
        cx="755"
        cy="128"
        r="10"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <line
        x1="765"
        y1="128"
        x2="785"
        y2="128"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <line
        x1="780"
        y1="128"
        x2="780"
        y2="122"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <line
        x1="775"
        y1="128"
        x2="775"
        y2="124"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="700"
        cy="40"
        r="2"
        fill="hsl(199 89% 48% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="55"
        r="2.5"
        fill="hsl(95 55% 42% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="860"
        cy="135"
        r="2"
        fill="hsl(38 80% 50% / 0.25)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function SettingsArt() {
  return (
    <>
      {/* Flowing curves */}
      <path
        d="M-30 108 C140 76, 300 138, 460 98 S690 52, 850 85 S1030 122, 1060 100"
        stroke="hsl(200 10% 55% / 0.22)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 48 C100 68, 270 28, 420 55 S640 88, 800 48 S960 32, 1060 50"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* Gear — main */}
      <circle
        cx="825"
        cy="50"
        r="16"
        stroke="hsl(217 91% 60% / 0.24)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-1"
      />
      <circle
        cx="825"
        cy="50"
        r="8"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Gear teeth (simple notches) */}
      <line
        x1="825"
        y1="30"
        x2="825"
        y2="34"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="3"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="825"
        y1="66"
        x2="825"
        y2="70"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="3"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="805"
        y1="50"
        x2="809"
        y2="50"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="3"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="841"
        y1="50"
        x2="845"
        y2="50"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="3"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="811"
        y1="36"
        x2="814"
        y2="39"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="3"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="836"
        y1="61"
        x2="839"
        y2="64"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="3"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="811"
        y1="64"
        x2="814"
        y2="61"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="3"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="836"
        y1="39"
        x2="839"
        y2="36"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="3"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Smaller gear — interconnected */}
      <circle
        cx="860"
        cy="78"
        r="10"
        stroke="hsl(199 89% 48% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      <circle
        cx="860"
        cy="78"
        r="5"
        stroke="hsl(199 89% 48% / 0.16)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Connection plugs — integration */}
      <rect
        x="910"
        y="90"
        width="28"
        height="32"
        rx="5"
        stroke="hsl(160 55% 42% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-2"
      />
      <line
        x1="918"
        y1="90"
        x2="918"
        y2="84"
        stroke="hsl(160 55% 42% / 0.20)"
        strokeWidth="2"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-2"
      />
      <line
        x1="930"
        y1="90"
        x2="930"
        y2="84"
        stroke="hsl(160 55% 42% / 0.20)"
        strokeWidth="2"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Toggle switch — config */}
      <rect
        x="740"
        y="118"
        width="30"
        height="14"
        rx="7"
        stroke="hsl(95 55% 42% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <circle
        cx="762"
        cy="125"
        r="5"
        fill="hsl(95 55% 42% / 0.16)"
        stroke="hsl(95 55% 42% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="700"
        cy="42"
        r="2"
        fill="hsl(217 91% 60% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="55"
        r="2.5"
        fill="hsl(160 55% 42% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="790"
        cy="135"
        r="2"
        fill="hsl(199 89% 48% / 0.25)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function BuilderAiArt() {
  return (
    <>
      {/* Neural-network style flowing curves */}
      <path
        d="M-30 108 C150 72, 310 138, 470 95 S700 48, 860 82 S1030 125, 1060 100"
        stroke="hsl(245 60% 58% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 50 C120 72, 280 28, 440 58 S650 92, 810 52 S970 32, 1060 55"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* Lucide Bot icon — outer g positions, inner g animates */}
      <g transform="translate(690 42) scale(2.4)">
        <g
          className="home-banner-shape home-banner-shape-1"
          stroke="hsl(245 55% 55% / 0.30)"
          strokeWidth="0.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        >
          <path d="M12 8V4H8" />
          <rect width="16" height="12" x="4" y="8" rx="2" />
          <path d="M2 14h2" />
          <path d="M20 14h2" />
          <path d="M15 13v2" />
          <path d="M9 13v2" />
        </g>
      </g>
      {/* Lucide Sparkles icon — outer g positions, inner g breathes */}
      <g transform="translate(920 72) scale(2.0)">
        <g
          className="ai-sparkle-breathe"
          stroke="hsl(38 90% 50% / 0.30)"
          strokeWidth="0.55"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="hsl(38 90% 50% / 0.06)"
        >
          <path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z" />
          <path d="M20 3v4" />
          <path d="M22 5h-4" />
          <path d="M4 17v2" />
          <path d="M5 18H3" />
        </g>
      </g>
      {/* Twinkling sparkle stars — staggered fade in/out */}
      <path
        d="M870 52 l2 6 6 2 -6 2 -2 6 -2 -6 -6 -2 6 -2Z"
        fill="hsl(245 55% 60% / 0.35)"
        className="ai-twinkle-1"
      />
      <path
        d="M960 65 l1.5 4.5 4.5 1.5 -4.5 1.5 -1.5 4.5 -1.5 -4.5 -4.5 -1.5 4.5 -1.5Z"
        fill="hsl(38 90% 50% / 0.35)"
        className="ai-twinkle-2"
      />
      <path
        d="M750 60 l1 3 3 1 -3 1 -1 3 -1 -3 -3 -1 3 -1Z"
        fill="hsl(217 91% 60% / 0.30)"
        className="ai-twinkle-3"
      />
      <path
        d="M830 100 l1.5 4.5 4.5 1.5 -4.5 1.5 -1.5 4.5 -1.5 -4.5 -4.5 -1.5 4.5 -1.5Z"
        fill="hsl(245 60% 58% / 0.30)"
        className="ai-twinkle-4"
      />
      <path
        d="M980 48 l1 3 3 1 -3 1 -1 3 -1 -3 -3 -1 3 -1Z"
        fill="hsl(199 89% 48% / 0.35)"
        className="ai-twinkle-5"
      />
      <path
        d="M720 95 l1.5 4.5 4.5 1.5 -4.5 1.5 -1.5 4.5 -1.5 -4.5 -4.5 -1.5 4.5 -1.5Z"
        fill="hsl(38 80% 50% / 0.25)"
        className="ai-twinkle-6"
      />
      {/* Dots — ambient scatter */}
      <circle
        cx="790"
        cy="75"
        r="2"
        fill="hsl(245 55% 55% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="950"
        cy="110"
        r="2.5"
        fill="hsl(38 90% 50% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="680"
        cy="80"
        r="2"
        fill="hsl(217 91% 60% / 0.25)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function BuilderManualArt() {
  return (
    <>
      {/* Flowing document curves */}
      <path
        d="M-30 112 C140 78, 300 140, 460 100 S690 55, 850 88 S1030 125, 1060 102"
        stroke="hsl(160 55% 42% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 48 C110 68, 280 28, 430 55 S640 88, 800 48 S960 30, 1060 50"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* FileText icon — document with lines */}
      <rect
        x="800"
        y="22"
        width="40"
        height="52"
        rx="5"
        stroke="hsl(217 91% 60% / 0.28)"
        strokeWidth="1.2"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Folded corner */}
      <path
        d="M828 22 v12 h12"
        stroke="hsl(217 91% 60% / 0.20)"
        strokeWidth="1"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      <path
        d="M828 22 l12 12"
        stroke="hsl(217 91% 60% / 0.10)"
        strokeWidth="0.8"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Text lines */}
      <line
        x1="810"
        y1="44"
        x2="832"
        y2="44"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1.2"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="810"
        y1="52"
        x2="828"
        y2="52"
        stroke="hsl(217 91% 60% / 0.14)"
        strokeWidth="1"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      <line
        x1="810"
        y1="60"
        x2="832"
        y2="60"
        stroke="hsl(217 91% 60% / 0.14)"
        strokeWidth="1"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Pencil icon — outer g positions/rotates, inner g animates */}
      <g transform="translate(910, 75) rotate(-45)">
        <g className="home-banner-shape home-banner-shape-2">
          {/* Pencil body */}
          <rect
            x="0"
            y="0"
            width="10"
            height="40"
            rx="2"
            stroke="hsl(38 80% 50% / 0.28)"
            strokeWidth="1.1"
            fill="none"
          />
          {/* Pencil tip */}
          <path
            d="M0 40 l5 8 5 -8"
            stroke="hsl(38 80% 50% / 0.24)"
            strokeWidth="1"
            fill="none"
          />
          {/* Pencil eraser band */}
          <line
            x1="0"
            y1="6"
            x2="10"
            y2="6"
            stroke="hsl(38 80% 50% / 0.18)"
            strokeWidth="1"
          />
        </g>
      </g>
      {/* Checklist marks — completed fields */}
      <path
        d="M755 95 l4 4 8 -8"
        stroke="hsl(160 55% 42% / 0.28)"
        strokeWidth="1.3"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
        className="home-banner-shape home-banner-shape-3"
      />
      <line
        x1="772"
        y1="93"
        x2="795"
        y2="93"
        stroke="hsl(160 55% 42% / 0.16)"
        strokeWidth="1"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-3"
      />
      <path
        d="M755 115 l4 4 8 -8"
        stroke="hsl(160 55% 42% / 0.22)"
        strokeWidth="1.3"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
        className="home-banner-shape home-banner-shape-3"
      />
      <line
        x1="772"
        y1="113"
        x2="790"
        y2="113"
        stroke="hsl(160 55% 42% / 0.14)"
        strokeWidth="1"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Empty checkbox */}
      <rect
        x="754"
        y="129"
        width="14"
        height="14"
        rx="3"
        stroke="hsl(200 10% 60% / 0.20)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <line
        x1="772"
        y1="134"
        x2="792"
        y2="134"
        stroke="hsl(200 10% 60% / 0.14)"
        strokeWidth="1"
        strokeLinecap="round"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="870"
        cy="30"
        r="2"
        fill="hsl(217 91% 60% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="960"
        cy="60"
        r="2.5"
        fill="hsl(38 80% 50% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="740"
        cy="82"
        r="2"
        fill="hsl(160 55% 42% / 0.25)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function RewardsArt() {
  return (
    <>
      {/* Flowing reward curves */}
      <path
        d="M-30 105 C150 75, 300 130, 460 95 S680 55, 840 85 S1020 120, 1060 95"
        stroke="hsl(38 80% 50% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 135 C180 100, 350 150, 500 115 S720 65, 880 100 S1040 140, 1070 115"
        stroke="hsl(217 91% 60% / 0.20)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* Trophy icon */}
      <path
        d="M805 35 l30 0 l0 15 a8 8 0 0 0 8 8 l4 0 l0 8 l-60 0 l0 -8 l4 0 a8 8 0 0 0 8 -8 l0 -15Z"
        stroke="hsl(38 80% 50% / 0.28)"
        strokeWidth="1.1"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Trophy handles */}
      <path
        d="M805 40 a6 10 0 0 0 0 15"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      <path
        d="M865 40 a6 10 0 0 1 0 15"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
        fill="none"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Star accent */}
      <path
        d="M920 40 l2 5 5 1 -4 3 1 5 -4 -3 -4 3 1 -5 -4 -3 5 -1Z"
        fill="hsl(217 91% 60% / 0.20)"
        stroke="hsl(217 91% 60% / 0.28)"
        strokeWidth="0.8"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Gift box with curve */}
      <rect
        x="960"
        y="80"
        width="28"
        height="28"
        rx="3"
        stroke="hsl(38 80% 50% / 0.24)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <line
        x1="974"
        y1="80"
        x2="974"
        y2="108"
        stroke="hsl(38 80% 50% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <line
        x1="960"
        y1="94"
        x2="988"
        y2="94"
        stroke="hsl(38 80% 50% / 0.18)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Decorative dots */}
      <circle
        cx="780"
        cy="35"
        r="2"
        fill="hsl(38 80% 50% / 0.30)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="940"
        cy="65"
        r="2.5"
        fill="hsl(217 91% 60% / 0.28)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="860"
        cy="130"
        r="2"
        fill="hsl(38 80% 50% / 0.28)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function DealQualifierArt() {
  return (
    <>
      {/* Flowing qualifier curves */}
      <path
        d="M-30 100 C130 70, 290 135, 450 95 S690 50, 850 88 S1030 125, 1060 100"
        stroke="hsl(160 55% 42% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-10 140 C160 105, 340 148, 490 118 S710 70, 870 105 S1040 138, 1065 118"
        stroke="hsl(217 91% 60% / 0.20)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />
      {/* Target/bullseye icon */}
      <circle
        cx="825"
        cy="50"
        r="22"
        stroke="hsl(160 55% 42% / 0.28)"
        strokeWidth="1.2"
        className="home-banner-shape home-banner-shape-1"
      />
      <circle
        cx="825"
        cy="50"
        r="15"
        stroke="hsl(160 55% 42% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      <circle
        cx="825"
        cy="50"
        r="8"
        stroke="hsl(160 55% 42% / 0.20)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-1"
      />
      <circle
        cx="825"
        cy="50"
        r="4"
        fill="hsl(160 55% 42% / 0.24)"
        className="home-banner-shape home-banner-shape-1"
      />
      {/* Checkmark circle */}
      <circle
        cx="910"
        cy="100"
        r="20"
        stroke="hsl(217 91% 60% / 0.24)"
        strokeWidth="1.1"
        className="home-banner-shape home-banner-shape-2"
      />
      <path
        d="M900 100 l6 6 14 -14"
        stroke="hsl(217 91% 60% / 0.28)"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
        className="home-banner-shape home-banner-shape-2"
      />
      {/* Achievement badge */}
      <circle
        cx="755"
        cy="130"
        r="16"
        stroke="hsl(160 55% 42% / 0.22)"
        strokeWidth="1"
        className="home-banner-shape home-banner-shape-3"
      />
      <path
        d="M755 120 l3 4 5 0 -4 3 2 5 -6 -3 -6 3 2 -5 -4 -3 5 0Z"
        fill="hsl(160 55% 42% / 0.16)"
        stroke="hsl(160 55% 42% / 0.20)"
        strokeWidth="0.6"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="710"
        cy="40"
        r="2"
        fill="hsl(160 55% 42% / 0.28)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="950"
        cy="55"
        r="2.5"
        fill="hsl(217 91% 60% / 0.25)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="880"
        cy="135"
        r="2"
        fill="hsl(160 55% 42% / 0.28)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function ViewIncentivesArt() {
  return (
    <>
      {/* Flowing incentive curves */}
      <path
        d="M-30 110 C140 78, 300 138, 460 98 S690 52, 850 85 S1030 122, 1060 100"
        stroke="hsl(245 60% 60% / 0.24)"
        strokeWidth="1.5"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 50 C120 72, 300 28, 450 58 S650 92, 810 52 S970 32, 1060 55"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />

      {/* Lucide Megaphone icon — scale(2.4) on 24×24 base paths */}
      <g transform="translate(700 20) scale(2.4)">
        <g
          className="home-banner-shape home-banner-shape-1"
          stroke="hsl(245 60% 60% / 0.35)"
          strokeWidth="0.55"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        >
          <path d="m3 11 18-5v12L3 14v-3z" />
          <path d="M11.6 16.8a3 3 0 1 1-5.8-1.6" />
        </g>
      </g>

      {/* Lucide BookOpen icon — scale(2.4) on 24×24 base paths */}
      <g transform="translate(900 80) scale(2.4)">
        <g
          className="home-banner-shape home-banner-shape-2"
          stroke="hsl(217 91% 60% / 0.35)"
          strokeWidth="0.55"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        >
          <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
          <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
        </g>
      </g>

      {/* Lucide Route icon — scale(2.4) on 24×24 base paths */}
      <g transform="translate(810 60) scale(2.4)">
        <g
          className="home-banner-shape home-banner-shape-3"
          stroke="hsl(245 60% 60% / 0.35)"
          strokeWidth="0.55"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        >
          <circle cx="6" cy="19" r="3" />
          <path d="M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15" />
          <circle cx="18" cy="5" r="3" />
        </g>
      </g>

      {/* Accent dots */}
      <circle
        cx="770"
        cy="30"
        r="2.5"
        fill="hsl(245 60% 60% / 0.32)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="970"
        cy="50"
        r="3"
        fill="hsl(217 91% 60% / 0.30)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="860"
        cy="140"
        r="2.5"
        fill="hsl(245 60% 60% / 0.32)"
        className="home-banner-dot home-banner-dot-3"
      />
    </>
  );
}

function HomeArt() {
  return (
    <>
      {/* Flowing curves — brighter than default page banners */}
      <path
        d="M-40 140 C120 100, 280 170, 440 130 S700 70, 860 120 S1040 160, 1060 130"
        stroke="hsl(217 91% 60% / 0.35)"
        strokeWidth="1.8"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-20 160 C140 120, 320 190, 480 150 S740 80, 900 140 S1060 180, 1080 150"
        stroke="hsl(199 89% 48% / 0.30)"
        strokeWidth="1.2"
        className="home-banner-line home-banner-line-2"
      />
      <path
        d="M-60 60 C100 90, 260 30, 400 70 S620 120, 800 60 S960 40, 1060 70"
        stroke="hsl(95 55% 50% / 0.28)"
        strokeWidth="1.2"
        className="home-banner-line home-banner-line-3"
      />
      {/* Geometric shapes — right side */}
      <circle
        cx="820"
        cy="50"
        r="35"
        stroke="hsl(217 91% 60% / 0.30)"
        strokeWidth="1.2"
        className="home-banner-shape home-banner-shape-1"
      />
      <rect
        x="880"
        y="120"
        width="50"
        height="50"
        rx="10"
        stroke="hsl(199 89% 48% / 0.25)"
        strokeWidth="1.2"
        className="home-banner-shape home-banner-shape-2"
        transform="rotate(15 905 145)"
      />
      <circle
        cx="740"
        cy="160"
        r="20"
        stroke="hsl(95 55% 50% / 0.25)"
        strokeWidth="1.2"
        className="home-banner-shape home-banner-shape-3"
      />
      {/* Dots */}
      <circle
        cx="680"
        cy="40"
        r="3"
        fill="hsl(217 91% 60% / 0.40)"
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="950"
        cy="80"
        r="2.5"
        fill="hsl(95 55% 50% / 0.35)"
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="780"
        cy="110"
        r="3"
        fill="hsl(199 89% 48% / 0.35)"
        className="home-banner-dot home-banner-dot-3"
      />
      <circle
        cx="860"
        cy="30"
        r="2.5"
        fill="hsl(95 55% 50% / 0.32)"
        className="home-banner-dot home-banner-dot-1"
      />
    </>
  );
}

const themeArt: Record<BannerTheme, () => React.JSX.Element> = {
  default: DefaultArt,
  home: HomeArt,
  incentives: IncentivesArt,
  claims: ClaimsArt,
  reports: ReportsArt,
  activity: ActivityArt,
  profile: ProfileArt,
  users: UsersArt,
  settings: SettingsArt,
  "builder-ai": BuilderAiArt,
  "builder-manual": BuilderManualArt,
  rewards: RewardsArt,
  "deal-qualifier": DealQualifierArt,
  "view-incentives": ViewIncentivesArt,
};

/* ─── Component ────────────────────────────────────────────────────────────── */

export function PageBanner({
  title,
  subtitle,
  actions,
  theme = "default",
  onBack,
}: PageBannerProps) {
  const Art = themeArt[theme];

  return (
    <div className={`${theme === "home" ? "home-banner " : ""}group relative overflow-hidden rounded-2xl border border-border bg-gradient-to-br from-background via-background to-primary/5 px-8 py-6 sm:px-10 sm:py-7 transition-[border-color,box-shadow] duration-300 hover:border-[hsl(217_91%_60%/0.25)] hover:shadow-[0_2px_16px_hsl(var(--primary-light)/0.18),0_1px_4px_hsl(var(--primary-light)/0.12)]`}>
      {/* Line-art illustrations — themed per page */}
      <div className="pointer-events-none absolute inset-0 select-none">
        <svg
          className="absolute inset-0 h-full w-full"
          viewBox="0 0 1000 160"
          preserveAspectRatio="xMidYMid slice"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <defs>
            <style>{`
              @keyframes bannerArtFadeIn {
                from { opacity: 0; }
                to   { opacity: 1; }
              }
            `}</style>
          </defs>
          <g
            key={theme}
            style={{ animation: "bannerArtFadeIn 400ms ease-out" }}
          >
            <Art />
          </g>
        </svg>
      </div>

      {/* Gradient fade mask — fades line art near the text area (left & center) */}
      <div
        className="pointer-events-none absolute inset-0 select-none"
        style={{
          background: `
            linear-gradient(
              to right,
              hsl(var(--background)) 0%,
              hsl(var(--background)) 30%,
              hsl(var(--background) / 0.85) 50%,
              hsl(var(--background) / 0) 70%,
              hsl(var(--background) / 0) 100%
            )
          `,
        }}
      />

      {/* Content — sits above the art */}
      <div className="relative z-10 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="p-1.5 -ml-1 rounded-lg text-[hsl(200_10%_46%)] hover:text-[hsl(200_20%_10%)] hover:bg-[hsl(210_20%_96%/0.6)] transition-colors"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="m12 19-7-7 7-7" />
                <path d="M19 12H5" />
              </svg>
            </button>
          )}
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              {title}
            </h1>
            <p className="mt-0.5 text-base text-muted-foreground animate-mode-fade-in">
              {subtitle}
            </p>
          </div>
        </div>
        {actions && (
          <div className="flex items-center gap-2 shrink-0">{actions}</div>
        )}
      </div>

      {/* Subtle accent line at bottom */}
      <div
        className="absolute bottom-0 left-[8%] right-[8%] h-[1px] rounded-full"
        style={{
          background:
            "linear-gradient(90deg, transparent, hsl(199 89% 48% / 0.15) 25%, hsl(217 91% 60% / 0.25) 50%, hsl(95 55% 50% / 0.15) 75%, transparent)",
        }}
      />
    </div>
  );
}
