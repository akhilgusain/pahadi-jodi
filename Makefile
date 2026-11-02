SHELL := /bin/bash
-include .env
export

.PHONY: up down build test run-identity run-profile run-gateway web
up:            ## start Postgres, Redis, Kafka, Elasticsearch
	docker compose up -d --wait
down:
	docker compose down
build:         ## compile + test all Java modules
	mvn -q install
test:
	mvn test
run-identity:  ## :8081
	mvn -q -pl services/identity-service spring-boot:run
run-profile:   ## :8082
	mvn -q -pl services/profile-service spring-boot:run
run-gateway:   ## :8080 (single entry point for web/mobile)
	mvn -q -pl services/api-gateway spring-boot:run
web:           ## :5173
	cd apps/web && npm install && npm run dev
