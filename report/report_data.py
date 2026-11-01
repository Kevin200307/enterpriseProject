"""
SwiftDrop Technical Project Report Data
System: SwiftDrop Parcel Delivery & Courier Management Platform
Architecture: Distributed Cloud-Native Microservices with Autonomous AI Agent
"""

REPORT_METADATA = {
    "title": "SwiftDrop: Autonomous Cloud-Native Parcel Delivery & Courier Management Platform",
    "project": "SwiftDrop",
    "stack": "Java 21, Spring Boot 4.1.1, Spring Cloud Gateway, PostgreSQL (Neon), Redis, RabbitMQ, React 18, TypeScript, Docker Compose, Kubernetes/Helm, Spring AI",
    "team_members": [
        {"id": "M1", "name": "Amandi Arangala", "role": "Identity & Auth Service", "github": "@AmandiArangala"},
        {"id": "M2", "name": "Kevin", "role": "API Gateway & Edge Platform", "github": "@Kevin200307"},
        {"id": "M3", "name": "Pavindu", "role": "Pricing & Zone Management", "github": "@Pavindu11"},
        {"id": "M4", "name": "Praveena Ishadi", "role": "Parcel Booking & Lifecycle", "github": "@LP-Ishadi"},
        {"id": "M5", "name": "Dimesh Methsara", "role": "Dispatch & Courier Workload", "github": "@Dimeshmethsara"},
        {"id": "M6", "name": "Charithma Dassanayake", "role": "Merchant API & Webhooks", "github": "@CDDassanayake"},
        {"id": "M7", "name": "Maheesha Paboth", "role": "Live Tracking & WebSockets", "github": "@MaheeshaPaboth"},
        {"id": "M8", "name": "Thiranjaya", "role": "Notification Pipeline & Templates", "github": "@thirsturn"},
        {"id": "M9", "name": "Tharusha Prabhashwara", "role": "AI Assistant & MCP Tools", "github": "@Prabhashwara2003"},
        {"id": "M10", "name": "Thanoj Buddhima", "role": "Frontend SPA Architecture & System Design", "github": "@ThanojBuddhima"},
    ]
}

