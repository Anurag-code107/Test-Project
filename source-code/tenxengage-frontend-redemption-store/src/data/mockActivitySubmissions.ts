export type ProofStatus =
  | "Not Started"
  | "Uploaded"
  | "Pending Review"
  | "Approved"
  | "Denied";

export interface ProofComment {
  id: string;
  author: string;
  role: "partner" | "approver";
  text: string;
  timestamp: string;
}

export interface ActivityProof {
  id: string;
  name: string;
  description: string;
  order: number;
  proofStatus: ProofStatus;
  uploadedFileName?: string;
  uploadedAt?: string;
  reviewedAt?: string;
  reviewedBy?: string;
  comments: ProofComment[];
}

export interface ActivitySubmission {
  id: string;
  incentiveId: string;
  incentiveName: string;
  partnerCompany: string;
  submittedBy: string;
  submittedAt: string;
  activities: ActivityProof[];
}

const activityProofsMap: Record<string, ActivityProof[]> = {
  "inc-011": [
    {
      id: "act-011-1",
      name: "Customer Interview",
      description:
        "Conduct and record an interview with the customer about their experience using the product.",
      order: 1,
      proofStatus: "Approved",
      uploadedFileName: "customer-interview-recording.mp4",
      uploadedAt: "2025-01-20T10:30:00Z",
      reviewedAt: "2025-01-22T14:00:00Z",
      reviewedBy: "Jane Approver",
      comments: [
        {
          id: "c1",
          author: "You",
          role: "partner",
          text: "Uploaded the full 30-minute interview recording.",
          timestamp: "2025-01-20T10:30:00Z",
        },
        {
          id: "c2",
          author: "Jane Approver",
          role: "approver",
          text: "Great quality interview. Approved.",
          timestamp: "2025-01-22T14:00:00Z",
        },
      ],
    },
    {
      id: "act-011-2",
      name: "Draft Case Study Document",
      description:
        "Write the case study document using the provided template, including metrics and outcomes.",
      order: 2,
      proofStatus: "Pending Review",
      uploadedFileName: "case-study-draft-v2.docx",
      uploadedAt: "2025-02-01T09:00:00Z",
      comments: [
        {
          id: "c3",
          author: "You",
          role: "partner",
          text: "Submitted revised draft with updated metrics.",
          timestamp: "2025-02-01T09:00:00Z",
        },
      ],
    },
    {
      id: "act-011-3",
      name: "Customer Sign-Off",
      description:
        "Get written approval from the customer to publish the case study.",
      order: 3,
      proofStatus: "Not Started",
      comments: [],
    },
  ],
  "inc-012": [
    {
      id: "act-012-1",
      name: "Event Registration Confirmation",
      description:
        "Upload your event registration confirmation email or ticket.",
      order: 1,
      proofStatus: "Approved",
      uploadedFileName: "registration-confirmation.pdf",
      uploadedAt: "2025-02-10T08:00:00Z",
      reviewedAt: "2025-02-11T10:00:00Z",
      reviewedBy: "Jane Approver",
      comments: [
        {
          id: "c4",
          author: "Jane Approver",
          role: "approver",
          text: "Registration confirmed. Approved.",
          timestamp: "2025-02-11T10:00:00Z",
        },
      ],
    },
    {
      id: "act-012-2",
      name: "Attendance Photo Proof",
      description:
        "Upload photos from the event showing your attendance (badge, stage, booth).",
      order: 2,
      proofStatus: "Denied",
      uploadedFileName: "event-photos.zip",
      uploadedAt: "2025-02-15T16:00:00Z",
      reviewedAt: "2025-02-16T11:00:00Z",
      reviewedBy: "Jane Approver",
      comments: [
        {
          id: "c5",
          author: "You",
          role: "partner",
          text: "Uploaded photos from the booth area.",
          timestamp: "2025-02-15T16:00:00Z",
        },
        {
          id: "c6",
          author: "Jane Approver",
          role: "approver",
          text: "Photos don't clearly show your badge or name. Please re-upload with clearer identification.",
          timestamp: "2025-02-16T11:00:00Z",
        },
      ],
    },
    {
      id: "act-012-3",
      name: "Session Notes Submission",
      description:
        "Submit notes from at least 2 sessions you attended at the event.",
      order: 3,
      proofStatus: "Not Started",
      comments: [],
    },
  ],
  "inc-013": [
    {
      id: "act-013-1",
      name: "Demo Recording",
      description:
        "Record the customer demo session showing product capabilities.",
      order: 1,
      proofStatus: "Approved",
      uploadedFileName: "demo-recording.mp4",
      uploadedAt: "2024-08-15T14:00:00Z",
      reviewedAt: "2024-08-17T09:00:00Z",
      reviewedBy: "Mark Reviewer",
      comments: [],
    },
    {
      id: "act-013-2",
      name: "Customer Feedback Form",
      description: "Have the customer complete the post-demo feedback form.",
      order: 2,
      proofStatus: "Approved",
      uploadedFileName: "feedback-form-signed.pdf",
      uploadedAt: "2024-08-20T10:00:00Z",
      reviewedAt: "2024-08-21T15:00:00Z",
      reviewedBy: "Mark Reviewer",
      comments: [],
    },
  ],
  "inc-023": [
    {
      id: "act-023-1",
      name: "Reference Call Recording",
      description:
        "Upload the recorded reference call (with customer consent).",
      order: 1,
      proofStatus: "Uploaded",
      uploadedFileName: "reference-call-jan.mp3",
      uploadedAt: "2025-01-25T11:00:00Z",
      comments: [
        {
          id: "c7",
          author: "You",
          role: "partner",
          text: "Call recording with consent form attached.",
          timestamp: "2025-01-25T11:00:00Z",
        },
      ],
    },
    {
      id: "act-023-2",
      name: "Prospect Feedback Summary",
      description:
        "Write a summary of the prospect's feedback after the reference call.",
      order: 2,
      proofStatus: "Not Started",
      comments: [],
    },
  ],
  "inc-024": [
    {
      id: "act-024-1",
      name: "Go-Live Confirmation",
      description:
        "Upload the go-live confirmation document signed by the customer.",
      order: 1,
      proofStatus: "Not Started",
      comments: [],
    },
    {
      id: "act-024-2",
      name: "Customer Satisfaction Survey",
      description:
        "Have the customer complete and sign the satisfaction survey.",
      order: 2,
      proofStatus: "Not Started",
      comments: [],
    },
    {
      id: "act-024-3",
      name: "Implementation Metrics Report",
      description:
        "Submit a report with key implementation metrics (timeline, adoption rate, ROI).",
      order: 3,
      proofStatus: "Not Started",
      comments: [],
    },
  ],
};

