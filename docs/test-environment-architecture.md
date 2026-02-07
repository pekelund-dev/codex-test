# Test Environment Architecture

This document provides visual representations of the test environment architecture and deployment flows.

## Resource Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      GCP Project                                 │
│                                                                   │
│  ┌──────────────────────────┐  ┌──────────────────────────┐    │
│  │   Production Resources    │  │   Test Resources          │    │
│  │                           │  │                           │    │
│  │  Cloud Run Services:      │  │  Cloud Run Services:      │    │
│  │   • pklnd-web            │  │   • pklnd-web-test       │    │
│  │   • pklnd-receipts       │  │   • pklnd-receipts-test  │    │
│  │                           │  │                           │    │
│  │  Firestore Database:      │  │  Firestore Database:      │    │
│  │   • receipts-db          │  │   • receipts-db-test     │    │
│  │                           │  │                           │    │
│  │  Storage Bucket:          │  │  Storage Bucket:          │    │
│  │   • pklnd-receipts-xxx   │  │   • pklnd-receipts-test- │    │
│  │                           │  │     xxx                   │    │
│  │  Secret Manager:          │  │  Secret Manager:          │    │
│  │   • pklnd-app-config     │  │   • pklnd-app-config-test│    │
│  │                           │  │                           │    │
│  └──────────────────────────┘  └──────────────────────────┘    │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              Shared Resources                            │    │
│  │                                                          │    │
│  │  • Service Accounts (cloud-run-runtime, receipt-processor)│  │
│  │  • Artifact Registry (web, receipts repositories)        │    │
│  │  • Cloud Build                                           │    │
│  │  • IAM Roles & Permissions                               │    │
│  │                                                          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Data Isolation

```
┌─────────────────────┐              ┌─────────────────────┐
│  Production User    │              │    Test User        │
└──────────┬──────────┘              └──────────┬──────────┘
           │                                    │
           │ HTTPS Request                      │ HTTPS Request
           ▼                                    ▼
    ┌──────────────┐                    ┌──────────────┐
    │  pklnd-web   │                    │pklnd-web-test│
    └──────┬───────┘                    └──────┬───────┘
           │                                    │
           │ Read/Write                         │ Read/Write
           ▼                                    ▼
    ┌──────────────┐                    ┌──────────────┐
    │ receipts-db  │                    │receipts-db-  │
    │              │ ◄── ISOLATED ──► │test          │
    └──────────────┘                    └──────────────┘
           │                                    │
           │ Store Files                        │ Store Files
           ▼                                    ▼
    ┌──────────────┐                    ┌──────────────┐
    │pklnd-receipts│                    │pklnd-receipts│
    │-<project>    │ ◄── ISOLATED ──► │-test-<proj>  │
    └──────────────┘                    └──────────────┘

    NO DATA SHARED BETWEEN ENVIRONMENTS
```

## Deployment Flow

```
┌────────────────────────────────────────────────────────────────┐
│                    Initial Setup (One-time)                     │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                 ┌────────────────────────┐
                 │ Create OAuth Credentials│
                 └────────┬───────────────┘
                          │
                          ▼
         ┌────────────────────────────────────────┐
         │  setup_test_infrastructure.sh          │
         │  • Firestore DB                        │
         │  • Storage Bucket                      │
         │  • Secret Manager                      │
         │  • IAM Permissions                     │
         └────────┬───────────────────────────────┘
                  │
                  ▼
         ┌────────────────────────────────────────┐
         │  deploy_to_test.sh                     │
         │  • Build Images                        │
         │  • Push to Artifact Registry           │
         │  • Deploy Cloud Run Services           │
         └────────┬───────────────────────────────┘
                  │
                  ▼
         ┌────────────────────────────────────────┐
         │  Update OAuth Redirect URIs            │
         └────────┬───────────────────────────────┘
                  │
                  ▼
         ┌────────────────────────────────────────┐
         │  Test Environment Ready! ✅            │
         └────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                    Update Deployment                            │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                 ┌────────────────────────┐
                 │   Make Code Changes    │
                 └────────┬───────────────┘
                          │
                          ▼
         ┌────────────────────────────────────────┐
         │  deploy_to_test.sh                     │
         │  • Build New Images                    │
         │  • Update Services                     │
         │  • Data Preserved                      │
         └────────┬───────────────────────────────┘
                  │
                  ▼
         ┌────────────────────────────────────────┐
         │  Test Changes                          │
         └────────┬───────────────────────────────┘
                  │
            ┌─────┴─────┐
            │           │
            ▼           ▼
    ┌──────────┐  ┌──────────┐
    │   OK?    │  │  Issues? │
    └────┬─────┘  └────┬─────┘
         │             │
         │             └────► Fix and Redeploy
         │
         ▼
┌────────────────────────┐
│ Deploy to Production   │
└────────────────────────┘
```

