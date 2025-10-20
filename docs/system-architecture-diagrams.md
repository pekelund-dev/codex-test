# System Architecture Schematics

The diagrams below illustrate how the responsive auth web application, receipt-processing Cloud Function, and shared Google Cloud resources work together. Replace placeholder values (for example `PROJECT_ID`, `DOMAIN`, or `RECEIPTS_BUCKET`) with your real identifiers when applying the design.

---

## High-Level Service Topology

```mermaid
flowchart LR
    subgraph Client["Client"]
        Browser["Web Browser"]
    end

    subgraph DNS["Porkbun DNS Zone"]
        Domain["DOMAIN (CNAME)"]
    end

    subgraph CloudRun["Google Cloud Project\n(PROJECT_ID)"]
        WebApp["Cloud Run Service\nSERVICE_NAME"]
        Secrets["Secret Manager (optional secrets)"]
    end

    subgraph SharedFirestore["Shared Firestore Project\n(SHARED_FIRESTORE_PROJECT_ID)"]
        Firestore["Firestore\nusers & receiptExtractions collections"]
    end

    subgraph Storage["Cloud Storage"]
        Bucket["RECEIPTS_BUCKET"]
    end

    subgraph Functions["Receipt Processing"]
        GCF["Cloud Function\nreceipt-parser"]
    end
    subgraph PubSub["Pub/Sub"]
        Topic["Topic\nreceipt-processing"]
        Subscription["Push subscription"]
    end

    subgraph VertexAI["Vertex AI\n(PROJECT_ID/REGION)"]
        Gemini["Gemini API"]
    end

    Browser -->|HTTPS request| Domain
    Domain -->|Custom domain mapping| WebApp
    WebApp -->|Runtime SA| Firestore
    WebApp --> Secrets
    WebApp -->|Signed upload URLs| Bucket
    WebApp -->|Pub/Sub push endpoint| Subscription
    Subscription -->|deliver message| WebApp
    Bucket -->|Finalize event| GCF
    GCF -->|Publish message| Topic
    Topic --> Subscription
    WebApp -->|Fetch receipt| Bucket
    WebApp -->|Parse receipt| Gemini
    WebApp -->|Persist data| Firestore
```

**Key points**

- The Cloud Run service owns Firestore access while the Cloud Function only requires Pub/Sub publish permissions, keeping the event bridge lightweight.
- Custom domain traffic (`DOMAIN`) is routed through Porkbun DNS to the Cloud Run HTTPS endpoint created by the Cloud Run domain mapping workflow.
- Secrets, OAuth credentials, and API keys should be stored in Secret Manager and referenced through environment variables rather than being hard-coded.

---

## Receipt Processing Sequence

```mermaid
sequenceDiagram
    participant User as User
    participant CloudRun as Cloud Run Web App
    participant Storage as Cloud Storage Bucket
    participant GCF as Receipt Cloud Function
    participant PubSub as Pub/Sub Topic
    participant Firestore
    participant Gemini as Vertex AI Gemini

    User->>CloudRun: Upload receipt (via UI)
    CloudRun->>Storage: PUT object using signed URL
    Storage-->>CloudRun: Upload success
    Storage-->>GCF: Trigger finalize event
    GCF->>PubSub: Publish ReceiptProcessingMessage
    PubSub-->>CloudRun: Push delivery to internal endpoint
    CloudRun->>Storage: Download PDF/image
    CloudRun->>Gemini: Request structured extraction
    Gemini-->>CloudRun: Parsed receipt data
    CloudRun->>Firestore: Upsert receipt document
    User->>CloudRun: View receipt dashboard
    CloudRun->>Firestore: Query receipts & users
    Firestore-->>CloudRun: Return documents
    CloudRun-->>User: Render combined view
```

**Notes**

- The Cloud Run service persists parsed receipts in Firestore using the `FIRESTORE_PROJECT_ID` / `RECEIPT_FIRESTORE_COLLECTION` configuration. The Cloud Function simply publishes to Pub/Sub and runs without Spring or additional frameworks.
- The signed URL upload pattern prevents the Cloud Run service from proxying large files, while still enforcing authenticated access and storage permissions.

---

## Deployment & Delivery Pipeline

```mermaid
flowchart LR
    Developer[Developer Workstation] -->|git push| Repo[Source Repository]
    Repo -->|Trigger| CloudBuild[Cloud Build]
    CloudBuild -->|Build container image| ArtifactRegistry[Artifact Registry]
    ArtifactRegistry -->|Image URI| CloudRunService[Cloud Run Service]
    CloudRunService -->|Serve HTTPS traffic| EndUsers[Users via DOMAIN]
```

**Operational checklist**

- The `scripts/deploy_cloud_run.sh` and `scripts/deploy_cloud_function.sh` scripts reuse the same environment configuration to keep the Firestore project, service accounts, and regions consistent.
- Cloud Build can be replaced with local Docker builds if preferred—ensure the final image is pushed to a registry accessible by Cloud Run.
- After deployment, run the domain mapping workflow so `DOMAIN` resolves to the new Cloud Run service, and verify TLS certificates are provisioned before flipping production traffic.