export function getActivityProofs(incentiveId: string): ActivityProof[] {
  return activityProofsMap[incentiveId] || [];
}

export const mockActivitySubmissions: ActivitySubmission[] = [
  {
    id: "sub-001",
    incentiveId: "inc-011",
    incentiveName: "Case Study Submission",
    partnerCompany: "Acme Solutions Inc.",
    submittedBy: "John Partner",
    submittedAt: "2025-02-01T09:00:00Z",
    activities: activityProofsMap["inc-011"] || [],
  },
  {
    id: "sub-002",
    incentiveId: "inc-012",
    incentiveName: "Event Attendance Proof",
    partnerCompany: "TechBridge Partners",
    submittedBy: "Sarah Seller",
    submittedAt: "2025-02-15T16:00:00Z",
    activities: activityProofsMap["inc-012"] || [],
  },
  {
    id: "sub-003",
    incentiveId: "inc-023",
    incentiveName: "Reference Call Documentation",
    partnerCompany: "CloudFirst Consulting",
    submittedBy: "Mike Rep",
    submittedAt: "2025-01-25T11:00:00Z",
    activities: activityProofsMap["inc-023"] || [],
  },
  {
    id: "sub-004",
    incentiveId: "inc-013",
    incentiveName: "Demo Completion Activity",
    partnerCompany: "Acme Solutions Inc.",
    submittedBy: "Lisa Demo",
    submittedAt: "2024-08-20T10:00:00Z",
    activities: activityProofsMap["inc-013"] || [],
  },
  {
    id: "sub-005",
    incentiveId: "inc-011",
    incentiveName: "Case Study Submission",
    partnerCompany: "TechBridge Partners",
    submittedBy: "David Chen",
    submittedAt: "2025-02-05T14:00:00Z",
    activities: [
      {
        id: "act-011-1b",
        name: "Customer Interview",
        description:
          "Conduct and record an interview with the customer about their experience using the product.",
        order: 1,
        proofStatus: "Pending Review",
        uploadedFileName: "interview-techbridge.mp4",
        uploadedAt: "2025-02-04T11:00:00Z",
        comments: [
          {
            id: "c20",
            author: "David Chen",
            role: "partner",
            text: "Interview with Nexus Corp completed.",
            timestamp: "2025-02-04T11:00:00Z",
          },
        ],
      },
      {
        id: "act-011-2b",
        name: "Draft Case Study Document",
        description:
          "Write the case study document using the provided template, including metrics and outcomes.",
        order: 2,
        proofStatus: "Uploaded",
        uploadedFileName: "nexus-case-study.docx",
        uploadedAt: "2025-02-05T14:00:00Z",
        comments: [],
      },
      {
        id: "act-011-3b",
        name: "Customer Sign-Off",
        description:
          "Get written approval from the customer to publish the case study.",
        order: 3,
        proofStatus: "Not Started",
        comments: [],
      },
    ],
  },
  {
    id: "sub-006",
    incentiveId: "inc-012",
    incentiveName: "Event Attendance Proof",
    partnerCompany: "Acme Solutions Inc.",
    submittedBy: "Rachel Adams",
    submittedAt: "2025-02-12T09:30:00Z",
    activities: [
      {
        id: "act-012-1b",
        name: "Event Registration Confirmation",
        description:
          "Upload your event registration confirmation email or ticket.",
        order: 1,
        proofStatus: "Approved",
        uploadedFileName: "reg-confirm-rachel.pdf",
        uploadedAt: "2025-02-10T08:30:00Z",
        reviewedAt: "2025-02-11T09:00:00Z",
        reviewedBy: "James Taylor",
        comments: [
          {
            id: "c21",
            author: "James Taylor",
            role: "approver",
            text: "Confirmed. Approved.",
            timestamp: "2025-02-11T09:00:00Z",
          },
        ],
      },
      {
        id: "act-012-2b",
        name: "Attendance Photo Proof",
        description:
          "Upload photos from the event showing your attendance (badge, stage, booth).",
        order: 2,
        proofStatus: "Pending Review",
        uploadedFileName: "badge-photo-rachel.jpg",
        uploadedAt: "2025-02-12T09:30:00Z",
        comments: [
          {
            id: "c22",
            author: "Rachel Adams",
            role: "partner",
            text: "Photo of my badge and the keynote stage.",
            timestamp: "2025-02-12T09:30:00Z",
          },
        ],
      },
      {
        id: "act-012-3b",
        name: "Session Notes Submission",
        description:
          "Submit notes from at least 2 sessions you attended at the event.",
        order: 3,
        proofStatus: "Not Started",
        comments: [],
      },
    ],
  },
  {
    id: "sub-007",
    incentiveId: "inc-023",
    incentiveName: "Reference Call Documentation",
    partnerCompany: "Acme Solutions Inc.",
    submittedBy: "Tom Wilson",
    submittedAt: "2025-01-28T15:00:00Z",
    activities: [
      {
        id: "act-023-1b",
        name: "Reference Call Recording",
        description:
          "Upload the recorded reference call (with customer consent).",
        order: 1,
        proofStatus: "Approved",
        uploadedFileName: "ref-call-wilson.mp3",
        uploadedAt: "2025-01-27T14:00:00Z",
        reviewedAt: "2025-01-28T10:00:00Z",
        reviewedBy: "James Taylor",
        comments: [],
      },
      {
        id: "act-023-2b",
        name: "Prospect Feedback Summary",
        description:
          "Write a summary of the prospect's feedback after the reference call.",
        order: 2,
        proofStatus: "Pending Review",
        uploadedFileName: "feedback-summary-wilson.pdf",
        uploadedAt: "2025-01-28T15:00:00Z",
        comments: [
          {
            id: "c23",
            author: "Tom Wilson",
            role: "partner",
            text: "Prospect was very interested in the enterprise plan.",
            timestamp: "2025-01-28T15:00:00Z",
          },
        ],
      },
    ],
  },
  {
    id: "sub-008",
    incentiveId: "inc-024",
    incentiveName: "Implementation Success Activity",
    partnerCompany: "CloudFirst Consulting",
    submittedBy: "Anika Patel",
    submittedAt: "2025-02-08T11:00:00Z",
    activities: [
      {
        id: "act-024-1b",
        name: "Go-Live Confirmation",
        description:
          "Upload the go-live confirmation document signed by the customer.",
        order: 1,
        proofStatus: "Uploaded",
        uploadedFileName: "golive-confirm-anika.pdf",
        uploadedAt: "2025-02-07T16:00:00Z",
        comments: [
          {
            id: "c24",
            author: "Anika Patel",
            role: "partner",
            text: "Customer went live on Feb 5th.",
            timestamp: "2025-02-07T16:00:00Z",
          },
        ],
      },
      {
        id: "act-024-2b",
        name: "Customer Satisfaction Survey",
        description:
          "Have the customer complete and sign the satisfaction survey.",
        order: 2,
        proofStatus: "Uploaded",
        uploadedFileName: "satisfaction-survey-signed.pdf",
        uploadedAt: "2025-02-08T11:00:00Z",
        comments: [],
      },
      {
        id: "act-024-3b",
        name: "Implementation Metrics Report",
        description:
          "Submit a report with key implementation metrics (timeline, adoption rate, ROI).",
        order: 3,
        proofStatus: "Not Started",
        comments: [],
      },
    ],
  },
  {
    id: "sub-009",
    incentiveId: "inc-013",
    incentiveName: "Demo Completion Activity",
    partnerCompany: "TechBridge Partners",
    submittedBy: "Emma Foster",
    submittedAt: "2025-01-15T10:00:00Z",
    activities: [
      {
        id: "act-013-1b",
        name: "Demo Recording",
        description:
          "Record the customer demo session showing product capabilities.",
        order: 1,
        proofStatus: "Pending Review",
        uploadedFileName: "demo-emma-jan.mp4",
        uploadedAt: "2025-01-14T15:00:00Z",
        comments: [
          {
            id: "c25",
            author: "Emma Foster",
            role: "partner",
            text: "45-minute demo with GlobalTech.",
            timestamp: "2025-01-14T15:00:00Z",
          },
        ],
      },
      {
        id: "act-013-2b",
        name: "Customer Feedback Form",
        description: "Have the customer complete the post-demo feedback form.",
        order: 2,
        proofStatus: "Uploaded",
        uploadedFileName: "feedback-globaltech.pdf",
        uploadedAt: "2025-01-15T10:00:00Z",
        comments: [],
      },
    ],
  },
  {
    id: "sub-010",
    incentiveId: "inc-011",
    incentiveName: "Case Study Submission",
    partnerCompany: "CloudFirst Consulting",
    submittedBy: "James Liu",
    submittedAt: "2025-02-10T08:00:00Z",
    activities: [
      {
        id: "act-011-1c",
        name: "Customer Interview",
        description:
          "Conduct and record an interview with the customer about their experience using the product.",
        order: 1,
        proofStatus: "Approved",
        uploadedFileName: "interview-liu.mp4",
        uploadedAt: "2025-02-08T10:00:00Z",
        reviewedAt: "2025-02-09T14:00:00Z",
        reviewedBy: "James Taylor",
        comments: [
          {
            id: "c26",
            author: "James Taylor",
            role: "approver",
            text: "Well-structured interview. Approved.",
            timestamp: "2025-02-09T14:00:00Z",
          },
        ],
      },
      {
        id: "act-011-2c",
        name: "Draft Case Study Document",
        description:
          "Write the case study document using the provided template, including metrics and outcomes.",
        order: 2,
        proofStatus: "Denied",
        uploadedFileName: "case-study-liu-v1.docx",
        uploadedAt: "2025-02-10T08:00:00Z",
        reviewedAt: "2025-02-11T09:00:00Z",
        reviewedBy: "James Taylor",
        comments: [
          {
            id: "c27",
            author: "James Liu",
            role: "partner",
            text: "First draft completed.",
            timestamp: "2025-02-10T08:00:00Z",
          },
          {
            id: "c28",
            author: "James Taylor",
            role: "approver",
            text: "Missing ROI metrics and customer quote. Please revise.",
            timestamp: "2025-02-11T09:00:00Z",
          },
        ],
      },
      {
        id: "act-011-3c",
        name: "Customer Sign-Off",
        description:
          "Get written approval from the customer to publish the case study.",
        order: 3,
        proofStatus: "Not Started",
        comments: [],
      },
    ],
  },
  {
    id: "sub-011",
    incentiveId: "inc-012",
    incentiveName: "Event Attendance Proof",
    partnerCompany: "CloudFirst Consulting",
    submittedBy: "Nina Kapoor",
    submittedAt: "2025-02-14T13:00:00Z",
    activities: [
      {
        id: "act-012-1c",
        name: "Event Registration Confirmation",
        description:
          "Upload your event registration confirmation email or ticket.",
        order: 1,
        proofStatus: "Pending Review",
        uploadedFileName: "reg-nina.pdf",
        uploadedAt: "2025-02-13T09:00:00Z",
        comments: [],
      },
      {
        id: "act-012-2c",
        name: "Attendance Photo Proof",
        description:
          "Upload photos from the event showing your attendance (badge, stage, booth).",
        order: 2,
        proofStatus: "Uploaded",
        uploadedFileName: "photos-nina.zip",
        uploadedAt: "2025-02-14T13:00:00Z",
        comments: [
          {
            id: "c29",
            author: "Nina Kapoor",
            role: "partner",
            text: "Badge and session photos included.",
            timestamp: "2025-02-14T13:00:00Z",
          },
        ],
      },
      {
        id: "act-012-3c",
        name: "Session Notes Submission",
        description:
          "Submit notes from at least 2 sessions you attended at the event.",
        order: 3,
        proofStatus: "Not Started",
        comments: [],
      },
    ],
  },
];
