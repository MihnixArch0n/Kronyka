# Kronyka: Nền Tảng Đấu Giá Hai Cơ Chế

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-green.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Exposed](https://img.shields.io/badge/Exposed-1.5.0-purple.svg)](https://github.com/JetBrains/Exposed)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg?logo=postgresql)](https://www.postgresql.org)
[![Java 25](https://img.shields.io/badge/JDK-25%20(Temurin)-orange.svg?logo=openjdk)](https://adoptium.net)
[![Docker](https://img.shields.io/badge/Container-Docker%20%7C%20Podman-blue.svg?logo=docker)](https://www.docker.com)

🌐 **Ngôn ngữ**: [🇻🇳 Tiếng Việt (Hiện tại)](README.vi.md) | [English](README.md)

**Kronyka** là dịch vụ backend cho sàn đấu giá, viết bằng **Kotlin 2.4**, **Spring Boot 4.1** (chạy Virtual Threads của Java 25) và **JetBrains Exposed SQL DSL**, tổ chức theo mô hình **Clean Architecture**.

Repo này là phần việc của **Pha 1**: hoàn thiện nghiệp vụ đấu giá cơ bản, chạy đúng logic, và giữ nguyên một số hạn chế ban đầu để các nhóm **Pha 2** tiếp quản, đo đạc và cải tiến hiệu năng.

---

## 1. Bài Toán & Nghiệp Vụ Đấu Giá

Đấu giá truyền thống (ai trả cao hơn thì đè giá lên) thường gặp 2 tật xấu:
1. **Canh giây cuối để bắn tỉa (Bid Sniping)**: Dùng bot đợi 1-2 giây cuối mới đặt giá, người mua thật không kịp trở tay.
2. **Cài chim mồi nâng giá (Shill Bidding)**: Người bán hoặc người quen tự vào thổi giá vì thấy được giá của người khác.

Để giải quyết, Kronyka làm 2 kiểu đấu giá khác nhau:

| Kiểu đấu giá | Cách hoạt động | Cách tính giá & người thắng | Xử lý khi có nhiều người cùng mua |
|---|---|---|---|
| **Vickrey (Đấu giá kín)** | Chia làm 3 bước: nộp giá bí mật (`COMMIT`) $\to$ mở giá (`REVEAL`) $\to$ chốt phiên (`SETTLED`). | Người mua giấu giá thật bằng mã băm SHA-256 kèm tiền cọc. Hết giờ, ai trả cao nhất sẽ thắng, nhưng **chỉ phải trả mức giá của người cao thứ nhì**. Nhờ vậy, người mua cứ trả đúng giá trị mình muốn mà không sợ bị hớ. | Hệ thống có tiến trình chạy ngầm quét định kỳ để tự chốt kết quả và trả lại tiền cọc. |
| **Đấu giá Hà Lan (Dutch)** | Giá khởi điểm ở mức cao, sau đó cứ sau một khoảng thời gian thì tự động giảm bớt một lượng tiền. | Giá được tính bằng công thức toán theo đồng hồ hệ thống. Người đầu tiên chấp nhận giá hiện tại và bấm mua sẽ thắng luôn. | Dùng khóa lạc quan (kiểm tra cột `version`). Người gửi lệnh thành công đầu tiên sẽ mua được; người đến sau bị từ chối với lỗi `409 Conflict`. |

> 📖 **Xem chi tiết công thức và sơ đồ luồng chạy**: [docs/DOMAIN_ENGINES.vi.md](docs/DOMAIN_ENGINES.vi.md) và [docs/ARCHITECTURE.vi.md](docs/ARCHITECTURE.vi.md).

---

## 2. Tổ Chức Mã Nguồn & Quy Định Code

Code được chia theo mô hình Clean Architecture (các tầng phụ thuộc hướng vào trong):

```
src/main/kotlin/example/kronyka/
├── domain/                  # 1. NGHIỆP VỤ CỐT LÕI (Kotlin thuần, KHÔNG dính framework hay thư viện ngoài)
│   ├── model/               #    Dữ liệu nghiệp vụ (Auction, Bid, User)
│   ├── repository/          #    Cổng giao tiếp dữ liệu (Interface thuần Kotlin)
│   └── service/             #    Xử lý logic đấu giá (Vickrey, Dutch)
├── presentation/            # 2. TẦNG GIAO TIẾP (Nhận request HTTP)
│   ├── controller/          #    REST Controller (Auction, Auth)
│   ├── dto/                 #    Dữ liệu request/response và validate dữ liệu
│   └── advice/              #    Bắt và trả về lỗi chuẩn (@RestControllerAdvice)
└── infrastructure/          # 3. TẦNG HẠ TẦNG (Kết nối bên ngoài)
    ├── persistence/         #    Bảng cơ sở dữ liệu và triển khai truy vấn bằng Exposed SQL DSL
    ├── security/            #    Xác thực đăng nhập bằng JWT và Spring Security
    ├── scheduler/           #    Tiến trình chạy nền tự động chốt đấu giá
    └── config/              #    Cấu hình ứng dụng và tài liệu API
```

### 3 quy tắc quan trọng khi viết code:
1. **Thư mục `domain/` phải sạch**: Viết bằng Kotlin thuần. Không dùng annotation của Spring (`@Service`, `@Autowired`), không dính thư viện Exposed hay JDBC.
2. **Chỉ dùng Exposed SQL DSL**: Mọi câu lệnh gọi DB đều dùng object `Table` và cú pháp DSL (`selectAll`, `insert`, `upsert`, `update`). Không dùng Exposed DAO (`EntityClass`, `IntEntity`).
3. **Tiền tệ (VNĐ) luôn dùng số nguyên `Long`**: Tuyệt đối không dùng số thực (`Double`, `Float`) để tránh sai lệch số học khi tính tiền.

---

## 3. Các Vấn Đề Dễ Thấy Để Pha 2 Cải Tiến

Hệ thống Pha 1 mới dừng ở mức chạy đúng chức năng, cố tình để lại các vấn đề sau để Pha 2 vào đo đạc và tối ưu:

1. **Đọc trực tiếp từ DB**: Mỗi lần xem danh sách đấu giá hay xem giá đấu giá Hà Lan đều gọi thẳng vào PostgreSQL. Chưa có bộ nhớ đệm (Redis) để giảm tải.
2. **Phải gọi API liên tục để cập nhật giá (Polling)**: Phía client phải tự gọi API liên tục để xem giá giảm, chưa dùng WebSocket hay Server-Sent Events để server tự đẩy giá xuống.
3. **Dễ xung đột khi nhiều người cùng bấm mua**: Đấu giá Hà Lan dùng kiểm tra `version`. Khi nhiều người cùng ấn mua trong 1 giây, chỉ 1 người được, những người còn lại sẽ bị trả về lỗi `409 Conflict`.
4. **Tiến trình chốt đơn chỉ chạy được trên 1 máy**: Bộ quét chốt đơn Vickrey chạy trực tiếp bằng `@Scheduled` trong app, chưa có cơ chế khóa phân tán (như ShedLock hoặc Redis). Nếu bật nhiều server cùng lúc thì sẽ bị quét trùng nhau.
5. **Chưa có giới hạn số lượng request**: Chưa cài đặt Rate Limit, nếu có lượng người dùng hoặc bot gửi request ồ ạt thì DB có thể bị quá tải kết nối.

---

## 4. Yêu Cầu Kỹ Thuật & Hướng Dẫn Chạy

### 4.1. Cần chuẩn bị
- **JDK**: Eclipse Temurin 25 (Java 25).
- **Docker hoặc Podman**: Đã cài Docker Compose hoặc podman-compose.
- **Cơ sở dữ liệu**: PostgreSQL 18 (đã có sẵn trong file `compose.yaml`).

### 4.2. Chạy nhanh bằng Docker / Podman (Khuyên dùng)
Dự án có sẵn file `Containerfile` dùng tính năng AOT Cache của Java 25 để app khởi động nhanh hơn:

```bash
# Build và bật cả PostgreSQL lẫn app
docker compose up --build -d

# Xem log chạy của app
docker compose logs -f app

# Kiểm tra trạng thái container
docker compose ps
```
- Link ứng dụng: `http://localhost:8080`
- Link kiểm tra sức khỏe app: `http://localhost:8080/actuator/health`

### 4.3. Chạy khi code ở Local
```bash
# 1. Chỉ bật container PostgreSQL
docker compose up -d postgres

# 2. Chạy ứng dụng Spring Boot (cần máy đã cài JDK 25)
./gradlew bootRun

# 3. Chạy toàn bộ test
./gradlew test
```

> 📖 **Xem hướng dẫn chi tiết về cấu hình biến môi trường và container**: [docs/DEPLOYMENT.vi.md](docs/DEPLOYMENT.vi.md).

---

## 5. Đặc Tả API & Danh Mục Tài Liệu

### 5.1. Xem tài liệu API
- **Xem trực tiếp trên web (Scalar UI)**: Mở `http://localhost:8080/scalar` khi app đang chạy (hoặc lấy JSON tại `http://localhost:8080/v3/api-docs`).
- **Tải file API (.json / .yaml)**: [GitHub Releases](https://github.com/MihnixArch0n/Kronyka/releases).

### 5.2. Các tài liệu chi tiết khác
- 🏛️ **[Kiến trúc hệ thống](docs/ARCHITECTURE.vi.md)**: Chi tiết phân tầng và sơ đồ luồng gọi nhau.
- 🗄️ **[Cơ sở dữ liệu](docs/DATABASE_SCHEMA.vi.md)**: Cấu trúc các bảng trong PostgreSQL, khóa chính, khóa ngoại.
- ⚙️ **[Chi tiết nghiệp vụ đấu giá](docs/DOMAIN_ENGINES.vi.md)**: Công thức tính toán và các bước xác thực giá thầu.
- 🚀 **[Hướng dẫn kỹ thuật deploy](docs/DEPLOYMENT.vi.md)**: Giải thích chi tiết Containerfile, cách app chạy và danh sách biến môi trường.

---

## 6. Đo Lường Hiệu Năng & Thử Tải (Phase 1 Baseline)

### 6.1. Môi trường kiểm thử
- **Phần cứng & Máy chủ**: Kaggle Cloud Container - 4 vCPUs, 30 GB RAM.
- **Môi trường chạy**: Eclipse Temurin OpenJDK 25 (Java 25 bật Virtual Threads), Spring Boot 4.1.1, PostgreSQL 18.
- **Database Driver**: JetBrains Exposed 1.5.0 SQL DSL với HikariCP connection pool mặc định.

---

### 6.2. So sánh hiệu năng tổng thể hệ thống (20 vs 100 vs 500 vs 1000 VUs)

| Mức tải | Người dùng ảo (VUs) | Tổng số request (1 phút) | Thông lượng (RPS) | Độ trễ Median | Độ trễ Trung bình | Độ trễ P95 | Độ trễ P99 | Tỷ lệ Assertion thành công |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Baseline Load** | **20 VUs** | 6.300 | **101.6 req/s** | 30.61 ms | 162.84 ms | **721.54 ms** | 1.15 s | **100.00%** (3.780 / 3.780) |
| **Normal High Load** | **100 VUs** | 7.556 | **118.8 req/s** | 370.00 ms | 796.85 ms | **2.95 s** | 5.33 s | **100.00%** (4.532 / 4.532) |
| **Stress Load** | **500 VUs** | 8.498 | **115.5 req/s** | 466.07 ms | 4.11 s | **20.68 s** | 40.41 s | **99.67%** (4.964 / 4.980) |
| **Saturation Limit** | **1000 VUs** | 5.382 | **59.8 req/s** | 1.29 s | 13.72 s | **59.78 s** | 60.00 s | **84.96%** (2.786 / 3.279) |

---

### 6.3. Chi tiết độ trễ từng Endpoint API

| # | Endpoint | 20 VUs (Avg / P95) | 100 VUs (Avg / P95) | 500 VUs (Avg / P95) | 1000 VUs (Avg / P95) |
| :---: | :--- | :---: | :---: | :---: | :---: |
| **1** | `/api/auth/register` | 634.3 ms / 1.34 s | 2.01 s / 6.64 s | 17.38 s / 49.17 s | 33.08 s / 59.91 s |
| **2** | `/api/auth/login` | 408.2 ms / 921.1 ms | 1.07 s / 4.14 s | 7.80 s / 30.61 s | 16.05 s / 59.40 s |
| **3** | `/api/auth/me` | 113.1 ms / 366.6 ms | 551.2 ms / 2.57 s | 2.73 s / 14.16 s | 5.79 s / 30.03 s |
| **4** | `/api/auctions` | 132.5 ms / 435.9 ms | 699.5 ms / 2.58 s | 3.97 s / 16.26 s | 6.97 s / 30.14 s |
| **5** | `/api/auctions` (Vickrey) | 72.6 ms / 257.3 ms | 619.6 ms / 2.41 s | 3.36 s / 15.38 s | 6.32 s / 30.26 s |
| **6** | `/api/auctions/{id}` | 54.0 ms / 210.4 ms | 663.6 ms / 2.50 s | 2.67 s / 12.98 s | 7.26 s / 30.37 s |
| **7** | `/api/auctions/{id}/commit` | 41.3 ms / 131.1 ms | 549.7 ms / 2.16 s | 2.12 s / 9.46 s | 5.94 s / 30.17 s |
| **8** | `/api/auctions/{id}/reveal` | 21.6 ms / 72.2 ms | 642.7 ms / 2.80 s | 2.44 s / 11.51 s | 5.00 s / 22.71 s |
| **9** | `/api/auctions/{id}` (Hủy) | 22.0 ms / 55.2 ms | 641.4 ms / 2.75 s | 1.08 s / 4.73 s | 10.80 s / 30.56 s |
| **10**| `/api/auctions/dutch` | 15.8 ms / 34.9 ms | 640.2 ms / 2.34 s | 2.49 s / 9.19 s | 12.95 s / 30.23 s |
| **11**| `/api/auctions/{id}/price` | **8.1 ms / 20.9 ms** | **526.9 ms / 2.44 s** | **1.36 s / 5.89 s** | **2.24 s / 10.60 s** |
| **12**| `/api/auctions/{id}/buy-dutch` | **14.5 ms / 30.5 ms** | **474.7 ms / 1.98 s** | **1.17 s / 5.12 s** | **2.72 s / 12.87 s** |
