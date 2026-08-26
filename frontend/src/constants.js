/* Reference data the interface needs before it has spoken to the server, plus
   the labels it renders. Categories and core values come from the API instead -
   those live in the enums so the form, the filters and any export describe them
   identically. */

/* Who you can view the app as. There is no sign-in, so this is the whole
   identity model - see store.jsx.

   Some of these have spent their nomination for the current quarter in the demo
   seed and some have not, so both sides of the one-per-quarter rule are one
   click apart. Anyone marked "free slot" below is a nominee in the seed data
   but never a nominator this quarter, so the submission form renders for them.

   Run out of free slots while clicking around? Either "+ New employee" in the
   profile menu, which invents a fresh identity with a slot of its own, or
   "Reset demo data", which hands everyone's back. */
export const PERSONAS = [
  { id: "calvin", name: "Calvin Ho", email: "calvin.ho@version1.com",
    role: "EMPLOYEE", title: "Consultant · Data & AI" },
  { id: "jamie", name: "Jamie Doyle", email: "jamie.doyle@version1.com",
    role: "EMPLOYEE", title: "Engineer · Cloud Engineering" },
  // Free slot.
  { id: "sarah", name: "Sarah Murphy", email: "sarah.murphy@version1.com",
    role: "EMPLOYEE", title: "Lead Consultant · Data & AI" },
  // Free slot.
  { id: "ravi", name: "Ravi Patel", email: "ravi.patel@version1.com",
    role: "EMPLOYEE", title: "Senior Engineer · Cloud Engineering" },
  // Free slot.
  { id: "michael", name: "Michael Chen", email: "michael.chen@version1.com",
    role: "EMPLOYEE", title: "Consultant · Digital" },
  // Free slot.
  { id: "grace", name: "Grace O'Sullivan", email: "grace.osullivan@version1.com",
    role: "EMPLOYEE", title: "Analyst · Digital" },
  // Free slot - her seeded nomination is in the previous quarter.
  { id: "aisling", name: "Aisling Kelly", email: "aisling.kelly@version1.com",
    role: "EMPLOYEE", title: "Delivery Lead · Data & AI" },
  { id: "colette", name: "Colette Lynch", email: "colette.lynch@version1.com",
    role: "COORDINATOR", title: "HR · Recognition coordinator" },
  // A second reviewer, so the activity log can show two different people
  // deciding rather than one.
  { id: "dara", name: "Dara Quinn", email: "dara.quinn@version1.com",
    role: "COORDINATOR", title: "HR · Recognition coordinator" },
];

export const ROLE_LABEL = { EMPLOYEE: "Employee", COORDINATOR: "Admin / HR" };

export const ROUTES = [
  { id: "home",      label: "Home",                ic: "⌂",  group: "Recognition", roles: ["EMPLOYEE", "COORDINATOR"] },
  { id: "submit",    label: "Submit Recognition",  ic: "✎",  group: "Recognition", roles: ["EMPLOYEE", "COORDINATOR"] },
  { id: "mine",      label: "My Recognition",      ic: "★",  group: "Recognition", roles: ["EMPLOYEE"] },
  { id: "praises",   label: "Praises Wall",        ic: "♡",  group: "Recognition", roles: ["EMPLOYEE", "COORDINATOR"] },
  { id: "stars",     label: "Star Awards",         ic: "✦",  group: "Recognition", roles: ["EMPLOYEE", "COORDINATOR"] },
  { id: "mtm",       label: "Moments that Matter", ic: "🎁", group: "Recognition", roles: ["EMPLOYEE", "COORDINATOR"] },
  { id: "queue",     label: "Review Queue",        ic: "☑",  group: "Coordinator", roles: ["COORDINATOR"], badge: "pending" },
  { id: "ai",        label: "AI Summary",          ic: "◎",  group: "Coordinator", roles: ["COORDINATOR"] },
  { id: "quarters",  label: "Quarters",            ic: "◷",  group: "Coordinator", roles: ["COORDINATOR"] },
  { id: "activity",  label: "Activity Log",        ic: "≡",  group: "Coordinator", roles: ["COORDINATOR"] },
  { id: "dashboard", label: "Dashboard",           ic: "▦",  group: "Coordinator", roles: ["COORDINATOR"] },
  { id: "reports",   label: "Reports",             ic: "▤",  group: "Coordinator", roles: ["COORDINATOR"] },
  { id: "help",      label: "Help & Guidelines",   ic: "?",  group: "Recognition", roles: ["EMPLOYEE", "COORDINATOR"] },
];

export const STATUS = {
  PENDING_REVIEW:     { cls: "pending",  g: "◔", label: "Pending review" },
  NEEDS_RESUBMISSION: { cls: "progress", g: "↩", label: "Needs resubmission" },
  APPROVED:           { cls: "approved", g: "✓", label: "Approved" },
  REJECTED:           { cls: "rejected", g: "✕", label: "Rejected" },
};

export const ACTION = {
  APPROVED:               { cls: "approved", g: "✓", label: "Approved" },
  REJECTED:               { cls: "rejected", g: "✕", label: "Rejected" },
  RESUBMISSION_REQUESTED: { cls: "progress", g: "↩", label: "Resubmission requested" },
};

export const AI_STATUS = {
  COMPLETED:          "Completed",
  FAILED:             "AI review unavailable — the evaluator call failed",
  SKIPPED_NO_API_KEY: "AI review skipped — no API key was configured",
};

/* Version 1's actual six. The earlier prototype carried a made-up set; these
   match what the submission form and the tagging rules use. */
export const VALUES = [
  "Honesty & Integrity", "Personal Commitment", "No Ego",
  "Customer First", "Excellence", "Drive",
];

export const CATEGORY_TINT = {
  COLLABORATION_AND_ENGAGEMENT: "var(--praise)",
  CUSTOMER_IMPACT: "var(--info)",
  INNOVATION_AND_GROWTH: "var(--star)",
  PERFORMANCE_AND_EFFICIENCY: "var(--mtm)",
  QUALITY_AND_COMPLIANCE: "var(--good)",
};

export const AV_COLORS = ["#6C4BD8", "#0F9E8E", "#C2410C", "#2a78d6", "#B0448F", "#0f766e"];
