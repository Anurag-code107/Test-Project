package com.tenxengage.app.batch.seed;

import java.util.Map;

/**
 * All static constants used across the seed data generation pipeline.
 */
public final class SeedConstants {

    private SeedConstants() {}

    // ── Volume constants ────────────────────────────────────────────────────────

    public static final int TOTAL_PARTNER_COUNT = 400;
    public static final int ENROLLED_PARTNER_COUNT = 100;
    public static final int MIN_USERS_PER_PARTNER = 5;
    public static final int MAX_USERS_PER_PARTNER = 10;
    public static final int POS_PER_PARTNER = 1750;
    public static final int MIN_LINES_PER_PO = 1;
    public static final int MAX_LINES_PER_PO = 5;
    public static final int BATCH_SIZE = 5000;

    /** Per-quarter partner growth for incremental seeding. */
    public static final int NEW_ENROLLED_PER_QUARTER = 3;
    public static final int NEW_NON_ENROLLED_PER_QUARTER = 8;

    // ── Regions and partner types ───────────────────────────────────────────────

    public static final String[] REGIONS = {"AMERICAS", "LATAM", "EMEAR", "APJ"};
    public static final String[] PARTNER_TYPES = {"Reseller", "Distributor", "OEM"};

    // ── Country distribution weights per region (for partner location seeding) ──

    public static final Map<String, double[]> COUNTRY_WEIGHTS = Map.of(
            "AMERICAS", new double[]{0.75, 0.25},
            "LATAM", new double[]{0.35, 0.30, 0.15, 0.10, 0.10},
            "EMEAR", new double[]{0.20, 0.18, 0.15, 0.10, 0.08, 0.07, 0.05, 0.05, 0.04, 0.04, 0.04},
            "APJ", new double[]{0.25, 0.20, 0.18, 0.15, 0.10, 0.12}
    );

    /** Country names per region, aligned with COUNTRY_WEIGHTS indices. */
    public static final Map<String, String[]> COUNTRIES_BY_REGION = Map.of(
            "AMERICAS", new String[]{"United States", "Canada"},
            "LATAM", new String[]{"Brazil", "Mexico", "Argentina", "Chile", "Colombia"},
            "EMEAR", new String[]{"United Kingdom", "Germany", "France", "Spain", "Italy",
                    "Netherlands", "Sweden", "Norway", "Denmark", "Finland", "Poland"},
            "APJ", new String[]{"Japan", "Australia", "India", "China", "South Korea", "Singapore"}
    );

    // ── Customer data ───────────────────────────────────────────────────────────

    public static final String[] CUSTOMER_SEGMENTS = {
            "Enterprise", "SMB", "Mid-Market", "Public Sector", "Education", "Commercial"
    };
    public static final String[] CUSTOMER_FIRST = {
            "Acme", "Global", "Pacific", "Summit", "Vertex", "Nova", "Pinnacle", "Atlas",
            "Horizon", "Apex", "Stellar", "Nexus", "Quantum", "Zenith", "Vanguard",
            "Titan", "Catalyst", "Dynamo", "Fusion", "Helix"
    };
    public static final String[] CUSTOMER_LAST = {
            "Corp", "Industries", "Solutions", "Technologies", "Systems", "Enterprises",
            "Group", "Holdings", "International", "Partners"
    };

    // ── Partner naming ──────────────────────────────────────────────────────────

    public static final String[] PARTNER_PREFIXES = {
            "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta",
            "Iota", "Kappa", "Lambda", "Sigma", "Omega", "Phoenix", "Atlas",
            "Nexus", "Vertex", "Prism", "Quantum", "Stellar"
    };
    public static final String[] PARTNER_SUFFIXES = {
            "Tech", "Solutions", "Systems", "Networks", "Digital", "IT Services",
            "Consulting", "Partners", "Group", "Global"
    };

    // ── User naming ─────────────────────────────────────────────────────────────

