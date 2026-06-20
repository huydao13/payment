#!/bin/bash
# Chạy tất cả 5 service trong background, log ra file riêng
# Dùng: ./start-all.sh
# Dừng: ./stop-all.sh

set -e
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

echo "🔨 Build toàn bộ project..."
cd "$ROOT_DIR"
mvn clean install -DskipTests -q

echo "🚀 Khởi động các service..."

start_service() {
  local name=$1
  local dir=$2
  echo "  → $name"
  cd "$ROOT_DIR/$dir"
  nohup mvn spring-boot:run > "$LOG_DIR/$name.log" 2>&1 &
  echo $! > "$LOG_DIR/$name.pid"
  cd "$ROOT_DIR"
}

start_service "order-service" "order-service"
sleep 3
start_service "payment-service" "payment-service"
sleep 3
start_service "inventory-service" "inventory-service"
sleep 3
start_service "saga-orchestrator" "saga-orchestrator"

echo ""
echo "⏳ Đợi 15 giây để tất cả service khởi động..."
sleep 15

echo ""
echo "✅ Tất cả service đã chạy!"
echo ""
echo "📋 Swagger UI:"
echo "   Orchestrator:  http://localhost:8080/swagger-ui.html"
echo "   Order:         http://localhost:8081/swagger-ui.html"
echo "   Payment:       http://localhost:8082/swagger-ui.html"
echo "   Inventory:     http://localhost:8083/swagger-ui.html"
echo ""
echo "📄 Logs tại: $LOG_DIR/*.log"
echo "🛑 Dừng tất cả: ./stop-all.sh"
