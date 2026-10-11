.PHONY: build test docker-build deploy undeploy port-forward load-test logs help

# ── Build ──────────────────────────────────────────────────────────────────────

build:
	./gradlew shadowJar -x test

test:
	./gradlew test

# ── Docker ─────────────────────────────────────────────────────────────────────

docker-build: build
	docker build -t finatom/account-service:latest account-service/
	docker build -t finatom/payment-service:latest payment-service/
	docker build -t finatom/antifraud-service:latest antifraud-service/
	docker build -t finatom/notification-service:latest notification-service/

# ── Kubernetes ─────────────────────────────────────────────────────────────────

deploy:
	kubectl apply -f k8s/

undeploy:
	kubectl delete -f k8s/

restart:
	kubectl rollout restart deployment -n finatom

status:
	kubectl get pods -n finatom

# ── Local access ───────────────────────────────────────────────────────────────

port-forward:
	kubectl port-forward -n finatom svc/account-service 8081:8081 &
	kubectl port-forward -n finatom svc/payment-service 8082:8082 &
	@echo "account-service → http://localhost:8081"
	@echo "payment-service → http://localhost:8082"
	@echo "grafana         → http://localhost:3000 (LoadBalancer, no port-forward needed)"
	@echo "prometheus      → http://localhost:9090 (LoadBalancer, no port-forward needed)"

# ── Load testing ───────────────────────────────────────────────────────────────

load-test:
	docker run --rm -i \
		-e ACCOUNT_SVC=http://host.docker.internal:8081 \
		-e PAYMENT_SVC=http://host.docker.internal:8082 \
		-v $(PWD)/k6:/k6 \
		grafana/k6 run /k6/load-test.js
	@echo "Requires: make port-forward to be running first"

# ── Logs ───────────────────────────────────────────────────────────────────────

logs-account:
	kubectl logs -n finatom deployment/account-service -f

logs-payment:
	kubectl logs -n finatom deployment/payment-service -f

logs-antifraud:
	kubectl logs -n finatom deployment/antifraud-service -f

# ── Help ───────────────────────────────────────────────────────────────────────

help:
	@echo "make build          — build all JARs"
	@echo "make test           — run unit + integration tests"
	@echo "make docker-build   — build all Docker images"
	@echo "make deploy         — apply k8s manifests"
	@echo "make undeploy       — delete k8s resources"
	@echo "make restart        — rolling restart all deployments"
	@echo "make status         — show pod status"
	@echo "make port-forward   — expose services locally"
	@echo "make load-test      — run k6 load test via Docker"
	@echo "make logs-payment   — tail payment-service logs"
