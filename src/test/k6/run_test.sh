set +e

echo "=================================================="
echo "1/4. CHẠY MỨC 20 VUs (1 PHÚT)"
echo "=================================================="
k6 run -e VUS=20 -e DURATION=1m \
       --summary-trend-stats="min,avg,med,p(90),p(95),p(99),max" \
       --summary-export=/kaggle/working/summary_20.json \
       /kaggle/working/full_suite_per_api.js

echo ">>> Nghỉ 10s giải phóng connection pool..."
sleep 10

echo "=================================================="
echo "2/4. CHẠY MỨC 100 VUs (1 PHÚT)"
echo "=================================================="
k6 run -e VUS=100 -e DURATION=1m \
       --summary-trend-stats="min,avg,med,p(90),p(95),p(99),max" \
       --summary-export=/kaggle/working/summary_100.json \
       /kaggle/working/full_suite_per_api.js

echo ">>> Nghỉ 10s giải phóng connection pool..."
sleep 10

echo "=================================================="
echo "3/4. CHẠY MỨC 500 VUs (1 PHÚT)"
echo "=================================================="
k6 run -e VUS=500 -e DURATION=1m \
       --summary-trend-stats="min,avg,med,p(90),p(95),p(99),max" \
       --summary-export=/kaggle/working/summary_500.json \
       /kaggle/working/full_suite_per_api.js

echo ">>> Nghỉ 10s giải phóng connection pool..."
sleep 10

echo "=================================================="
echo "4/4. CHẠY MỨC 1000 VUs (1 PHÚT)"
echo "=================================================="
k6 run -e VUS=1000 -e DURATION=1m \
       --summary-trend-stats="min,avg,med,p(90),p(95),p(99),max" \
       --summary-export=/kaggle/working/summary_1000.json \
       /kaggle/working/full_suite_per_api.js

echo "HOÀN THÀNH TOÀN BỘ 4 MỐC KIỂM THỬ!"