REPORT_TOPICS = [
    {
        "topic_number": 1,
        "title": "Introduction and Project Context",
        "sections": [
            {
                "subheading": "1.1 Background",
                "content": (
                    "The rapid growth of digital commerce has made agile, reliable last-mile logistics essential for modern trade. "
                    "Traditional courier operations continue to suffer from disconnected paper records, manual dispatch ledgers, and centralized "
                    "monolithic architectures that create operational silos, lack live courier visibility, and degrade under peak delivery demands.\n\n"
                    "SwiftDrop is a cloud-native, distributed parcel delivery and courier management platform designed to automate and streamline "
                    "the entire logistics lifecycle. Built as an autonomous microservices ecosystem, SwiftDrop unifies parcel booking, dynamic "
                    "regional pricing, courier fleet dispatching, real-time WebSocket tracking, multi-channel notifications, and commercial merchant "
                    "integrations. Crucially, the platform incorporates an Autonomous AI Agent powered by Spring AI and the Model Context Protocol (MCP), "
                    "enabling the system to understand natural language instructions, invoke domain tools, and execute end-to-end logistics operations "
                    "automatically without manual human intervention."
                )
            },
            {
                "subheading": "1.2 Problem Statement",
                "content": (
                    "Despite the availability of basic courier tracking tools, contemporary logistics platforms continue to struggle with several "
                    "critical operational, technological, and architectural deficiencies:\n\n"
                    "1. Information Asymmetry and Opaque Transit Visibility: Traditional courier systems rely on batch-updated database records that "
                    "fail to reflect instantaneous transit checkpoints. End customers and business recipients experience persistent uncertainty regarding "
                    "delivery windows, while couriers lack standardized, tamper-evident mechanisms to capture real-time photographic Proof-of-Delivery (POD).\n\n"
                    "2. Inflexible, Manual, and Non-Standardized Pricing: Most courier platforms compute shipment charges using coarse, manual rate cards that "
                    "fail to accommodate multi-tier geographic zones, volumetric parcel dimensions, dynamic speed tiers (standard vs. express), and negotiated "
                    "commercial merchant volume discounts. This causes revenue leakage for operators and unexpected fee discrepancies for customers.\n\n"
                    "3. Sub-optimal Dispatch Queuing and Courier Workload Imbalance: Parcel dispatching in distribution centers is frequently conducted through "
                    "ad-hoc manual assignment. Dispatchers lack unified visibility into courier capacity, route congestion, and active shifts, resulting in "
                    "delayed pickups, uneven courier workload distribution, and elevated delivery failure rates.\n\n"
                    "4. Friction in Commercial Merchant and B2B Partner Integration: E-commerce merchants requiring programmatic shipping integrations are "
                    "hindered by proprietary, unstandardized APIs lacking robust developer tooling, self-service API key provisioning, secure scoped "
                    "authorization, and automated webhook notification pipelines with resilient retry mechanisms.\n\n"
                    "5. Monolithic Architectural Vulnerability and Scalability Limitations: Legacy monolithic backends suffer from shared database contention "
                    "and tight coupling. High-volume read traffic directly degrades transactional paths. A single failure in an auxiliary subsystem risks "
                    "cascading across the entire application.\n\n"
                    "6. High Human Operational Overhead and Absence of Autonomous Intelligent Execution: Traditional logistics systems require manual human "
                    "intervention at every stage—manually filling parcel forms, monitoring dispatch queues, verifying tracking numbers, resolving delivery "
                    "exceptions, and auditing failed webhooks. The absence of an integrated, context-aware Autonomous AI Agent capable of understanding natural "
                    "language instructions, executing microservice tools, and completing complex logistics workflows end-to-end without human friction creates "
                    "severe operational delays, elevates human error rates, and escalates support overhead."
                )
            },
            {
                "subheading": "1.3 Objectives",
                "content": (
                    "The SwiftDrop platform was designed and implemented around four core dimensions:\n\n"
                    "1. Functional Objectives:\n"
                    "- Dynamic Fee Estimation & Booking: Self-service parcel booking portal calculating tariffs in real time based on postal codes, weight, volume, and service speed.\n"
                    "- Unified Multi-Role Portals: Dedicated responsive web interfaces for Customers, Merchants, Couriers, Dispatchers, and Administrators.\n"
                    "- Real-Time Live Tracking: Push-based shipment tracking utilizing STOMP over WebSockets with SSE fallback.\n"
                    "- Automated Asynchronous Notification Pipeline: Instant transactional email notifications on parcel milestones (pickup, out-for-delivery, delivered, failed).\n"
                    "- Enterprise Partner Integration: Developer REST API with client-credentials, scoped API keys, and webhook retries with exponential backoff.\n\n"
                    "2. Autonomous AI Agent & End-to-End Automation Objectives:\n"
                    "- Autonomous Agentic Workflow Execution: Intelligent, tool-calling AI agent powered by Spring AI, LLMs, and Model Context Protocol (MCP) capable of executing the complete suite of system functionalities automatically based on natural language instructions.\n"
                    "- Automated Parcel Booking & Fee Estimation: Parse conversational customer requests, extract dimensional and routing attributes, invoke the dynamic pricing engine, and book deliveries end-to-end autonomously.\n"
                    "- Intelligent Tracking & Exception Explanation: Retrieve real-time tracking data, summarize transit history, interpret delivery exceptions, and provide human-friendly explanations while strictly enforcing data privacy and RBAC boundaries.\n"
                    "- Autonomous Dispatch & Workload Management: Evaluate unassigned booking queues, assess courier shift capacities and geographic zones, and recommend or trigger optimal parcel assignments automatically.\n"
                    "- Automated Merchant Operations & Auditing: Interact with merchant services to generate API keys, validate webhook delivery statuses, inspect dead letter queues, and trigger automated retries for failed event notifications.\n"
                    "- Real-Time Streaming Feedback & Tool Auditability: Stream agent reasoning steps, tool invocation payloads, and execution results using Server-Sent Events (SSE) to ensure full operational transparency and human oversight.\n\n"
                    "3. Architectural & Technical Objectives:\n"
                    "- Microservice Domain Decomposition: Autonomous Spring Boot microservices aligned with DDD bounded contexts.\n"
                    "- Database-per-Service Encapsulation: Strict data isolation where each microservice owns its schema and PostgreSQL database (Neon Cloud).\n"
                    "- Centralized Edge Gateway & BFF: Reactive Spring Cloud Gateway (Port 8081) for routing, CORS, JWT validation, and Redis rate limiting.\n"
                    "- Event-Driven Decoupling via AMQP: RabbitMQ topic exchanges (swiftdrop.events) publishing ParcelStatusChangedV1 domain events.\n"
                    "- Cloud-Native Deployment: Docker Compose for local dev and Kubernetes umbrella Helm chart for production.\n\n"
                    "4. Security, Reliability & Quality Objectives:\n"
                    "- Cryptographic Asymmetric Auth: RSA 2048-bit (RS256) JWT with RFC 7517 JWKS discovery for stateless verification.\n"
                    "- Federated Identity & PKCE: Google OAuth 2.0 with PKCE for seamless customer authentication.\n"
                    "- Role-Based Tool Authorization for AI: Enforce strict role-based capability filtering within the AI assistant so that the agent only executes tools matching the authenticated caller's granted authorities.\n"
                    "- Multi-Tier Quality Assurance: Unit testing, Testcontainers slice testing (PostgreSQL & RabbitMQ), RBAC authorization matrix, and Playwright smoke tests."
                )
            }
        ]
    },
    {
        "topic_number": 2,
        "title": "Requirements Engineering & Use Case Modeling",
        "sections": [
            {
                "subheading": "2.1 System Actors & Authorisation Boundaries",
                "content": (
                    "Authorization is enforced through role-based access control (RBAC). Roles are formally codified in contracts.domain.Role "
                    "and emitted in the 'roles' claim of every cryptographically signed RS256 access token:\n"
                    "- Guest (Unauthenticated): Restricted to public rate-limited parcel tracking by tracking number.\n"
                    "- Customer (CUSTOMER): Can request shipping fee estimates, book parcel pickups, view history, and download receipts.\n"
                    "- Merchant (MERCHANT): Vetted commercial partners capable of bulk order ingestion, API key management, and webhook configurations.\n"
                    "- Courier (COURIER): Delivery personnel authorized to view assigned manifests, accept/reject jobs, update transit statuses, and upload proof-of-delivery (POD).\n"
                    "- Dispatcher (DISPATCHER): Operational hub managers who monitor incoming booking queues, manually assign couriers, and manage delivery exceptions.\n"
                    "- Administrator (ADMIN): Holds superset privileges including user lifecycle management, merchant accreditation, and pricing rule modifications."
                )
            },
            {
                "subheading": "2.2 Use Case Specifications by Role",
                "content": (
                    "The system implements five comprehensive role-focused use case packages (Diagrams: uc-00 to uc-04):\n"
                    "- Customer Package: UC-CUST-01 (Estimate Shipping Fee), UC-CUST-02 (Book Parcel Delivery), UC-CUST-03 (Track Parcel Live), UC-CUST-04 (Download Delivery Receipt).\n"
                    "- Merchant Package: UC-MERCH-01 (Generate Developer API Keys), UC-MERCH-02 (Create Bulk Delivery Orders), UC-MERCH-03 (Configure Webhook Endpoints), UC-MERCH-04 (Audit Webhook Delivery Logs).\n"
                    "- Courier Package: UC-COUR-01 (View Active Shift Manifest), UC-COUR-02 (Update Parcel Status: Picked Up, In Transit, Out for Delivery), UC-COUR-03 (Upload Photographic Proof of Delivery), UC-COUR-04 (Record Delivery Failure Reason).\n"
                    "- Dispatcher & Admin Package: UC-DISP-01 (View Real-time Dispatch Board), UC-DISP-02 (Assign Parcel to Courier), UC-DISP-03 (Rebalance Courier Workload), UC-ADM-01 (Manage System Users & Roles), UC-ADM-02 (Configure Pricing Zones & Weight Tiers)."
                )
            }
        ]
    },
    {
        "topic_number": 3,
        "title": "System Architecture & Deployment Infrastructure",
        "sections": [
            {
                "subheading": "3.1 Microservices Architecture vs. Monolith Justification",
                "content": (
                    "A distributed microservices architecture was selected over a modular monolith due to clear structural and team requirements:\n"
                    "1. Team Independence: Facilitates simultaneous engineering by a 10-member team across separate Git branches and domains without merge conflicts.\n"
                    "2. Fault Isolation: A crash or database failure in notification-service or tracking-service does not impede parcel booking or edge authentication.\n"
                    "3. Asymmetric Scaling: Parcel booking and tracking workloads experience orders-of-magnitude higher traffic than administrative pricing configurations.\n"
                    "4. Technology Alignment: Enables specialized protocols (STOMP WebSockets for tracking, AMQP for event distribution, S3 APIs for image storage)."
                )
            },
            {
                "subheading": "3.2 Layered System Topology",
                "content": (
                    "The architecture is organized into five strict structural layers (Diagram: arch-01-layers):\n"
                    "1. Presentation Layer: React SPA rendered client-side, consuming REST APIs and WebSockets.\n"
                    "2. Edge & Security Layer: Spring Cloud Gateway acting as the single public entry point (Port 8081). Validates JWT signatures, filters internal headers, and enforces Redis token-bucket rate limits.\n"
                    "3. Microservices Application Layer: Autonomous Spring Boot services containing API controllers, domain logic, JPA repositories, and AMQP event listeners.\n"
                    "4. Persistence Layer: Independent PostgreSQL instances per service (Neon Cloud), ensuring absolute database encapsulation.\n"
                    "5. Messaging & Integration Layer: RabbitMQ message broker facilitating asynchronous pub/sub communication."
                )
            },
            {
                "subheading": "3.3 Deployment Infrastructure (Docker Compose & Kubernetes Helm)",
                "content": (
                    "Deployment is designed for dual-target execution (Diagram: arch-02-deployment):\n"
                    "- Local Environment: docker-compose.yml manages RabbitMQ, Redis, MinIO, and all backend microservices, wired through shared Docker network bridges.\n"
                    "- Production Kubernetes: An umbrella Helm chart (infra/k8s/helm/swiftdrop) defines Kubernetes Deployments, ClusterIP Services, Ingress-NGINX routing, Horizontal Pod Autoscalers (HPA), ConfigMaps for environment variables, and Kubernetes Secrets for credential isolation."
                )
            }
        ]
    },
    {
        "topic_number": 4,
        "title": "Database & Entity-Relationship Modeling",
        "sections": [
            {
                "subheading": "4.1 Database-per-Service Paradigm",
                "content": (
                    "SwiftDrop strictly implements the Database-per-Service pattern. Microservices never access each other's physical tables. "
                    "Foreign key constraints across service boundaries are eliminated; cross-domain relationships are maintained exclusively through immutable UUID identifiers. "
                    "Data consistency across boundaries is achieved through asynchronous event propagation and eventual consistency."
                )
            },
            {
                "subheading": "4.2 Schema Breakdown & 24-Entity Domain Model",
                "content": (
                    "The system spans 24 normalized tables across service databases (Diagrams: er-01-core, er-02-full, er-03-ownership):\n"
                    "- identity_service: 'users', 'roles', 'user_roles', 'refresh_tokens'.\n"
                    "- parcel_service: 'parcels', 'parcel_items', 'delivery_proofs', 'receipts'.\n"
                    "- pricing_service: 'zones', 'pricing_rules', 'weight_tiers', 'pricing_logs'.\n"
                    "- dispatch_service: 'couriers', 'courier_shifts', 'assignments', 'dispatch_queue_items'.\n"
                    "- merchant_service: 'merchants', 'merchant_api_keys', 'webhook_endpoints', 'webhook_deliveries'.\n"
                    "- tracking_service: 'tracking_events', 'checkpoints', 'live_courier_positions'.\n"
                    "- notification_service: 'notification_logs', 'notification_templates'."
                )
            }
        ]
    },
    {
        "topic_number": 5,
        "title": "Integration Architecture & Messaging Topology",
        "sections": [
            {
                "subheading": "5.1 Enterprise Integration Patterns (EIP) Applied",
                "content": (
                    "The system implements 13 recognized Enterprise Integration Patterns (Diagram: int-01-patterns-map):\n"
                    "1. API Gateway: Central entry point routing client requests.\n"
                    "2. Asynchronous Event-Driven Architecture: Decoupling state changes from downstream actions.\n"
                    "3. Publish-Subscribe Channel: Topic exchange broadcasting parcel events.\n"
                    "4. Competing Consumers: Horizontally scaled workers consuming from queues.\n"
                    "5. Dead Letter Channel: DLQ queues capturing poison-pill messages after retry limits.\n"
                    "6. Webhook Polling & Exponential Backoff: Guaranteed partner delivery.\n"
                    "7. Database per Service: Zero cross-service table joins.\n"
                    "8. Shared Contract Jar: Canonical data types packaged in swiftdrop-contracts.\n"
                    "9. Claims-based Identity Propagation: Transmitting validated user roles via JWT claims.\n"
                    "10. Idempotent Consumer: Preventing duplicate status transitions using event UUIDs.\n"
                    "11. Content-Based Router: Gateway route predicates matching path prefixes.\n"
                    "12. Request-Reply Pattern: Synchronous REST for booking and estimation.\n"
                    "13. Point-to-Point Messaging: Direct WebSocket channels for customer tracking."
                )
            },
            {
                "subheading": "5.2 RabbitMQ Event Topology & Message Contracts",
                "content": (
                    "Messaging centers on the topic exchange 'swiftdrop.events' (Diagram: int-02-event-topology).\n"
                    "Events are wrapped in a generic envelope 'EventEnvelope<T>' containing eventId, timestamp, traceId, and eventType.\n"
                    "Core event: 'ParcelStatusChangedV1' published with routing key 'parcel.status.<STATUS>'.\n"
                    "Queues bound to exchange:\n"
                    "- 'dispatch.parcel-status': Consumed by dispatch-service to update assignments.\n"
                    "- 'tracking.parcel-status': Consumed by tracking-service to update timeline and push WebSocket alerts.\n"
                    "- 'notification.parcel-status': Consumed by notification-service to trigger email alerts.\n"
                    "- 'merchant.parcel-status': Consumed by merchant-service to dispatch outbound HTTP webhooks."
                )
            }
        ]
    },
    {
        "topic_number": 6,
        "title": "Core Workflows & Sequence Analysis",
        "sections": [
            {
                "subheading": "6.1 Customer Booking & Fee Calculation Workflow",
                "content": (
                    "Sequence 1 (seq-01-create-delivery):\n"
                    "1. Customer submits pickup and delivery postal codes and package dimensions to Gateway.\n"
                    "2. Gateway forwards request to pricing-service which computes fee via Strategy Pattern.\n"
                    "3. Customer confirms order; Gateway posts payload to parcel-service.\n"
                    "4. parcel-service validates inputs, persists parcel entity with status 'CREATED', and publishes 'ParcelStatusChangedV1' event to RabbitMQ.\n"
                    "5. Response returns tracking number and 201 Created location header to client."
                )
            },
            {
                "subheading": "6.2 Courier Status Transition & Proof-of-Delivery",
                "content": (
                    "Sequence 3 (seq-03-status-update-notification):\n"
                    "1. Courier selects parcel on mobile web view, captures recipient signature/photo, and sets status to 'DELIVERED'.\n"
                    "2. dispatch-service verifies courier assignment, uploads photo to MinIO bucket, and invokes parcel-service status transition API.\n"
                    "3. parcel-service updates status, generates PDF receipt, and publishes 'parcel.status.DELIVERED' event.\n"
                    "4. tracking-service receives event, appends checkpoint, and emits STOMP frame to connected browser.\n"
                    "5. notification-service consumes event, resolves customer email via identity-service lookup, and dispatches delivery confirmation email."
                )
            },
            {
                "subheading": "6.3 OAuth 2.0 PKCE Google Authentication",
                "content": (
                    "Sequence 4 (seq-04-oauth-login):\n"
                    "1. Frontend initiates Google OAuth 2.0 flow using PKCE code challenge.\n"
                    "2. Google redirects with authorization code to frontend; frontend exchanges code via identity-service.\n"
                    "3. identity-service verifies token with Google OIDC, creates or synchronizes local user account, and mints an asymmetric RS256 JWT access token (15-min expiry) and an encrypted refresh token (7-day expiry).\n"
                    "4. All subsequent requests provide the JWT in the Authorization Bearer header, validated by Gateway against identity-service JWKS."
                )
            },
            {
                "subheading": "6.4 Parcel Lifecycle State Machine",
                "content": (
                    "The parcel entity enforces strict, non-reversible state transitions (Diagram: act-01-parcel-lifecycle):\n"
                    "CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED.\n"
                    "Terminal or exception states include FAILED (with mandatory failure reason category) and CANCELLED (only permitted prior to PICKED_UP). "
                    "Any illegal transition (e.g. CREATED -> DELIVERED) is rejected with an HTTP 422 Unprocessable Entity error."
                )
            }
        ]
    },
    {
        "topic_number": 7,
        "title": "Autonomous AI Agent Architecture & System-Wide Automation",
        "sections": [
            {
                "subheading": "7.1 Agentic Vision & Conceptual Architecture",
                "content": (
                    "Beyond traditional graphical user interfaces and manual CRUD interactions, SwiftDrop incorporates an autonomous "
                    "AI Assistant Service (services/ai-assistant-service) designed to orchestrate and execute platform functionalities automatically "
                    "through natural language intent.\n\n"
                    "Built using Spring AI, Java 21, and the Model Context Protocol (MCP), the AI agent functions as an intelligent operational "
                    "copilot and autonomous task executor. Rather than acting as a static conversational bot, the agent maintains an internal "
                    "reasoning loop: it interprets user objectives, determines the requisite operational steps, dynamically queries domain "
                    "microservices via function/tool calling, evaluates returned schemas, and carries out multi-step business transactions "
                    "across the enterprise ecosystem."
                )
            },
            {
                "subheading": "7.2 System-Wide Automation Capabilities Across Bounded Contexts",
                "content": (
                    "The AI agent is equipped with tool adapters enabling end-to-end automation across all platform modules:\n"
                    "1. Automated Parcel Booking & Dynamic Tariff Inquiries: Autonomously extracts pickup locations, destination addresses, parcel weights, and volume classifications from unstructured user prompts. Invokes pricing-service via estimateFee and parcel-service to persist bookings.\n"
                    "2. Autonomous Live Tracking & Anomaly Resolution: Uses trackParcel tool to query parcel tracking timelines, analyze status timestamps, interpret delivery exceptions, and deliver natural language summaries while shielding PII.\n"
                    "3. Autonomous Dispatch Allocation & Workload Balancing: Evaluates unassigned dispatch queues, assesses courier shift capacities, and recommends or triggers courier assignments automatically.\n"
                    "4. Automated Merchant Operations & Webhook Diagnostics: Allows merchants to generate API keys, inspect failed webhook deliveries, and trigger automated retries from dead-letter queues via conversational instructions."
                )
            },
            {
                "subheading": "7.3 Role-Based Tool Isolation & Real-Time Streaming",
                "content": (
                    "- Least-Privilege Tool Filtering: AssistantTools dynamically inspects caller authorities from validated RS256 JWT claims, ensuring only permitted tools are exposed to the LLM.\n"
                    "- Stateless Bearer Propagation: When executing tool callbacks, the agent forwards the caller's JWT bearer token to downstream microservices, preserving security boundaries.\n"
                    "- Server-Sent Events (SSE) Auditability: Streams real-time ToolEvent frames to the client interface before and after tool execution, ensuring full operational visibility and human oversight."
                )
            }
        ]
    },
    {
        "topic_number": 8,
        "title": "Design Patterns & Object-Oriented Principles",
        "sections": [
            {
                "subheading": "8.1 SOLID Principles Applied in Implementation",
                "content": (
                    "- Single Responsibility Principle (SRP): Microservices and classes maintain single concerns (e.g. JwksController serves keys, ParcelStateService manages state changes).\n"
                    "- Open-Closed Principle (OCP): Pricing engine defines PricingCalculator interface, allowing new promotional or zone pricing algorithms without modifying core calculation pipelines.\n"
                    "- Liskov Substitution Principle (LSP): File storage abstraction StorageClient allows seamless swapping between MinIO and AWS S3.\n"
                    "- Interface Segregation Principle (ISP): Spring Data JPA repositories expose precise query methods rather than bloated fat interfaces.\n"
                    "- Dependency Inversion Principle (DIP): High-level service classes depend on abstractions injected via Spring IoC constructor injection."
                )
            },
            {
                "subheading": "8.2 GoF & Enterprise Design Patterns",
                "content": (
                    "1. Strategy Pattern: pricing-service uses dynamic pricing strategies (StandardPricingStrategy, ExpressPricingStrategy, BulkMerchantStrategy) selected at runtime (Diagram: pat-01-pricing-strategy).\n"
                    "2. Repository Pattern: Spring Data JPA isolates persistence operations behind type-safe interfaces.\n"
                    "3. Observer / Pub-Sub Pattern: RabbitMQ message broker distributes state transitions across independent microservice observers.\n"
                    "4. Front Controller / API Gateway Pattern: Centralizes cross-cutting edge concerns at Spring Cloud Gateway.\n"
                    "5. Builder & Factory Patterns: Used in event envelope construction and DTO mapping."
                )
            }
        ]
    },
    {
        "topic_number": 9,
        "title": "Security Architecture & Quality Attributes",
        "sections": [
            {
                "subheading": "9.1 Cryptographic Auth & JWKS Infrastructure",
                "content": (
                    "SwiftDrop employs asymmetric cryptographic verification. The identity-service maintains an RSA key pair, exposing public keys via standard RFC 7517 JWKS (/api/v1/auth/jwks.json). "
                    "Downstream microservices and the API Gateway act as OAuth 2.0 Resource Servers validating tokens locally via the cached public JWKS without making synchronous database verification calls."
                )
            },
            {
                "subheading": "9.2 Defense-in-Depth & Non-Functional Attributes",
                "content": (
                    "- Edge Rate Limiting: Redis token-bucket filter limits public tracking endpoints to 10 requests/minute per IP, mitigating scraping and DoS.\n"
                    "- Sensitive Data Protection: Passwords hashed with BCrypt (strength 12). Personally Identifiable Information (PII) such as recipient phone numbers is redacted from log output.\n"
                    "- MinIO Secure Access: Delivery photos are kept in private object storage; viewing links are generated as time-limited presigned URLs (15-minute expiry).\n"
                    "- Fault Tolerance: RabbitMQ dead-letter exchanges retain unroutable messages; Spring retry handles transient database disconnects."
                )
            }
        ]
    },
    {
        "topic_number": 10,
        "title": "Quality Assurance & Testing Strategy",
        "sections": [
            {
                "subheading": "10.1 Multi-Tiered Verification Hierarchy",
                "content": (
                    "- Unit Testing: JUnit 5 and Mockito verifying isolated business logic, pricing formula accuracy, and state machine transitions.\n"
                    "- Integration Testing: Testcontainers spinning up real, ephemeral PostgreSQL and RabbitMQ Docker containers to test end-to-end messaging and repository operations.\n"
                    "- Authorization Testing: RbacAuthorizationIntegrationTest verifies security matrices (e.g. verifying a Customer cannot invoke courier assignment routes, and a Courier cannot view another courier's workload).\n"
                    "- Contract & E2E Testing: MockMvc API contract verification and Playwright end-to-end browser workflows."
                )
            },
            {
                "subheading": "10.2 Code Coverage & Known Architectural Gaps",
                "content": (
                    "Automated CI builds via GitHub Actions enforce test pass criteria and generate JaCoCo coverage reports. "
                    "In adherence to rigorous engineering practice, six architectural edge cases were documented during verification:\n"
                    "1. JWT claim naming convention (merchantId vs merchant_id consistency across services).\n"
                    "2. Gateway API-key passthrough configuration for merchant external integrations.\n"
                    "3. Asynchronous internal service lookup authorization hardening."
                )
            }
        ]
    },
    {
        "topic_number": 11,
        "title": "Team Contributions & Conclusion",
        "sections": [
            {
                "subheading": "11.1 Ten-Member Contribution Matrix",
                "content": (
                    "The workload was evenly distributed across the 10 team members, mapping each engineer to a microservice, Git feature branch, and architectural deliverable:\n"
                    "- M1 (Amandi Arangala): Identity & Auth Service, Flyway migrations, Google OAuth, JWKS.\n"
                    "- M2 (Kevin): API Gateway, Netty filters, Redis rate limiting, Docker Compose infrastructure.\n"
                    "- M3 (Pavindu): Pricing Service, zone calculation algorithms, Strategy pattern implementation.\n"
                    "- M4 (Praveena Ishadi): Parcel Service, booking engine, state machine, receipt generation.\n"
                    "- M5 (Dimesh Methsara): Dispatch Service, courier scheduling, assignment logic, MinIO POD upload.\n"
                    "- M6 (Charithma Dassanayake): Merchant Service, API key management, webhook retry engine.\n"
                    "- M7 (Maheesha Paboth): Live Tracking Service, WebSocket STOMP handlers, SSE fallback.\n"
                    "- M8 (Thiranjaya): Notification Service, RabbitMQ consumer, HTML email templates via Mailpit.\n"
                    "- M9 (Tharusha Prabhashwara): AI Assistant Service, Spring AI, MCP tools for parcel lookup.\n"
                    "- M10 (Thanoj Buddhima): Frontend Architecture (React 18/TypeScript), responsive UI design, system documentation pack."
                )
            },
            {
                "subheading": "11.2 Conclusion & Future Roadmap",
                "content": (
                    "The SwiftDrop project demonstrates a resilient, production-grade enterprise logistics platform conforming to modern microservice patterns. "
                    "Future extensions include multi-agent swarms for autonomous dispatching, native mobile applications with GPS background telemetry, and automated cash-on-delivery reconciliation."
                )
            }
        ]
    }
]

if __name__ == "__main__":
    print(f"Loaded {len(REPORT_TOPICS)} topics for {REPORT_METADATA['title']}")
    for topic in REPORT_TOPICS:
        print(f"Topic {topic['topic_number']}: {topic['title']} ({len(topic['sections'])} sections)")