## GitHub Actions Flow

```
┌──────────────────────────────────────────────────────────────┐
│                    GitHub Repository                          │
└──────────────────────────────────────────────────────────────┘
                          │
                          │ Push Code
                          ▼
           ┌──────────────────────────────┐
           │     GitHub Actions            │
           │                               │
           │  1. Select Environment        │
           │     ├─ production             │
           │     ├─ test ◄── Selected     │
           │     └─ staging                │
           │                               │
           │  2. Click "Run workflow"      │
           └──────────┬────────────────────┘
                      │
                      ▼
        ┌─────────────────────────────────────┐
        │   Workflow Execution                │
        │                                     │
        │   • Set WEB_SERVICE_NAME            │
        │     = pklnd-web-test                │
        │   • Set RECEIPT_SERVICE_NAME        │
        │     = pklnd-receipts-test           │
        │                                     │
        │   • Build web image                 │
        │   • Build receipt-parser image      │
        │   • Push to Artifact Registry       │
        │                                     │
        │   • Deploy to test services         │
        │                                     │
        └──────────┬──────────────────────────┘
                   │
                   ▼
          ┌────────────────────┐
          │   GCP Test Env     │
          │   Updated ✅       │
          └────────────────────┘
```

## Terraform State Management

```
┌─────────────────────────────────────────────────────────────┐
│  GCS Bucket: pklnd-terraform-state-<project>                │
│                                                             │
│  ├─ infrastructure/              (Production Infrastructure)│
│  │  └─ default.tfstate                                     │
│  │                                                          │
│  ├─ infrastructure-test/          (Test Infrastructure)    │
│  │  └─ default.tfstate                                     │
│  │                                                          │
│  ├─ deployment/                   (Production Services)    │
│  │  └─ default.tfstate                                     │
│  │                                                          │
│  └─ deployment-test/              (Test Services)          │
│     └─ default.tfstate                                     │
│                                                             │
│  Each prefix maintains separate state for:                 │
│  • Resource tracking                                        │
│  • Change detection                                         │
│  • Dependency management                                    │
│                                                             │
│  ✅ Prevents accidental production changes                 │
│  ✅ Enables independent environment management             │
└─────────────────────────────────────────────────────────────┘
```

## Cost Structure

```
┌─────────────────────────────────────────────────────────────┐
│                      Monthly Costs                           │
└─────────────────────────────────────────────────────────────┘

    SHARED COSTS (One-time per project)
    ├─ Artifact Registry Storage ........... $0.10/GB
    ├─ Terraform State Storage ............. $0.02/GB
    └─ Service Accounts .................... FREE

    PRODUCTION COSTS
    ├─ Cloud Run Web Service ............... Usage-based
    ├─ Cloud Run Receipt Service ........... Usage-based
    ├─ Firestore (receipts-db) ............. $0.18/GB + ops
    ├─ Cloud Storage (receipts) ............ $0.02/GB
    └─ Secret Manager ...................... $0.06/secret/month

    TEST COSTS (Incremental)
    ├─ Cloud Run Web Test Service .......... Usage-based ¹
    ├─ Cloud Run Receipt Test Service ...... Usage-based ¹
    ├─ Firestore (receipts-db-test) ........ $0.18/GB + ops ²
    ├─ Cloud Storage (receipts-test) ....... $0.02/GB ²
    └─ Secret Manager (test) ............... $0.06/secret/month

    DEPLOYMENT COSTS (Per deployment)
    └─ Cloud Build ......................... $0.003/min ³

    ¹ Scales to zero when idle (no cost)
    ² Typically minimal for testing (<1GB)
    ³ ~10 min per deployment = $0.03

    ESTIMATED MONTHLY TEST COST: $5-15
    (Assuming light testing usage with minimal data)
```

