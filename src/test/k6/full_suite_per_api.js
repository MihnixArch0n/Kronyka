k6_script = """
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import crypto from 'k6/crypto';
import { Trend, Counter } from 'k6/metrics';

// ====================================================
// 1. TẠO METRICS ĐO ĐẠC RIÊNG CHO TỪNG API (12 ENDPOINTS)
// ====================================================
const rt_auth_register = new Trend('api_1_auth_register_rt', true);
const rt_auth_login    = new Trend('api_2_auth_login_rt', true);
const rt_auth_me       = new Trend('api_3_auth_me_rt', true);
const rt_auctions_list = new Trend('api_4_auctions_list_rt', true);
const rt_vickrey_create= new Trend('api_5_vickrey_create_rt', true);
const rt_auction_detail= new Trend('api_6_auction_detail_rt', true);
const rt_vickrey_commit= new Trend('api_7_vickrey_commit_rt', true);
const rt_vickrey_reveal= new Trend('api_8_vickrey_reveal_rt', true);
const rt_auction_cancel= new Trend('api_9_auction_cancel_rt', true);
const rt_dutch_create  = new Trend('api_10_dutch_create_rt', true);
const rt_dutch_price   = new Trend('api_11_dutch_price_rt', true);
const rt_dutch_buy     = new Trend('api_12_dutch_buy_rt', true);

// ====================================================
// 2. CẤU HÌNH TẢI DỰA TRÊN THAM SỐ MÔI TRƯỜNG
// ====================================================
const TARGET_VUS = parseInt(__ENV.VUS || '20');
const DURATION = __ENV.DURATION || '1m';

export const options = {
  vus: TARGET_VUS,
  duration: DURATION,
  // Không đặt threshold chặn exit code để tránh bash bị fail khi tải nặng
  thresholds: {},
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };
}

export default function () {
  const vuId = __VU;
  const iterId = __ITER;
  const timestamp = Date.now();
  const username = `u_${vuId}_${iterId}_${timestamp}`;
  const password = 'StrongPassword123@';

  let token = null;
  let buyerToken = null;

  // ----------------------------------------------------
  // GROUP 1: AUTHENTICATION APIs
  // ----------------------------------------------------
  // 1. [POST] /api/auth/register
  const regPayload = JSON.stringify({
    username: username,
    password: password,
    full_name: `User ${username}`,
  });
  const regRes = http.post(`${BASE_URL}/auth/register`, regPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  rt_auth_register.add(regRes.timings.duration);
  check(regRes, { 'Register 201': (r) => r.status === 201 });

  // 2. [POST] /api/auth/login
  const loginPayload = JSON.stringify({ username, password });
  const loginRes = http.post(`${BASE_URL}/auth/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  rt_auth_login.add(loginRes.timings.duration);
  const loginOk = check(loginRes, { 'Login 200': (r) => r.status === 200 });

  if (loginOk) {
    token = loginRes.json('result.access_token');
  }

  // Đăng ký buyer phụ để test mua đấu giá
  const buyerName = `b_${vuId}_${iterId}_${timestamp}`;
  http.post(`${BASE_URL}/auth/register`, JSON.stringify({
    username: buyerName,
    password: password,
    full_name: `Buyer ${buyerName}`,
  }), { headers: { 'Content-Type': 'application/json' } });

  const buyerLoginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: buyerName,
    password: password,
  }), { headers: { 'Content-Type': 'application/json' } });

  if (buyerLoginRes.status === 200) {
    buyerToken = buyerLoginRes.json('result.access_token');
  }

  // 3. [GET] /api/auth/me
  if (token) {
    const meRes = http.get(`${BASE_URL}/auth/me`, { headers: authHeaders(token) });
    rt_auth_me.add(meRes.timings.duration);
    check(meRes, { 'Get Me 200': (r) => r.status === 200 });
  }

  if (!token) return;

  // ----------------------------------------------------
  // GROUP 2: PUBLIC BROWSING APIs
  // ----------------------------------------------------
  // 4. [GET] /api/auctions (Danh sách phân trang)
  const listRes = http.get(`${BASE_URL}/auctions?page=0&size=10`);
  rt_auctions_list.add(listRes.timings.duration);
  check(listRes, { 'List Auctions 200': (r) => r.status === 200 });

  // ----------------------------------------------------
  // GROUP 3: VICKREY AUCTION APIs
  // ----------------------------------------------------
  const now = new Date();
  const startTime = new Date(now.getTime() - 2000).toISOString();
  const commitEndTime = new Date(now.getTime() + 600000).toISOString();
  const revealEndTime = new Date(now.getTime() + 1200000).toISOString();

  // 5. [POST] /api/auctions (Tạo Vickrey auction)
  const vickreyPayload = JSON.stringify({
    title: `Vickrey Item ${username}`,
    reserve_price: 500000,
    start_time: startTime,
    commit_end_time: commitEndTime,
    reveal_end_time: revealEndTime,
  });
  const createVickreyRes = http.post(`${BASE_URL}/auctions`, vickreyPayload, {
    headers: authHeaders(token),
  });
  rt_vickrey_create.add(createVickreyRes.timings.duration);

  if (createVickreyRes.status === 201) {
    const auctionId = createVickreyRes.json('result.auction_id');

    // 6. [GET] /api/auctions/{id} (Chi tiết đấu giá)
    const detailRes = http.get(`${BASE_URL}/auctions/${auctionId}`);
    rt_auction_detail.add(detailRes.timings.duration);
    check(detailRes, { 'Detail 200': (r) => r.status === 200 });

    // 7. [POST] /api/auctions/{id}/commit (Commit blind bid)
    const actualBid = 600000;
    const secretSalt = `salt_${timestamp}`;
    const blindHash = crypto.sha256(`${actualBid}_${secretSalt}`, 'hex');

    const commitPayload = JSON.stringify({
      blind_hash: blindHash,
      lockup_deposit: 800000,
    });
    const bidderHeader = buyerToken ? authHeaders(buyerToken) : authHeaders(token);
    const commitRes = http.post(`${BASE_URL}/auctions/${auctionId}/commit`, commitPayload, {
      headers: bidderHeader,
    });
    rt_vickrey_commit.add(commitRes.timings.duration);
    check(commitRes, { 'Commit 201': (r) => r.status === 201 });

    // 8. [POST] /api/auctions/{id}/reveal (Reveal bid)
    const revealPayload = JSON.stringify({
      actual_bid: actualBid,
      secret_salt: secretSalt,
    });
    const revealRes = http.post(`${BASE_URL}/auctions/${auctionId}/reveal`, revealPayload, {
      headers: bidderHeader,
    });
    rt_vickrey_reveal.add(revealRes.timings.duration);
  }

  // 9. [DELETE] /api/auctions/{id} (Hủy đấu giá)
  const cancelPayload = JSON.stringify({
    title: `Cancel Item ${username}`,
    reserve_price: 100000,
    start_time: startTime,
    commit_end_time: commitEndTime,
    reveal_end_time: revealEndTime,
  });
  const cancelCreateRes = http.post(`${BASE_URL}/auctions`, cancelPayload, {
    headers: authHeaders(token),
  });
  if (cancelCreateRes.status === 201) {
    const cancelAuctionId = cancelCreateRes.json('result.auction_id');
    const deleteRes = http.del(`${BASE_URL}/auctions/${cancelAuctionId}`, null, {
      headers: authHeaders(token),
    });
    rt_auction_cancel.add(deleteRes.timings.duration);
    check(deleteRes, { 'Cancel 200': (r) => r.status === 200 });
  }

  // ----------------------------------------------------
  // GROUP 4: DUTCH AUCTION APIs
  // ----------------------------------------------------
  const endTime = new Date(now.getTime() + 3600000).toISOString();

  // 10. [POST] /api/auctions/dutch (Tạo Dutch auction)
  const dutchPayload = JSON.stringify({
    title: `Dutch Item ${username}`,
    start_price: 5000000,
    floor_price: 1000000,
    step_decrement: 50000,
    step_interval_seconds: 5,
    start_time: startTime,
    end_time: endTime,
  });
  const createDutchRes = http.post(`${BASE_URL}/auctions/dutch`, dutchPayload, {
    headers: authHeaders(token),
  });
  rt_dutch_create.add(createDutchRes.timings.duration);

  if (createDutchRes.status === 201) {
    const dutchId = createDutchRes.json('result.auction_id');

    // 11. [GET] /api/auctions/{id}/price (Tính giá thời gian thực)
    const priceRes = http.get(`${BASE_URL}/auctions/${dutchId}/price`);
    rt_dutch_price.add(priceRes.timings.duration);
    check(priceRes, { 'Price 200': (r) => r.status === 200 });

    // 12. [POST] /api/auctions/{id}/buy-dutch (Mua ngay)
    if (buyerToken) {
      const buyRes = http.post(`${BASE_URL}/auctions/${dutchId}/buy-dutch`, null, {
        headers: authHeaders(buyerToken),
      });
      rt_dutch_buy.add(buyRes.timings.duration);
      check(buyRes, { 'Buy Dutch OK/Conflict': (r) => r.status === 200 || r.status === 409 });
    }
  }

  sleep(0.5);
}
"""

with open("/kaggle/working/full_suite_per_api.js", "w") as f:
    f.write(k6_script)

print("Đã ghi thành công /kaggle/working/full_suite_per_api.js")