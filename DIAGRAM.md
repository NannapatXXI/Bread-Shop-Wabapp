# BreadShop XXI — Project Diagram

---

## 1. System Overview (ภาพรวมทั้งระบบ)

```mermaid
graph TB
    subgraph CLIENT["🖥️  Frontend — Next.js (port 3000)"]
        subgraph AUTH_PAGES["(auth) — ไม่ต้อง login"]
            L[Login]
            R[Register]
            FP[Forgot Password]
            RP[Reset Password]
        end

        subgraph USER_PAGES["(user) — ต้อง login"]
            HM[Home]
            PR[Product]
            PF[Profile]
            HI[History]
        end

        subgraph ADMIN_PAGES["admin — ต้องเป็น ADMIN"]
            DB[Dashboard]
            MP[Manage Product]
            AP[Add Product]
            EP[Edit Product]
        end

        AX["axios (lib/api.js)\n+ interceptor auto-unwrap ApiResponse\n+ interceptor auto-refresh token"]
    end

    subgraph SERVER["☕  Backend — Spring Boot (port 8080)"]
        subgraph CONTROLLERS["Controllers"]
            AC[AuthController\n/api/v1/auth/**]
            ADC[AdminController\n/api/v1/admin/**]
            PC[ProductController\n/api/v1/products/**]
            OC[OrderController\n/api/orders/**]
            UAC[UserAddressController\n/api/users/**]
            DC[DashboardController\n/api/v1/admin/dashboard/**]
        end

        subgraph SECURITY["Security Layer"]
            JF[JwtAuthenticationFilter]
            SC[SecurityConfig\nROLE_USER / ROLE_ADMIN]
        end

        subgraph SERVICES["Services"]
            AS[AuthService]
            PS[ProductService]
            OS[OrderService]
            DS[DashboardService]
            UAS[UserAddressService]
            JS[JwtService]
            RTS[RefreshTokenService]
            OTP[OtpService]
            MS[MailService]
        end

        subgraph REPOSITORIES["Repositories — JPA"]
            UR[UserRepository]
            PRR[ProductRepository]
            OR[OrderRepository]
            OLR[OrderLineRepository]
            UAR[UserAddressRepository]
            RTR[RefreshTokenRepository]
            EOTP[EmailOtpRepository]
        end

        AR["ApiResponse wrapper\n{ success, message, data }"]
        GEH[GlobalExceptionHandler]
        FW[Flyway Migration\nV1__initial_schema.sql]
    end

    subgraph DATABASE["🗄️  MySQL (port 3306) — breadProject"]
        TB1[(usersapp)]
        TB2[(products)]
        TB3[(orders)]
        TB4[(order_lines)]
        TB5[(user_addresses)]
        TB6[(promotions)]
        TB7[(refresh_tokens)]
        TB8[(email_otp)]
    end

    subgraph EXTERNAL["🌐  External Services"]
        G[Google OAuth 2.0]
        SM[Gmail SMTP]
    end

    CLIENT -->|HTTP + Cookie JWT| SERVER
    AX -.->|ทุก request ผ่านที่นี่| CONTROLLERS
    JF -->|ตรวจ JWT ทุก request| SC
    CONTROLLERS --> SERVICES
    SERVICES --> REPOSITORIES
    REPOSITORIES --> DATABASE
    FW -->|migrate schema ตอน start| DATABASE
    AS -->|Google Login| G
    OTP -->|ส่ง OTP email| SM
```

---

## 2. Authentication Flow (การ Login)

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant AC as AuthController
    participant AS as AuthService
    participant JS as JwtService
    participant RTS as RefreshTokenService
    participant DB as MySQL

    Note over FE,DB: ── Login ──

    FE->>AC: POST /api/v1/auth/login { email, password }
    AC->>AS: loginUser(request)
    AS->>DB: findByEmail(email)
    DB-->>AS: User
    AS->>AS: BCrypt.verify(password)
    AS->>JS: generateAccessToken(user) → 15 นาที
    AS->>RTS: createRefreshToken(user) → 7 วัน
    RTS->>DB: save refresh_token
    AS-->>AC: { accessToken, refreshToken }
    AC-->>FE: 200 OK\nSet-Cookie: access_token (httpOnly, 15min)\nSet-Cookie: refresh_token (httpOnly, 7d)\nbody: ApiResponse { success:true }

    Note over FE,DB: ── เรียก API ปกติ ──

    FE->>AC: GET /api/v1/auth/me\n(Cookie: access_token อัตโนมัติ)
    AC->>JS: validateToken(access_token)
    JS-->>AC: UserDetails
    AC-->>FE: ApiResponse { data: { id, email, username, roles } }

    Note over FE,DB: ── Token หมดอายุ (401) ──

    FE->>AC: GET /api/v1/products → 401
    AC-->>FE: 401 Unauthorized
    FE->>AC: POST /api/v1/auth/refresh\n(Cookie: refresh_token)
    AC->>RTS: validate(refresh_token)
    RTS->>DB: findByToken
    DB-->>RTS: RefreshToken (ยังไม่ expired)
    AC->>JS: generateAccessToken(user)
    AC-->>FE: Set-Cookie: access_token (ใหม่)\nApiResponse { success:true }
    FE->>AC: GET /api/v1/products (retry ด้วย token ใหม่)
    AC-->>FE: ApiResponse { data: [...] }

    Note over FE,DB: ── Logout ──

    FE->>AC: POST /api/v1/auth/logout
    AC->>RTS: revoke(refresh_token) → DB
    AC-->>FE: Set-Cookie: access_token (maxAge=0 ลบ)\nSet-Cookie: refresh_token (maxAge=0 ลบ)