## Security & Access Control

```
┌─────────────────────────────────────────────────────────────┐
│                   Access Control Layers                      │
└─────────────────────────────────────────────────────────────┘

    NETWORK LAYER
    ├─ Cloud Run Services
    │  ├─ Production: pklnd-web (public HTTPS)
    │  └─ Test: pklnd-web-test (public HTTPS)
    │     └─ Different URLs prevent accidental access

    AUTHENTICATION LAYER
    ├─ OAuth 2.0 (Google Sign-in)
    │  ├─ Production: Production OAuth Client
    │  └─ Test: Test OAuth Client (can be same or different)
    │     └─ Separate redirect URIs per environment

    DATA LAYER
    ├─ Firestore
    │  ├─ Production: receipts-db
    │  └─ Test: receipts-db-test
    │     └─ Physically separate databases
    │
    ├─ Cloud Storage
    │  ├─ Production: pklnd-receipts-<project>
    │  └─ Test: pklnd-receipts-test-<project>
    │     └─ Physically separate buckets
    │
    └─ Secret Manager
       ├─ Production: pklnd-app-config
       └─ Test: pklnd-app-config-test
          └─ Separate secrets with different credentials

    IAM LAYER (Shared)
    ├─ Service Account: cloud-run-runtime@
    │  └─ Permissions: datastore.user, storage.objectAdmin
    │     └─ Can access both prod and test resources
    │         ⚠️  Separation enforced by application config
    └─ Service Account: receipt-processor@
       └─ Permissions: datastore.user, aiplatform.user, storage.objectAdmin
          └─ Can access both prod and test resources
              ⚠️  Separation enforced by application config
```

## Key Design Decisions

### 1. Same Project vs Separate Project
**Decision**: Support both, recommend same project

**Rationale**:
- Same project: Lower cost, easier management, shared quotas
- Separate project: Complete isolation, independent billing
- Flexibility lets teams choose based on their needs

### 2. Resource Naming Convention
**Decision**: Append `-test` suffix to all resource names

**Rationale**:
- Clear identification of environment
- Prevents accidental changes to production
- Simple to understand and maintain

### 3. Shared vs Separate Service Accounts
**Decision**: Share service accounts between environments

**Rationale**:
- Reduces management overhead
- Lower cost (fewer IAM bindings)
- Application config provides data separation
- Risk is low since both environments are controlled

### 4. Terraform State Separation
**Decision**: Use separate state prefixes per environment

**Rationale**:
- Prevents accidental production changes
- Enables parallel development
- Independent lifecycle management
- Easy to tear down test without affecting production

### 5. GitHub Actions Integration
**Decision**: Single workflow with environment parameter

**Rationale**:
- DRY principle (don't repeat workflow code)
- Easy to add more environments
- Consistent deployment process
- Clear in UI which environment is being deployed

## Migration Path

For teams with existing production deployments:

```
Current State                   Add Test Environment
─────────────                   ────────────────────

Production Only                 Production + Test
    │                               │         │
    ▼                               ▼         ▼
┌──────────┐                   ┌──────────┬──────────┐
│  prod    │        ──────►    │   prod   │   test   │
└──────────┘                   └──────────┴──────────┘

No infrastructure changes needed for production!
Test is additive - doesn't modify existing setup.
```

## Troubleshooting Decision Tree

```
                    Issue?
                      │
        ┌─────────────┼─────────────┐
        │             │             │
    Deployment    Data Issue   Access Issue
        │             │             │
        ▼             ▼             ▼
    Check logs    Check DB      Check OAuth
    Check build   Check bucket   Check IAM
    Check images  Check config   Check secrets
        │             │             │
        └─────────────┴─────────────┘
                      │
                      ▼
                See docs/test-environment-setup.md
                Troubleshooting section
```

## Related Documentation

- [Test Environment Setup Guide](test-environment-setup.md) - Complete setup instructions
- [Quick Reference](test-environment-quick-reference.md) - Daily usage commands
- [Implementation Summary](IMPLEMENTATION_SUMMARY.md) - All manual steps listed
- [Terraform Deployment](terraform-deployment.md) - Infrastructure details
