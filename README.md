# salePro

salePro 是一个从零实现的高并发售票后端示例，面向演唱会、赛事等需要选座抢票的场景。项目演示了从节目查询、库存预热、验证码和限流，到 Redis 原子锁座、RocketMQ 异步创单、超时关单及支付确认的完整链路。

> 当前版本用于学习、作品展示和二次开发。支付渠道使用模拟适配器，不可直接用于生产交易。仓库提供 JMeter 脚本，但尚未在统一硬件和参数下完成 1000 QPS 基准测试，因此不宣称具体吞吐量。

## 核心能力

- 节目检索与详情查询
- MySQL 座位库存预热至 Redis
- Redis Lua 一次性校验并锁定多个座位，避免部分扣减和超卖
- 锁座失败、消息发送失败和创单失败时按订单号补偿库存
- RocketMQ 异步创建订单，通过唯一订单号与数据库约束保证幂等
- RocketMQ 延迟消息关闭未支付订单并释放座位
- 支付成功后以条件更新确认订单，并将座位标记为已售
- Caffeine + Redis 两级节目缓存
- Redisson 布隆过滤器防缓存穿透，分布式锁防热点缓存击穿
- 网关 Redis Lua 固定窗口限流
- 一次性验证码与网关内部一次性票据，应用直连时仍会执行验证码校验
- MOCK、ALIPAY、WECHAT 支付策略及工厂路由
- Flyway 数据库版本管理、Docker Compose 本地基础设施

## 技术栈

- Java 17
- Spring Boot 3.3
- Spring Cloud Gateway 2023
- MyBatis-Plus、Flyway、MySQL 8
- Redis、Redisson、Caffeine
- Apache RocketMQ 5
- Maven Wrapper、JUnit 5、Mockito、JMeter

## 项目结构

```text
salePro
├─ sale-app/                 核心售票、订单和支付服务（8081）
├─ sale-gateway/             API 网关、限流和验证码校验（8080）
├─ deploy/rocketmq/          RocketMQ Broker 配置
├─ load-test/                JMeter 抢票链路脚本
├─ docker-compose.yml        MySQL、Redis、RocketMQ
└─ pom.xml                   Maven 聚合工程
```

## 业务流程

1. 客户端查询节目并获取验证码。
2. 管理端在开售前预热指定节目的可售座位。
3. 客户端携带验证码经网关提交选座请求。
4. 网关执行 Lua 限流，并原子消费验证码。
5. 应用通过 Lua 原子锁定全部目标座位，再发送创单消息。
6. RocketMQ 消费者幂等写入订单和订单明细，同时投递延迟关单消息。
7. 用户完成支付后，支付策略返回结果，订单和座位状态被确认。
8. 到期仍未支付的订单由延迟消息关闭，MySQL 与 Redis 座位库存同步释放。

## 快速开始

### 环境要求

- JDK 17
- Docker Desktop（或兼容 Docker Compose 的运行环境）

Maven 无需预装，仓库已包含 Maven Wrapper。

### 1. 启动基础设施

```bash
docker compose up -d
```

示例密码仅用于本地开发。可在启动前通过 `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` 等环境变量覆盖，生产环境不得沿用默认值。

### 2. 构建与测试

Windows：

```powershell
.\mvnw.cmd clean test
```

Linux/macOS：

```bash
./mvnw clean test
```

### 3. 启动服务

分别打开两个终端：

```powershell
.\mvnw.cmd -pl sale-app spring-boot:run
.\mvnw.cmd -pl sale-gateway spring-boot:run
```

网关地址为 `http://localhost:8080`，核心应用地址为 `http://localhost:8081`。

### 4. 调用示例

首次抢票前预热示例节目：

```bash
curl -X POST http://localhost:8080/api/programs/1/inventory/warm
```

获取验证码：

```bash
curl http://localhost:8080/api/captcha
```

从返回的 SVG 图片读取验证码，然后提交选座请求：

```bash
curl -X POST http://localhost:8080/api/orders/reserve \
  -H "Content-Type: application/json" \
  -H "X-Captcha-Token: 替换为token" \
  -H "X-Captcha-Code: 替换为验证码" \
  -d '{"userId":1,"programId":1,"seatIds":[1],"paymentType":"MOCK"}'
```

查询订单并支付：

```bash
curl http://localhost:8080/api/orders/替换为订单号
curl -X POST http://localhost:8080/api/orders/替换为订单号/pay
```

## 配置

主要环境变量：

- `MYSQL_URL`、`MYSQL_USER`、`MYSQL_PASSWORD`
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`
- `ROCKETMQ_NAMESERVER`
- `SALE_APP_URL`

抢票接口默认按来源 IP 每秒最多请求 10 次，可在 `sale-gateway/src/main/resources/application.yml` 中调整。RocketMQ Broker 当前使用自定义延迟级别，代码中的延迟等级 17 对应 15 分钟。

## 压测

`load-test/sale-flow.jmx` 提供基础抢票流程。执行前应：

1. 准备足量节目和座位数据并完成预热；
2. 根据机器规格调整线程数、启动时间和持续时间；
3. 将数据库、Redis、Broker 与 JVM 指标一同采集；
4. 对总请求、成功订单、已售座位及剩余库存做最终对账。

只有保存测试参数、硬件环境、JMeter 报告和库存对账结果后，才能对外描述 QPS、延迟或“零超卖”等结论。

## 生产化建议

- 接入认证授权并限制库存预热接口的管理权限
- 将模拟支付适配器替换为真实支付 SDK，并实现签名验证、回调幂等、主动查单和退款
- 为 RocketMQ 增加重试、死信告警与人工补偿平台
- 增加配置中心、服务注册发现、链路追踪和指标告警
- 对订单表进行分库分表，并按业务规模评估搜索引擎
- 使用密钥管理服务，不在仓库或镜像中保存凭据

## 作者与许可证

作者：[@728good](https://github.com/728good)

本项目为独立原创实现，使用 [MIT License](LICENSE) 开源。
