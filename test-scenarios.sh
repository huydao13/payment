#!/bin/bash
# Test các scenario SAGA bằng curl
# Dùng: ./test-scenarios.sh [scenario]
# scenario: success | payment-fail | inventory-fail | delivery-fail | idempotency | all

ORCHESTRATOR="http://localhost:8080/api/saga"
PAYMENT="http://localhost:8082/api/payments"

reset_state() {
  echo "🔄 Reset trạng thái hệ thống..."
  curl -s -X DELETE "$ORCHESTRATOR/reset" > /dev/null
  curl -s -X POST "$PAYMENT/reset" > /dev/null
}

run_saga() {
  local failAt=$1
  local body="{\"userId\":\"user-001\",\"productId\":\"product-A\",\"quantity\":1,\"amount\":300000"
  if [ -n "$failAt" ]; then
    body="$body,\"simulateFailAt\":\"$failAt\""
  fi
  body="$body}"

  echo "📤 POST /api/saga/start"
  echo "   Body: $body"
  echo ""
  curl -s -X POST "$ORCHESTRATOR/start" \
    -H "Content-Type: application/json" \
    -d "$body" | python3 -m json.tool
}

case "$1" in
  success)
    echo "═══ Scenario: HAPPY PATH ═══"
    reset_state
    run_saga ""
    ;;
  payment-fail)
    echo "═══ Scenario: PAYMENT FAIL → Compensate cancelOrder ═══"
    reset_state
    run_saga "PAYMENT"
    ;;
  inventory-fail)
    echo "═══ Scenario: INVENTORY FAIL → Compensate refund + cancelOrder ═══"
    reset_state
    run_saga "INVENTORY"
    ;;
  idempotency)
    echo "═══ Scenario: IDEMPOTENCY — gọi charge 3 lần cùng key ═══"
    reset_state
    SAGA_ID="test-idempotency-$(date +%s)"
    for i in 1 2 3; do
      echo "--- Lần gọi $i ---"
      curl -s -X POST "$PAYMENT/charge" \
        -H "Content-Type: application/json" \
        -d "{\"sagaId\":\"$SAGA_ID\",\"userId\":\"user-001\",\"amount\":300000}" \
        | python3 -m json.tool
      echo ""
    done
    echo "👉 Check balance — chỉ nên trừ 1 lần 300,000đ:"
    curl -s "$PAYMENT/balance" | python3 -m json.tool
    ;;
  all)
    $0 success
    echo -e "\n\n"
    $0 payment-fail
    echo -e "\n\n"
    $0 inventory-fail
    echo -e "\n\n"
    $0 idempotency
    ;;
  *)
    echo "Dùng: $0 [success|payment-fail|inventory-fail|idempotency|all]"
    ;;
esac