```

---

## 3. Request Flow (ทุก request วิ่งผ่านอะไรบ้าง)

```mermaid
flowchart TD
    A([Browser]) -->|HTTP Request + Cookie| B

    subgraph FILTER["Security Filter Chain"]
        B[JwtAuthenticationFilter\nดึง access_token จาก Cookie]
        B -->|valid token| C[SecurityContext\nเก็บ UserDetails ไว้]
        B -->|ไม่มี token / expired| D[401 Unauthorized]
    end

    C --> E

    subgraph LAYER["Spring MVC Layers"]
        E[Controller\nรับ request, ส่ง response\nห้ามมี logic ที่นี่]
        E --> F[Service\nlogic ทางธุรกิจ\nvalidation, calculation]
        F --> G[Repository\nJPA query ไปที่ DB]
        G --> H[(MySQL)]
        G -->|Entity| F
        F -->|DTO| E
        E -->|ApiResponse wraps DTO| I[Response]
    end

    subgraph ERR["Error Handling"]
        J[GlobalExceptionHandler\n@RestControllerAdvice]
    end

    D --> ERR
    F -->|throw Exception| ERR
    ERR -->|ApiResponse.error| I

    I -->|JSON| A
```

---

## 4. Database Schema (ความสัมพันธ์ตาราง)

```mermaid
erDiagram
    usersapp {
        int id PK
        string username
        string email
        string password
        string provider
        string role
        datetime created_at
        datetime updated_at
        datetime last_login_at
    }

    products {
        bigint id PK
        string name
        decimal price
        int stock
        string description
        string image_url
        string category
        date expiry_date
        datetime created_at
        datetime updated_at
    }

    user_addresses {
        int id PK
        int user_id FK
        string name
        string recipient_name
        string phone
        text address
        string province
        string district
        string subdistrict
        string postcode
        boolean is_default
    }

    promotions {
        int id PK
        string code
        string discount_type
        decimal discount_value
        decimal min_order_amount
        int usage_limit
        int used_count
        datetime expired_at
        boolean is_active
    }

    orders {
        int id PK
        int user_id FK
        int address_id FK
        int promotion_id FK
        string order_no
        string shipping_name
        string shipping_address
        decimal subtotal
        decimal discount_amount
        decimal total_amount
        string status
        string tracking_no
        datetime created_at
    }

    order_lines {
        int id PK
        int order_id FK
        bigint product_id FK
        string product_name
        decimal unit_price
        int quantity
        decimal total_price
    }

    refresh_tokens {
        bigint id PK
        int user_id FK
        string token
        datetime expires_at
        boolean revoked
    }

    email_otp {
        bigint id PK
        string email
        string otp_hash
        string purpose
        datetime expires_at
        boolean used
        boolean verified
        string token
    }

    usersapp        ||--o{ user_addresses  : "มีได้หลายที่อยู่"
    usersapp        ||--o{ orders          : "สั่งซื้อได้หลาย order"
    usersapp        ||--o{ refresh_tokens  : "มีได้หลาย token"
    user_addresses  ||--o{ orders          : "ใช้เป็นที่จัดส่ง"
    promotions      ||--o{ orders          : "ใช้โปรโมชั่นได้"
    orders          ||--|{ order_lines     : "มีอย่างน้อย 1 รายการ"
    products        ||--o{ order_lines     : "ถูกสั่งใน order line"
```

---

## 5. Frontend Page Structure (โครงสร้างหน้าเว็บ)

```mermaid
flowchart TD
    ROOT["/  →  redirect"] --> LOGIN

    subgraph AUTH["(auth) — ไม่ต้อง login"]
        LOGIN["/login"]
        REGISTER["/register"]
        FORGOT["/forgot-password"]
        VERIFY["/verify-email"]
        RESET["/reset-password"]
        LOGIN --> REGISTER
        FORGOT --> VERIFY --> RESET --> LOGIN
    end

    subgraph USER["(user) — ต้อง login (ROLE_USER)"]
        HOME["/home"]
        PRODUCT["/product\nดูสินค้า + ค้นหา Boyer-Moore"]
        PROFILE["/profile\nแก้ข้อมูล + จัดการที่อยู่ + Map Picker"]
        HISTORY["/history\nประวัติ order"]
    end

    subgraph ADMIN["admin — ต้อง login (ROLE_ADMIN)"]
        DASHBOARD["/admin/dashbord\nยอดขาย + กราฟ + top products"]
        MANAGE["/admin/products\nตาราง + ค้นหา + pagination 5/page"]
        ADD["/admin/products/addproduct"]
        EDIT["/admin/products/editproduct/id"]
        MANAGE --> ADD
        MANAGE --> EDIT
    end

    LOGIN -->|login success| HOME
    HOME --> PRODUCT
    HOME --> PROFILE
    HOME --> HISTORY
    LOGIN -->|ROLE_ADMIN| DASHBOARD
    DASHBOARD --> MANAGE

    subgraph AUTHCTX["AuthContext (React Context)"]
        CTX["user { id, email, username, roles }\nloading, logout()"]
    end

    CTX -.->|ทุกหน้าอ่านได้| USER
    CTX -.->|ทุกหน้าอ่านได้| ADMIN
```
