import type { LmsCourseCategory } from "@/types/incentive.types";

export const lmsCourseCategories: LmsCourseCategory[] = [
  {
    name: "Product Knowledge",
    courses: [
      {
        id: "course-001",
        name: "Enterprise Networking Fundamentals",
        description:
          "Core concepts of enterprise networking architecture and design principles",
        category: "Product Knowledge",
        duration: "4 hours",
        level: "BEGINNER",
        provider: "TenX Academy",
      },
      {
        id: "course-002",
        name: "Advanced Server Configuration",
        description:
          "Deep dive into server deployment, virtualization, and management",
        category: "Product Knowledge",
        duration: "6 hours",
        level: "ADVANCED",
        provider: "TenX Academy",
      },
      {
        id: "course-003",
        name: "Cloud Infrastructure Overview",
        description:
          "Understanding hybrid cloud architectures and migration strategies",
        category: "Product Knowledge",
        duration: "3 hours",
        level: "INTERMEDIATE",
        provider: "TenX Academy",
      },
    ],
  },
  {
    name: "Sales Methodology",
    courses: [
      {
        id: "course-004",
        name: "Solution Selling for Channel Partners",
        description:
          "Learn consultative selling techniques for complex technology solutions",
        category: "Sales Methodology",
        duration: "2 hours",
        level: "BEGINNER",
        provider: "Sales Institute",
      },
      {
        id: "course-005",
        name: "Competitive Positioning Workshop",
        description:
          "Strategies for positioning products against key competitors",
        category: "Sales Methodology",
        duration: "3 hours",
        level: "INTERMEDIATE",
        provider: "Sales Institute",
      },
    ],
  },
  {
    name: "Technical Certification",
    courses: [
      {
        id: "course-006",
        name: "Network Security Specialist",
        description:
          "Prepare for the Network Security Specialist certification exam",
        category: "Technical Certification",
        duration: "8 hours",
        level: "ADVANCED",
        provider: "CertPro",
      },
      {
        id: "course-007",
        name: "Cloud Solutions Architect",
        description:
          "Master cloud architecture patterns and earn your certification",
        category: "Technical Certification",
        duration: "10 hours",
        level: "ADVANCED",
        provider: "CertPro",
      },
      {
        id: "course-008",
        name: "Storage Administration Basics",
        description:
          "Foundational course on enterprise storage management and configuration",
        category: "Technical Certification",
        duration: "5 hours",
        level: "BEGINNER",
        provider: "CertPro",
      },
    ],
  },
  {
    name: "Customer Success",
    courses: [
      {
        id: "course-009",
        name: "Customer Lifecycle Management",
        description:
          "Best practices for managing the full customer lifecycle from onboarding to renewal",
        category: "Customer Success",
        duration: "2 hours",
        level: "INTERMEDIATE",
        provider: "TenX Academy",
      },
      {
        id: "course-010",
        name: "Building Customer References",
        description:
          "How to develop and leverage customer success stories for sales enablement",
        category: "Customer Success",
        duration: "1.5 hours",
        level: "BEGINNER",
        provider: "TenX Academy",
      },
    ],
  },
];

export const allCourses = lmsCourseCategories.flatMap((cat) => cat.courses);