    public static final String[] FIRST_NAMES = {
            "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
            "David", "Elizabeth", "William", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
            "Thomas", "Sarah", "Christopher", "Karen", "Daniel", "Lisa", "Matthew", "Nancy",
            "Anthony", "Betty", "Mark", "Margaret", "Donald", "Sandra"
    };
    public static final String[] LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
            "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
            "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson"
    };
    public static final String[] AREA_CODES = {
            "212", "310", "415", "512", "617", "702", "813", "904", "206", "303",
            "404", "503", "602", "713", "816", "919", "214", "312", "480", "704"
    };
    public static final String[] WEBSITES_TLD = {".com", ".io", ".net", ".tech", ".co"};

    // ── PO statuses ─────────────────────────────────────────────────────────────

    public static final String[] PO_STATUSES = {"COMPLETED", "COMPLETED", "COMPLETED", "SHIPPED", "INVOICED"};

    // ── Payout band patterns ────────────────────────────────────────────────────

    public static final long[][] BAND_PATTERNS_2 = {{50_000}, {75_000}, {100_000}};
    public static final long[][] BAND_PATTERNS_3 = {
            {25_000, 75_000}, {50_000, 100_000}, {25_000, 50_000}, {50_000, 150_000}
    };
    public static final long[][] BAND_PATTERNS_4 = {
            {10_000, 50_000, 100_000}, {25_000, 75_000, 150_000}, {25_000, 50_000, 100_000}
    };
    public static final double[] NICE_PERCENTAGES = {1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0};
    public static final int[] NICE_CASH_FLAT = {250, 500, 750, 1000, 1500, 2000, 2500};
    public static final int[] NICE_POINTS_FLAT = {500, 750, 1000, 1500, 2000, 2500, 3000};
    public static final int[] NICE_MAX_PER_DEAL = {1000, 1500, 2000, 2500, 3000, 5000};
    public static final long[] NICE_BOOKING_THRESHOLDS = {10_000, 25_000, 50_000, 75_000, 100_000};
    public static final int[] NICE_ENABLEMENT_CASH = {100, 150, 200, 250, 500};
    public static final int[] NICE_ENABLEMENT_POINTS = {200, 250, 500, 750, 1000};
    public static final int[] NICE_CREDITS = {1, 2, 3, 5, 10};
    public static final int[] NICE_TICKETS = {1, 2, 3, 5};

    // ── Product catalog ─────────────────────────────────────────────────────────

    public static final Map<String, String[]> PRODUCT_CATALOG = Map.ofEntries(
            Map.entry("Servers", new String[]{
                    "PowerEdge R760", "PowerEdge R660", "ProLiant DL380", "ProLiant DL360",
                    "ThinkSystem SR650", "PowerEdge R960", "ProLiant DL560",
                    "ThinkSystem SR850", "UCS C240 M7", "Flex System x240"
            }),
            Map.entry("Routers", new String[]{
                    "Catalyst 8500", "Catalyst 8300", "ISR 4000 Series", "ASR 1000 Series",
                    "Catalyst 8200", "ISR 1100 Series", "ASR 9000 Series",
                    "NCS 5500 Series", "NetVanta 6310", "AR2050V"
            }),
            Map.entry("Switches", new String[]{
                    "Catalyst 9300", "Catalyst 9200", "Nexus 9000", "Meraki MS390",
                    "Catalyst 9500", "Nexus 3000", "Meraki MS250",
                    "EX4400 Series", "CloudEngine S5735", "FlexSwitch 5520"
            }),
            Map.entry("Storage", new String[]{
                    "PowerStore 500T", "Unity XT 480", "NetApp AFF A250", "PowerStore 1200T",
                    "Unity XT 880", "NetApp AFF A400", "PowerScale F600",
                    "NetApp FAS500f", "FlashArray//X50", "EqualLogic PS6210"
            }),
            Map.entry("Security", new String[]{
                    "Firepower 4100", "Secure Firewall 3100", "FortiGate 600F", "Firepower 2100",
                    "Secure Firewall 1200", "FortiGate 200F", "Meraki MX450",
                    "PA-5400 Series", "SonicWall NSa 4700", "Check Point 6700"
            }),
            Map.entry("Software & Licensing", new String[]{
                    "DNA Essentials", "DNA Advantage", "Meraki Enterprise License", "SecureX Platform",
                    "ThousandEyes", "Duo Security", "Umbrella DNS",
                    "Webex Suite", "AppDynamics Pro", "Intersight Advantage"
            }),
            Map.entry("Wireless", new String[]{
                    "Catalyst 9120AX", "Catalyst 9130AX", "Meraki MR56", "Meraki MR46",
                    "Catalyst 9166", "Aironet 2800", "Meraki MR36",
                    "AP505i", "FortiAP 431G", "Aruba AP-635"
            }),
            Map.entry("Collaboration", new String[]{
                    "Webex Board 85", "Webex Desk Pro", "Room Navigator", "Webex Room Kit Plus",
                    "Cisco IP Phone 8861", "Webex Desk Mini", "Room Kit EQ",
                    "DX80 Desk Camera", "Headset 730", "Desk Camera 4K"
            }),
            Map.entry("Cloud Services", new String[]{
                    "Intersight IaaS", "CloudCenter Suite", "HyperFlex Edge", "ACI Anywhere",
                    "Multicloud Defense", "Full-Stack Observability", "Cloud Network Controller",
                    "Nexus Dashboard", "SD-WAN vManage", "Meraki Cloud Managed"
            }),
            Map.entry("Managed Services", new String[]{
                    "Smart Net Total Care", "Solution Support", "Business Critical Services",
                    "CX Cloud Agent", "Success Tracks", "Managed Detection & Response",
                    "Managed Firewall", "Managed SD-WAN", "NOC as a Service", "Lifecycle Advantage"
            })
    );

    public static final Map<String, String> CATEGORY_ABBREVIATIONS = Map.ofEntries(
            Map.entry("Servers", "srv"), Map.entry("Routers", "rtr"),
            Map.entry("Switches", "swt"), Map.entry("Storage", "str"),
            Map.entry("Security", "sec"), Map.entry("Software & Licensing", "sw"),
            Map.entry("Wireless", "wls"), Map.entry("Collaboration", "col"),
            Map.entry("Cloud Services", "cld"), Map.entry("Managed Services", "mgs")
    );

    public static final Map<String, double[]> PRICE_RANGES = Map.ofEntries(
            Map.entry("Servers", new double[]{10000, 55000}),
            Map.entry("Routers", new double[]{3600, 30000}),
            Map.entry("Switches", new double[]{2400, 21600}),
            Map.entry("Storage", new double[]{6000, 42000}),
            Map.entry("Security", new double[]{4800, 33600}),
            Map.entry("Software & Licensing", new double[]{600, 9600}),
            Map.entry("Wireless", new double[]{960, 6000}),
            Map.entry("Collaboration", new double[]{600, 14400}),
            Map.entry("Cloud Services", new double[]{3000, 25000}),
            Map.entry("Managed Services", new double[]{1200, 18000})
    );

    // ── Incentive naming ────────────────────────────────────────────────────────

    public static final String[] PRODUCT_FOCUS = {
            "Server Infrastructure", "Network Security", "Cloud & Software",
            "Storage Solutions", "Enterprise Networking", "Managed Services",
            "Collaboration & Wireless", "Data Center", "Digital Transformation"
    };
    public static final String[] PROGRAM_TYPES = {
            "Accelerator", "Sprint", "SPIF", "Growth Program",
            "Deal Bonus", "Blitz", "Fast Track", "Challenge", "Rewards", "Advantage"
    };

    // ── Seasonal patterns ───────────────────────────────────────────────────────

    /** Volume multipliers per calendar quarter: Q1, Q2, Q3, Q4 */
    public static final double[] QUARTERLY_VOLUME = {1.05, 0.90, 0.95, 1.10};

    /** Monthly claim rates (index 0 = Jan) */
    public static final double[] MONTHLY_CLAIM_RATES = {
            0.50, 0.45, 0.42, 0.38, 0.35, 0.38,
            0.32, 0.30, 0.35, 0.40, 0.28, 0.25
    };

    /** Regional volume shares: AMERICAS, LATAM, EMEAR, APJ (remaining 12% buffer) */
    public static final double[] REGION_BASE_SHARE = {0.30, 0.10, 0.28, 0.20};

    // ── Roles ───────────────────────────────────────────────────────────────────

    public static final String ROLE_COMPANY_ADMIN = "Partner Admin";
    public static final String ROLE_PARTNER_SELLER = "Partner Seller";

    // ── Training courses ────────────────────────────────────────────────────────

    /** {name, category, provider, duration, level} */
    public static final String[][] TRAINING_COURSES = {
            {"Introduction to Network Security", "Security", "Cisco Learning", "8h", "BEGINNER"},
            {"Advanced Firewall Configuration", "Security", "Cisco Learning", "16h", "ADVANCED"},
            {"Cloud Infrastructure Fundamentals", "Cloud", "Coursera", "12h", "BEGINNER"},
            {"AWS Solutions Architect Prep", "Cloud", "Udemy", "20h", "INTERMEDIATE"},
            {"Data Center Design Principles", "Data Center", "Cisco Learning", "10h", "INTERMEDIATE"},
            {"Software-Defined Networking", "Networking", "LinkedIn Learning", "6h", "INTERMEDIATE"},
            {"Wireless Network Planning", "Wireless", "Cisco Learning", "8h", "BEGINNER"},
            {"Enterprise Storage Solutions", "Storage", "Coursera", "14h", "ADVANCED"},
            {"Cybersecurity Incident Response", "Security", "Udemy", "12h", "ADVANCED"},
            {"Meraki Cloud Management", "Cloud", "Cisco Learning", "4h", "BEGINNER"},
            {"Collaboration Tools Deployment", "Collaboration", "LinkedIn Learning", "6h", "INTERMEDIATE"},
            {"SD-WAN Architecture & Design", "Networking", "Cisco Learning", "16h", "ADVANCED"},
            {"IoT Security Best Practices", "Security", "Coursera", "10h", "INTERMEDIATE"},
            {"Managed Services Sales Training", "Services", "LinkedIn Learning", "4h", "BEGINNER"},
            {"Next-Gen Routing Protocols", "Networking", "Udemy", "12h", "ADVANCED"}
    };

    // ── Activity templates ──────────────────────────────────────────────────────

    /** Keyed by category: {name, description} */
    public static final Map<String, String[][]> ACTIVITY_TEMPLATES = Map.of(
            "CONTENT_CREATION", new String[][]{
                    {"Create Product Demo Video", "Record and publish a product demonstration video for customer use"},
                    {"Write Technical Blog Post", "Author a technical blog post showcasing solution capabilities"}
            },
            "COMPLIANCE", new String[][]{
                    {"Complete Annual Compliance Review", "Review and acknowledge updated partner compliance requirements"},
                    {"Submit Partner Certification Docs", "Upload required certification documentation for annual audit"}
            },
            "EVENT_PARTICIPATION", new String[][]{
                    {"Attend Partner Summit", "Participate in the regional partner summit event"},
                    {"Host Customer Workshop", "Organize and lead a customer-facing technical workshop"}
            },
            "CUSTOMER_ENGAGEMENT", new String[][]{
                    {"Conduct Customer QBR", "Complete a quarterly business review with an assigned customer"},
                    {"Submit Customer Success Story", "Document and submit a customer success story for publication"}
            },
            "IMPLEMENTATION", new String[][]{
                    {"Complete POC Deployment", "Deploy and document a proof-of-concept for a customer opportunity"},
                    {"Perform Network Assessment", "Conduct a comprehensive network assessment for a prospect"}
            }
    );

    public static final String[] ACTIVITY_CATEGORY_NAMES = {
            "CONTENT_CREATION", "COMPLIANCE", "EVENT_PARTICIPATION",
            "CUSTOMER_ENGAGEMENT", "IMPLEMENTATION"
    };

    /** {name, description} */
    public static final String[][] DOC_REQUIREMENTS = {
            {"Completion Certificate", "Upload a certificate of completion or signed acknowledgment"},
            {"Photo Evidence", "Provide photographic evidence of activity completion"},
            {"Customer Sign-Off", "Submit signed customer acknowledgment form"},
            {"Summary Report", "Upload a summary report detailing the activity outcome"},
            {"Attendance Record", "Provide attendance log or registration confirmation"}
    };
}
